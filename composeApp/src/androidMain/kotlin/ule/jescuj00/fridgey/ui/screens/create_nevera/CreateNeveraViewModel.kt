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
import ule.jescuj00.fridgey.domain.usecase.SubirANubeUseCase

data class CreateNeveraUiState(
    val name: String = "",
    /** Toggle "Guardar en mi cuenta". APAGADO por defecto (privacidad por defecto). */
    val guardarEnCuenta: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    /**
     * Aviso NO bloqueante: la nevera se creó en LOCAL pero la subida a la nube
     * falló. Se informa y, al confirmar, se navega de vuelta igualmente.
     */
    val uploadWarning: String? = null,
    val success: Boolean = false
)

class CreateNeveraViewModel(
    private val createNeveraUseCase: CreateNeveraUseCase,
    private val subirANubeUseCase: SubirANubeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateNeveraUiState())
    val uiState: StateFlow<CreateNeveraUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onGuardarEnCuentaChanged(value: Boolean) {
        _uiState.update { it.copy(guardarEnCuenta = value) }
    }

    fun onCreatePressed(usuarioId: String) {
        val name = _uiState.value.name.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(error = "El nombre no puede estar vacío") }
            return
        }
        val guardar = _uiState.value.guardarEnCuenta
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = createNeveraUseCase(name, usuarioId)) {
                is OperationResult.Error ->
                    _uiState.update { it.copy(isLoading = false, error = result.message) }

                is OperationResult.Success -> {
                    // La nevera nace LOCAL. Solo si el toggle está activo se
                    // encadena la subida a la nube (crear LOCAL → SubirANube).
                    if (guardar) {
                        subirNuevaNevera(neveraId = result.data, usuarioId = usuarioId)
                    } else {
                        _uiState.update { it.copy(isLoading = false, success = true) }
                    }
                }
            }
        }
    }

    /**
     * Sube a la nube una nevera recién creada. SubirANube ya trae su timeout +
     * compensación (borra el doc remoto huérfano si falla). Un fallo NO revierte
     * la creación: la nevera queda en LOCAL y se informa con un aviso no
     * bloqueante; el camino feliz navega de vuelta ya SYNCED.
     */
    private suspend fun subirNuevaNevera(neveraId: String, usuarioId: String) {
        when (val upload = subirANubeUseCase(neveraId, usuarioId)) {
            is OperationResult.Success ->
                _uiState.update { it.copy(isLoading = false, success = true) }

            is OperationResult.Error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        uploadWarning = "Tu nevera se ha creado, pero no se pudo guardar " +
                            "en tu cuenta ahora. Podrás guardarla más tarde desde sus opciones."
                    )
                }
        }
    }

    /** El usuario confirma el aviso de subida fallida → volvemos a la lista. */
    fun onUploadWarningAcknowledged() {
        _uiState.update { it.copy(uploadWarning = null, success = true) }
    }
}
