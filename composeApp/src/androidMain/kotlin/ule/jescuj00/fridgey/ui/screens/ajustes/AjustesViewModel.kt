package ule.jescuj00.fridgey.ui.screens.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.data.repository.PreferenciasRepository
import ule.jescuj00.fridgey.domain.model.ModoAnadirProducto
import ule.jescuj00.fridgey.domain.notification.NotificacionCaducidadScheduler

data class AjustesUiState(
    // Refleja la INTENCIÓN del usuario (la preferencia), no el permiso del SO.
    val avisosCaducidad: Boolean = true,
    // Modo con el que el botón "+" de una nevera abre el alta de producto.
    val modoAnadir: ModoAnadirProducto = ModoAnadirProducto.DEFAULT,
)

class AjustesViewModel(
    private val preferenciasRepository: PreferenciasRepository,
    private val scheduler: NotificacionCaducidadScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AjustesUiState())
    val uiState: StateFlow<AjustesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val avisos = preferenciasRepository.avisosCaducidadActivados()
            val modo = preferenciasRepository.modoAnadirProducto()
            _uiState.update { it.copy(avisosCaducidad = avisos, modoAnadir = modo) }
        }
    }

    /**
     * Persiste el modo de añadido por defecto del botón "+". Preferencia LOCAL,
     * sin efectos secundarios (no programa nada): solo guarda la elección.
     */
    fun onModoAnadirSeleccionado(modo: ModoAnadirProducto) {
        _uiState.update { it.copy(modoAnadir = modo) }
        viewModelScope.launch {
            preferenciasRepository.setModoAnadirProducto(modo)
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
