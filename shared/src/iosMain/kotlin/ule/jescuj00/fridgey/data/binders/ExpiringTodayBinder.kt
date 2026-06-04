package ule.jescuj00.fridgey.data.binders

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.domain.model.ExpiringTodaySummary

/**
 * Bridges the cross-fridge `Flow<ExpiringTodaySummary>` ("caducan hoy" home
 * banner) to a Swift callback. Same pattern as [NeveraListBinder].
 */
class ExpiringTodayBinder(
    private val neveraRepository: NeveraRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    fun start(
        usuarioId: String,
        onValue: (ExpiringTodaySummary) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        job?.cancel()
        job = scope.launch {
            neveraRepository.observeExpiringTodaySummary(usuarioId)
                .catch { e -> onError(e) }
                .collect { onValue(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}
