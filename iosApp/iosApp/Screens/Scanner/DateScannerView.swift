import SwiftUI
import AVFoundation
import Shared

/// iOS scanner screen — full functional parity with `DateScannerScreen.kt`
/// (Android).
///
/// ## Layout (two stable layers)
///
/// The body is a `ZStack` of two layers:
///
///  1. **Camera layer** — `CameraPreviewView(session:)`, present
///     whenever `shouldShowCameraPreview` is true. Its SwiftUI
///     structural identity is anchored at the top of the `body`'s
///     `ZStack` so it survives every `.scanning` ↔ `.datesDetected`
///     transition. Putting the preview INSIDE a state-switch caused a
///     ~10 s freeze: SwiftUI tore down and rebuilt the `UIView` on
///     every stability-counter tick, and re-binding the
///     `AVCaptureSession` to a fresh preview layer blanks the camera
///     for ~3 s per re-bind.
///  2. **Overlay layer** — `stateOverlay`, a `@ViewBuilder` that
///     renders the per-state UI on top of the camera. Lightweight; safe
///     to rebuild on every state tick.
///
/// ## State map (mirrors Kotlin VM)
///
///  - `.requestingPermission` → spinner overlay (no camera layer).
///  - `.permissionDenied`     → icon + copy + "Abrir ajustes" + "Cancelar"
///    (no camera layer).
///  - `.scanning` & `.datesDetected` → camera layer beneath; overlay
///    holds viewfinder + close (X) + bottom bar (chip when
///    `datesDetected`, plus "Introducir manualmente").
///  - `.error(message)`       → icon + copy + "Reintentar" (no camera
///    layer).
///
/// ## Auto-confirm
///
/// When the VM's stability counter hits 3 it emits `.datePicked`; the
/// View captures it via `.onReceive(viewModel.events)` and calls
/// `onDatePicked`. Manual entry button posts `.manualEntryRequested`,
/// the View calls `onManualEntry`. Cancel (X) is View-local — calls
/// `onCancel` directly without going through the VM.
struct DateScannerView: View {

    let onCancel: () -> Void
    let onDatePicked: (Kotlinx_datetimeLocalDate, ProductAutoFill?) -> Void
    // Carries whatever the CÓDIGO phase resolved (name + category + cantidad +
    // unit + image + barcode), so the manual route arrives pre-filled with
    // everything except the date. Nil when no barcode was scanned (user tapped
    // "manual" while still in the barcode phase) → manual entry from scratch.
    let onManualEntry: (ProductAutoFill?) -> Void

    @StateObject private var viewModel = DateScannerViewModel()

    /// `AVCaptureSession` lives in the View as a stable `@State` reference,
    /// configured once when the VM transitions to `.scanning`. The View
    /// also owns the dispatch queue for the analyzer's video data output.
    @State private var session = AVCaptureSession()
    @State private var sessionConfigured = false


    /// Serial queue for `AVCaptureVideoDataOutput` callbacks. Must be
    /// serial per Apple docs; QoS `.userInitiated` because `.background`
    /// throttles the camera too aggressively.
    private let frameQueue = DispatchQueue(
        label: "ule.jescuj00.fridgey.scanner.frames",
        qos: .userInitiated
    )

    var body: some View {
        // Two-layer ZStack. The camera preview lives at the top of the
        // body so its SwiftUI structural identity is stable across every
        // state transition in which it is visible — concretely, the
        // `.scanning` ↔ `.datesDetected` ping-pong driven by the
        // stability counter. Previously the preview lived INSIDE a
        // `switch` that emitted a fresh `scanningView(...)` per case;
        // SwiftUI treated those as different identities and tore down /
        // recreated the underlying `UIView` on every tick, which
        // re-attaches the `AVCaptureSession` to a brand-new
        // `AVCaptureVideoPreviewLayer`. Each re-attach blanks the
        // preview for ~3 s; three ticks → ~10 s freeze. Keeping the
        // preview here, gated only by `shouldShowCameraPreview`,
        // guarantees it survives every `.scanning` ↔ `.datesDetected`
        // hop without rebuild.
        ZStack {
            // CAPA 1 — camera preview. Stable identity while
            // `shouldShowCameraPreview` stays true. Only torn down when
            // we leave the scanning states entirely (permission lost,
            // error) — which is the desired moment to stop holding the
            // camera anyway.
            if shouldShowCameraPreview {
                CameraPreviewView(session: session)
                    .ignoresSafeArea()
            }

            // CAPA 2 — state-driven UI on top. The overlays are
            // transparent outside their content so the camera layer
            // beneath remains visible.
            stateOverlay
        }
        .task {
            await viewModel.onAppear()
            await syncSession()
        }
        .onChange(of: shouldShowCameraPreview) { _, _ in
            Task { await syncSession() }
        }
        .onDisappear {
            let session = self.session
            Task.detached(priority: .userInitiated) {
                if session.isRunning { session.stopRunning() }
            }
        }
        .onReceive(viewModel.events) { event in
            switch event {
            case let .datePicked(date):
                // Hand back both the date and whatever the CODE phase resolved.
                onDatePicked(date, viewModel.pendingAutoFill)
            case .manualEntryRequested:
                // Same autofill the date path carries — just without a date.
                onManualEntry(viewModel.pendingAutoFill)
            }
        }
    }

    /// True iff the camera preview UIView should be alive AND the
    /// `AVCaptureSession` should be running. Held constant across
    /// `.scanning` ↔ `.datesDetected` so the preview's SwiftUI identity
    /// never flips during a stability-counter cycle. Also used as the
    /// key in `.onChange(of:)` to drive session start/stop — single
    /// Bool means we don't re-fire `syncSession` on every progress
    /// tick inside `.datesDetected`.
    private var shouldShowCameraPreview: Bool {
        switch viewModel.state {
        case .scanningBarcode, .searchingProduct, .scanning, .datesDetected:
            // Camera stays up across the whole CÓDIGO → FECHA flow: the
            // session starts once and is never rebuilt on a phase change,
            // which is what keeps CameraPreviewView stable.
            return true
        default:
            return false
        }
    }

    /// Renders the overlay UI for the current state. Camera preview is
    /// NOT here — see CAPA 1 in `body`. Marked `@ViewBuilder` so the
    /// `switch` branches can return heterogeneous view types.
    @ViewBuilder
    private var stateOverlay: some View {
        switch viewModel.state {
        case .requestingPermission:
            requestingPermissionView
        case .permissionDenied:
            permissionDeniedView
        case let .scanningBarcode(progress):
            barcodeOverlay(progress: progress)
        case .searchingProduct:
            searchingOverlay
        case .scanning:
            scanningOverlay(detectedDate: nil, stabilityProgress: 0)
        case let .datesDetected(date, progress):
            scanningOverlay(detectedDate: date, stabilityProgress: progress)
        case let .error(message):
            errorView(message: message)
        }
    }

    // MARK: - State subviews

    private var requestingPermissionView: some View {
        ProgressView()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var permissionDeniedView: some View {
        VStack(spacing: 16) {
            Image(systemName: "camera.fill")
                .font(.system(size: 56))
                .foregroundStyle(.secondary)
            Text("Necesitamos acceso a la cámara para escanear fechas de caducidad")
                .font(.title3)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 280)
            Button("Abrir ajustes") { viewModel.openAppSettings() }
                .buttonStyle(.borderedProminent)
            Button("Cancelar", action: onCancel)
                .buttonStyle(.bordered)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// Shared camera-overlay chrome: viewfinder + phase-specific guidance
    /// text + top-left close button + a bottom bar. All lightweight SwiftUI
    /// (no `UIViewRepresentable`, no AVFoundation) — the camera preview itself
    /// lives in CAPA 1 of `body`, so rebuilding this on every state tick never
    /// touches the capture session.
    private func cameraScaffold<Bottom: View>(
        guidance: String,
        @ViewBuilder bottom: () -> Bottom
    ) -> some View {
        ZStack {
            ViewfinderOverlay(guidanceText: guidance)

            VStack {
                HStack {
                    Button(action: onCancel) {
                        Image(systemName: "xmark")
                            .font(.title2.weight(.semibold))
                            .foregroundStyle(.white)
                            .padding(12)
                            .background(.black.opacity(0.5), in: Circle())
                    }
                    .padding(.top, 8)
                    .padding(.leading, 16)
                    .accessibilityLabel("Cancelar escaneo")
                    Spacer()
                }
                Spacer()
            }

            VStack {
                Spacer()
                bottom().padding(.bottom, 24)
            }
        }
    }

    private var manualEntryButton: some View {
        Button(action: { viewModel.onManualEntry() }) {
            Text("Introducir manualmente")
                .fontWeight(.medium)
                .foregroundStyle(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(.black.opacity(0.4), in: Capsule())
        }
    }

    private func bannerPill(_ text: String) -> some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(.white)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(.black.opacity(0.55), in: Capsule())
    }

    /// CODE phase — looking for a barcode.
    private func barcodeOverlay(progress: Float) -> some View {
        cameraScaffold(guidance: "Escanea el código de barras") {
            VStack(spacing: 12) {
                if progress > 0 {
                    ProgressView(value: Double(progress))
                        .progressViewStyle(.linear)
                        .tint(Color.fridgeyMint)
                        .frame(width: 140)
                }
                manualEntryButton
            }
        }
    }

    /// CODE phase — barcode confirmed, querying Open Food Facts.
    private var searchingOverlay: some View {
        cameraScaffold(guidance: "Buscando producto…") {
            VStack(spacing: 12) {
                ProgressView().tint(.white)
                manualEntryButton
            }
        }
    }

    /// DATE phase — the pre-existing OCR overlay, now also surfacing the
    /// Open Food Facts banner from the CODE phase.
    private func scanningOverlay(
        detectedDate: Kotlinx_datetimeLocalDate?,
        stabilityProgress: Float
    ) -> some View {
        cameraScaffold(guidance: "Coloca la fecha de caducidad dentro del recuadro") {
            VStack(spacing: 12) {
                if let date = detectedDate {
                    DetectionChip(date: date, stabilityProgress: stabilityProgress)
                }
                if let banner = viewModel.productBanner {
                    bannerPill(banner)
                }
                manualEntryButton
            }
        }
    }

    private func errorView(message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 56))
                .foregroundStyle(.yellow)
            Text(message)
                .font(.title3)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 280)
            Button("Reintentar") {
                Task { await viewModel.onRetryPermission() }
            }
            .buttonStyle(.borderedProminent)
            Button("Cancelar", action: onCancel)
                .buttonStyle(.bordered)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Session lifecycle

    /// Brings the AVCaptureSession in line with the requested view-state.
    /// Off-main for `startRunning` / `stopRunning` to avoid the iOS 17+
    /// main-thread checker (those calls block ~100 ms on first invocation).
    private func syncSession() async {
        if shouldShowCameraPreview {
            if !sessionConfigured {
                let configured = configureSession()
                if !configured {
                    // configureSession already pushed an error to the VM.
                    return
                }
                sessionConfigured = true
            }
            let session = self.session
            await Task.detached(priority: .userInitiated) {
                if !session.isRunning { session.startRunning() }
            }.value
            // NOTE: Vision OCR warm-up moved to `DateScannerFrameAnalyzer.warmUp()`,
            // fired from the VM's `enterDatePhase()`. The old warm-up here warmed
            // a THROWAWAY `TextRecognizer()`, NOT the singleton the analyzer uses
            // for live frames, so the real recogniser still arrived cold to the
            // FECHA phase and returned nothing. See `warmUp()` for the full story.
        } else {
            let session = self.session
            await Task.detached(priority: .userInitiated) {
                if session.isRunning { session.stopRunning() }
            }.value
        }
    }


    /// Adds the back camera input AND the AVCaptureVideoDataOutput wired to
    /// the VM's analyzer. Returns `true` on success, `false` after asking
    /// the VM to transition to `.error`.
    private func configureSession() -> Bool {
        session.beginConfiguration()
        defer { session.commitConfiguration() }

        session.sessionPreset = .high

        // 1. Back camera input.
        guard let device = AVCaptureDevice.default(
            .builtInWideAngleCamera, for: .video, position: .back
        ) else {
            viewModel.reportConfigurationError(
                "No se ha encontrado una cámara trasera disponible."
            )
            return false
        }
        let input: AVCaptureDeviceInput
        do {
            input = try AVCaptureDeviceInput(device: device)
        } catch {
            viewModel.reportConfigurationError(
                "No se pudo iniciar la cámara: \(error.localizedDescription)"
            )
            return false
        }
        guard session.canAddInput(input) else {
            viewModel.reportConfigurationError(
                "La cámara no puede usarse en este momento."
            )
            return false
        }
        session.addInput(input)

        // 2. Frame analyzer output. The VM creates the analyzer when
        //    permission is granted, BEFORE state becomes `.scanning`, so
        //    `viewModel.analyzer` is non-nil here.
        if let analyzer = viewModel.analyzer {
            let videoOutput = AVCaptureVideoDataOutput()
            // Discard late frames — we throttle to 2 fps internally and
            // don't want a backlog.
            videoOutput.alwaysDiscardsLateVideoFrames = true
            videoOutput.setSampleBufferDelegate(analyzer, queue: frameQueue)
            if session.canAddOutput(videoOutput) {
                session.addOutput(videoOutput)
            }
        }

        return true
    }
}
