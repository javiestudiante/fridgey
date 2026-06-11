package ule.jescuj00.fridgey.ui.screens.nevera_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.OperationResult
import ule.jescuj00.fridgey.domain.model.Producto
import ule.jescuj00.fridgey.domain.model.Usuario
import ule.jescuj00.fridgey.domain.usecase.DejarDeCompartirUseCase
import ule.jescuj00.fridgey.domain.usecase.QuitarDeNubeUseCase
import ule.jescuj00.fridgey.domain.usecase.SubirANubeUseCase

data class NeveraDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val productos: List<Producto> = emptyList(),
    val neveraNombre: String = "",
    val miembros: List<Usuario> = emptyList(),
    // --- ejes nube/colaboración ---
    val esPropietario: Boolean = false,
    /** Eje de persistencia: LOCAL (solo este dispositivo) o SYNCED (en la nube). */
    val modo: ModoNevera = ModoNevera.LOCAL,
    /** Eje de colaboración DERIVADO: hay al menos un colaborador (getColaboradorCount > 0). */
    val tieneColaboradores: Boolean = false,
    /** "Guardar en mi cuenta" (LOCAL→SYNCED) en curso. */
    val guardando: Boolean = false,
    /** "Dejar de compartir" (vaciar colaboradores, sigue SYNCED) en curso. */
    val dejandoDeCompartir: Boolean = false,
    /** "Quitar de mi cuenta" (SYNCED→LOCAL) en curso. */
    val quitando: Boolean = false,
    val errorCompartir: String? = null,
)

class NeveraDetailViewModel(
    private val productoRepository: ProductoRepository,
    private val neveraRepository: NeveraRepository,
    private val subirANubeUseCase: SubirANubeUseCase,
    private val dejarDeCompartirUseCase: DejarDeCompartirUseCase,
    private val quitarDeNubeUseCase: QuitarDeNubeUseCase,
) : ViewModel() {

    private companion object {
        /**
         * Tope de las transiciones que esperan al servidor. SubirANube ya se
         * autolimita dentro del use case; aquí acotamos las que no lo hacen
         * (quitar de la cuenta, dejar de compartir) para no dejar el spinner
         * colgado sin conexión.
         */
        const val TRANSITION_TIMEOUT_MS = 15_000L
    }

    private val _uiState = MutableStateFlow(NeveraDetailUiState())
    val uiState: StateFlow<NeveraDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var neveraId: String? = null
    private var currentUserId: String? = null

    fun loadProducts(neveraId: String, currentUserId: String) {
        this.neveraId = neveraId
        this.currentUserId = currentUserId
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            refreshNevera()

            // Mirror NeveraListViewModel.observeNeveras / iOS ProductoListBinder:
            // a failure in the underlying SQLDelight Flow must surface as an
            // error UI state, never escape the coroutine and crash the process.
            productoRepository.getProductosByNevera(neveraId)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Error al cargar los productos"
                        )
                    }
                }
                .collect { list ->
                    _uiState.update { it.copy(isLoading = false, productos = list, error = null) }
                }
        }
    }

    fun deleteProducto(productoId: String) {
        viewModelScope.launch {
            try {
                productoRepository.deleteProducto(productoId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al eliminar") }
            }
        }
    }

    // --- Nube + colaboración (acciones del propietario) ---

    /**
     * "Guardar en mi cuenta" (LOCAL→SYNCED): sube la nevera a la nube. Síncrona
     * contra servidor — el timeout vive DENTRO de [SubirANubeUseCase], que
     * espera el ack del doc antes de voltear el modo y devuelve error si no
     * llega. Aquí solo mostramos el spinner.
     */
    fun guardarEnMiCuenta() {
        val nevId = neveraId ?: return
        val uid = currentUserId ?: return
        if (_uiState.value.guardando) return  // guard anti doble-tap
        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, errorCompartir = null) }
            when (val result = subirANubeUseCase(nevId, uid)) {
                is OperationResult.Success -> refreshNevera()
                is OperationResult.Error ->
                    _uiState.update { it.copy(errorCompartir = result.message) }
            }
            _uiState.update { it.copy(guardando = false) }
        }
    }

    /**
     * "Dejar de compartir": vacía los colaboradores pero la nevera SIGUE en la
     * nube (sigue SYNCED) y en los dispositivos del dueño. No pausa el sync;
     * el listener vivo re-confirma el conjunto vacío. Síncrona contra servidor
     * → spinner + timeout.
     */
    fun dejarDeCompartir() {
        val nevId = neveraId ?: return
        val uid = currentUserId ?: return
        if (_uiState.value.dejandoDeCompartir) return  // guard anti doble-tap
        viewModelScope.launch {
            _uiState.update { it.copy(dejandoDeCompartir = true, errorCompartir = null) }
            val result = withTimeoutOrNull(TRANSITION_TIMEOUT_MS) {
                dejarDeCompartirUseCase(nevId, uid)
            }
            aplicarResultadoTransicion(result, sinConexion = "dejar de compartir")
            _uiState.update { it.copy(dejandoDeCompartir = false) }
        }
    }

    /**
     * "Quitar de mi cuenta" (SYNCED→LOCAL): baja la nevera de la nube y
     * conserva los datos locales. Síncrona contra servidor (revocación de
     * acceso) → spinner + timeout; el use case reanuda el sync igualmente si
     * el timeout cancela (finally NonCancellable).
     */
    fun quitarDeMiCuenta() {
        val nevId = neveraId ?: return
        val uid = currentUserId ?: return
        if (_uiState.value.quitando) return  // guard anti doble-tap
        viewModelScope.launch {
            _uiState.update { it.copy(quitando = true, errorCompartir = null) }
            val result = withTimeoutOrNull(TRANSITION_TIMEOUT_MS) {
                quitarDeNubeUseCase(nevId, uid)
            }
            aplicarResultadoTransicion(result, sinConexion = "quitar la nevera de tu cuenta")
            _uiState.update { it.copy(quitando = false) }
        }
    }

    fun limpiarErrorCompartir() {
        _uiState.update { it.copy(errorCompartir = null) }
    }

    /**
     * Aplica el resultado de una transición síncrona acotada por timeout:
     * `null` = se agotó el tiempo (sin conexión), Success = refrescar, Error =
     * mostrar el mensaje del use case.
     */
    private suspend fun aplicarResultadoTransicion(
        result: OperationResult<Unit>?,
        sinConexion: String,
    ) {
        when (result) {
            null -> _uiState.update {
                it.copy(
                    errorCompartir = "Sin conexión con el servidor. " +
                        "Para $sinConexion necesitas conexión; inténtalo de nuevo."
                )
            }
            is OperationResult.Success -> refreshNevera()
            is OperationResult.Error ->
                _uiState.update { it.copy(errorCompartir = result.message) }
        }
    }

    /**
     * Re-lee nombre / miembros / modo / ownership y el derivado
     * `tieneColaboradores` tras cargar o transicionar.
     */
    private suspend fun refreshNevera() {
        val nevId = neveraId ?: return
        val uid = currentUserId ?: return
        try {
            val nevera = neveraRepository.getNeveraById(nevId, uid)
            val miembros = neveraRepository.getMiembros(nevId)
            val numColaboradores = neveraRepository.getColaboradorCount(nevId)
            _uiState.update {
                it.copy(
                    neveraNombre = nevera?.nombre.orEmpty(),
                    miembros = miembros,
                    esPropietario = nevera?.esPropietario ?: false,
                    modo = nevera?.modo ?: ModoNevera.LOCAL,
                    tieneColaboradores = numColaboradores > 0,
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message) }
        }
    }
}
