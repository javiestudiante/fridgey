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
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.model.ProductAutoFill
import ule.jescuj00.fridgey.domain.model.ProductLookupResult
import ule.jescuj00.fridgey.domain.scanner.BarcodeResult
import ule.jescuj00.fridgey.domain.scanner.BarcodeScanner
import ule.jescuj00.fridgey.domain.scanner.DateScanResult
import ule.jescuj00.fridgey.domain.usecase.LookupProductByBarcodeUseCase
import ule.jescuj00.fridgey.domain.usecase.ScanExpirationDateUseCase

// =============================================================================
// Tunables
// =============================================================================

/**
 * Consecutive identical detections required to auto-confirm. Shared by both
 * phases: 3 same dates → date confirmed; 3 same barcodes → barcode confirmed.
 * Three matches a ~1.5 s window at the analyzers' 2 fps throttle.
 */
private const val STABILITY_THRESHOLD = 3

// =============================================================================
// Phases
// =============================================================================

/**
 * The scanner is a SEQUENTIAL two-phase flow in a single camera session:
 *  1. [CODE] — read the product barcode, look it up on Open Food Facts.
 *  2. [DATE] — the pre-existing OCR expiry-date flow, untouched.
 */
private enum class Phase { CODE, DATE }

// =============================================================================
// UI state
// =============================================================================

sealed class ScannerUiState {
    object RequestingPermission : ScannerUiState()
    data class PermissionDenied(val canAskAgain: Boolean) : ScannerUiState()

    // ---- CODE phase ----
    /** Looking for a barcode. [barcodeProgress] 0..1 stability of the current candidate. */
    data class ScanningBarcode(val barcodeProgress: Float = 0f) : ScannerUiState()
    /** A barcode was confirmed; querying Open Food Facts. */
    object SearchingProduct : ScannerUiState()

    // ---- DATE phase (unchanged semantics) ----
    object Scanning : ScannerUiState()
    /**
     * A date has been detected. [stabilityProgress] runs 0.0..1.0; at 1.0
     * the VM auto-emits [ScannerEvent.DatePicked].
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
    /**
     * The user finished the flow with a date. The resolved Open Food Facts
     * fields (if any) are read separately from [DateScannerViewModel.pendingAutoFill]
     * by the screen when this fires — keeping this event's shape unchanged.
     */
    data class DatePicked(val date: LocalDate) : ScannerEvent()
    object ManualEntryRequested : ScannerEvent()
}

// =============================================================================
// ViewModel
// =============================================================================

/**
 * Drives the sequential CÓDIGO → FECHA scanner over one camera session.
 *
 * CODE phase: a [BarcodeFrameAnalyzer] feeds barcodes; three consecutive
 * identical reads auto-confirm, then [LookupProductByBarcodeUseCase] queries
 * Open Food Facts. Whatever the outcome (found / not found / network error) we
 * stash a [ProductAutoFill] in [pendingAutoFill] — at least the scanned
 * barcode — surface a [productBanner], and advance to the DATE phase. The
 * "Introducir manualmente" escape is always available.
 *
 * DATE phase: the pre-existing OCR pipeline ([ScanExpirationDateUseCase] +
 * 3-frame stability auto-confirm) is preserved verbatim.
 *
 * The analyzer factories are constructor seams so unit tests can substitute
 * in-memory fakes without CameraX / ML Kit / Vision.
 */
class DateScannerViewModel(
    private val scanUseCase: ScanExpirationDateUseCase,
    private val barcodeScanner: BarcodeScanner,
    private val lookupProduct: LookupProductByBarcodeUseCase,
    private val dateAnalyzerFactory: (CoroutineScope) -> FrameAnalyzer = { scope ->
        DateScannerAnalyzer(scanUseCase = scanUseCase, coroutineScope = scope)
    },
    private val barcodeAnalyzerFactory: (CoroutineScope) -> BarcodeFrameAnalyzer = { scope ->
        BarcodeAnalyzer(barcodeScanner = barcodeScanner, coroutineScope = scope)
    },
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.RequestingPermission)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    /** Feedback shown during the DATE phase: "Encontrado: …" / "no encontrado". */
    private val _productBanner = MutableStateFlow<String?>(null)
    val productBanner: StateFlow<String?> = _productBanner.asStateFlow()

    private val _events = MutableSharedFlow<ScannerEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<ScannerEvent> = _events.asSharedFlow()

    /**
     * Resolved Open Food Facts fields from the CODE phase. Read by the screen
     * when [ScannerEvent.DatePicked] fires so it can hand both the autofill and
     * the date back to AddProducto. Non-null once a barcode has been scanned
     * (carries at least the barcode); null if the barcode phase was skipped.
     */
    var pendingAutoFill: ProductAutoFill? = null
        private set

    private var phase: Phase = Phase.CODE

    private var _barcodeAnalyzer: BarcodeFrameAnalyzer? = null
    private var _dateAnalyzer: FrameAnalyzer? = null

    /** The screen binds this to CameraX; it returns the phase-appropriate
     *  analyzer, so a phase switch triggers a CameraX rebind. */
    val analyzer: androidx.camera.core.ImageAnalysis.Analyzer?
        get() = when (phase) {
            Phase.CODE -> _barcodeAnalyzer
            Phase.DATE -> _dateAnalyzer
        }

    // -- Stability tracking (separate counters per phase) ------------------------

    private var dateCount = 0
    private var lastStableDate: LocalDate? = null

    private var barcodeCount = 0
    private var lastStableBarcode: String? = null

    // -- Public API -------------------------------------------------------------

    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        if (granted) {
            phase = Phase.CODE
            resetBarcodeStability()
            ensureBarcodeAnalyzerStarted()
            _uiState.value = ScannerUiState.ScanningBarcode()
        } else {
            resetDateStability()
            resetBarcodeStability()
            _uiState.value = ScannerUiState.PermissionDenied(canAskAgain)
        }
    }

    fun onConfirmDate(date: LocalDate) {
        viewModelScope.launch { _events.emit(ScannerEvent.DatePicked(date)) }
    }

    fun onManualEntry() {
        viewModelScope.launch { _events.emit(ScannerEvent.ManualEntryRequested) }
    }

    // -- CODE phase --------------------------------------------------------------

    private fun ensureBarcodeAnalyzerStarted() {
        if (_barcodeAnalyzer != null) return
        val analyzer = barcodeAnalyzerFactory(viewModelScope)
        _barcodeAnalyzer = analyzer
        viewModelScope.launch {
            analyzer.results.collect { handleBarcodeResult(it) }
        }
    }

    private fun handleBarcodeResult(result: BarcodeResult?) {
        // Ignore emissions once we've left barcode scanning (lookup in flight
        // or already in the date phase).
        if (_uiState.value !is ScannerUiState.ScanningBarcode) return

        val code = result?.rawValue
        if (code.isNullOrBlank()) return   // momentary gap; keep progress

        if (code == lastStableBarcode) {
            barcodeCount++
        } else {
            barcodeCount = 1
            lastStableBarcode = code
        }

        val progress = (barcodeCount.toFloat() / STABILITY_THRESHOLD).coerceIn(0f, 1f)
        _uiState.value = ScannerUiState.ScanningBarcode(barcodeProgress = progress)

        if (barcodeCount == STABILITY_THRESHOLD) {
            resetBarcodeStability()
            onBarcodeConfirmed(code)
        }
    }

    private fun onBarcodeConfirmed(barcode: String) {
        _uiState.value = ScannerUiState.SearchingProduct
        viewModelScope.launch {
            val result = lookupProduct(barcode)
            val (autoFill, banner) = when (result) {
                is ProductLookupResult.Found -> {
                    val name = result.product.nombre.ifBlank { barcode }
                    result.product to "Encontrado: $name"
                }
                ProductLookupResult.NotFound ->
                    barcodeOnly(barcode) to "Producto no encontrado · introdúcelo a mano"
                ProductLookupResult.NetworkError ->
                    barcodeOnly(barcode) to "Sin conexión con Open Food Facts · introdúcelo a mano"
                ProductLookupResult.RateLimited ->
                    barcodeOnly(barcode) to
                        "Demasiadas consultas a Open Food Facts. Inténtalo en un momento o introduce el producto a mano."
            }
            pendingAutoFill = autoFill
            _productBanner.value = banner
            enterDatePhase()
        }
    }

    /** Minimal autofill carrying just the scanned barcode (lookup miss/error). */
    private fun barcodeOnly(barcode: String) = ProductAutoFill(
        codigoBarras = barcode,
        nombre = "",
        cantidad = 1.0,
        unidad = Categoria.OTROS.unidadDefault,
        imagenUrl = null,
        categoria = Categoria.OTROS,
    )

    // -- DATE phase (pre-existing OCR logic, unchanged) --------------------------

    private fun enterDatePhase() {
        phase = Phase.DATE
        resetDateStability()
        ensureDateAnalyzerStarted()
        _uiState.value = ScannerUiState.Scanning
    }

    private fun ensureDateAnalyzerStarted() {
        if (_dateAnalyzer != null) return
        val analyzer = dateAnalyzerFactory(viewModelScope)
        _dateAnalyzer = analyzer
        viewModelScope.launch {
            analyzer.results.collect { handleResult(it) }
        }
    }

    private fun handleResult(result: DateScanResult?) {
        when (result) {
            null -> Unit
            is DateScanResult.Success -> onSuccessfulDetection(result.date)
            is DateScanResult.MultipleDatesFound -> {
                result.dates.maxOrNull()?.let { onSuccessfulDetection(it) }
            }
            is DateScanResult.NoDateFound -> {
                // Keep the stability counter as-is: a momentary loss of
                // detection should not reset progress. (See original rationale.)
            }
            is DateScanResult.Error -> {
                resetDateStability()
                _uiState.value = ScannerUiState.Error(result.message)
            }
        }
    }

    private fun onSuccessfulDetection(date: LocalDate) {
        if (date == lastStableDate) {
            dateCount++
        } else {
            dateCount = 1
            lastStableDate = date
        }

        val progress = (dateCount.toFloat() / STABILITY_THRESHOLD).coerceIn(0f, 1f)
        _uiState.value = ScannerUiState.DatesDetected(date = date, stabilityProgress = progress)

        if (dateCount == STABILITY_THRESHOLD) {
            viewModelScope.launch { _events.emit(ScannerEvent.DatePicked(date)) }
            resetDateStability()
        }
    }

    private fun resetDateStability() {
        dateCount = 0
        lastStableDate = null
    }

    private fun resetBarcodeStability() {
        barcodeCount = 0
        lastStableBarcode = null
    }
}
