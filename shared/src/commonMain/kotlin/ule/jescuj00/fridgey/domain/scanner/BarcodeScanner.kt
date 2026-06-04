package ule.jescuj00.fridgey.domain.scanner

/**
 * On-device barcode detector backed by ML Kit (Android) or Vision (iOS).
 * Reuses the SAME [ImageData] wrapper as [TextRecognizer], so the camera
 * pipeline feeds one frame type to both the OCR (date) and barcode phases.
 *
 * [detect] returns the first usable EAN/UPC found in the frame, or `null`
 * when the frame contains none.
 */
expect class BarcodeScanner() {
    suspend fun detect(imageData: ImageData): BarcodeResult?
    fun close()
}
