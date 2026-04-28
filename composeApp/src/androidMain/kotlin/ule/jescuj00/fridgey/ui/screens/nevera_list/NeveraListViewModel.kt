package ule.jescuj00.fridgey.ui.screens.nevera_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun loadNeveras(usuarioId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val list = neveraRepository.getNeverasByUsuario(usuarioId)
                _uiState.update { it.copy(isLoading = false, neveras = list) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Error al cargar neveras")
                }
            }
        }
    }
}
