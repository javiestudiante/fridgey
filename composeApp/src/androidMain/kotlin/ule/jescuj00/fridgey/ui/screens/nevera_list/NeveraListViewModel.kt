package ule.jescuj00.fridgey.ui.screens.nevera_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.domain.model.ExpiringTodaySummary
import ule.jescuj00.fridgey.domain.model.NeveraResumen

data class NeveraListUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val neveras: List<NeveraResumen> = emptyList(),
    val expiringToday: ExpiringTodaySummary? = null,
)

class NeveraListViewModel(
    private val neveraRepository: NeveraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NeveraListUiState())
    val uiState: StateFlow<NeveraListUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    /**
     * Subscribes to the reactive home feed: the per-fridge resúmenes
     * (products / por caducar / members) AND the cross-fridge "caducan hoy"
     * summary, combined into one state. Emissions land on any Producto /
     * Nevera / colaborador change.
     */
    fun observeNeveras(usuarioId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            combine(
                neveraRepository.observeNeverasResumen(usuarioId),
                neveraRepository.observeExpiringTodaySummary(usuarioId),
            ) { neveras, expiring -> neveras to expiring }
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Error al cargar neveras")
                    }
                }
                .collect { (neveras, expiring) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            neveras = neveras,
                            expiringToday = expiring,
                            error = null,
                        )
                    }
                }
        }
    }
}
