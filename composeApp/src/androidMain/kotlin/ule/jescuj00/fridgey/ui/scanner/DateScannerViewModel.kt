package ule.jescuj00.fridgey.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import ule.jescuj00.fridgey.domain.scanner.DateScanResult
import ule.jescuj00.fridgey.domain.usecase.ScanExpirationDateUseCase

// =============================================================================
// Tunables
// =============================================================================

/**
 * Number of consecutive identical detections required to auto-confirm a
 * date. Three was chosen to match a ~1.5 s confirmation window at the
 * analyzer's 2 fps throttle (two frames feels twitchy, four feels slow).
 */
private const val STABILITY_THRESHOLD = 3

// =============================================================================
// UI state
// =============================================================================

/**
 * The screen's view-state. Note there is no `Idle`: the scanner only exists
 * once the user has navigated into it, so the very first state is always
 * [RequestingPermission].
 */
sealed class ScannerUiState {
    object RequestingPermission : ScannerUiState()
    data class PermissionDenied(val canAskAgain: Boolean) : ScannerUiState()
    object Scanning : ScannerUiState()
    /**
     * A date has been detected. [stabilityProgress] runs from `0.0` to `1.0`;
     * once it reaches `1.0` (i.e. [STABILITY_THRESHOLD] consecutive identical
     * detections) the VM emits [ScannerEvent.DatePicked] automatically.
     */
    data class DatesDetected(
        val date: LocalDate,
        val stabilityProgress: Float,
    ) : ScannerUiState()
    data class Error(val message: String) : ScannerUiState()
}

// =============================================================================
// One-shot events (navigation triggers)
// =============================================================================

sealed class ScannerEvent {
    data class DatePicked(val date: LocalDate) : ScannerEvent()
    object ManualEntryRequested : ScannerEvent()
}

// =============================================================================
// ViewModel
// =============================================================================

/**
 * Wires the camera analyzer's results into a UI state, plus exposes one-shot
 * navigation events for "user picked a date" and "user wants manual entry".
 *
 * Auto-confirmation: when the analyzer reports the same date on
 * [STABILITY_THRESHOLD] consecutive frames, the VM emits
 * [ScannerEvent.DatePicked] without waiting for the user to tap. This keeps
 * the manual `onConfirmDate` path available (the user can also confirm by
 * tapping a chip) but lets the common case finish hands-free.
 *
 * `analyzerFactory` exists so unit tests can substitute a [FrameAnalyzer]
 * fake whose `results` SharedFlow is mutable, without spinning up CameraX
 * or ML Kit. In production the default builds a real [DateScannerAnalyzer]
 * tied to [viewModelScope]. The factory is invoked **lazily on permission
 * grant** — we don't allocate ML Kit resources before they're needed.
 */
class DateScannerViewModel(
    private val scanUseCase: ScanExpirationDateUseCase,
    private val analyzerFactory: (CoroutineScope) -> FrameAnalyzer = { scope ->
        DateScannerAnalyzer(scanUseCase = scanUseCase, coroutineScope = scope)
    },
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.RequestingPermission)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    // replay = 0 so re-subscriptions don't re-trigger old navigation events;
    // extraBufferCapacity = 1 so emit() doesn't suspend if the screen hasn't
    // started collecting yet.
    private val _events = MutableSharedFlow<ScannerEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<ScannerEvent> = _events.asSharedFlow()

    private var _analyzer: FrameAnalyzer? = null
    /** Null until permission is granted. The screen reads this when state
     *  transitions to [ScannerUiState.Scanning] and attaches it to CameraX. */
    val analyzer: FrameAnalyzer?
        get() = _analyzer

    // -- Stability tracking -----------------------------------------------------

    private var consecutiveCount = 0
    private var lastStableDate: LocalDate? = null

    // -- Public API -------------------------------------------------------------

    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        if (granted) {
            ensureAnalyzerStarted()
            _uiState.value = ScannerUiState.Scanning
        } else {
            // Transition out of Scanning: any in-flight stability progress is
            // stale and would re-trigger an unrelated auto-confirm if the user
            // re-grants and lands on a different date.
            resetStability()
            _uiState.value = ScannerUiState.PermissionDenied(canAskAgain)
        }
    }

    fun onConfirmDate(date: LocalDate) {
        viewModelScope.launch {
            _events.emit(ScannerEvent.DatePicked(date))
        }
    }

    fun onManualEntry() {
        viewModelScope.launch {
            _events.emit(ScannerEvent.ManualEntryRequested)
        }
    }

    // -- Internals --------------------------------------------------------------

    /** Idempotent. Lazily builds the analyzer the first time permission is
     *  granted and starts collecting its results into the UI state.
     *  Stability counting requires non-deduplicated emissions, so the
     *  analyzer publishes via `SharedFlow`; see [FrameAnalyzer]. */
    private fun ensureAnalyzerStarted() {
        if (_analyzer != null) return
        val analyzer = analyzerFactory(viewModelScope)
        _analyzer = analyzer
        viewModelScope.launch {
            analyzer.results.collect { handleResult(it) }
        }
    }

    private fun handleResult(result: DateScanResult?) {
        when (result) {
            null -> Unit                                 // SharedFlow shouldn't emit null in practice
            is DateScanResult.Success -> onSuccessfulDetection(result.date)
            is DateScanResult.MultipleDatesFound -> {
                // Reduce to the latest valid date — the spec interprets that
                // as the expiration. The use case has already filtered to
                // valid future dates, so `max()` is meaningful.
                result.dates.maxOrNull()?.let { onSuccessfulDetection(it) }
            }
            is DateScanResult.NoDateFound -> {
                // Stay in Scanning AND keep the stability counter as it is.
                //
                // Trade-off, made deliberately: the counter is *only* reset by
                //   (a) a successful detection of a *different* date, or
                //   (b) an Error.
                // A momentary loss of detection (single blurry/empty frame) does
                // NOT reset progress. A user who dipped the camera for one frame
                // shouldn't have to start counting from zero again.
                //
                // Visible consequence: if the user moves the camera away from a
                // date while the chip already shows that date, the chip stays
                // frozen on the previous detection until the next *different*
                // success or an error arrives. That is intended — re-pointing at
                // the same date will resume counting toward auto-confirm.
            }
            is DateScanResult.Error -> {
                resetStability()
                _uiState.value = ScannerUiState.Error(result.message)
            }
        }
    }

    /**
     * Updates stability tracking for a new successful detection of [date],
     * publishes the progress to the UI, and emits the auto-confirm event
     * when the threshold is reached.
     */
    private fun onSuccessfulDetection(date: LocalDate) {
        if (date == lastStableDate) {
            consecutiveCount++
        } else {
            consecutiveCount = 1
            lastStableDate = date
        }

        val progress = (consecutiveCount.toFloat() / STABILITY_THRESHOLD).coerceIn(0f, 1f)
        _uiState.value = ScannerUiState.DatesDetected(date = date, stabilityProgress = progress)

        // Use `==` (not `>=`) so the auto-confirm fires exactly once per
        // detection cycle. After firing, `resetStability` puts us back at
        // count = 0, so a continuing stream would have to count up from 1
        // again before re-firing.
        if (consecutiveCount == STABILITY_THRESHOLD) {
            viewModelScope.launch {
                _events.emit(ScannerEvent.DatePicked(date))
            }
            resetStability()
        }
    }

    private fun resetStability() {
        consecutiveCount = 0
        lastStableDate = null
    }
}
