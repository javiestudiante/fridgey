package ule.jescuj00.fridgey.ui.screens.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.data.repository.PreferenciasRepository
import ule.jescuj00.fridgey.domain.notification.NotificacionCaducidadScheduler

data class AjustesUiState(
    // Refleja la INTENCIÓN del usuario (la preferencia), no el permiso del SO.
    val avisosCaducidad: Boolean = true,
)

class AjustesViewModel(
    private val preferenciasRepository: PreferenciasRepository,
    private val scheduler: NotificacionCaducidadScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AjustesUiState())
    val uiState: StateFlow<AjustesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(avisosCaducidad = preferenciasRepository.avisosCaducidadActivados())
            }
        }
    }

    /**
     * Persiste la intención del usuario y (des)programa el motor:
     *  - ON  -> guarda true, programa el periódico (KEEP) + un barrido inmediato.
     *  - OFF -> guarda false, cancela el periódico.
     *
     * Activar pone la preferencia a true AUNQUE falte el permiso del SO: el
     * Worker ya lo gestiona (mostrar() devuelve false, no marca) y los avisos
     * arrancan solos cuando se conceda. La PETICIÓN de permiso la dispara el
     * Composable (HITO 4B), nunca el ViewModel.
     */
    fun onToggle(activado: Boolean) {
        _uiState.update { it.copy(avisosCaducidad = activado) }
        viewModelScope.launch {
            preferenciasRepository.setAvisosCaducidadActivados(activado)
            if (activado) {
                scheduler.programarComprobacionDiaria()
                scheduler.comprobarAhora()
            } else {
                scheduler.cancelar()
            }
        }
    }

    /**
     * Resultado de la petición de permiso (la LANZA el Composable, no el VM).
     * Marca que ya se solicitó y, si se concedió, dispara un barrido inmediato
     * para no esperar al ciclo diario.
     */
    fun onPermisoResultado(concedido: Boolean) {
        viewModelScope.launch {
            preferenciasRepository.setPermisoNotifSolicitado(true)
            if (concedido) scheduler.comprobarAhora()
        }
    }
}
