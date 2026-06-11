package ule.jescuj00.fridgey.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.remote.firestore.ProductoDoc
import ule.jescuj00.fridgey.data.remote.firestore.toProductoDoc
import ule.jescuj00.fridgey.database.NeveraQueries
import ule.jescuj00.fridgey.database.ProductoQueries
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.Producto
import ule.jescuj00.fridgey.domain.model.UnidadMedida

class ProductoRepository(
    private val queries: ProductoQueries,
    private val neveraQueries: NeveraQueries,
    // Lazy for the same reason as in NeveraRepository: building the repo must
    // not force Firestore initialization; only the first SYNCED push does.
    private val remoteRepository: Lazy<NeveraRemoteRepository>,
    private val syncScope: CoroutineScope,
) {

    // --- Reactive queries (Flow) ---

    /**
     * Emits the product list for [neveraId] whenever the table changes,
     * ordered by expiry date ascending.
     */
    fun getProductosByNevera(neveraId: String): Flow<List<Producto>> =
        queries.selectByNevera(neveraId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    /**
     * Emits products whose name or category matches [query] (case-insensitive).
     */
    fun searchProductos(neveraId: String, query: String): Flow<List<Producto>> =
        queries.searchInNevera(neveraId, "%$query%")
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    // --- One-shot writes ---

    suspend fun insertProducto(producto: Producto): Unit = withContext(Dispatchers.Default) {
        queries.insert(
            id = producto.id,
            id_nevera = producto.idNevera,
            codigo_barras = producto.codigoBarras,
            nombre = producto.nombre,
            categoria = producto.categoria.valor,
            fecha_caducidad = producto.fechaCaducidad.toEpochSeconds(),
            fecha_registro = producto.fechaRegistro.toEpochSeconds(),
            imagen_url = producto.imagenUrl,
            // `cantidad` is now REAL in SQLite — SQLDelight binds it as
            // `Double` directly, so no .toLong() coercion any more.
            cantidad = producto.cantidad,
            dias_aviso_antes = producto.diasAvisoAntes.toLong(),
            unidad = producto.unidad.valor,
        )
        pushSiSynced(producto.idNevera) {
            remoteRepository.value.setProducto(producto.idNevera, producto.id, producto.toProductoDoc())
        }
    }

    suspend fun updateProducto(producto: Producto): Unit = withContext(Dispatchers.Default) {
        queries.update(
            nombre = producto.nombre,
            categoria = producto.categoria.valor,
            fecha_caducidad = producto.fechaCaducidad.toEpochSeconds(),
            imagen_url = producto.imagenUrl,
            cantidad = producto.cantidad,
            dias_aviso_antes = producto.diasAvisoAntes.toLong(),
            unidad = producto.unidad.valor,
            id = producto.id,
        )
        pushSiSynced(producto.idNevera) {
            remoteRepository.value.setProducto(producto.idNevera, producto.id, producto.toProductoDoc())
        }
    }

    suspend fun deleteProducto(productoId: String): Unit = withContext(Dispatchers.Default) {
        // Read the row BEFORE deleting it — we need id_nevera to know whether
        // (and where) to push the deletion.
        val row = queries.selectById(productoId).executeAsOneOrNull()
            ?: return@withContext
        queries.deleteById(productoId)
        pushSiSynced(row.id_nevera) {
            remoteRepository.value.deleteProducto(row.id_nevera, productoId)
        }
    }

    /**
     * Enqueues [push] to Firestore only when the product's fridge is SYNCED
     * (LOCAL fridges never touch Firestore). Fire-and-forget: the local
     * operation never fails because of the push — Firestore queues it while
     * offline and the snapshot listeners + last-write-wins reconcile.
     */
    private fun pushSiSynced(neveraId: String, push: suspend () -> Unit) {
        val modo = neveraQueries.selectById(neveraId).executeAsOneOrNull()
            ?.modo?.let(ModoNevera::fromString) ?: ModoNevera.LOCAL
        when (modo) {
            ModoNevera.SYNCED -> syncScope.launch { runCatching { push() } }
            ModoNevera.LOCAL -> Unit
        }
    }

    // --- LOCAL/SYNCED sync ---

    /**
     * One-shot snapshot of a fridge's products, used to upload the existing
     * inventory during the LOCAL→SYNCED transition.
     */
    suspend fun getProductosByNeveraOnce(neveraId: String): List<Producto> =
        withContext(Dispatchers.Default) {
            queries.selectByNevera(neveraId).executeAsList().map { it.toDomain() }
        }

    /**
     * Applies a remote product snapshot with last-write-wins: applied if it
     * has no server timestamp yet, if the local row has never been synced, or
     * if the remote timestamp is not older than the last one applied locally
     * ([updatedAtSeconds] >= local `updated_at`); otherwise discarded as
     * stale. Guarded so that only products of an existing SYNCED fridge are
     * written — this avoids resurrecting products of fridges that were
     * deleted locally or reverted to LOCAL.
     */
    suspend fun aplicarProductoRemoto(
        productoId: String,
        neveraId: String,
        doc: ProductoDoc,
        updatedAtSeconds: Long?
    ): Unit = withContext(Dispatchers.Default) {
        val neveraRow = neveraQueries.selectById(neveraId).executeAsOneOrNull()
            ?: return@withContext
        when (ModoNevera.fromString(neveraRow.modo)) {
            ModoNevera.SYNCED -> Unit
            ModoNevera.LOCAL -> return@withContext
        }
        val existing = queries.selectById(productoId).executeAsOneOrNull()
        val localUpdatedAt = existing?.updated_at
        val aplicar = updatedAtSeconds == null ||
            localUpdatedAt == null ||
            updatedAtSeconds >= localUpdatedAt
        if (!aplicar) return@withContext
        // doc.fechaCaducidad / doc.fechaRegistro already arrive as epoch
        // seconds (Long) → they map straight to the columns, no LocalDate
        // round-trip needed.
        queries.transaction {
            if (existing == null) {
                queries.insertFromRemote(
                    id = productoId,
                    id_nevera = neveraId,
                    codigo_barras = doc.codigoBarras,
                    nombre = doc.nombre,
                    categoria = doc.categoria,
                    fecha_caducidad = doc.fechaCaducidad,
                    fecha_registro = doc.fechaRegistro,
                    imagen_url = doc.imagenUrl,
                    cantidad = doc.cantidad,
                    dias_aviso_antes = doc.diasAvisoAntes.toLong(),
                    unidad = doc.unidad,
                    updated_at = updatedAtSeconds,
                )
            } else {
                queries.updateFromRemote(
                    codigo_barras = doc.codigoBarras,
                    nombre = doc.nombre,
                    categoria = doc.categoria,
                    fecha_caducidad = doc.fechaCaducidad,
                    fecha_registro = doc.fechaRegistro,
                    imagen_url = doc.imagenUrl,
                    cantidad = doc.cantidad,
                    dias_aviso_antes = doc.diasAvisoAntes.toLong(),
                    unidad = doc.unidad,
                    updated_at = updatedAtSeconds,
                    id = productoId,
                )
            }
        }
    }

    /**
     * Deletes a product locally WITHOUT pushing to Firestore. Used by the
     * sync layer to apply remote deletions — going through [deleteProducto]
     * here would push the deletion back to Firestore (a pointless echo).
     */
    suspend fun eliminarProductoLocal(productoId: String): Unit =
        withContext(Dispatchers.Default) {
            queries.deleteById(productoId)
        }

    // --- Date conversion helpers ---

    private fun Long.toLocalDate(): LocalDate =
        Instant.fromEpochSeconds(this)
            .toLocalDateTime(TimeZone.UTC)
            .date

    private fun LocalDate.toEpochSeconds(): Long =
        atStartOfDayIn(TimeZone.UTC).epochSeconds

    // --- Row mapper ---

    private fun ule.jescuj00.fridgey.database.Producto.toDomain(): Producto =
        Producto(
            id = id,
            idNevera = id_nevera,
            codigoBarras = codigo_barras,
            nombre = nombre,
            categoria = Categoria.fromString(categoria),
            fechaCaducidad = fecha_caducidad.toLocalDate(),
            fechaRegistro = fecha_registro.toLocalDate(),
            imagenUrl = imagen_url,
            // `cantidad` comes back as `Double` from the REAL column —
            // no coercion needed. `dias_aviso_antes` is still INTEGER,
            // so SQLDelight gives us `Long` and we narrow to `Int` (no
            // real value will overflow). `unidad` is round-tripped from
            // its canonical `valor` string.
            cantidad = cantidad,
            unidad = UnidadMedida.fromString(unidad),
            diasAvisoAntes = dias_aviso_antes.toInt(),
        )
}
