package ule.jescuj00.fridgey.data.binders

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.domain.model.Nevera

/**
 * Bridges the Kotlin `Flow<List<Nevera>>` for a given user to a
 * callback-based API SwiftUI can subscribe to. Same pattern as
 * [ule.jescuj00.fridgey.data.binders.ProductoListBinder] — Kotlin/Native
 * does not expose `Flow<T>` to Swift in a usable form, so we wrap the
 * collection in a long-lived coroutine and forward each value through
 * a Swift-set lambda.
 */
class NeveraListBinder(
    private val neveraRepository: NeveraRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    /** Starts (or restarts) collection for [usuarioId]. */
    fun start(
        usuarioId: String,
        onValue: (List<Nevera>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        job?.cancel()
        job = scope.launch {
            neveraRepository.observeNeverasByUsuario(usuarioId)
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
