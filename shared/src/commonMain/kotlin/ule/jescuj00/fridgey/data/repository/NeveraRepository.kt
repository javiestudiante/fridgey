package ule.jescuj00.fridgey.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import ule.jescuj00.fridgey.data.remote.firestore.NeveraDoc
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.database.NeveraColaboradorQueries
import ule.jescuj00.fridgey.database.NeveraQueries
import ule.jescuj00.fridgey.database.ProductoQueries
import ule.jescuj00.fridgey.database.UsuarioQueries
import ule.jescuj00.fridgey.domain.model.ExpiringTodaySummary
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.Nevera
import ule.jescuj00.fridgey.domain.model.NeveraResumen
import ule.jescuj00.fridgey.domain.model.Proveedor
import ule.jescuj00.fridgey.domain.model.Usuario
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class NeveraRepository(
    private val neveraQueries: NeveraQueries,
    private val colaboradorQueries: NeveraColaboradorQueries,
    private val productoQueries: ProductoQueries,
    private val usuarioQueries: UsuarioQueries,
    // Lazy on purpose: constructing the repository must never force Firestore
    // (Firebase) initialization — LOCAL-only code paths, and their unit
    // tests, run without it. The instance is materialized on the first
    // SYNCED push.
    private val remoteRepository: Lazy<NeveraRemoteRepository>,
    private val syncScope: CoroutineScope,
) {

    private companion object {
        /** Days-ahead window for the "por caducar" stat. Matches the design's
         *  section cut for "caduca ya" + "esta semana" (diasRestantes <= 7). */
        const val EXPIRING_SOON_DAYS = 7
    }

    /**
     * Returns all fridges the user owns or collaborates in.
     */
    suspend fun getNeverasByUsuario(usuarioId: String): List<Nevera> =
        withContext(Dispatchers.Default) {
            neveraQueries.selectByUsuario(usuarioId).executeAsList().map { row ->
                row.toDomain(
                    esPropietario = row.id_propietario == usuarioId,
                    numeroProductos = productoQueries.countByNevera(row.id)
                        .executeAsOne().toInt()
                )
            }
        }

    /**
     * Reactive variant of [getNeverasByUsuario]: emits a fresh list whenever
     * either the Nevera table OR the Producto table changes — the latter
     * matters because each fridge carries its own product count.
     *
     * The Producto-side trigger is `countAll()`, an unfiltered query whose
     * sole purpose is to invalidate on any insert/update/delete. SQLDelight
     * re-emits the Flow on table change regardless of whether the value
     * itself changed.
     */
    fun observeNeverasByUsuario(usuarioId: String): Flow<List<Nevera>> {
        val neverasFlow = neveraQueries.selectByUsuario(usuarioId)
            .asFlow()
            .mapToList(Dispatchers.Default)

        val productosTrigger = productoQueries.countAll()
            .asFlow()
            .mapToOne(Dispatchers.Default)

        return combine(neverasFlow, productosTrigger) { rows, _ ->
            rows.map { row ->
                row.toDomain(
                    esPropietario = row.id_propietario == usuarioId,
                    numeroProductos = productoQueries.countByNevera(row.id)
                        .executeAsOne().toInt()
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Reactive "Mis neveras" home feed: each fridge with its product count,
     * its "por caducar" count, and its members (owner + collaborators). Built
     * from existing data — no activity tracking. Re-emits on any Producto
     * change (via [ProductoQueries.countAll]) and on Nevera/colaborador
     * changes (the underlying `selectByUsuario` LEFT-JOINs NeveraColaborador).
     */
    fun observeNeverasResumen(usuarioId: String): Flow<List<NeveraResumen>> {
        val neverasFlow = neveraQueries.selectByUsuario(usuarioId)
            .asFlow()
            .mapToList(Dispatchers.Default)

        val productosTrigger = productoQueries.countAll()
            .asFlow()
            .mapToOne(Dispatchers.Default)

        return combine(neverasFlow, productosTrigger) { rows, _ ->
            val threshold = expiringSoonThresholdEpoch()
            rows.map { row ->
                val nevera = row.toDomain(
                    esPropietario = row.id_propietario == usuarioId,
                    numeroProductos = productoQueries.countByNevera(row.id).executeAsOne().toInt()
                )
                val expiringCount = productoQueries
                    .countExpiringByNevera(neveraId = row.id, threshold = threshold)
                    .executeAsOne().toInt()
                NeveraResumen(
                    nevera = nevera,
                    expiringCount = expiringCount,
                    miembros = membersOf(neveraId = row.id, ownerId = row.id_propietario),
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Reactive cross-fridge "caducan hoy" summary for the home banner. Emits
     * total + product names + (single) fridge name. Re-emits on any change to
     * the joined tables (Producto / Nevera / NeveraColaborador).
     */
    fun observeExpiringTodaySummary(usuarioId: String): Flow<ExpiringTodaySummary> {
        val hoy = todayEpoch()
        return productoQueries.selectExpiringTodayForUsuario(hoy = hoy, usuarioId = usuarioId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                ExpiringTodaySummary(
                    total = rows.size,
                    productNames = rows.map { it.producto_nombre },
                    // Only name / link the fridge when every product shares one.
                    neveraNombre = rows.map { it.nevera_nombre }.distinct().singleOrNull(),
                    neveraId = rows.map { it.nevera_id }.distinct().singleOrNull(),
                )
            }
            .flowOn(Dispatchers.Default)
    }

    /** Owner + collaborators of a fridge — one-shot (detail header avatars). */
    suspend fun getMiembros(neveraId: String): List<Usuario> = withContext(Dispatchers.Default) {
        val ownerId = neveraQueries.selectById(neveraId).executeAsOneOrNull()?.id_propietario
            ?: return@withContext emptyList()
        membersOf(neveraId, ownerId)
    }

    /** Owner + collaborators of a fridge (for the avatar stack / MIEMBROS). */
    private fun membersOf(neveraId: String, ownerId: String): List<Usuario> {
        val owner = usuarioQueries.selectById(ownerId).executeAsOneOrNull()?.toDomainUsuario()
        val colaboradores = colaboradorQueries.selectUsuariosByNevera(neveraId)
            .executeAsList().map { it.toDomainUsuario() }
        return listOfNotNull(owner) + colaboradores
    }

    /** Epoch-seconds of midnight-UTC of today (matches fecha_caducidad storage). */
    private fun todayEpoch(): Long =
        Clock.System.todayIn(TimeZone.currentSystemDefault())
            .atStartOfDayIn(TimeZone.UTC).epochSeconds

    /** Epoch-seconds threshold for "expires within EXPIRING_SOON_DAYS days". */
    private fun expiringSoonThresholdEpoch(): Long =
        Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(EXPIRING_SOON_DAYS, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.UTC).epochSeconds

    private fun ule.jescuj00.fridgey.database.Usuario.toDomainUsuario(): Usuario =
        Usuario(
            id = id,
            email = email,
            nombre = nombre,
            proveedor = Proveedor.fromString(proveedor),
            fotoUrl = foto_url,
        )

    suspend fun getNeveraById(neveraId: String, currentUserId: String): Nevera? =
        withContext(Dispatchers.Default) {
            neveraQueries.selectById(neveraId).executeAsOneOrNull()?.let { row ->
                row.toDomain(
                    esPropietario = row.id_propietario == currentUserId,
                    numeroProductos = productoQueries.countByNevera(row.id)
                        .executeAsOne().toInt()
                )
            }
        }

    /**
     * Creates a new fridge and returns its ID.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun createNevera(nombre: String, idPropietario: String): String =
        withContext(Dispatchers.Default) {
            val id = Uuid.random().toString()
            neveraQueries.insert(
                id = id,
                nombre = nombre,
                id_propietario = idPropietario,
                fecha_creacion = Clock.System.now().epochSeconds
            )
            id
        }

    /**
     * Renames a fridge locally and — only when the fridge is SYNCED — enqueues
     * the rename to Firestore (fire-and-forget: Firestore's offline queue plus
     * the snapshot listeners + last-write-wins guarantee convergence, so a
     * failed push must never break the local operation).
     */
    suspend fun updateNevera(neveraId: String, nuevoNombre: String): Unit =
        withContext(Dispatchers.Default) {
            neveraQueries.updateNombre(nombre = nuevoNombre, id = neveraId)
            val row = neveraQueries.selectById(neveraId).executeAsOneOrNull()
                ?: return@withContext
            when (ModoNevera.fromString(row.modo)) {
                ModoNevera.SYNCED -> syncScope.launch {
                    runCatching { remoteRepository.value.updateNombre(neveraId, nuevoNombre) }
                }
                ModoNevera.LOCAL -> Unit
            }
        }

    /**
     * Deletes a fridge and all its dependent rows. If it was SYNCED, the
     * deletion is also enqueued to Firestore (fire-and-forget).
     */
    suspend fun deleteNevera(neveraId: String): Unit = withContext(Dispatchers.Default) {
        val row = neveraQueries.selectById(neveraId).executeAsOneOrNull()
            ?: return@withContext
        // The Android driver does not enable PRAGMA foreign_keys, so the
        // schema's ON DELETE CASCADE clauses never fire at runtime; deleting
        // children explicitly avoids orphaned rows (pre-existing latent bug).
        neveraQueries.transaction {
            productoQueries.deleteByNevera(neveraId)
            colaboradorQueries.deleteAllByNevera(neveraId)
            neveraQueries.deleteById(neveraId)
        }
        when (ModoNevera.fromString(row.modo)) {
            ModoNevera.SYNCED -> syncScope.launch {
                runCatching { remoteRepository.value.deleteNevera(neveraId) }
            }
            ModoNevera.LOCAL -> Unit
        }
    }

    /**
     * Adds a collaborator to a fridge. Does NOT enforce limits — use [AddColaboradorUseCase].
     */
    suspend fun addColaborador(neveraId: String, usuarioId: String): Unit =
        withContext(Dispatchers.Default) {
            colaboradorQueries.insert(
                id_nevera = neveraId,
                id_usuario = usuarioId,
                fecha_union = Clock.System.now().epochSeconds
            )
        }

    suspend fun removeColaborador(neveraId: String, usuarioId: String): Unit =
        withContext(Dispatchers.Default) {
            colaboradorQueries.delete(id_nevera = neveraId, id_usuario = usuarioId)
        }

    /**
     * Returns collaborator users (excludes owner) for a fridge via JOIN.
     */
    suspend fun getColaboradores(neveraId: String): List<Usuario> =
        withContext(Dispatchers.Default) {
            colaboradorQueries.selectUsuariosByNevera(neveraId).executeAsList().map { row ->
                Usuario(
                    id = row.id,
                    email = row.email,
                    nombre = row.nombre,
                    proveedor = Proveedor.fromString(row.proveedor),
                    fotoUrl = row.foto_url
                )
            }
        }

    /**
     * Returns number of collaborators (excludes owner).
     */
    suspend fun getColaboradorCount(neveraId: String): Int =
        withContext(Dispatchers.Default) {
            colaboradorQueries.selectCountByNevera(neveraId).executeAsOne().toInt()
        }

    /**
     * Checks whether [usuarioId] is the owner of [neveraId].
     */
    suspend fun isOwner(neveraId: String, usuarioId: String): Boolean =
        withContext(Dispatchers.Default) {
            neveraQueries.selectById(neveraId).executeAsOneOrNull()
                ?.id_propietario == usuarioId
        }

    /**
     * Counts how many fridges a user owns.
     */
    suspend fun countNeverasByPropietario(usuarioId: String): Int =
        withContext(Dispatchers.Default) {
            neveraQueries.selectByUsuario(usuarioId).executeAsList()
                .count { it.id_propietario == usuarioId }
        }

    // --- LOCAL/SYNCED sync ---

    /**
     * Switches a fridge between [ModoNevera.LOCAL] and [ModoNevera.SYNCED].
     */
    suspend fun updateModo(neveraId: String, modo: ModoNevera): Unit =
        withContext(Dispatchers.Default) {
            neveraQueries.updateModo(modo = modo.valor, id = neveraId)
        }

    /**
     * Emits the set of fridge IDs currently in SYNCED mode. Feeds the
     * SyncManager so it can start/stop the per-fridge Firestore listeners as
     * fridges enter or leave the cloud. [distinctUntilChanged] avoids
     * re-emitting on unrelated Nevera table writes.
     */
    fun observeSyncedNeveraIds(): Flow<Set<String>> =
        neveraQueries.selectSyncedIds()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { it.toSet() }
            .distinctUntilChanged()

    /**
     * One-shot, sync-oriented view of a fridge row (or null if it does not
     * exist). Unlike [getNeveraById] it carries no derived UI fields, just
     * what the sync layer needs.
     */
    suspend fun getSyncSnapshot(neveraId: String): NeveraSyncSnapshot? =
        withContext(Dispatchers.Default) {
            neveraQueries.selectById(neveraId).executeAsOneOrNull()?.let { row ->
                NeveraSyncSnapshot(
                    id = row.id,
                    nombre = row.nombre,
                    idPropietario = row.id_propietario,
                    fechaCreacion = row.fecha_creacion,
                    modo = ModoNevera.fromString(row.modo),
                    updatedAt = row.updated_at,
                )
            }
        }

    /**
     * Applies a remote fridge snapshot with last-write-wins: the change is
     * applied if it has no server timestamp yet, if the local row has never
     * been synced, or if the remote timestamp is not older than the last one
     * applied locally ([updatedAtSeconds] >= local `updated_at`); otherwise
     * it is discarded as stale. The local `updated_at` stores the last
     * APPLIED server timestamp — local writes never touch it.
     */
    suspend fun aplicarNeveraRemota(
        neveraId: String,
        doc: NeveraDoc,
        updatedAtSeconds: Long?
    ): Unit = withContext(Dispatchers.Default) {
        // No local row → the fridge was deleted locally; do not resurrect
        // orphaned rows from a late-arriving snapshot.
        val local = neveraQueries.selectById(neveraId).executeAsOneOrNull()
            ?: return@withContext
        val localUpdatedAt = local.updated_at
        val aplicar = updatedAtSeconds == null ||
            localUpdatedAt == null ||
            updatedAtSeconds >= localUpdatedAt
        if (!aplicar) return@withContext
        val ahora = Clock.System.now().epochSeconds
        neveraQueries.transaction {
            neveraQueries.updateFromRemote(
                nombre = doc.nombre,
                id_propietario = doc.idPropietario,
                updated_at = updatedAtSeconds ?: local.updated_at,
                id = neveraId,
            )
            // Users must exist BEFORE collaborator rows (FK). proveedor =
            // "google" is a placeholder: the column is NOT NULL and the real
            // provider is only knowable for the current user (and only used
            // on their own profile); insertOrIgnore + updateProfile keeps the
            // real email/proveedor of pre-existing rows intact.
            doc.miembros.forEach { miembro ->
                usuarioQueries.insertOrIgnore(
                    id = miembro.uid,
                    email = "",
                    nombre = miembro.nombre,
                    proveedor = "google",
                    foto_url = miembro.fotoUrl,
                    fecha_creacion = ahora,
                )
                usuarioQueries.updateProfile(
                    nombre = miembro.nombre,
                    foto_url = miembro.fotoUrl,
                    id = miembro.uid,
                )
            }
            // Mirror the remote collaborator set WITHOUT resetting the
            // fecha_union of rows that survive.
            val existentes = colaboradorQueries.selectByNevera(neveraId)
                .executeAsList().map { it.id_usuario }.toSet()
            val remotos = doc.colaboradores.toSet()
            (existentes - remotos).forEach { usuarioId ->
                colaboradorQueries.delete(id_nevera = neveraId, id_usuario = usuarioId)
            }
            (remotos - existentes).forEach { usuarioId ->
                colaboradorQueries.insertOrIgnore(
                    id_nevera = neveraId,
                    id_usuario = usuarioId,
                    fecha_union = ahora,
                )
            }
        }
    }

    /**
     * Hooks a remote fridge into the local mirror: creates the row directly in
     * SYNCED mode — which makes the SyncManager attach its per-fridge listener
     * — and folds the given remote snapshot in. Used both by the invite-accept
     * flow and by the SyncManager's collection discovery (owned + collaborated
     * fridges), so the cloud follows the user onto a fresh device. Idempotent:
     * re-hooking an already-mirrored fridge only refreshes its data, which is
     * why concurrent emissions from the collection listener and the per-doc
     * listener cannot conflict (both funnel through [aplicarNeveraRemota]'s
     * last-write-wins). Products arrive via the listener, not here.
     */
    suspend fun engancharNeveraRemota(
        neveraId: String,
        doc: NeveraDoc,
        updatedAtSeconds: Long?
    ): Unit = withContext(Dispatchers.Default) {
        neveraQueries.insertOrIgnoreFromRemote(
            id = neveraId,
            nombre = doc.nombre,
            id_propietario = doc.idPropietario,
            fecha_creacion = doc.fechaCreacion,
        )
        aplicarNeveraRemota(neveraId, doc, updatedAtSeconds)
    }

    /**
     * The fridge leaves the cloud (SYNCED→LOCAL) but the owner KEEPS the data:
     * back to LOCAL mode, sync state cleared and collaborators removed (without
     * the cloud there is no collaboration). Products are kept — their stale
     * `updated_at` values are harmless once the fridge no longer syncs.
     * Used by QuitarDeNubeUseCase.
     */
    suspend fun revertirANoCompartida(neveraId: String): Unit =
        withContext(Dispatchers.Default) {
            neveraQueries.transaction {
                neveraQueries.updateModo(modo = ModoNevera.LOCAL.valor, id = neveraId)
                neveraQueries.clearSyncState(neveraId)
                colaboradorQueries.deleteAllByNevera(neveraId)
            }
        }

    /**
     * Drops every local collaborator row WITHOUT leaving the cloud: the fridge
     * stays SYNCED and keeps syncing across the owner's devices. Used by
     * DejarDeCompartirUseCase for immediate local consistency after the remote
     * `colaboradores` array is emptied; the live per-fridge listener later
     * re-confirms the same empty set (idempotent). The `modo` and the sync
     * watermark are left untouched on purpose — this is NOT a cloud exit.
     */
    suspend fun vaciarColaboradoresLocal(neveraId: String): Unit =
        withContext(Dispatchers.Default) {
            colaboradorQueries.deleteAllByNevera(neveraId)
        }

    /**
     * A collaborator lost access (the fridge was deleted remotely or their
     * permission was revoked) → the whole local copy is removed. Local-only:
     * no remote push.
     */
    suspend fun eliminarNeveraLocal(neveraId: String): Unit =
        withContext(Dispatchers.Default) {
            neveraQueries.transaction {
                productoQueries.deleteByNevera(neveraId)
                colaboradorQueries.deleteAllByNevera(neveraId)
                neveraQueries.deleteById(neveraId)
            }
        }

    // --- Row mapper ---

    private fun ule.jescuj00.fridgey.database.Nevera.toDomain(
        esPropietario: Boolean,
        numeroProductos: Int
    ): Nevera = Nevera(
        id = id,
        nombre = nombre,
        idPropietario = id_propietario,
        esPropietario = esPropietario,
        numeroProductos = numeroProductos,
        modo = ModoNevera.fromString(modo)
    )
}

/**
 * Sync-layer view of a fridge row: the raw persisted fields plus the
 * LOCAL/SYNCED mode and the last APPLIED remote server timestamp.
 */
data class NeveraSyncSnapshot(
    val id: String,
    val nombre: String,
    val idPropietario: String,
    val fechaCreacion: Long,
    val modo: ModoNevera,
    val updatedAt: Long?,
)
