package ule.jescuj00.fridgey.ui.scanner

import androidx.camera.core.ImageProxy
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Rule
import ule.jescuj00.fridgey.data.repository.ProductLookupRepository
import ule.jescuj00.fridgey.domain.model.ProductAutoFill
import ule.jescuj00.fridgey.domain.model.ProductLookupResult
import ule.jescuj00.fridgey.domain.model.UnidadMedida
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.scanner.BarcodeResult
import ule.jescuj00.fridgey.domain.scanner.BarcodeScanner
import ule.jescuj00.fridgey.domain.scanner.DateScanResult
import ule.jescuj00.fridgey.domain.usecase.LookupProductByBarcodeUseCase
import ule.jescuj00.fridgey.domain.usecase.ScanExpirationDateUseCase
import ule.jescuj00.fridgey.test.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DateScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scanUseCase: ScanExpirationDateUseCase = mockk(relaxed = true)
    private val barcodeScanner: BarcodeScanner = mockk(relaxed = true)
    private val lookupRepo: ProductLookupRepository = mockk()
    private val lookupUseCase = LookupProductByBarcodeUseCase(lookupRepo)

    private val fakeDateAnalyzer = FakeFrameAnalyzer()
    private val fakeBarcodeAnalyzer = FakeBarcodeAnalyzer()

    private fun newVm(): DateScannerViewModel = DateScannerViewModel(
        scanUseCase = scanUseCase,
        barcodeScanner = barcodeScanner,
        lookupProduct = lookupUseCase,
        dateAnalyzerFactory = { fakeDateAnalyzer },
        barcodeAnalyzerFactory = { fakeBarcodeAnalyzer },
    )

    /** Pushes a stable barcode + a stubbed OFF lookup so the VM advances into
     *  the DATE phase, where the pre-existing date assertions apply. */
    private fun TestScope.driveToDatePhase(
        vm: DateScannerViewModel,
        lookup: ProductLookupResult = ProductLookupResult.NotFound,
    ) {
        coEvery { lookupRepo.lookup(any()) } returns lookup
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()
        repeat(3) {
            fakeBarcodeAnalyzer.push(BarcodeResult("8410000000000", "EAN_13"))
            advanceUntilIdle()
        }
        advanceUntilIdle()  // let the lookup coroutine + enterDatePhase run
    }

    // ---- Initial / permission ----

    @Test
    fun initialState_isRequestingPermission() = runTest {
        assertEquals(ScannerUiState.RequestingPermission, newVm().uiState.value)
    }

    @Test
    fun onPermissionResult_granted_startsBarcodePhase() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()
        assertEquals(ScannerUiState.ScanningBarcode(0f), vm.uiState.value)
    }

    @Test
    fun onPermissionResult_deniedCanAskAgain() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = false, canAskAgain = true)
        advanceUntilIdle()
        assertEquals(ScannerUiState.PermissionDenied(canAskAgain = true), vm.uiState.value)
    }

    @Test
    fun onPermissionResult_deniedCannotAskAgain() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = false, canAskAgain = false)
        advanceUntilIdle()
        assertEquals(ScannerUiState.PermissionDenied(canAskAgain = false), vm.uiState.value)
    }

    // ---- CODE phase ----

    @Test
    fun barcodeProgress_incrementsOnRepeats() = runTest {
        val vm = newVm()
        coEvery { lookupRepo.lookup(any()) } returns ProductLookupResult.NotFound
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        fakeBarcodeAnalyzer.push(BarcodeResult("8410000000000", "EAN_13"))
        advanceUntilIdle()
        assertEquals(
            ScannerUiState.ScanningBarcode(1f / 3f),
            vm.uiState.value,
        )

        fakeBarcodeAnalyzer.push(BarcodeResult("8410000000000", "EAN_13"))
        advanceUntilIdle()
        assertEquals(
            ScannerUiState.ScanningBarcode(2f / 3f),
            vm.uiState.value,
        )
    }

    @Test
    fun threeIdenticalBarcodes_lookupFound_entersDatePhase_andSetsAutoFill() = runTest {
        val vm = newVm()
        val found = ProductLookupResult.Found(
            ProductAutoFill(
                codigoBarras = "8410000000000",
                nombre = "Leche (Hacendado)",
                cantidad = 1.0,
                unidad = UnidadMedida.LITROS,
                imagenUrl = "https://img",
                categoria = Categoria.OTROS,
            )
        )
        driveToDatePhase(vm, lookup = found)

        assertEquals(ScannerUiState.Scanning, vm.uiState.value)
        assertEquals("Leche (Hacendado)", vm.pendingAutoFill?.nombre)
        assertEquals("8410000000000", vm.pendingAutoFill?.codigoBarras)
    }

    @Test
    fun threeIdenticalBarcodes_lookupNotFound_stillEntersDatePhase_withBarcodeOnly() = runTest {
        val vm = newVm()
        driveToDatePhase(vm, lookup = ProductLookupResult.NotFound)

        assertEquals(ScannerUiState.Scanning, vm.uiState.value)
        assertEquals("8410000000000", vm.pendingAutoFill?.codigoBarras)
        assertEquals("", vm.pendingAutoFill?.nombre)
    }

    @Test
    fun rateLimited_setsDistinctBanner_keepsBarcode_andEntersDatePhase() = runTest {
        val vm = newVm()
        driveToDatePhase(vm, lookup = ProductLookupResult.RateLimited)

        // Manual entry stays reachable (date phase opens) and the barcode is kept…
        assertEquals(ScannerUiState.Scanning, vm.uiState.value)
        assertEquals("8410000000000", vm.pendingAutoFill?.codigoBarras)
        // …but the banner is the rate-limit one, NOT "no encontrado".
        assertTrue(
            vm.productBanner.value?.contains("Demasiadas consultas") == true,
            "expected a distinct rate-limit banner, got: ${vm.productBanner.value}",
        )
    }

    // ---- DATE phase (pre-existing OCR logic, now reached via the barcode phase) ----

    @Test
    fun dateSuccess_becomesDatesDetected() = runTest {
        val vm = newVm()
        driveToDatePhase(vm)

        val date = LocalDate(2026, 5, 15)
        fakeDateAnalyzer.push(DateScanResult.Success(date))
        advanceUntilIdle()

        assertEquals(
            ScannerUiState.DatesDetected(date = date, stabilityProgress = 1f / 3f),
            vm.uiState.value,
        )
    }

    @Test
    fun multipleDatesFound_reducesToLatest() = runTest {
        val vm = newVm()
        driveToDatePhase(vm)

        val older = LocalDate(2026, 5, 15)
        val newer = LocalDate(2026, 6, 1)
        fakeDateAnalyzer.push(DateScanResult.MultipleDatesFound(listOf(older, newer)))
        advanceUntilIdle()

        assertEquals(newer, (vm.uiState.value as ScannerUiState.DatesDetected).date)
    }

    @Test
    fun threeConsecutiveSameDate_emitsDatePicked() = runTest {
        val vm = newVm()
        driveToDatePhase(vm)

        val captured = mutableListOf<ScannerEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.toList(captured)
        }

        val date = LocalDate(2026, 5, 15)
        repeat(3) {
            fakeDateAnalyzer.push(DateScanResult.Success(date))
            advanceUntilIdle()
        }
        collector.cancel()

        assertEquals(listOf<ScannerEvent>(ScannerEvent.DatePicked(date)), captured)
    }

    @Test
    fun twoSameThenDifferentDate_doesNotAutoConfirm() = runTest {
        val vm = newVm()
        driveToDatePhase(vm)

        val captured = mutableListOf<ScannerEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.toList(captured)
        }

        val d1 = LocalDate(2026, 5, 15)
        val d2 = LocalDate(2026, 6, 1)
        fakeDateAnalyzer.push(DateScanResult.Success(d1)); advanceUntilIdle()
        fakeDateAnalyzer.push(DateScanResult.Success(d1)); advanceUntilIdle()
        fakeDateAnalyzer.push(DateScanResult.Success(d2)); advanceUntilIdle()
        collector.cancel()

        assertTrue(captured.isEmpty(), "expected no events, got $captured")
    }

    // ---- One-shot events ----

    @Test
    fun onConfirmDate_emitsDatePicked() = runTest {
        val vm = newVm()
        val captured = mutableListOf<ScannerEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.toList(captured)
        }

        val date = LocalDate(2026, 5, 15)
        vm.onConfirmDate(date)
        advanceUntilIdle()
        collector.cancel()

        assertEquals(listOf<ScannerEvent>(ScannerEvent.DatePicked(date)), captured)
    }

    @Test
    fun onManualEntry_emitsManualEntryRequested() = runTest {
        val vm = newVm()
        val captured = mutableListOf<ScannerEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.toList(captured)
        }

        vm.onManualEntry()
        advanceUntilIdle()
        collector.cancel()

        assertEquals(listOf<ScannerEvent>(ScannerEvent.ManualEntryRequested), captured)
    }
}

// =============================================================================
// Test doubles
// =============================================================================

private class FakeFrameAnalyzer : FrameAnalyzer {
    private val _results = MutableSharedFlow<DateScanResult?>(
        replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val results: SharedFlow<DateScanResult?> = _results.asSharedFlow()
    fun push(result: DateScanResult?) { _results.tryEmit(result) }
    override fun analyze(image: ImageProxy) { image.close() }
}

private class FakeBarcodeAnalyzer : BarcodeFrameAnalyzer {
    private val _results = MutableSharedFlow<BarcodeResult?>(
        replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val results: SharedFlow<BarcodeResult?> = _results.asSharedFlow()
    fun push(result: BarcodeResult?) { _results.tryEmit(result) }
    override fun analyze(image: ImageProxy) { image.close() }
}
