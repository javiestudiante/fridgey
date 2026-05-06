package ule.jescuj00.fridgey.ui.scanner

import androidx.camera.core.ImageProxy
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Rule
import ule.jescuj00.fridgey.domain.scanner.DateScanResult
import ule.jescuj00.fridgey.domain.usecase.ScanExpirationDateUseCase
import ule.jescuj00.fridgey.test.MainDispatcherRule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DateScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scanUseCase: ScanExpirationDateUseCase = mockk(relaxed = true)
    private val fakeAnalyzer = FakeFrameAnalyzer()

    private fun newVm(
        analyzer: FrameAnalyzer = fakeAnalyzer,
    ): DateScannerViewModel = DateScannerViewModel(
        scanUseCase = scanUseCase,
        analyzerFactory = { analyzer },
    )

    @AfterTest
    fun stopFakeAnalyzer() {
        // No-op for FakeFrameAnalyzer, but kept symmetric in case future
        // fakes hold real resources.
    }

    // ---- 1. Initial state ----

    @Test
    fun initialState_isRequestingPermission() = runTest {
        val vm = newVm()
        assertEquals(ScannerUiState.RequestingPermission, vm.uiState.value)
    }

    // ---- 2-4. Permission outcomes ----

    @Test
    fun onPermissionResult_granted_transitionsToScanning() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()
        assertEquals(ScannerUiState.Scanning, vm.uiState.value)
    }

    @Test
    fun onPermissionResult_deniedCanAskAgain_transitionsToPermissionDeniedTrue() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = false, canAskAgain = true)
        advanceUntilIdle()
        assertEquals(ScannerUiState.PermissionDenied(canAskAgain = true), vm.uiState.value)
    }

    @Test
    fun onPermissionResult_deniedCannotAskAgain_transitionsToPermissionDeniedFalse() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = false, canAskAgain = false)
        advanceUntilIdle()
        assertEquals(ScannerUiState.PermissionDenied(canAskAgain = false), vm.uiState.value)
    }

    // ---- 5-6. Analyzer result mapping ----

    @Test
    fun analyzerEmitsSuccess_stateBecomesDatesDetectedSingleton() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        val date = LocalDate(2026, 5, 15)
        fakeAnalyzer.push(DateScanResult.Success(date))
        advanceUntilIdle()

        assertEquals(
            ScannerUiState.DatesDetected(date = date, stabilityProgress = 1f / 3f),
            vm.uiState.value
        )
    }

    @Test
    fun analyzerEmitsMultiple_stateBecomesDatesDetectedAll() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        val d1 = LocalDate(2026, 5, 15)
        val d2 = LocalDate(2026, 6, 1)
        fakeAnalyzer.push(DateScanResult.MultipleDatesFound(listOf(d1, d2)))
        advanceUntilIdle()

        // After the reduce-to-latest step the singleton is `d2`.
        assertEquals(
            ScannerUiState.DatesDetected(date = d2, stabilityProgress = 1f / 3f),
            vm.uiState.value
        )
    }

    // ---- Pre-grant lifecycle ----

    @Test
    fun analyzerPushBeforeGrant_isReplayedAfterGrant() = runTest {
        val vm = newVm()
        val date = LocalDate(2026, 5, 15)

        // Pre-grant push: in production this can't happen because the analyzer
        // doesn't exist yet, but we lock in the SharedFlow replay-1 contract:
        // whatever value is current at grant time will be processed.
        fakeAnalyzer.push(DateScanResult.Success(date))
        advanceUntilIdle()
        assertEquals(ScannerUiState.RequestingPermission, vm.uiState.value)

        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        assertEquals(
            ScannerUiState.DatesDetected(date = date, stabilityProgress = 1f / 3f),
            vm.uiState.value
        )
    }

    // ---- 7. Two identical Success in a row → both reach the VM ----

    @Test
    fun analyzerEmitsSameSuccessTwice_progressesStability() = runTest {
        val vm = newVm()
        val emissions = mutableListOf<ScannerUiState>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.toList(emissions)
        }

        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        val date = LocalDate(2026, 5, 15)
        fakeAnalyzer.push(DateScanResult.Success(date))
        advanceUntilIdle()
        fakeAnalyzer.push(DateScanResult.Success(date))   // identical
        advanceUntilIdle()

        collector.cancel()

        // Both Success emissions reach the VM (SharedFlow doesn't deduplicate),
        // each producing a *distinct* DatesDetected with a higher progress.
        // The transition list locks in: 1/3 first, then 2/3 — and nothing else.
        assertEquals(
            listOf(
                ScannerUiState.RequestingPermission,
                ScannerUiState.Scanning,
                ScannerUiState.DatesDetected(date = date, stabilityProgress = 1f / 3f),
                ScannerUiState.DatesDetected(date = date, stabilityProgress = 2f / 3f),
            ),
            emissions
        )
    }

    // ---- 8-9. One-shot events ----

    @Test
    fun onConfirmDate_emitsDatePickedEvent_uiStateUnchanged() = runTest {
        val vm = newVm()
        // Move past the initial state so we can detect "unchanged".
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()
        val stateBefore = vm.uiState.value

        val captured = mutableListOf<ScannerEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.toList(captured)
        }

        val date = LocalDate(2026, 5, 15)
        vm.onConfirmDate(date)
        advanceUntilIdle()

        collector.cancel()

        assertEquals(listOf<ScannerEvent>(ScannerEvent.DatePicked(date)), captured)
        assertEquals(stateBefore, vm.uiState.value)
    }

    @Test
    fun onManualEntry_emitsManualEntryRequestedEvent() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        val captured = mutableListOf<ScannerEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.toList(captured)
        }

        vm.onManualEntry()
        advanceUntilIdle()

        collector.cancel()

        assertEquals(listOf<ScannerEvent>(ScannerEvent.ManualEntryRequested), captured)
    }

    // ---- New: stability auto-confirm tests ----

    @Test
    fun threeConsecutiveSameDate_emitsDatePicked() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        val captured = mutableListOf<ScannerEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.toList(captured)
        }

        val date = LocalDate(2026, 5, 15)
        repeat(3) {
            fakeAnalyzer.push(DateScanResult.Success(date))
            advanceUntilIdle()
        }

        collector.cancel()

        assertEquals(listOf<ScannerEvent>(ScannerEvent.DatePicked(date)), captured)
    }

    @Test
    fun twoConsecutiveSameThenDifferent_doesNotAutoConfirm() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        val captured = mutableListOf<ScannerEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.toList(captured)
        }

        val d1 = LocalDate(2026, 5, 15)
        val d2 = LocalDate(2026, 6, 1)
        fakeAnalyzer.push(DateScanResult.Success(d1))
        advanceUntilIdle()
        fakeAnalyzer.push(DateScanResult.Success(d1))
        advanceUntilIdle()
        fakeAnalyzer.push(DateScanResult.Success(d2))   // resets the counter
        advanceUntilIdle()

        collector.cancel()

        assertTrue(captured.isEmpty(), "expected no events, got $captured")
    }

    @Test
    fun stabilityProgressIncrements() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        val date = LocalDate(2026, 5, 15)

        fakeAnalyzer.push(DateScanResult.Success(date))
        advanceUntilIdle()
        assertEquals(
            1f / 3f,
            (vm.uiState.value as ScannerUiState.DatesDetected).stabilityProgress
        )

        fakeAnalyzer.push(DateScanResult.Success(date))
        advanceUntilIdle()
        assertEquals(
            2f / 3f,
            (vm.uiState.value as ScannerUiState.DatesDetected).stabilityProgress
        )

        // Subscribe before the third push so we capture the auto-confirm.
        val captured = mutableListOf<ScannerEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.toList(captured)
        }
        fakeAnalyzer.push(DateScanResult.Success(date))
        advanceUntilIdle()
        collector.cancel()

        assertEquals(listOf<ScannerEvent>(ScannerEvent.DatePicked(date)), captured)
    }

    @Test
    fun multipleDatesFound_reducesToLatest() = runTest {
        val vm = newVm()
        vm.onPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        val older = LocalDate(2026, 5, 15)
        val newer = LocalDate(2026, 6, 1)
        fakeAnalyzer.push(DateScanResult.MultipleDatesFound(listOf(older, newer)))
        advanceUntilIdle()

        val state = vm.uiState.value as ScannerUiState.DatesDetected
        assertEquals(newer, state.date)
    }

    // ---- Sanity check on the fake itself ----

    @Test
    fun fakeAnalyzer_pushUpdatesResultsFlow() = runTest {
        // SharedFlow has no `.value`; the replay buffer is the test-side
        // equivalent. Empty before any push, contains the latest after.
        assertEquals(null, fakeAnalyzer.results.replayCache.firstOrNull())
        fakeAnalyzer.push(DateScanResult.NoDateFound("raw"))
        assertTrue(fakeAnalyzer.results.replayCache.firstOrNull() is DateScanResult.NoDateFound)
    }
}

// =============================================================================
// Test double
// =============================================================================

/**
 * In-memory [FrameAnalyzer]. Tests call [push] to drive emissions; `analyze`
 * is unused at the VM level (the VM only consumes [results]).
 */
private class FakeFrameAnalyzer : FrameAnalyzer {
    private val _results = MutableSharedFlow<DateScanResult?>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val results: SharedFlow<DateScanResult?> = _results.asSharedFlow()

    fun push(result: DateScanResult?) {
        _results.tryEmit(result)
    }

    override fun analyze(image: ImageProxy) {
        // VM tests don't drive frames through the analyzer, but the contract
        // still requires every accepted ImageProxy to be closed.
        image.close()
    }
}
