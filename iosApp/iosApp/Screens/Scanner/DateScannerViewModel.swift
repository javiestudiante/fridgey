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

    /// One-shot navigation events. The View subscribes via `.onReceive`
    /// in the same way Android subscribes to the Kotlin VM's `events`
    /// SharedFlow. `PassthroughSubject` so a late subscriber doesn't
    /// re-receive past events (replay = 0 semantics).
    let events = PassthroughSubject<Event, Never>()

    private(set) var analyzer: DateScannerFrameAnalyzer?
    private var resultsSubscription: AnyCancellable?

    private let permissionService: CameraPermissionService

    // -- Stability tracking (matches Android exactly) ---------------------------

    private var consecutiveCount: Int = 0
    private var lastStableDate: Kotlinx_datetimeLocalDate?

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
            ensureAnalyzerStarted()
            state = .scanning
        case .notDetermined:
            state = .requestingPermission
            let result = await permissionService.requestPermission()
            if result == .authorized {
                ensureAnalyzerStarted()
                state = .scanning
            } else {
                resetStability()
                state = .permissionDenied
            }
        case .denied, .restricted:
            resetStability()
            state = .permissionDenied
        }
    }

    // -- Analyzer wiring --------------------------------------------------------

    /// Idempotent. Builds the analyzer the first time permission is granted
    /// and starts collecting its results. Called from `checkAndRequestPermission`
    /// BEFORE `state` is set to `.scanning`, so the View can safely read
    /// `viewModel.analyzer` while configuring its AVCaptureSession.
    private func ensureAnalyzerStarted() {
        guard analyzer == nil else { return }
        let analyzer = DateScannerFrameAnalyzer()
        self.analyzer = analyzer
        // Hop emissions to main before delivering to handleResult — analyzer
        // sends from a Task whose continuation may be off-main.
        resultsSubscription = analyzer.results
            .receive(on: DispatchQueue.main)
            .sink { [weak self] result in
                self?.handleResult(result)
            }
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
