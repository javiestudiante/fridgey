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
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.domain.model.Producto

data class NeveraDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val productos: List<Producto> = emptyList(),
    val neveraNombre: String = ""
)

class NeveraDetailViewModel(
    private val productoRepository: ProductoRepository,
    private val neveraRepository: NeveraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NeveraDetailUiState())
    val uiState: StateFlow<NeveraDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun loadProducts(neveraId: String, currentUserId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            try {
                val nevera = neveraRepository.getNeveraById(neveraId, currentUserId)
                _uiState.update { it.copy(neveraNombre = nevera?.nombre.orEmpty()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }

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
}
