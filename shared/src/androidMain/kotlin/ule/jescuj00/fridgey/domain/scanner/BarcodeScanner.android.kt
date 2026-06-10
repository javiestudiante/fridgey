package ule.jescuj00.fridgey.domain.scanner

import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android barcode detector via ML Kit Barcode Scanning.
 *
 * The format set is parameterized per CLIENT, not per call: ML Kit clients
 * are configured at construction, so each format family gets its own
 * reusable client.
 *  - [detect] (the common expect API) stays restricted to the retail product
 *    symbologies (EAN-13 / EAN-8 / UPC-A / UPC-E) so QR codes and other 2D
 *    symbologies don't produce spurious hits in the product scanner.
 *  - [detectQr] is an Android-only extra member (NOT part of the common
 *    expect — iOS will grow its own counterpart in a later session) used by
 *    the "unirse con código" flow; its QR client is created lazily so the
 *    product scanner never pays for it.
 *
 * Mirrors [TextRecognizer]'s shape: reusable clients + a callback→coroutine
 * bridge, consuming the shared [ImageData] (which wraps ML Kit's `InputImage`).
 */
actual class BarcodeScanner {

    private val productScanner = buildClient(
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
    )

    private val qrScannerLazy = lazy { buildClient(Barcode.FORMAT_QR_CODE) }
    private val qrScanner by qrScannerLazy

    private fun buildClient(format: Int, vararg moreFormats: Int) =
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(format, *moreFormats)
                .build()
        )

    actual suspend fun detect(imageData: ImageData): BarcodeResult? =
        detectWith(productScanner, imageData)

    /**
     * Android-only: first QR code found in the frame (invite-joining flow),
     * or `null` when the frame contains none.
     */
    suspend fun detectQr(imageData: ImageData): BarcodeResult? =
        detectWith(qrScanner, imageData)

    private suspend fun detectWith(
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        imageData: ImageData,
    ): BarcodeResult? {
        val barcodes = suspendCancellableCoroutine { cont ->
            scanner.process(imageData.inputImage)
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { cont.resume(null) }
        }
        val first = barcodes?.firstOrNull { !it.rawValue.isNullOrBlank() } ?: return null
        return BarcodeResult(
            rawValue = first.rawValue!!,
            format = formatName(first.format),
        )
    }

    actual fun close() {
        productScanner.close()
        if (qrScannerLazy.isInitialized()) qrScanner.close()
    }

    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        else -> "OTHER"
    }
}
