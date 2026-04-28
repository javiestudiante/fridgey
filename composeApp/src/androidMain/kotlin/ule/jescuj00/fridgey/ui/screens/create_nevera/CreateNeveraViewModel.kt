package ule.jescuj00.fridgey.ui.screens.create_nevera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.domain.model.OperationResult
import ule.jescuj00.fridgey.domain.usecase.CreateNeveraUseCase

data class CreateNeveraUiState(
    val name: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class CreateNeveraViewModel(
    private val createNeveraUseCase: CreateNeveraUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateNeveraUiState())
    val uiState: StateFlow<CreateNeveraUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onCreatePressed(usuarioId: String) {
        val name = _uiState.value.name.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(error = "El nombre no puede estar vacío") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = createNeveraUseCase(name, usuarioId)) {
                is OperationResult.Success ->
                    _uiState.update { it.copy(isLoading = false, success = true) }
                is OperationResult.Error ->
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }
}
