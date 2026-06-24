package ule.jescuj00.fridgey.data.remote.firestore

import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.Serializable
import ule.jescuj00.fridgey.domain.model.Producto

/**
 * Wire-format documents for the `neveras` collection and its `productos`
 * subcollection.
 *
 * FIELD NAMES ARE FROZEN: the deployed security rules and composite indexes
 * reference them literally (`idPropietario`, `colaboradores`, `fechaCreacion`,
 * `idNevera`, `fechaCaducidad`) — renaming a property here silently breaks
 * rule checks and index-backed queries. Do not change them.
 *
 * Conventions baked into the schema:
 *  - `colaboradores` is an array of Firebase Auth UIDs and does NOT include
 *    the owner; the rules treat `idPropietario` + `colaboradores` as the
 *    write-access set.
 *  - `miembros` denormalizes the profile (uid/nombre/fotoUrl) of EVERY
 *    member, owner included, so collaborators can render names and avatars
 *    without reading `usuarios/{uid}` (whose rules only allow reading one's
 *    own doc). Eventually consistent by design: a stale nombre/foto is
 *    acceptable until the next write refreshes it.
 *  - Dates are epoch seconds at midnight UTC (same convention as the local
 *    SQLite storage); `updatedAt` is a server timestamp used for
 *    last-write-wins conflict resolution.
 *
 * Every field has a default so partially written / older documents still
 * deserialize instead of throwing.
 */

const val COLECCION_NEVERAS = "neveras"
const val COLECCION_PRODUCTOS = "productos"
const val COLECCION_INVITACIONES = "invitaciones"

/** Denormalized member profile embedded in [NeveraDoc.miembros]. */
@Serializable
data class MiembroDoc(
    val uid: String = "",
    val nombre: String = "",
    val fotoUrl: String? = null,
)

/** Document at `neveras/{neveraId}`. */
@Serializable
data class NeveraDoc(
    val nombre: String = "",
    val idPropietario: String = "",
    val colaboradores: List<String> = emptyList(),
    val miembros: List<MiembroDoc> = emptyList(),
    val fechaCreacion: Long = 0,
    val updatedAt: BaseTimestamp? = null,
)

/** Document at `neveras/{neveraId}/productos/{productoId}`. */
@Serializable
data class ProductoDoc(
    val idNevera: String = "",
    val codigoBarras: String? = null,
    val nombre: String = "",
    val categoria: String = "",
    val fechaCaducidad: Long = 0,
    val fechaRegistro: Long = 0,
    val imagenUrl: String? = null,
    val cantidad: Double = 1.0,
    val diasAvisoAntes: Int = 3,
    val unidad: String = "unidades",
    // UID de quien añadió el producto. Lo consume la Cloud Function de
    // "producto añadido" como ACTOR del fan-out (se excluye del envío). Default
    // "" para que documentos antiguos sin el campo sigan deserializando.
    val creadoPor: String = "",
    val updatedAt: BaseTimestamp? = null,
)

/**
 * Document at `invitaciones/{codigo}`. The code itself is the document id
 * (capability token: whoever holds it may read the invitation).
 *
 * `expiraEn` is epoch MILLIS — the deployed rules validate it against
 * `request.time.toMillis()` (server clock). `nombreNevera` is denormalized
 * on purpose: the invitee cannot read `neveras/{id}` before joining (the
 * rules only allow members), yet the join UI wants to show which fridge
 * they are joining.
 */
@Serializable
data class InvitacionDoc(
    val neveraId: String = "",
    val creadaPor: String = "",
    val expiraEn: Long = 0,
    val usada: Boolean = false,
    val nombreNevera: String = "",
)

/**
 * Epoch seconds of a resolved server timestamp, or null while the write is
 * still pending locally (the placeholder is not a concrete [Timestamp] yet).
 */
fun BaseTimestamp?.epochSecondsOrNull(): Long? = (this as? Timestamp)?.seconds

/**
 * Domain → wire mapping. Enums travel as their canonical `valor` string and
 * dates as midnight-UTC epoch seconds. `updatedAt` stays null here on
 * purpose: [NeveraRemoteRepository] stamps the server timestamp at write
 * time, the mapper never does.
 */
fun Producto.toProductoDoc(): ProductoDoc = ProductoDoc(
    idNevera = idNevera,
    codigoBarras = codigoBarras,
    nombre = nombre,
    categoria = categoria.valor,
    fechaCaducidad = fechaCaducidad.atStartOfDayIn(TimeZone.UTC).epochSeconds,
    fechaRegistro = fechaRegistro.atStartOfDayIn(TimeZone.UTC).epochSeconds,
    imagenUrl = imagenUrl,
    cantidad = cantidad,
    diasAvisoAntes = diasAvisoAntes,
    unidad = unidad.valor,
    creadoPor = creadoPor.orEmpty(),
    updatedAt = null,
)
