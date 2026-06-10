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
import ule.jescuj00.fridgey.domain.usecase.ShareNeveraUseCase
import ule.jescuj00.fridgey.domain.usecase.UnshareNeveraUseCase

data class NeveraDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val productos: List<Producto> = emptyList(),
    val neveraNombre: String = "",
    val miembros: List<Usuario> = emptyList(),
    // --- compartir ---
    val esPropietario: Boolean = false,
    val modo: ModoNevera = ModoNevera.LOCAL,
    /** Transición LOCAL→SHARED en curso. */
    val compartiendo: Boolean = false,
    /** Transición SHARED→LOCAL en curso (espera ack del servidor → spinner). */
    val dejandoDeCompartir: Boolean = false,
    val errorCompartir: String? = null,
)

class NeveraDetailViewModel(
    private val productoRepository: ProductoRepository,
    private val neveraRepository: NeveraRepository,
    private val shareNeveraUseCase: ShareNeveraUseCase,
    private val unshareNeveraUseCase: UnshareNeveraUseCase,
) : ViewModel() {

    private companion object {
        /** El unshare espera el ack del servidor; sin conexión cortamos aquí. */
        const val UNSHARE_TIMEOUT_MS = 15_000L
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

    // --- Compartir (LOCAL ↔ SHARED) ---

    /** Transición LOCAL→SHARED. Optimista: encola el upload y voltea el modo. */
    fun hacerColaborativa() {
        val nevId = neveraId ?: return
        val uid = currentUserId ?: return
        if (_uiState.value.compartiendo) return  // guard anti doble-tap
        viewModelScope.launch {
            _uiState.update { it.copy(compartiendo = true, errorCompartir = null) }
            when (val result = shareNeveraUseCase(nevId, uid)) {
                is OperationResult.Success -> refreshNevera()
                is OperationResult.Error ->
                    _uiState.update { it.copy(errorCompartir = result.message) }
            }
            _uiState.update { it.copy(compartiendo = false) }
        }
    }

    /**
     * Transición SHARED→LOCAL. Estricta: espera el ack del servidor (es una
     * revocación de acceso), de ahí el spinner y el timeout — si se agota, la
     * pausa del sync se levanta igualmente (finally NonCancellable en el use
     * case) y, si el borrado encolado llegara a completarse más tarde, el
     * propio listener reconcilia (el dueño revierte a LOCAL conservando datos).
     */
    fun dejarDeCompartir() {
        val nevId = neveraId ?: return
        val uid = currentUserId ?: return
        if (_uiState.value.dejandoDeCompartir) return  // guard anti doble-tap
        viewModelScope.launch {
            _uiState.update { it.copy(dejandoDeCompartir = true, errorCompartir = null) }
            val result = withTimeoutOrNull(UNSHARE_TIMEOUT_MS) {
                unshareNeveraUseCase(nevId, uid)
            }
            when (result) {
                null -> _uiState.update {
                    it.copy(
                        errorCompartir = "Sin conexión con el servidor. " +
                            "Dejar de compartir requiere conexión; inténtalo de nuevo."
                    )
                }
                is OperationResult.Success -> refreshNevera()
                is OperationResult.Error ->
                    _uiState.update { it.copy(errorCompartir = result.message) }
            }
            _uiState.update { it.copy(dejandoDeCompartir = false) }
        }
    }

    fun limpiarErrorCompartir() {
        _uiState.update { it.copy(errorCompartir = null) }
    }

    /** Re-lee nombre / miembros / modo / ownership tras cargar o transicionar. */
    private suspend fun refreshNevera() {
        val nevId = neveraId ?: return
        val uid = currentUserId ?: return
        try {
            val nevera = neveraRepository.getNeveraById(nevId, uid)
            val miembros = neveraRepository.getMiembros(nevId)
            _uiState.update {
                it.copy(
                    neveraNombre = nevera?.nombre.orEmpty(),
                    miembros = miembros,
                    esPropietario = nevera?.esPropietario ?: false,
                    modo = nevera?.modo ?: ModoNevera.LOCAL,
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message) }
        }
    }
}
