package ule.jescuj00.fridgey.ui.screens.add_producto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.model.Producto
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Two ways to fill in the expiration date: scan it via the camera (OCR)
 * or type it by hand. Used both in the form's state (which mode is
 * active) and by the toggle UI.
 */
enum class ScanMode { Scan, Manual }

data class AddProductoUiState(
    val name: String = "",
    val categoria: Categoria = Categoria.OTROS,
    val fechaCaducidad: LocalDate = defaultExpiry(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val scanMode: ScanMode = ScanMode.Manual,
    /**
     * One-way pristine flag: starts `true`; flips to `false` the first time
     * any field is changed away from its default. Once `false`, stays
     * `false` for the lifetime of this VM instance — clearing the form
     * does NOT bring back the toggle. Drives the visibility of the
     * scan/manual toggle in the UI.
     */
    val isFormPristine: Boolean = true,
)

private fun defaultExpiry(): LocalDate =
    Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(7, DateTimeUnit.DAY)

class AddProductoViewModel(
    private val productoRepository: ProductoRepository
) : ViewModel() {

    /**
     * Captured at construction so `onFechaSelected` can compare against the
     * exact same value that seeded the state (avoids midnight-rollover edge
     * case where two `defaultExpiry()` calls could disagree).
     */
    private val initialExpiry: LocalDate = defaultExpiry()

    private val _uiState = MutableStateFlow(AddProductoUiState(fechaCaducidad = initialExpiry))
    val uiState: StateFlow<AddProductoUiState> = _uiState.asStateFlow()

    // -- Field setters: each guards `isFormPristine` against its field's default --

    fun onNameChanged(name: String) {
        _uiState.update {
            it.copy(
                name = name,
                error = null,
                // Stay pristine only if (was pristine) AND (new value still equals default).
                isFormPristine = it.isFormPristine && name.isBlank(),
            )
        }
    }

    fun onCategoriaSelected(categoria: Categoria) {
        _uiState.update {
            it.copy(
                categoria = categoria,
                isFormPristine = it.isFormPristine && categoria == Categoria.OTROS,
            )
        }
    }

    fun onFechaSelected(fecha: LocalDate) {
        _uiState.update {
            it.copy(
                fechaCaducidad = fecha,
                error = null,
                isFormPristine = it.isFormPristine && fecha == initialExpiry,
            )
        }
    }

    // -- Scan-mode toggle plumbing --

    /**
     * Updates the toggle selection. Does NOT touch `isFormPristine` — toggling
     * between Scan/Manual without entering data leaves the form pristine,
     * which is what keeps the toggle visible.
     */
    fun setScanMode(mode: ScanMode) {
        _uiState.update { it.copy(scanMode = mode) }
    }

    /**
     * Called by the NavHost when the scanner returns with a date. Always
     * marks the form dirty (regardless of whether `date` happens to equal
     * `initialExpiry`) and switches the active mode back to Manual so the
     * user can adjust the rest of the fields.
     */
    fun onScannedDateReceived(date: LocalDate) {
        _uiState.update {
            it.copy(
                fechaCaducidad = date,
                error = null,
                isFormPristine = false,
                scanMode = ScanMode.Manual,
            )
        }
    }

    // -- Save --

    @OptIn(ExperimentalUuidApi::class)
    fun onSavePressed(neveraId: String) {
        val state = _uiState.value
        val name = state.name.trim()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        if (name.isEmpty()) {
            _uiState.update { it.copy(error = "El nombre es obligatorio") }
            return
        }
        if (state.fechaCaducidad < today) {
            _uiState.update { it.copy(error = "La fecha debe ser hoy o futura") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val producto = Producto(
                    id = Uuid.random().toString(),
                    idNevera = neveraId,
                    codigoBarras = null,
                    nombre = name,
                    categoria = state.categoria,
                    fechaCaducidad = state.fechaCaducidad,
                    fechaRegistro = today,
                    imagenUrl = null
                )
                productoRepository.insertProducto(producto)
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Error al guardar")
                }
            }
        }
    }
}
