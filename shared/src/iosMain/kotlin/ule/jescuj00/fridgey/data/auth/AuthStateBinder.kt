package ule.jescuj00.fridgey.data.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.domain.model.auth.AuthState
import ule.jescuj00.fridgey.domain.usecase.auth.ObserveAuthStateUseCase

/**
 * Bridges the Kotlin `Flow<AuthState>` to a callback-based API that
 * SwiftUI can subscribe to. Kotlin/Native exports raw `Flow` only as an
 * opaque type, and there is no built-in way for Swift to iterate it
 * directly — this class wraps the collection in a long-lived coroutine
 * and forwards each value through a Swift-set lambda.
 */
class AuthStateBinder(
    private val observeAuthStateUseCase: ObserveAuthStateUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    /** Starts (or restarts) collection. Calls [onValue] on the main thread
     *  for each emission, [onError] if the flow throws. */
    fun start(onValue: (AuthState) -> Unit, onError: (Throwable) -> Unit) {
        job?.cancel()
        job = scope.launch {
            observeAuthStateUseCase()
                .catch { e -> onError(e) }
                .collect { onValue(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Call from `deinit` on the Swift side to release the underlying scope. */
    fun dispose() {
        stop()
        scope.cancel()
    }
}
