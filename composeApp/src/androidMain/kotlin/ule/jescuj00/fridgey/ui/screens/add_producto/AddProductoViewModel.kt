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
import kotlinx.datetime.daysUntil
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.model.Producto
import ule.jescuj00.fridgey.domain.model.ProductAutoFill
import ule.jescuj00.fridgey.domain.model.UnidadMedida
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
    val cantidad: Double = 1.0,
    val unidad: UnidadMedida = UnidadMedida.UNIDADES,
    val diasAvisoAntes: Int = 3,
    // Pre-filled by the scanner's barcode/Open Food Facts phase; saved with
    // the product. Null when added by hand (no barcode scanned).
    val codigoBarras: String? = null,
    val imagenUrl: String? = null,
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
    /**
     * `true` once [AddProductoViewModel.startEdit] has loaded an existing
     * product into the form (UC-10). Drives the dynamic screen title and
     * makes `onSavePressed` route to `updateProducto` instead of
     * `insertProducto`. Stays `false` for the plain add flow.
     */
    val isEditing: Boolean = false,
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

    /**
     * The product being edited (UC-10), or `null` in the plain add flow.
     * Captured at [startEdit] and consulted at save time so the edited
     * `Producto` carries forward the original `id`, `idNevera`,
     * `fechaRegistro` AND `creadoPor` — none of which the form exposes.
     * Critical for the Firestore push: `updateProducto` serializes the whole
     * doc, so a null/blank `creadoPor` here would wipe the author in the
     * cloud and break the collaborative-notification actor exclusion.
     */
    private var editing: Producto? = null

    /**
     * Synchronous re-entry guard for [startEdit]. The actual load is async,
     * so this flips `true` immediately to ensure the one-shot fetch + prefill
     * runs exactly once even if the host recomposes (or the process is
     * restored) and calls `startEdit` again — re-loading would clobber the
     * user's in-progress edits with the stored values.
     */
    private var editStarted = false

    /**
     * Switches the form into edit mode and pre-fills it with the current
     * values of [productoId] (looked up within [neveraId]). Idempotent: the
     * load runs only the first time (see [editStarted]). A missing product
     * (deleted meanwhile) silently leaves the form in add mode.
     */
    fun startEdit(neveraId: String, productoId: String) {
        if (editStarted) return
        editStarted = true
        viewModelScope.launch {
            val producto = productoRepository.getProductosByNeveraOnce(neveraId)
                .find { it.id == productoId } ?: return@launch
            editing = producto
            _uiState.update {
                it.copy(
                    name = producto.nombre,
                    categoria = producto.categoria,
                    fechaCaducidad = producto.fechaCaducidad,
                    cantidad = producto.cantidad,
                    unidad = producto.unidad,
                    diasAvisoAntes = producto.diasAvisoAntes,
                    codigoBarras = producto.codigoBarras,
                    imagenUrl = producto.imagenUrl,
                    isEditing = true,
                    // Not pristine → hides the Escanear/A mano toggle, which
                    // makes no sense when editing an existing product.
                    isFormPristine = false,
                )
            }
        }
    }

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

    /**
     * Changing the category also re-applies that category's default unit
     * (`unidadDefault`). This is intentional and overrides any manual unit
     * the user had picked previously — we do NOT remember a per-user
     * override across category switches. The contract documented in the
     * spec: pick category → unit snaps to the suggestion → user is free
     * to override the unit afterwards.
     */
    fun onCategoriaSelected(categoria: Categoria) {
        _uiState.update {
            it.copy(
                categoria = categoria,
                unidad = categoria.unidadDefault,
                // Pristine only if BOTH the category is the default (OTROS)
                // AND the resulting unit is the default-of-OTROS (UNIDADES).
                // Because we auto-sync unidad here, the second clause is
                // implied by the first, but expressed explicitly for
                // clarity in case `unidadDefault` for OTROS ever changes.
                isFormPristine = it.isFormPristine && categoria == Categoria.OTROS,
            )
        }
    }

    /**
     * Re-anchors the expiry date. Also clamps `diasAvisoAntes` to a valid
     * range relative to the new date — see [clampAvisoAntes] for the
     * exact rule. The clamp is a derived side-effect; the pristine flag
     * still depends only on whether `fecha` equals the initial default.
     */
    fun onFechaSelected(fecha: LocalDate) {
        _uiState.update {
            val newDias = clampAvisoAntes(it.diasAvisoAntes, fecha)
            it.copy(
                fechaCaducidad = fecha,
                diasAvisoAntes = newDias,
                error = null,
                isFormPristine = it.isFormPristine && fecha == initialExpiry,
            )
        }
    }

    /**
     * Cantidad now flows as a positive `Double` to support continuous
     * units (g, ml, kg, l). Non-positive inputs are silently rejected
     * here — the UI text field keeps the user's raw typing but the VM
     * only accepts values > 0.
     */
    fun onCantidadChanged(cantidad: Double) {
        if (cantidad <= 0.0) return
        _uiState.update {
            it.copy(
                cantidad = cantidad,
                error = null,
                isFormPristine = it.isFormPristine && cantidad == 1.0,
            )
        }
    }

    /**
     * Manual override of the unit (after the auto-default from category).
     * The pristine flag is consulted against the CURRENT category's
     * `unidadDefault` so that "user just picked a category and didn't
     * touch the unit" still counts as pristine.
     */
    fun onUnidadChanged(unidad: UnidadMedida) {
        _uiState.update {
            it.copy(
                unidad = unidad,
                error = null,
                isFormPristine = it.isFormPristine && unidad == it.categoria.unidadDefault,
            )
        }
    }

    fun onDiasAvisoAntesChanged(dias: Int) {
        _uiState.update {
            it.copy(
                diasAvisoAntes = dias,
                error = null,
                isFormPristine = it.isFormPristine && dias == 3,
            )
        }
    }

    /**
     * Clamps a `diasAvisoAntes` value so it never exceeds `daysUntil(fecha)`
     * from today. Examples:
     *  - fecha today + 10 days, current aviso 3  → 3 (untouched).
     *  - fecha today + 2 days,  current aviso 7  → 2 (clamped down).
     *  - fecha today + 0 days,  current aviso 3  → 0 (warn same day).
     *  - fecha in the past (defensive),          → 0.
     *
     * Returning the new aviso lets the caller `.copy()` the state in a
     * single update without re-emitting.
     */
    private fun clampAvisoAntes(currentDias: Int, fecha: LocalDate): Int {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val maxDias = today.daysUntil(fecha).coerceAtLeast(0)
        return currentDias.coerceAtMost(maxDias)
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
    /**
     * Applies the Open Food Facts autofill resolved by the scanner's CÓDIGO
     * phase. Sets the fields directly (NOT via [onCategoriaSelected], which
     * would override `unidad` with the category default) and marks the form
     * dirty. A blank name (barcode found but no product / lookup miss) leaves
     * the current name untouched so the user can type it.
     */
    fun onScannedProductReceived(autoFill: ProductAutoFill) {
        _uiState.update {
            it.copy(
                name = autoFill.nombre.ifBlank { it.name },
                categoria = autoFill.categoria,
                cantidad = autoFill.cantidad,
                unidad = autoFill.unidad,
                codigoBarras = autoFill.codigoBarras,
                imagenUrl = autoFill.imagenUrl,
                error = null,
                isFormPristine = false,
                scanMode = ScanMode.Manual,
            )
        }
    }

    fun onScannedDateReceived(date: LocalDate) {
        _uiState.update {
            val newDias = clampAvisoAntes(it.diasAvisoAntes, date)
            it.copy(
                fechaCaducidad = date,
                diasAvisoAntes = newDias,
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
        if (state.cantidad <= 0.0) {
            _uiState.update { it.copy(error = "La cantidad debe ser mayor que cero") }
            return
        }
        if (state.diasAvisoAntes < 0) {
            _uiState.update { it.copy(error = "Los días de aviso deben ser cero o positivos") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val original = editing
                if (original != null) {
                    // EDICIÓN (UC-10): partir del producto original con `.copy`
                    // conserva id, idNevera, fechaRegistro Y creadoPor intactos
                    // (el formulario no expone ninguno) — sólo se sobrescriben
                    // los campos editables. Esto satisface el invariante: el
                    // autor original viaja entero en el push a Firestore.
                    val actualizado = original.copy(
                        nombre = name,
                        categoria = state.categoria,
                        fechaCaducidad = state.fechaCaducidad,
                        imagenUrl = state.imagenUrl,
                        cantidad = state.cantidad,
                        unidad = state.unidad,
                        diasAvisoAntes = state.diasAvisoAntes,
                    )
                    productoRepository.updateProducto(actualizado)
                } else {
                    val producto = Producto(
                        id = Uuid.random().toString(),
                        idNevera = neveraId,
                        codigoBarras = state.codigoBarras,
                        nombre = name,
                        categoria = state.categoria,
                        fechaCaducidad = state.fechaCaducidad,
                        fechaRegistro = today,
                        imagenUrl = state.imagenUrl,
                        cantidad = state.cantidad,
                        unidad = state.unidad,
                        diasAvisoAntes = state.diasAvisoAntes,
                    )
                    productoRepository.insertProducto(producto)
                }
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Error al guardar")
                }
            }
        }
    }
}
