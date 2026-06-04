import AVFoundation
import Combine
import CoreImage
import Foundation
import Shared
import UIKit

/// Bridge between `AVCaptureVideoDataOutput` and the shared
/// `ScanExpirationDateUseCase`.
///
/// Responsibilities, mirroring `DateScannerAnalyzer.kt` (Android):
///  - Throttle incoming frames to ~2 fps (one analysis per 500 ms minimum).
///  - Single-flight: never analyse a new frame while a previous one is still
///    being processed by Vision (Vision requests are cheap to discard but
///    expensive to queue up — letting them stack would lag the UI).
///  - Convert the camera's `CMSampleBuffer` to a `UIImage` and hand it to
///    the shared use case. The shared layer takes care of OCR, date parsing,
///    and validity filtering.
///  - Publish each result through a Combine `PassthroughSubject` so the
///    `DateScannerViewModel`'s stability counter sees every emission,
///    including duplicates (mirrors the Kotlin contract: `SharedFlow`, NOT
///    `StateFlow` — duplicate `Success(sameDate)` frames must propagate).
///
/// Synchronisation:
///   `lastAnalysisTimestampMs` and `isProcessing` are touched both from the
///   AVCapture sample queue (via `captureOutput`) and from the Task that
///   awaits the shared use case (via `defer`). Wrapped in `NSLock` for the
///   tiny critical sections — actor would be cleaner but its async semantics
///   would break the synchronous "should I throttle this frame?" check that
///   AVCapture expects.
final class DateScannerFrameAnalyzer: NSObject,
    AVCaptureVideoDataOutputSampleBufferDelegate {

    /// Which analysis the single AVCapture delegate performs per frame. The
    /// scanner runs CÓDIGO first (`.barcode`) then FECHA (`.date`); the VM
    /// flips this at the phase transition. Switching the MODE (not the
    /// delegate or the session) keeps `CameraPreviewView` perfectly stable —
    /// nothing about the capture pipeline is rebuilt.
    enum Mode { case barcode, date }

    /// `PassthroughSubject` (NOT `CurrentValueSubject`) so duplicate
    /// emissions reach the subscriber. The VM's stability counter requires
    /// counting consecutive identical `Success(sameDate)` results; a
    /// deduplicating publisher would silently break that.
    let results = PassthroughSubject<DateScanResult, Never>()

    /// Barcode-phase counterpart of [results]. Same non-deduplicating
    /// contract (the VM counts consecutive identical barcodes). `nil` = no
    /// barcode in this frame.
    let barcodeResults = PassthroughSubject<BarcodeResult?, Never>()

    private let scanUseCase = KoinIosKt.getScanExpirationDateUseCase()
    private let barcodeScanner = KoinIosKt.getBarcodeScanner()
    private let minIntervalMs: Double
    private let ciContext = CIContext(options: nil)

    private let lock = NSLock()
    private var lastAnalysisTimestampMs: Double?
    private var isProcessing: Bool = false
    private var mode: Mode = .barcode
    /// One-shot guard for `warmUp()`. The Vision text model stays loaded
    /// process-wide once warmed, so this only matters per analyzer instance.
    private var didWarmUp: Bool = false

    init(minIntervalMs: Double = 500.0) {
        self.minIntervalMs = minIntervalMs
        super.init()
    }

    /// Flips the per-frame analysis mode. Thread-safe (touched from the VM on
    /// main; read on the AVCapture queue).
    func setMode(_ newMode: Mode) {
        lock.lock()
        mode = newMode
        lock.unlock()
    }

    // ========================================================================
    // ROOT-CAUSE FIX — "FECHA no detecta tras CÓDIGO"
    // ========================================================================

    /// Eagerly loads Apple Vision's `.accurate` text-recognition model into the
    /// process by running ONE OCR pass through the **same singleton recognizer
    /// this analyzer uses for live frames** (`scanUseCase`).
    ///
    /// Root cause: the CÓDIGO phase only ever exercises the BARCODE request, so
    /// the OCR recognizer's `VNRecognizeTextRequest` was first created COLD on
    /// the first live FECHA frame. Vision's first text request is 5–10 s cold;
    /// under live capture + single-flight that whole window comes back empty →
    /// the FECHA phase "no detecta nada". The previous warm-up in
    /// `DateScannerView` warmed a THROWAWAY `TextRecognizer()` — a *different*
    /// object — so it never warmed the recognizer the analyzer actually calls.
    ///
    /// Holds the single-flight slot for the duration so this warm-up pass cannot
    /// race a live frame on the shared request; live frames resume against an
    /// already-warm model. One-shot per analyzer instance.
    func warmUp() {
        lock.lock()
        if didWarmUp {
            lock.unlock()
            return
        }
        didWarmUp = true
        isProcessing = true   // gate live-frame OCR until the model is warm
        lock.unlock()

        print("🔥 OCR warm-up started (real singleton recognizer)")
        let warmUpImage = DateScannerFrameAnalyzer.makeWarmUpImage()
        // Strong `self` capture on purpose — see the note in `captureOutput`.
        Task {
            defer {
                self.releaseProcessing()
                print("🔥 OCR warm-up finished — model warm; FECHA frames now flow")
            }
            _ = try? await self.scanUseCase.invoke(imageData: ImageData(uiImage: warmUpImage))
        }
    }

    /// Robustness (mirrors Android's fresh-analyzer-per-phase): clears the
    /// per-frame transient state so FECHA starts from a clean slate and never
    /// inherits the CÓDIGO phase's throttle timestamp or in-flight flag. Not the
    /// root cause — `isProcessing` already self-releases per frame via the
    /// Task's `defer` — but it removes any carried-over `lastAnalysisTimestampMs`
    /// and guards against a stuck flag. Call BEFORE `warmUp()` so the warm-up's
    /// slot claim survives.
    func resetTransientState() {
        lock.lock()
        isProcessing = false
        lastAnalysisTimestampMs = nil
        lock.unlock()
    }

    /// 256×64 white card with a date-like string. Forces Vision's `.accurate`
    /// recognition weights to page in — a blank image short-circuits the
    /// pipeline and only inits the lightweight detector, not the recogniser.
    private static func makeWarmUpImage() -> UIImage {
        let size = CGSize(width: 256, height: 64)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            let text = "TEST 01/01/2030" as NSString
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 28, weight: .medium),
                .foregroundColor: UIColor.black,
            ]
            text.draw(at: CGPoint(x: 16, y: 16), withAttributes: attributes)
        }
    }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        let now = Date().timeIntervalSince1970 * 1000.0

        // Synchronously decide: should we analyse this frame? Quickly.
        lock.lock()
        if isProcessing {
            lock.unlock()
            return
        }
        if let last = lastAnalysisTimestampMs, now - last < minIntervalMs {
            lock.unlock()
            return
        }
        // Optimistically claim the slot. We'll release it in the Task's
        // defer. If conversion fails below, release immediately.
        isProcessing = true
        lastAnalysisTimestampMs = now
        let currentMode = mode   // snapshot under the lock
        lock.unlock()

        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            releaseProcessing()
            return
        }
        // CRITICAL: convert to UIImage SYNCHRONOUSLY here, inside the
        // AVCapture callback, BEFORE launching the Task that calls Vision.
        //
        // Why: Vision lazy-loads its OCR model on the first call (5–10 s
        // cold). If the Task were given the raw `CMSampleBuffer` /
        // `CVPixelBuffer`, the buffer would stay retained for the full
        // duration of that first Vision call — and AVCaptureSession's
        // buffer pool is tiny (typically 3–4 slots). One stuck buffer
        // starves the camera preview of frames, blanking it for ~10 s on
        // the first frame after `startRunning()`.
        //
        // `createCGImage` renders the pixel buffer into a standalone
        // `CGImage`; once it returns, the source buffer is no longer
        // referenced, so when this callback returns the slot is recycled
        // immediately even if Vision is still warming up.
        guard let uiImage = uiImage(from: pixelBuffer) else {
            releaseProcessing()
            return
        }

        // Capture `self` STRONGLY for the lifetime of this one analysis. The
        // single-flight slot MUST be released; a `[weak self]` that nilled out
        // mid-flight would skip the `defer` (the old `guard let self else
        // { return }` returned BEFORE the defer was registered) and leak
        // `isProcessing = true` forever — after which no future frame would
        // ever be analysed. The retain is bounded by a single Vision call.
        Task {
            defer { self.releaseProcessing() }
            do {
                let imageData = ImageData(uiImage: uiImage)
                switch currentMode {
                case .barcode:
                    let barcode = try await self.barcodeScanner.detect(imageData: imageData)
                    await MainActor.run { self.barcodeResults.send(barcode) }
                case .date:
                    // 🔬 TEMP DIAGNOSTIC (CÓDIGO→FECHA OCR bug). Remove once confirmed.
                    print("🟢 FECHA frame → OCR (currentMode=\(currentMode))")
                    let result = try await self.scanUseCase.invoke(imageData: imageData)
                    let rawTextDesc = (result as? DateScanResult.NoDateFound)?.rawText
                        ?? "<\(type(of: result))>"
                    print("🔎 OCR ran. result=\(type(of: result)) rawText='\(rawTextDesc)'")
                    await MainActor.run { self.results.send(result) }
                }
            } catch {
                // Barcode failures are "no barcode this frame" (nil); OCR
                // failures surface as a `DateScanResult.Error` (the K/N export
                // name — see `swift_name("DateScanResult.Error")` in `Shared.h`).
                if currentMode == .date {
                    print("🔎 OCR threw: \(error.localizedDescription)")
                    let errorResult = DateScanResult.Error(message: error.localizedDescription)
                    await MainActor.run { self.results.send(errorResult) }
                } else {
                    await MainActor.run { self.barcodeResults.send(nil) }
                }
            }
        }
    }

    private func releaseProcessing() {
        lock.lock()
        isProcessing = false
        lock.unlock()
    }

    /// Converts a `CVPixelBuffer` to `UIImage` with the orientation that
    /// matches a portrait-held device using the back camera. The sensor on
    /// most iPhones is mounted such that the natural buffer orientation is
    /// landscape-right; for portrait UIs we render with `.right` so the
    /// resulting image reads upright, which is what Vision expects.
    private func uiImage(from pixelBuffer: CVPixelBuffer) -> UIImage? {
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        guard let cgImage = ciContext.createCGImage(ciImage, from: ciImage.extent) else {
            return nil
        }
        return UIImage(cgImage: cgImage, scale: 1.0, orientation: .right)
    }
}
