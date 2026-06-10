package ule.jescuj00.fridgey.ui.screens.unirse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.domain.model.ResultadoInvitacion
import ule.jescuj00.fridgey.domain.usecase.AceptarInvitacionUseCase

data class UnirseUiState(
    val codigo: String = "",
    /** Cámara abierta escaneando un QR. */
    val escaneando: Boolean = false,
    /** Aceptación en curso (lecturas + batch contra el servidor). */
    val validando: Boolean = false,
    /** Resultado de la última aceptación; null mientras no se ha intentado. */
    val resultado: ResultadoInvitacion? = null,
)

class UnirseViewModel(
    private val aceptarInvitacionUseCase: AceptarInvitacionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnirseUiState())
    val uiState: StateFlow<UnirseUiState> = _uiState.asStateFlow()

    fun onCodigoChange(valor: String) {
        // El alfabeto del código no tiene minúsculas; normalizamos al teclear.
        _uiState.update { it.copy(codigo = valor.uppercase(), resultado = null) }
    }

    fun empezarEscaneo() {
        _uiState.update { it.copy(escaneando = true, resultado = null) }
    }

    fun cancelarEscaneo() {
        _uiState.update { it.copy(escaneando = false) }
    }

    /** Primer QR leído por la cámara: cerrar el escáner y aceptar directamente. */
    fun onQrDetectado(rawValue: String, currentUserId: String) {
        if (_uiState.value.validando) return  // un frame tardío no debe re-entrar
        _uiState.update { it.copy(codigo = rawValue.uppercase(), escaneando = false) }
        unirse(currentUserId)
    }

    /**
     * Acepta la invitación con el código actual. Guard anti doble-tap aquí y
     * aceptación idempotente en el use case: re-intentos no duplican ni fallan.
     */
    fun unirse(currentUserId: String) {
        val codigo = _uiState.value.codigo
        if (codigo.isBlank() || _uiState.value.validando) return
        viewModelScope.launch {
            _uiState.update { it.copy(validando = true, resultado = null) }
            val resultado = aceptarInvitacionUseCase(codigo, currentUserId)
            _uiState.update { it.copy(validando = false, resultado = resultado) }
        }
    }
}
