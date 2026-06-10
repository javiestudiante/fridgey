import AVFoundation
import Combine
import CoreImage
import Foundation
import Shared
import UIKit

/// CameraX-less sibling of `DateScannerFrameAnalyzer` for the "unirse con
/// código" QR flow. Deliberately a SIBLING, not a new mode on the existing
/// analyzer: the product scanner (CÓDIGO/FECHA phases and their
/// stability-counting contract) stays untouched; this one only calls the
/// iOS-only `BarcodeScanner.detectQr` and needs no duplicate-emission
/// guarantees — the join flow stops at the first hit.
///
/// Same frame discipline as the existing analyzer: ~2 fps throttle,
/// single-flight, and `NSLock` around the tiny critical sections shared
/// between the AVCapture sample queue and the analysis `Task`.
final class QrFrameAnalyzer: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {

    /// `nil` = no QR in this frame. `PassthroughSubject` to mirror the
    /// sibling analyzers' non-deduplicating contract.
    let results = PassthroughSubject<BarcodeResult?, Never>()

    private let barcodeScanner = KoinIosKt.getBarcodeScanner()
    private let ciContext = CIContext(options: nil)

    private let minIntervalMs: Int64 = 500
    private var lastAnalysisTimestampMs: Int64?
    private var isProcessing = false
    private let lock = NSLock()

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)

        lock.lock()
        let throttled = lastAnalysisTimestampMs.map { now - $0 < minIntervalMs } ?? false
        if isProcessing || throttled {
            lock.unlock()
            return
        }
        lastAnalysisTimestampMs = now
        isProcessing = true
        lock.unlock()

        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer),
              let uiImage = uiImage(from: pixelBuffer) else {
            lock.lock(); isProcessing = false; lock.unlock()
            return
        }

        let imageData = ImageData(uiImage: uiImage)
        Task { [weak self] in
            guard let self = self else { return }
            defer {
                self.lock.lock()
                self.isProcessing = false
                self.lock.unlock()
            }
            // A detection failure is "no QR this frame" (nil), not an error
            // state — keep the camera scanning.
            let result = try? await self.barcodeScanner.detectQr(imageData: imageData)
            self.results.send(result)
        }
    }

    private func uiImage(from pixelBuffer: CVPixelBuffer) -> UIImage? {
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        guard let cgImage = ciContext.createCGImage(ciImage, from: ciImage.extent) else {
            return nil
        }
        return UIImage(cgImage: cgImage)
    }
}
