package ule.jescuj00.fridgey.ui.screens.nevera_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.OperationResult
import ule.jescuj00.fridgey.domain.model.Producto
import ule.jescuj00.fridgey.domain.model.Usuario
import ule.jescuj00.fridgey.domain.usecase.BorrarNeveraUseCase
import ule.jescuj00.fridgey.domain.usecase.DejarDeCompartirUseCase
import ule.jescuj00.fridgey.domain.usecase.ExpulsarColaboradorUseCase
import ule.jescuj00.fridgey.domain.usecase.QuitarDeNubeUseCase
import ule.jescuj00.fridgey.domain.usecase.SalirDeNeveraUseCase
import ule.jescuj00.fridgey.domain.usecase.SubirANubeUseCase

data class NeveraDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val productos: List<Producto> = emptyList(),
    /** Texto de búsqueda FTS5 en curso (vacío = lista completa de la nevera). */
    val query: String = "",
    val neveraNombre: String = "",
    val miembros: List<Usuario> = emptyList(),
    // --- ejes nube/colaboración ---
    val esPropietario: Boolean = false,
    /** uid del propietario — distingue su fila en la hoja de miembros. */
    val idPropietario: String = "",
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
    // --- borrar / salir / expulsar (gestión de miembros) ---
    /** "Borrar nevera" (dueño) o "Salir de la nevera" (colaborador) en curso. */
    val borrandoOSaliendo: Boolean = false,
    /** Error del diálogo de confirmación de borrar/salir. */
    val errorBorrado: String? = null,
    /** La nevera ya no existe en este dispositivo (borrada o salida): volver atrás. */
    val neveraCerrada: Boolean = false,
    /** uid del colaborador cuya expulsión está en curso (spinner por fila). */
    val expulsandoUid: String? = null,
    /** Error de la hoja de miembros (expulsión fallida). */
    val errorMiembros: String? = null,
)

class NeveraDetailViewModel(
    private val productoRepository: ProductoRepository,
    private val neveraRepository: NeveraRepository,
    private val subirANubeUseCase: SubirANubeUseCase,
    private val dejarDeCompartirUseCase: DejarDeCompartirUseCase,
    private val quitarDeNubeUseCase: QuitarDeNubeUseCase,
    private val borrarNeveraUseCase: BorrarNeveraUseCase,
    private val salirDeNeveraUseCase: SalirDeNeveraUseCase,
    private val expulsarColaboradorUseCase: ExpulsarColaboradorUseCase,
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

    /** Fuente reactiva del texto de búsqueda; alimenta el flatMapLatest de [loadProducts]. */
    private val queryFlow = MutableStateFlow("")

    private var observeJob: Job? = null
    private var neveraId: String? = null
    private var currentUserId: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadProducts(neveraId: String, currentUserId: String) {
        this.neveraId = neveraId
        this.currentUserId = currentUserId
        queryFlow.value = ""  // reentrada limpia: cada carga arranca sin búsqueda activa
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            refreshNevera()

            // Mirror NeveraListViewModel.observeNeveras / iOS ProductoListBinder:
            // a failure in the underlying SQLDelight Flow must surface as an
            // error UI state, never escape the coroutine and crash the process.
            //
            // Se reutiliza la búsqueda FTS5 ya testeada (ProductoFtsSearchTest): con
            // query en blanco, searchProductos enruta a selectByNevera (lista completa,
            // mismo Flow reactivo y mismo orden que antes); con texto, al índice FTS5.
            // flatMapLatest cancela la consulta anterior en cada pulsación → filtrado
            // en tiempo real.
            queryFlow
                .flatMapLatest { q -> productoRepository.searchProductos(neveraId, q) }
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

    /** Actualiza el texto de búsqueda; el flujo de [loadProducts] re-filtra en vivo. */
    fun onQueryChange(q: String) {
        queryFlow.value = q
        _uiState.update { it.copy(query = q) }
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

    // --- Borrar / salir / expulsar (gestión de miembros) ---

    /**
     * Acción del diálogo de confirmación: el DUEÑO borra la nevera (casos
     * 1-3, mismo use case — el aviso dinámico es de UI) o el COLABORADOR sale
     * de ella (caso 4). Síncrona contra servidor en los casos con nube →
     * spinner + timeout (los use cases reanudan el sync con NonCancellable si
     * el timeout cancela). Con éxito se marca [NeveraDetailUiState.neveraCerrada]
     * y la pantalla navega de vuelta a "Mis neveras".
     */
    fun borrarOSalir() {
        val nevId = neveraId ?: return
        val uid = currentUserId ?: return
        if (_uiState.value.borrandoOSaliendo) return  // guard anti doble-tap
        viewModelScope.launch {
            _uiState.update { it.copy(borrandoOSaliendo = true, errorBorrado = null) }
            val esPropietario = _uiState.value.esPropietario
            val result = withTimeoutOrNull(TRANSITION_TIMEOUT_MS) {
                if (esPropietario) {
                    borrarNeveraUseCase(nevId, uid)
                } else {
                    salirDeNeveraUseCase(nevId, uid)
                }
            }
            val accion = if (esPropietario) "borrar la nevera" else "salir de la nevera"
            when (result) {
                null -> _uiState.update {
                    it.copy(
                        errorBorrado = "Sin conexión con el servidor. " +
                            "Para $accion necesitas conexión; inténtalo de nuevo."
                    )
                }
                is OperationResult.Success ->
                    _uiState.update { it.copy(neveraCerrada = true) }
                is OperationResult.Error ->
                    _uiState.update { it.copy(errorBorrado = result.message) }
            }
            _uiState.update { it.copy(borrandoOSaliendo = false) }
        }
    }

    fun limpiarErrorBorrado() {
        _uiState.update { it.copy(errorBorrado = null) }
    }

    /**
     * El DUEÑO expulsa a un colaborador concreto desde la hoja de miembros.
     * Spinner por fila vía [NeveraDetailUiState.expulsandoUid]; la hoja sigue
     * abierta y se refresca con el conjunto resultante.
     */
    fun expulsarColaborador(colaboradorId: String) {
        val nevId = neveraId ?: return
        val uid = currentUserId ?: return
        if (_uiState.value.expulsandoUid != null) return  // una expulsión a la vez
        viewModelScope.launch {
            _uiState.update { it.copy(expulsandoUid = colaboradorId, errorMiembros = null) }
            val result = withTimeoutOrNull(TRANSITION_TIMEOUT_MS) {
                expulsarColaboradorUseCase(nevId, uid, colaboradorId)
            }
            when (result) {
                null -> _uiState.update {
                    it.copy(
                        errorMiembros = "Sin conexión con el servidor. " +
                            "Para expulsar necesitas conexión; inténtalo de nuevo."
                    )
                }
                is OperationResult.Success -> refreshNevera()
                is OperationResult.Error ->
                    _uiState.update { it.copy(errorMiembros = result.message) }
            }
            _uiState.update { it.copy(expulsandoUid = null) }
        }
    }

    fun limpiarErrorMiembros() {
        _uiState.update { it.copy(errorMiembros = null) }
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
                    idPropietario = nevera?.idPropietario.orEmpty(),
                    modo = nevera?.modo ?: ModoNevera.LOCAL,
                    tieneColaboradores = numColaboradores > 0,
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message) }
        }
    }
}
