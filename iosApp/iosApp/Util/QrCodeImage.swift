import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit

/// Renders an invite code as a QR `UIImage`. Mirror of Android's
/// `QrCodeGenerator.kt` (zxing) — same content contract (the RAW code, no
/// dash) and same error-correction level (M), so both platforms produce
/// interchangeable codes.
///
/// Native Core Image (`CIQRCodeGenerator`) on purpose: no third-party
/// dependency needed on iOS, unlike Android where zxing-core fills the gap.
/// CPU-bound — call from a background task; the view side wraps it in
/// `.task` + `Task.detached`.
enum QrCodeImage {

    static func generate(from content: String, sidePx: CGFloat) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(content.utf8)
        filter.correctionLevel = "M"  // parity with zxing ErrorCorrectionLevel.M

        guard let output = filter.outputImage else { return nil }

        // `samplingNearest` keeps the module edges crisp when scaling the
        // tiny native matrix up to display size (linear sampling blurs it).
        let scale = sidePx / output.extent.width
        let scaled = output.samplingNearest()
            .transformed(by: CGAffineTransform(scaleX: scale, y: scale))

        guard let cgImage = CIContext().createCGImage(scaled, from: scaled.extent) else {
            return nil
        }
        return UIImage(cgImage: cgImage)
    }
}
