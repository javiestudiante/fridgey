package ule.jescuj00.fridgey.data.binders

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.domain.model.Producto

/**
 * Bridges the Kotlin `Flow<List<Producto>>` for a given fridge to a
 * callback-based API SwiftUI can subscribe to. Same pattern as
 * [ule.jescuj00.fridgey.data.auth.AuthStateBinder] — Kotlin/Native does
 * not expose `Flow<T>` to Swift in a usable form, so we wrap the
 * collection in a long-lived coroutine and forward each value through
 * a Swift-set lambda.
 *
 * The list is driven by [ProductoRepository.searchProductos]: a blank query
 * routes to `selectByNevera` (the full list, same reactive SQLDelight Flow as
 * before), a non-blank query to the FTS5 index. Swift feeds the text via
 * [setQuery]; `flatMapLatest` re-runs the search on each change. Mirrors the
 * Android `queryFlow.flatMapLatest { searchProductos(...) }` wiring.
 */
class ProductoListBinder(
    private val productoRepository: ProductoRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    /** Texto de búsqueda reactivo; en blanco = lista completa de la nevera. */
    private val queryFlow = MutableStateFlow("")

    /** Starts (or restarts) collection for [neveraId], from a blank query. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(
        neveraId: String,
        onValue: (List<Producto>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        job?.cancel()
        queryFlow.value = ""  // reentrada limpia: cada arranque empieza sin búsqueda
        job = scope.launch {
            queryFlow
                .flatMapLatest { q -> productoRepository.searchProductos(neveraId, q) }
                .catch { e -> onError(e) }
                .collect { onValue(it) }
        }
    }

    /** Swift actualiza el texto en cada pulsación; la lista se re-filtra en vivo. */
    fun setQuery(query: String) {
        queryFlow.value = query
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Call from Swift `deinit` to release the underlying scope. */
    fun dispose() {
        stop()
        scope.cancel()
    }
}
