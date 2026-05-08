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

    /// `PassthroughSubject` (NOT `CurrentValueSubject`) so duplicate
    /// emissions reach the subscriber. The VM's stability counter requires
    /// counting consecutive identical `Success(sameDate)` results; a
    /// deduplicating publisher would silently break that.
    let results = PassthroughSubject<DateScanResult, Never>()

    private let scanUseCase = KoinIosKt.getScanExpirationDateUseCase()
    private let minIntervalMs: Double
    private let ciContext = CIContext(options: nil)

    private let lock = NSLock()
    private var lastAnalysisTimestampMs: Double?
    private var isProcessing: Bool = false

    init(minIntervalMs: Double = 500.0) {
        self.minIntervalMs = minIntervalMs
        super.init()
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

        Task { [weak self] in
            guard let self = self else { return }
            defer { self.releaseProcessing() }
            do {
                let imageData = ImageData(uiImage: uiImage)
                let result = try await self.scanUseCase.invoke(imageData: imageData)
                await MainActor.run {
                    self.results.send(result)
                }
            } catch {
                // `DateScanResult.Error` is the K/N export name (see
                // `swift_name("DateScanResult.Error")` in `Shared.h`).
                let errorResult = DateScanResult.Error(
                    message: error.localizedDescription
                )
                await MainActor.run {
                    self.results.send(errorResult)
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
