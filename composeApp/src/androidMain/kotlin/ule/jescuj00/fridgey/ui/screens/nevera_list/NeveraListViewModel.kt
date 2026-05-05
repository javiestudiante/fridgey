package ule.jescuj00.fridgey.ui.screens.nevera_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.domain.model.Nevera

data class NeveraListUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val neveras: List<Nevera> = emptyList()
)

class NeveraListViewModel(
    private val neveraRepository: NeveraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NeveraListUiState())
    val uiState: StateFlow<NeveraListUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    /**
     * Subscribes to the reactive fridge list. Emissions land on every Nevera
     * insert/update/delete and on every Producto change (the per-fridge
     * counter depends on it).
     */
    fun observeNeveras(usuarioId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            neveraRepository.observeNeverasByUsuario(usuarioId)
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Error al cargar neveras")
                    }
                }
                .collect { list ->
                    _uiState.update { it.copy(isLoading = false, neveras = list, error = null) }
                }
        }
    }
}
