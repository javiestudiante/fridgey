import Combine
import Foundation
import UIKit
import Shared

/// ViewModel for the iOS scanner screen.
///
/// Mirrors `DateScannerViewModel.kt` (Android) — same states, same events,
/// same stability counter (`STABILITY_THRESHOLD = 3`), same MultipleDatesFound
/// "max date wins" reduction, same `NoDateFound` keeps the counter intact.
///
/// Architecture differs from the Kotlin VM in two places, dictated by iOS:
///  1. Frame analysis is delivered via Combine (`PassthroughSubject`)
///     instead of `SharedFlow` — closest equivalent in the iOS toolbox
///     that propagates duplicates (vital for stability counting).
///  2. The analyzer is `NSObject` (so it can be an
///     `AVCaptureVideoDataOutputSampleBufferDelegate`) and lives detached
///     from any Kotlin coroutine scope; it self-cleans through the AVCapture
///     session lifecycle managed by `DateScannerView`.
@MainActor
final class DateScannerViewModel: ObservableObject {

    /// Three same-date frames in a row trigger auto-confirm. Matches the
    /// Android constant; chosen to give a ~1.5 s confirmation window at
    /// the analyzer's 2 fps throttle.
    private static let stabilityThreshold = 3

    enum UIState {
        case requestingPermission
        /// Covers both `.denied` and `.restricted` from
        /// `CameraPermissionStatus`; both lead to the same "open Settings" UI.
        case permissionDenied
        // ---- CODE phase ----
        /// Looking for a barcode. `barcodeProgress` 0.0..1.0 stability of the
        /// current candidate.
        case scanningBarcode(progress: Float)
        /// A barcode was confirmed; querying Open Food Facts.
        case searchingProduct
        // ---- DATE phase (unchanged) ----
        case scanning
        /// `stabilityProgress` runs from 0.0 to 1.0; once it hits 1.0 the VM
        /// emits `.datePicked` automatically (mirroring Android).
        case datesDetected(date: Kotlinx_datetimeLocalDate, stabilityProgress: Float)
        case error(String)
    }

    enum Event {
        case datePicked(Kotlinx_datetimeLocalDate)
        case manualEntryRequested
    }

    @Published private(set) var state: UIState = .requestingPermission

    /// Feedback shown during the DATE phase: "Encontrado: …" / "no encontrado".
    @Published private(set) var productBanner: String? = nil

    /// One-shot navigation events. The View subscribes via `.onReceive`
    /// in the same way Android subscribes to the Kotlin VM's `events`
    /// SharedFlow. `PassthroughSubject` so a late subscriber doesn't
    /// re-receive past events (replay = 0 semantics).
    let events = PassthroughSubject<Event, Never>()

    /// Open Food Facts fields resolved during the CODE phase. The View reads
    /// this when `.datePicked` fires and hands it back to AddProducto along
    /// with the date. Non-nil once a barcode has been scanned (carries at
    /// least the barcode).
    private(set) var pendingAutoFill: ProductAutoFill?

    private(set) var analyzer: DateScannerFrameAnalyzer?
    private var resultsSubscription: AnyCancellable?
    private var barcodeSubscription: AnyCancellable?

    private let permissionService: CameraPermissionService
    private let lookupUseCase = KoinIosKt.getLookupProductByBarcodeUseCase()

    // -- Stability tracking (matches Android exactly) ---------------------------

    private var consecutiveCount: Int = 0
    private var lastStableDate: Kotlinx_datetimeLocalDate?

    private var consecutiveBarcodeCount: Int = 0
    private var lastStableBarcode: String?

    // -- Init -------------------------------------------------------------------

    init(permissionService: CameraPermissionService = DefaultCameraPermissionService()) {
        self.permissionService = permissionService
    }

    // -- Public API -------------------------------------------------------------

    func onAppear() async {
        await checkAndRequestPermission()
    }

    func onRetryPermission() async {
        await checkAndRequestPermission()
    }

    func onManualEntry() {
        events.send(.manualEntryRequested)
    }

    func openAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    /// Called by the View when the AVCaptureSession cannot be initialised
    /// (no back camera, input creation throws, …).
    func reportConfigurationError(_ message: String) {
        state = .error(message)
    }

    // -- Permission flow --------------------------------------------------------

    private func checkAndRequestPermission() async {
        let status = permissionService.currentStatus
        switch status {
        case .authorized:
            startBarcodePhase()
        case .notDetermined:
            state = .requestingPermission
            let result = await permissionService.requestPermission()
            if result == .authorized {
                startBarcodePhase()
            } else {
                resetStability()
                resetBarcodeStability()
                state = .permissionDenied
            }
        case .denied, .restricted:
            resetStability()
            resetBarcodeStability()
            state = .permissionDenied
        }
    }

    // -- Analyzer wiring --------------------------------------------------------

    /// Idempotent. Builds the analyzer the first time permission is granted
    /// and starts collecting its results. Called from `checkAndRequestPermission`
    /// BEFORE `state` is set to `.scanning`, so the View can safely read
    /// `viewModel.analyzer` while configuring its AVCaptureSession.
    /// Builds the analyzer (if needed), starts it in `.barcode` mode and moves
    /// to the CODE phase. Called BEFORE the View configures its
    /// AVCaptureSession, so `viewModel.analyzer` is non-nil there.
    private func startBarcodePhase() {
        ensureAnalyzerStarted()
        analyzer?.setMode(.barcode)
        resetBarcodeStability()
        state = .scanningBarcode(progress: 0)
    }

    private func ensureAnalyzerStarted() {
        guard analyzer == nil else { return }
        let analyzer = DateScannerFrameAnalyzer()
        self.analyzer = analyzer
        // Hop emissions to main before delivering — the analyzer sends from a
        // Task whose continuation may be off-main. Subscribe to BOTH streams;
        // each handler ignores emissions outside its phase.
        resultsSubscription = analyzer.results
            .receive(on: DispatchQueue.main)
            .sink { [weak self] result in
                self?.handleResult(result)
            }
        barcodeSubscription = analyzer.barcodeResults
            .receive(on: DispatchQueue.main)
            .sink { [weak self] barcode in
                self?.handleBarcodeResult(barcode)
            }
    }

    // -- CODE phase --------------------------------------------------------------

    private func handleBarcodeResult(_ result: BarcodeResult?) {
        // Ignore once we've left barcode scanning (lookup in flight / date phase).
        guard case .scanningBarcode = state else { return }
        guard let code = result?.rawValue, !code.isEmpty else { return }

        if let last = lastStableBarcode, last == code {
            consecutiveBarcodeCount += 1
        } else {
            consecutiveBarcodeCount = 1
            lastStableBarcode = code
        }

        let progress = min(max(Float(consecutiveBarcodeCount) / Float(Self.stabilityThreshold), 0), 1)
        state = .scanningBarcode(progress: progress)

        if consecutiveBarcodeCount == Self.stabilityThreshold {
            resetBarcodeStability()
            onBarcodeConfirmed(code)
        }
    }

    private func onBarcodeConfirmed(_ barcode: String) {
        state = .searchingProduct
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                let result = try await self.lookupUseCase.invoke(barcode: barcode)
                self.applyLookup(result, barcode: barcode)
            } catch {
                // The repository normally maps failures to NetworkError without
                // throwing; treat any thrown error the same way.
                self.pendingAutoFill = self.barcodeOnly(barcode)
                self.productBanner = "Sin conexión con Open Food Facts · introdúcelo a mano"
                self.enterDatePhase()
            }
        }
    }

    private func applyLookup(_ result: ProductLookupResult, barcode: String) {
        // `ProductLookupResult` is a sealed INTERFACE → exported as an ObjC
        // protocol, so its variants are FLAT classes (ProductLookupResultFound),
        // not nested types like the sealed-class DateScanResult.Success.
        switch result {
        case let found as ProductLookupResultFound:
            pendingAutoFill = found.product
            let name = found.product.nombre.isEmpty ? barcode : found.product.nombre
            productBanner = "Encontrado: \(name)"
        case is ProductLookupResultNotFound:
            pendingAutoFill = barcodeOnly(barcode)
            productBanner = "Producto no encontrado · introdúcelo a mano"
        case is ProductLookupResultNetworkError:
            pendingAutoFill = barcodeOnly(barcode)
            productBanner = "Sin conexión con Open Food Facts · introdúcelo a mano"
        default:
            pendingAutoFill = barcodeOnly(barcode)
        }
        enterDatePhase()
    }

    /// Minimal autofill carrying just the scanned barcode (lookup miss/error).
    private func barcodeOnly(_ barcode: String) -> ProductAutoFill {
        ProductAutoFill(
            codigoBarras: barcode,
            nombre: "",
            cantidad: 1.0,
            unidad: Categoria.otros.unidadDefault,
            imagenUrl: nil,
            categoria: .otros
        )
    }

    /// Switches the analyzer to OCR and moves to the (unchanged) DATE phase.
    ///
    /// Order matters:
    ///  1. `setMode(.date)` — frames now route to the OCR branch.
    ///  2. `resetTransientState()` — clear any CÓDIGO-phase throttle/flag
    ///     (robustness; mirrors Android's fresh-analyzer-per-phase).
    ///  3. `warmUp()` — ROOT-CAUSE FIX: load Vision's text model into the REAL
    ///     recognizer the analyzer uses, BEFORE the first live FECHA frame.
    ///     Must come AFTER `resetTransientState()` so its single-flight claim
    ///     isn't wiped.
    private func enterDatePhase() {
        analyzer?.setMode(.date)
        analyzer?.resetTransientState()
        analyzer?.warmUp()
        resetStability()
        state = .scanning
    }

    private func resetBarcodeStability() {
        consecutiveBarcodeCount = 0
        lastStableBarcode = nil
    }

    private func handleResult(_ result: DateScanResult) {
        // K/N exports the sealed-class variants under `DateScanResult.<Name>`
        // (per the `swift_name("DateScanResult.Error")` ObjC attribute on
        // `Shared.h`). Use the dotted form — there is no top-level
        // `DateScanResultSuccess` symbol in the Swift module.
        switch result {
        case let success as DateScanResult.Success:
            onSuccessfulDetection(success.date)
        case let multi as DateScanResult.MultipleDatesFound:
            // Reduce to maximum date — same heuristic as Android
            // (`dates.maxOrNull()`). Use case has already filtered to valid
            // future dates, so `max` is meaningful.
            let dates = (multi.dates as? [Kotlinx_datetimeLocalDate]) ?? []
            if let latest = dates.max(by: Self.dateLessThan) {
                onSuccessfulDetection(latest)
            }
        case is DateScanResult.NoDateFound:
            // Stay put. Counter is NOT reset on a single empty frame —
            // see the long comment in Kotlin's handleResult.
            break
        case let err as DateScanResult.Error:
            resetStability()
            state = .error(err.message)
        default:
            // K/N exports the sealed-class subtypes as concrete classes;
            // any new variant added later should be handled explicitly,
            // but we don't crash on unexpected input.
            break
        }
    }

    /// Updates stability tracking for a new successful detection of `date`,
    /// publishes progress to the UI, and emits the auto-confirm event when
    /// the threshold is hit.
    private func onSuccessfulDetection(_ date: Kotlinx_datetimeLocalDate) {
        if let last = lastStableDate, Self.datesEqual(last, date) {
            consecutiveCount += 1
        } else {
            consecutiveCount = 1
            lastStableDate = date
        }

        let progress = Float(consecutiveCount) / Float(Self.stabilityThreshold)
        let clamped = min(max(progress, 0.0), 1.0)
        state = .datesDetected(date: date, stabilityProgress: clamped)

        // Use `==` (not `>=`) so the auto-confirm fires exactly once per
        // detection cycle. After firing, resetStability puts us back at
        // count = 0; a continuing stream would have to count up from 1 again.
        if consecutiveCount == Self.stabilityThreshold {
            events.send(.datePicked(date))
            resetStability()
        }
    }

    private func resetStability() {
        consecutiveCount = 0
        lastStableDate = nil
    }

    // -- Helpers ----------------------------------------------------------------

    private static func datesEqual(
        _ a: Kotlinx_datetimeLocalDate, _ b: Kotlinx_datetimeLocalDate
    ) -> Bool {
        a.year == b.year && a.monthNumber == b.monthNumber && a.dayOfMonth == b.dayOfMonth
    }

    private static func dateLessThan(
        _ a: Kotlinx_datetimeLocalDate, _ b: Kotlinx_datetimeLocalDate
    ) -> Bool {
        if a.year != b.year { return a.year < b.year }
        if a.monthNumber != b.monthNumber { return a.monthNumber < b.monthNumber }
        return a.dayOfMonth < b.dayOfMonth
    }
}
