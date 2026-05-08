import SwiftUI
import AVFoundation
import Shared

/// iOS scanner screen — full functional parity with `DateScannerScreen.kt`
/// (Android).
///
/// State map (mirrors Kotlin VM):
///  - `.requestingPermission` → centred spinner.
///  - `.permissionDenied`     → icon + copy + "Abrir ajustes" + "Cancelar".
///  - `.scanning` & `.datesDetected` → camera preview + viewfinder overlay
///    + close (X) top-left + bottom bar (chip when datesDetected, plus
///    "Introducir manualmente").
///  - `.error(message)`       → icon + copy + "Reintentar".
///
/// Auto-confirm: when the VM's stability counter hits 3, it emits
/// `.datePicked`; the View captures it via `.onReceive(viewModel.events)`
/// and calls `onDatePicked`. Manual entry button posts
/// `.manualEntryRequested`, the View calls `onManualEntry`. Cancel (X) is
/// View-local — calls `onCancel` directly without going through the VM.
struct DateScannerView: View {

    let onCancel: () -> Void
    let onDatePicked: (Kotlinx_datetimeLocalDate) -> Void
    let onManualEntry: () -> Void

    @StateObject private var viewModel = DateScannerViewModel()

    /// `AVCaptureSession` lives in the View as a stable `@State` reference,
    /// configured once when the VM transitions to `.scanning`. The View
    /// also owns the dispatch queue for the analyzer's video data output.
    @State private var session = AVCaptureSession()
    @State private var sessionConfigured = false

    /// Apple Vision lazy-loads its OCR model the first time
    /// `VNRecognizeTextRequest` runs (5–10 s cold). If that load happens on
    /// the first real camera frame, that frame stays retained for the
    /// duration and starves AVCaptureSession's small buffer pool, blanking
    /// the preview. We pre-trigger Vision with a 1×1 dummy image while the
    /// user is still seeing the viewfinder, so by the time real frames
    /// arrive the model is already warm. Fire-and-forget; result discarded.
    /// One-shot per View instance.
    @State private var didWarmUpVision = false

    /// Serial queue for `AVCaptureVideoDataOutput` callbacks. Must be
    /// serial per Apple docs; QoS `.userInitiated` because `.background`
    /// throttles the camera too aggressively.
    private let frameQueue = DispatchQueue(
        label: "ule.jescuj00.fridgey.scanner.frames",
        qos: .userInitiated
    )

    var body: some View {
        Group {
            switch viewModel.state {
            case .requestingPermission:
                requestingPermissionView
            case .permissionDenied:
                permissionDeniedView
            case .scanning:
                scanningView(detectedDate: nil, stabilityProgress: 0)
            case let .datesDetected(date, progress):
                scanningView(detectedDate: date, stabilityProgress: progress)
            case let .error(message):
                errorView(message: message)
            }
        }
        .task {
            await viewModel.onAppear()
            await syncSession()
        }
        .onChange(of: scanningEnabled) { _, _ in
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
                onDatePicked(date)
            case .manualEntryRequested:
                onManualEntry()
            }
        }
    }

    /// Bool projection of the state: are we in a state where the camera
    /// session should be running? Used as `.onChange(of:)` key so we don't
    /// re-fire syncSession on every progress tick inside `.datesDetected`.
    private var scanningEnabled: Bool {
        switch viewModel.state {
        case .scanning, .datesDetected:
            return true
        default:
            return false
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

    private func scanningView(
        detectedDate: Kotlinx_datetimeLocalDate?,
        stabilityProgress: Float
    ) -> some View {
        ZStack {
            // Camera preview at the back, full-bleed.
            CameraPreviewView(session: session)
                .ignoresSafeArea()

            // Dark overlay with viewfinder cutout + guidance text.
            ViewfinderOverlay()

            // Top-left close button.
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

            // Bottom bar: chip (when detected) + "Introducir manualmente".
            VStack {
                Spacer()
                VStack(spacing: 12) {
                    if let date = detectedDate {
                        DetectionChip(date: date, stabilityProgress: stabilityProgress)
                    }
                    Button(action: { viewModel.onManualEntry() }) {
                        Text("Introducir manualmente")
                            .fontWeight(.medium)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(.black.opacity(0.4), in: Capsule())
                    }
                }
                .padding(.bottom, 24)
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
        if scanningEnabled {
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
            // Fire the Vision warm-up only after the session is up — there
            // is no harm in it running in parallel with `startRunning`, but
            // keeping it after means we don't compete for the CPU during
            // the brief AVCapture configuration spike. See `didWarmUpVision`
            // KDoc above for the rationale.
            warmUpVisionIfNeeded()
        } else {
            let session = self.session
            await Task.detached(priority: .userInitiated) {
                if session.isRunning { session.stopRunning() }
            }.value
        }
    }

    /// Forces Apple Vision's OCR model to load NOW (during viewfinder
    /// display) instead of LATER (on the first real camera frame). One-shot
    /// per View instance; subsequent calls are no-ops. Result is discarded.
    private func warmUpVisionIfNeeded() {
        guard !didWarmUpVision else { return }
        didWarmUpVision = true
        Task.detached(priority: .userInitiated) {
            let blank = Self.makeBlankWarmUpImage()
            let recognizer = TextRecognizer()
            _ = try? await recognizer.recognizeText(
                imageData: ImageData(uiImage: blank)
            )
            recognizer.close()
        }
    }

    /// 1×1 white pixel. Smallest valid input that still triggers Vision's
    /// model load. Static to avoid capturing `self` in the detached Task.
    private static func makeBlankWarmUpImage() -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1))
        return renderer.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 1, height: 1))
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
