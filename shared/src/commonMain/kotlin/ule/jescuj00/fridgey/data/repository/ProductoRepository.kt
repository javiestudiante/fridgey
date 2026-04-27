package ule.jescuj00.fridgey.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import ule.jescuj00.fridgey.database.ProductoQueries
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.model.Producto

class ProductoRepository(private val queries: ProductoQueries) {

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
            imagen_url = producto.imagenUrl
        )
    }

    suspend fun updateProducto(producto: Producto): Unit = withContext(Dispatchers.Default) {
        queries.update(
            nombre = producto.nombre,
            categoria = producto.categoria.valor,
            fecha_caducidad = producto.fechaCaducidad.toEpochSeconds(),
            imagen_url = producto.imagenUrl,
            id = producto.id
        )
    }

    suspend fun deleteProducto(productoId: String): Unit = withContext(Dispatchers.Default) {
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
            imagenUrl = imagen_url
        )
}
