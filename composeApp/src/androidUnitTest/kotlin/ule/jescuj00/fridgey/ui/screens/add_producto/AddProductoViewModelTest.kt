package ule.jescuj00.fridgey.ui.screens.add_producto

import io.mockk.mockk
import kotlinx.datetime.LocalDate
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.domain.model.Categoria
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * No coroutine rule needed: the methods exercised here (`onNameChanged`,
 * `setScanMode`, `onScannedDateReceived`) are synchronous state updates.
 * `onSavePressed` (which uses `viewModelScope.launch`) is not tested in
 * this file.
 */
class AddProductoViewModelTest {

    private val repo: ProductoRepository = mockk(relaxed = true)

    private fun newVm(): AddProductoViewModel = AddProductoViewModel(repo)

    @Test
    fun initialState_formIsPristine_scanModeIsManual() {
        val vm = newVm()
        val state = vm.uiState.value
        assertTrue(state.isFormPristine, "fresh form should be pristine")
        assertEquals(ScanMode.Manual, state.scanMode)
    }

    @Test
    fun onNameChange_nonBlank_marksFormDirty() {
        val vm = newVm()
        vm.onNameChanged("Yogur")
        assertFalse(vm.uiState.value.isFormPristine)
        assertEquals("Yogur", vm.uiState.value.name)
    }

    @Test
    fun onNameChange_blankAfterNonBlank_staysDirty() {
        val vm = newVm()
        vm.onNameChanged("Yogur")
        assertFalse(vm.uiState.value.isFormPristine)

        vm.onNameChanged("")
        // One-way gate: clearing the field does NOT bring back pristine.
        assertFalse(vm.uiState.value.isFormPristine)
        assertEquals("", vm.uiState.value.name)
    }

    @Test
    fun onScannedDateReceived_setsDateAndMarksDirty_andSwitchesToManualMode() {
        val vm = newVm()
        val scannedDate = LocalDate(2026, 12, 31)

        vm.onScannedDateReceived(scannedDate)

        val state = vm.uiState.value
        assertEquals(scannedDate, state.fechaCaducidad)
        assertFalse(state.isFormPristine, "scanner-set date must mark form dirty")
        assertEquals(ScanMode.Manual, state.scanMode)
    }

    @Test
    fun setScanMode_doesNotAffectPristine() {
        val vm = newVm()
        // Initial state is pristine + Manual.
        assertTrue(vm.uiState.value.isFormPristine)

        // Toggling to Scan and back to Manual should not flip pristine.
        vm.setScanMode(ScanMode.Scan)
        assertTrue(vm.uiState.value.isFormPristine)
        assertEquals(ScanMode.Scan, vm.uiState.value.scanMode)

        vm.setScanMode(ScanMode.Manual)
        assertTrue(vm.uiState.value.isFormPristine)
        assertEquals(ScanMode.Manual, vm.uiState.value.scanMode)
    }

    // Bonus: lock in that selecting the default category is treated as
    // "no real change" by the pristine check (defends against accidentally
    // tightening the rule to "any setter call dirties the form").
    @Test
    fun onCategoriaSelected_defaultCategory_keepsPristine() {
        val vm = newVm()
        vm.onCategoriaSelected(Categoria.OTROS)
        assertTrue(vm.uiState.value.isFormPristine)
    }

    @Test
    fun onCategoriaSelected_nonDefaultCategory_marksDirty() {
        val vm = newVm()
        vm.onCategoriaSelected(Categoria.LACTEOS)
        assertFalse(vm.uiState.value.isFormPristine)
    }
}
