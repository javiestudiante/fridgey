package ule.jescuj00.fridgey.data.remote.firestore

import dev.gitlive.firebase.firestore.ChangeType
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/** Remote-driven change to a nevera document. */
sealed interface RemoteNeveraEvent {
    data class Actualizada(val doc: NeveraDoc, val updatedAtSeconds: Long?) : RemoteNeveraEvent
    data object Eliminada : RemoteNeveraEvent
}

/** Remote-driven change to one producto in the subcollection. */
sealed interface RemoteProductoChange {
    data class Upsert(val productoId: String, val doc: ProductoDoc, val updatedAtSeconds: Long?) : RemoteProductoChange
    data class Eliminado(val productoId: String) : RemoteProductoChange
}

/**
 * A fridge surfaced by the collection-level discovery query (owned or
 * collaborated). Carries enough to hook it into the local mirror.
 */
data class NeveraDescubierta(
    val neveraId: String,
    val doc: NeveraDoc,
    val updatedAtSeconds: Long?,
)

/**
 * Marcador de expulsión que SOLO el DUEÑO estampa en su escritura de baja de
 * miembros (expulsar a uno / dejar de compartir). Lo lee la Cloud Function de
 * "colaborador eliminado" para distinguir una EXPULSIÓN (la provoca el dueño,
 * hay que avisar también al expulsado) de una AUTO-SALIDA (la provoca el propio
 * colaborador, que no escribe marcador alguno — la regla `esAutoSalidaValida`
 * solo le deja tocar colaboradores/miembros/updatedAt).
 *
 * La función lo trata como expulsión SOLO si, en el mismo evento, [objetivos]
 * coincide con los uids retirados en el diff Y el timestamp del marcador
 * coincide con el `updatedAt` del write (ambos `serverTimestamp` resueltos en la
 * MISMA escritura atómica → idénticos). Así un marcador viejo que quede en el
 * doc no puede contaminar una auto-salida posterior del mismo uid.
 *
 * @property actorUid  el dueño que ejecuta la baja.
 * @property objetivos los uids retirados de `colaboradores` en este write.
 */
data class MarcadorExpulsion(
    val actorUid: String,
    val objetivos: List<String>,
)

/**
 * Single point of contact with Firestore for collaborative fridges
 * (`neveras/{id}` + its `productos` subcollection). Lives in commonMain on
 * top of the GitLive SDK, so the same code drives Android and iOS.
 *
 * Writes ride Firestore's internal offline queue (offline-first); conflicts
 * resolve last-write-wins via the `updatedAt` server timestamp. The
 * `observe*` listeners are the remote source of truth that the SyncManager
 * folds into SQLDelight.
 *
 * Suspend functions deliberately let exceptions propagate — the caller
 * decides how to retry/surface failures.
 */
class NeveraRemoteRepository(private val firestore: FirebaseFirestore) {

    private companion object {
        /** Firestore caps batches at 500 ops; 400 leaves comfortable margin. */
        const val MAX_BATCH_OPS = 400
    }

    private fun neveraRef(neveraId: String): DocumentReference =
        firestore.collection(COLECCION_NEVERAS).document(neveraId)

    private fun productosRef(neveraId: String): CollectionReference =
        neveraRef(neveraId).collection(COLECCION_PRODUCTOS)

    private fun invitacionRef(codigo: String): DocumentReference =
        firestore.collection(COLECCION_INVITACIONES).document(codigo)

    /**
     * Uploads a whole fridge (LOCAL→SYNCED transition), preserving the local
     * IDs given as map keys so domain references stay valid.
     */
    suspend fun uploadNevera(neveraId: String, nevera: NeveraDoc, productos: Map<String, ProductoDoc>) {
        // The nevera doc must be acked FIRST: the productos security rules
        // get() the parent nevera doc, and a get() inside a batch sees the
        // pre-batch server state — bundling nevera + productos in one batch
        // would make the rules deny every producto write. So: set the parent,
        // await its ack, then batch the children.
        neveraRef(neveraId).set(nevera.copy(updatedAt = Timestamp.ServerTimestamp))

        productos.entries.chunked(MAX_BATCH_OPS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (productoId, doc) ->
                batch.set(
                    productosRef(neveraId).document(productoId),
                    doc.copy(updatedAt = Timestamp.ServerTimestamp),
                )
            }
            batch.commit()
        }
    }

    /** Creates or overwrites one producto, stamping the server timestamp. */
    suspend fun setProducto(neveraId: String, productoId: String, producto: ProductoDoc) {
        productosRef(neveraId).document(productoId)
            .set(producto.copy(updatedAt = Timestamp.ServerTimestamp))
    }

    /** Deletes one producto document. */
    suspend fun deleteProducto(neveraId: String, productoId: String) {
        productosRef(neveraId).document(productoId).delete()
    }

    /**
     * Partial update of the fridge name. Field-level update on purpose: a
     * full set() would clobber `colaboradores`/`miembros`, which other
     * members may be mutating concurrently.
     */
    suspend fun updateNombre(neveraId: String, nombre: String) {
        neveraRef(neveraId).update(
            "nombre" to nombre,
            "updatedAt" to FieldValue.serverTimestamp,
        )
    }

    /**
     * Rewrites the membership arrays (`colaboradores` + `miembros`) of a
     * fridge that STAYS in the cloud (the doc is NOT deleted). This is the
     * single engine behind every "remove member(s)" operation:
     *  - DejarDeCompartirUseCase → empty list + owner-only `miembros`;
     *  - SalirDeNeveraUseCase   → current arrays minus the leaver's own uid
     *    (allowed to a non-owner by the esAutoSalidaValida rule);
     *  - ExpulsarColaboradorUseCase → current arrays minus the expelled uid
     *    (owner-only full update rights).
     *
     * Field-level update so the rest of the doc (nombre, fechaCreacion) is
     * untouched; the new server timestamp lets every member's listener
     * converge. Whole-array writes on purpose — arrayRemove on `miembros`
     * would require exact equality of the denormalized map (fragile), and the
     * self-leave rule validates the full diff anyway.
     *
     * Members removed from `colaboradores` lose access on their own devices:
     * their per-fridge listener hits PERMISSION_DENIED.
     *
     * The miembros travel as plain maps (same rationale as
     * [aceptarInvitacion]: identical wire shape on both platforms).
     *
     * [expulsion] != null SOLO en escrituras del dueño (expulsar / dejar de
     * compartir): añade el marcador `ultimoEventoColab` + su sello
     * `ultimoEventoColabAt`. Ambos `serverTimestamp` de ESTE update resuelven al
     * mismo instante, así que `ultimoEventoColabAt == updatedAt` le prueba a la
     * Cloud Function que el marcador pertenece a este evento (ver
     * [MarcadorExpulsion]). El dueño tiene `allow update` completo, por lo que
     * estos campos extra no chocan con ninguna regla; la auto-salida pasa
     * expulsion=null y no toca campos fuera del whitelist de `esAutoSalidaValida`.
     * El marcador NO está en [NeveraDoc] a propósito: el decoder de GitLive
     * ignora claves desconocidas, así que el cliente lo round-tripea sin verlo y
     * solo lo consume la función (admin SDK, sin esquema).
     */
    suspend fun actualizarMiembros(
        neveraId: String,
        colaboradores: List<String>,
        miembros: List<MiembroDoc>,
        expulsion: MarcadorExpulsion? = null,
    ) {
        val campos = buildList<Pair<String, Any?>> {
            add("colaboradores" to colaboradores)
            add(
                "miembros" to miembros.map { miembro ->
                    mapOf(
                        "uid" to miembro.uid,
                        "nombre" to miembro.nombre,
                        "fotoUrl" to miembro.fotoUrl,
                    )
                }
            )
            add("updatedAt" to FieldValue.serverTimestamp)
            if (expulsion != null) {
                add(
                    "ultimoEventoColab" to mapOf(
                        "tipo" to "expulsion",
                        "actorUid" to expulsion.actorUid,
                        "objetivos" to expulsion.objetivos,
                    )
                )
                add("ultimoEventoColabAt" to FieldValue.serverTimestamp)
            }
        }
        neveraRef(neveraId).update(*campos.toTypedArray())
    }

    /**
     * Live stream of fridges the user OWNS (`idPropietario == uid`). Together
     * with [observeNeverasColaborando] this is the collection-level discovery
     * that makes a user's cloud fridges follow them onto a fresh device, where
     * the local DB is empty and the per-fridge listeners have nothing to attach
     * to yet. Each emission is the full current result set; the SyncManager
     * hooks every entry via the idempotent `engancharNeveraRemota`.
     *
     * Single-field equality → automatic index, no composite (no Terraform).
     */
    fun observeNeverasPropias(uid: String): Flow<List<NeveraDescubierta>> =
        firestore.collection(COLECCION_NEVERAS)
            .where { "idPropietario" equalTo uid }
            .snapshots
            .map { it.toDescubiertas() }

    /**
     * Live stream of fridges the user COLLABORATES in (`uid` in the
     * `colaboradores` array). Closes the same multi-device gap as
     * [observeNeverasPropias] for the collaborator side (today a collaborator
     * only gets a fridge via the explicit accept flow). array-contains →
     * automatic index, no composite (no Terraform).
     */
    fun observeNeverasColaborando(uid: String): Flow<List<NeveraDescubierta>> =
        firestore.collection(COLECCION_NEVERAS)
            .where { "colaboradores" contains uid }
            .snapshots
            .map { it.toDescubiertas() }

    /**
     * Maps a discovery [QuerySnapshot] to the fridges to hook. Docs still
     * pending the server ack of our OWN write are skipped (their serverTimestamp
     * is unresolved); they reappear server-confirmed on the next emission. This
     * mirrors the anti-echo guard in [observeNevera].
     */
    private fun QuerySnapshot.toDescubiertas(): List<NeveraDescubierta> =
        documents.mapNotNull { snapshot ->
            if (snapshot.metadata.hasPendingWrites) {
                null
            } else {
                val doc = snapshot.data(NeveraDoc.serializer())
                NeveraDescubierta(
                    neveraId = snapshot.id,
                    doc = doc,
                    updatedAtSeconds = doc.updatedAt.epochSecondsOrNull(),
                )
            }
        }

    /**
     * Deletes a fridge and everything under it (SYNCED→LOCAL transition or
     * full removal). Firestore does not cascade subcollection deletes from
     * the client, so the productos are read and deleted explicitly — in
     * batches, and BEFORE the nevera doc: the productos rules get() the
     * parent nevera, so it must still exist for the child deletes to pass
     * (exact inverse of the [uploadNevera] ordering).
     */
    suspend fun deleteNevera(neveraId: String) {
        val productos = productosRef(neveraId).get()
        productos.documents.chunked(MAX_BATCH_OPS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit()
        }
        neveraRef(neveraId).delete()
    }

    // --- Invitaciones (UC-03) ---
    //
    // All invitation reads go Source.SERVER on purpose: an invitation must be
    // validated against the live server state, never against a stale cache —
    // and both generating and accepting one are online-by-nature operations
    // (the security rules check request.time / the parent doc on the server).

    /**
     * The fridge doc as the SERVER sees it right now, or null if it does not
     * exist there (e.g. an upload still sitting in the offline queue).
     *
     * NOTE: the rules only let members read a fridge doc — for a non-member
     * this throws PERMISSION_DENIED, which [AceptarInvitacionUseCase] uses as
     * the membership probe.
     */
    suspend fun getNevera(neveraId: String): NeveraDoc? {
        val snapshot = neveraRef(neveraId).get(Source.SERVER)
        return if (snapshot.exists) snapshot.data(NeveraDoc.serializer()) else null
    }

    /** The invitation for [codigo] as the SERVER sees it, or null. */
    suspend fun getInvitacion(codigo: String): InvitacionDoc? {
        val snapshot = invitacionRef(codigo).get(Source.SERVER)
        return if (snapshot.exists) snapshot.data(InvitacionDoc.serializer()) else null
    }

    /** Registers a new invitation (rules: only the fridge owner may). */
    suspend fun createInvitacion(codigo: String, invitacion: InvitacionDoc) {
        invitacionRef(codigo).set(invitacion)
    }

    /**
     * Accepts an invitation atomically: one batch that (a) adds [uid] to the
     * fridge's `colaboradores` plus their denormalized profile to `miembros`,
     * and (b) marks the invitation as used. Field-level updates + arrayUnion
     * on purpose:
     *  - the rules' self-join clause only allows touching exactly
     *    `colaboradores` / `miembros` / `updatedAt`;
     *  - arrayUnion appends without a prior read (a non-member cannot read
     *    the fridge doc) and is safe under concurrent accepts;
     *  - the limit (≤ 3 collaborators = 4 users with the owner), expiry and
     *    single-use are all re-validated server-side by the rules, so a
     *    PERMISSION_DENIED here means "no longer acceptable", not a bug.
     *
     * The miembro travels as a plain map: arrayUnion elements bypass the
     * kotlinx-serialization encoder, and a map keeps the wire shape identical
     * on Android today and iOS later.
     */
    suspend fun aceptarInvitacion(codigo: String, neveraId: String, uid: String, miembro: MiembroDoc) {
        val batch = firestore.batch()
        batch.update(
            neveraRef(neveraId),
            "colaboradores" to FieldValue.arrayUnion(uid),
            "miembros" to FieldValue.arrayUnion(
                mapOf(
                    "uid" to miembro.uid,
                    "nombre" to miembro.nombre,
                    "fotoUrl" to miembro.fotoUrl,
                )
            ),
            "updatedAt" to FieldValue.serverTimestamp,
        )
        batch.update(invitacionRef(codigo), "usada" to true)
        batch.commit()
    }

    /**
     * Live stream of the nevera doc. Emits [RemoteNeveraEvent.Eliminada] only
     * for server-confirmed deletions and [RemoteNeveraEvent.Actualizada] only
     * for server-confirmed states — locally-pending echoes are swallowed.
     */
    fun observeNevera(neveraId: String): Flow<RemoteNeveraEvent> =
        neveraRef(neveraId).snapshots.mapNotNull { snapshot ->
            when {
                // Only treat a missing doc as deleted when the server says so:
                // an initial cache-miss while offline also yields exists=false
                // but with isFromCache=true, and must NOT nuke local data.
                !snapshot.exists ->
                    if (!snapshot.metadata.isFromCache) RemoteNeveraEvent.Eliminada else null

                // Echo of our own write with an unresolved serverTimestamp.
                // Skip it: once the server acks, another snapshot arrives with
                // hasPendingWrites=false. This is the anti-loop guard that
                // keeps SyncManager from re-ingesting its own uploads.
                snapshot.metadata.hasPendingWrites -> null

                else -> {
                    val doc = snapshot.data(NeveraDoc.serializer())
                    RemoteNeveraEvent.Actualizada(doc, doc.updatedAt.epochSecondsOrNull())
                }
            }
        }

    /**
     * Live stream of producto changes, one list per query snapshot (the list
     * may be empty — the collector ignores it at no cost). Upserts carry the
     * resolved `updatedAt` for last-write-wins merging in SQLDelight.
     */
    fun observeProductos(neveraId: String): Flow<List<RemoteProductoChange>> =
        productosRef(neveraId).snapshots.map { snapshot ->
            snapshot.documentChanges.mapNotNull { change ->
                when (change.type) {
                    ChangeType.ADDED, ChangeType.MODIFIED ->
                        if (change.document.metadata.hasPendingWrites) {
                            // Echo of our own pending write — same anti-loop
                            // guard as observeNevera; the server-confirmed
                            // snapshot will follow.
                            null
                        } else {
                            val doc = change.document.data(ProductoDoc.serializer())
                            RemoteProductoChange.Upsert(
                                productoId = change.document.id,
                                doc = doc,
                                updatedAtSeconds = doc.updatedAt.epochSecondsOrNull(),
                            )
                        }

                    // REMOVED always passes through, even from cache: deleting
                    // locally something already gone is idempotent, while
                    // filtering by isFromCache would drop real deletions.
                    ChangeType.REMOVED -> RemoteProductoChange.Eliminado(change.document.id)
                }
            }
        }
}
