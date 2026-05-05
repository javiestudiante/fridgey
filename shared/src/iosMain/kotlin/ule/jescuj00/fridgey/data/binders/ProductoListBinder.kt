package ule.jescuj00.fridgey.data.binders

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
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
 */
class ProductoListBinder(
    private val productoRepository: ProductoRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    /** Starts (or restarts) collection for [neveraId]. */
    fun start(
        neveraId: String,
        onValue: (List<Producto>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        job?.cancel()
        job = scope.launch {
            productoRepository.getProductosByNevera(neveraId)
                .catch { e -> onError(e) }
                .collect { onValue(it) }
        }
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
