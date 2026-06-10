package ule.jescuj00.fridgey.ui.screens.invitar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.domain.model.OperationResult
import ule.jescuj00.fridgey.domain.usecase.GenerarInvitacionUseCase

/**
 * Estados de la pantalla de invitación. Sellado: la pantalla los cubre con
 * un `when` exhaustivo.
 */
sealed interface InvitarUiState {
    data object Generando : InvitarUiState
    data class Generada(val codigo: String, val expiraEnMillis: Long) : InvitarUiState
    data class Error(val mensaje: String) : InvitarUiState
}

class InvitarViewModel(
    private val generarInvitacionUseCase: GenerarInvitacionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InvitarUiState>(InvitarUiState.Generando)
    val uiState: StateFlow<InvitarUiState> = _uiState.asStateFlow()

    private var generando = false

    /** Genera (o re-genera) un código. Cada llamada emite un código nuevo. */
    fun generar(neveraId: String, currentUserId: String) {
        if (generando) return  // guard anti doble-tap
        generando = true
        _uiState.value = InvitarUiState.Generando
        viewModelScope.launch {
            _uiState.value = when (val result = generarInvitacionUseCase(neveraId, currentUserId)) {
                is OperationResult.Success -> InvitarUiState.Generada(
                    codigo = result.data.codigo,
                    expiraEnMillis = result.data.expiraEnMillis,
                )
                is OperationResult.Error -> InvitarUiState.Error(result.message)
            }
            generando = false
        }
    }
}
