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
import ule.jescuj00.fridgey.domain.model.ProductoParaAviso
import ule.jescuj00.fridgey.domain.model.UnidadMedida

class ProductoRepository(
    private val queries: ProductoQueries,
    private val neveraQueries: NeveraQueries,
    // Lazy for the same reason as in NeveraRepository: building the repo must
    // not force Firestore initialization; only the first SYNCED push does.
    private val remoteRepository: Lazy<NeveraRemoteRepository>,
    private val syncScope: CoroutineScope,
    // UID del usuario autenticado en el momento de crear un producto, o null
    // (neveras LOCAL sin cuenta). Función y no AuthRepository directo para no
    // acoplar este repo a la capa de auth y mantenerlo trivial de falsear en
    // tests. Se estampa SOLO al insertar; las ediciones conservan el autor.
    private val currentUid: () -> String? = { null },
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
     * Emits products whose name or category matches [query], accent-insensitive,
     * via the FTS5 index. Matching is whole-token + prefix on the last token (see
     * [buildFtsMatchQuery]). A blank [query] returns ALL products in the fridge —
     * preserving the previous behavior — by routing to the plain listing query,
     * since an empty FTS5 MATCH string is a SQLite syntax error.
     */
    fun searchProductos(neveraId: String, query: String): Flow<List<Producto>> {
        val match = buildFtsMatchQuery(query)
        val rows = if (match == null) {
            queries.selectByNevera(neveraId)
        } else {
            queries.searchInNevera(neveraId, match)
        }
        return rows
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }
    }

    // --- One-shot writes ---

    suspend fun insertProducto(producto: Producto): Unit = withContext(Dispatchers.Default) {
        // Autor del producto: se respeta el que traiga el dominio y, si no, se
        // estampa el usuario autenticado actual. Se aplica tanto a la fila local
        // como al doc remoto (vía `conAutor`), de modo que el `creadoPor` que lee
        // la Cloud Function de "producto añadido" identifica al actor del fan-out.
        val conAutor = producto.copy(creadoPor = producto.creadoPor ?: currentUid())
        queries.insert(
            id = conAutor.id,
            id_nevera = conAutor.idNevera,
            codigo_barras = conAutor.codigoBarras,
            nombre = conAutor.nombre,
            categoria = conAutor.categoria.valor,
            fecha_caducidad = conAutor.fechaCaducidad.toEpochSeconds(),
            fecha_registro = conAutor.fechaRegistro.toEpochSeconds(),
            imagen_url = conAutor.imagenUrl,
            // `cantidad` is now REAL in SQLite — SQLDelight binds it as
            // `Double` directly, so no .toLong() coercion any more.
            cantidad = conAutor.cantidad,
            dias_aviso_antes = conAutor.diasAvisoAntes.toLong(),
            unidad = conAutor.unidad.valor,
            creado_por = conAutor.creadoPor,
        )
        pushSiSynced(conAutor.idNevera) {
            remoteRepository.value.setProducto(conAutor.idNevera, conAutor.id, conAutor.toProductoDoc())
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
                    creado_por = doc.creadoPor.ifBlank { null },
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
                    creado_por = doc.creadoPor.ifBlank { null },
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

    // --- Avisos de caducidad (LOCAL, Fase 1) ---

    /**
     * Snapshot one-shot de TODOS los productos locales como [ProductoParaAviso],
     * para el barrido de avisos de caducidad. Sin filtro por usuario: incluye
     * neveras LOCAL y funciona offline / sin cuenta (los datos locales persisten
     * tras logout). El dedup nullable se mapea a `LocalDate?` con la misma
     * conversión UTC que el resto de fechas.
     */
    suspend fun getProductosParaAviso(): List<ProductoParaAviso> =
        withContext(Dispatchers.Default) {
            queries.selectAllParaAviso().executeAsList().map { row ->
                ProductoParaAviso(
                    productId = row.id,
                    neveraId = row.id_nevera,
                    nombreProducto = row.nombre,
                    fechaCaducidad = row.fecha_caducidad.toLocalDate(),
                    diasAvisoAntes = row.dias_aviso_antes.toInt(),
                    fechaCaducidadUltimoAviso = row.fecha_caducidad_ultimo_aviso?.toLocalDate(),
                )
            }
        }

    /**
     * Marca un producto como ya avisado para [fechaCaducidad]: la convierte a
     * epoch UTC-midnight (misma base que `fecha_caducidad`) y escribe SÓLO la
     * columna de dedup (no toca `updated_at`).
     */
    suspend fun marcarAvisado(productId: String, fechaCaducidad: LocalDate): Unit =
        withContext(Dispatchers.Default) {
            queries.marcarAvisado(
                fecha_caducidad_ultimo_aviso = fechaCaducidad.toEpochSeconds(),
                id = productId,
            )
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
            creadoPor = creado_por,
        )
}

/**
 * Builds a safe FTS5 MATCH expression from raw user input, or `null` when the input
 * is blank (the caller routes blank → "show all", because an empty MATCH string is a
 * SQLite syntax error).
 *
 * Each whitespace-separated token is wrapped in double quotes — with any internal
 * double quote doubled — so the token is treated as a literal string. This neutralizes
 * FTS5 operators (`*`, `:`, `^`, `AND`/`OR`/`NOT`, parentheses, …) and prevents
 * MATCH-syntax injection from user input. The LAST token additionally gets a trailing
 * `*` for prefix / as-you-type matching, e.g. `lech ent` → `"lech" "ent"*`. Tokens are
 * joined by spaces, which FTS5 treats as implicit AND.
 *
 * Behavior change vs the old `LIKE '%term%'`: this is whole-token + prefix matching,
 * NOT arbitrary mid-word substring — "che" no longer finds "Leche", but it is indexed
 * and accent-insensitive ("platano" finds "plátano"). Intentional and documented.
 */
internal fun buildFtsMatchQuery(raw: String): String? {
    val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null
    return tokens.mapIndexed { index, token ->
        val quoted = "\"" + token.replace("\"", "\"\"") + "\""
        if (index == tokens.lastIndex) "$quoted*" else quoted
    }.joinToString(separator = " ")
}
