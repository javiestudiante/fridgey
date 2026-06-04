package ule.jescuj00.fridgey.domain.scanner

import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android barcode detector via ML Kit Barcode Scanning. Restricted to the
 * retail product symbologies (EAN-13 / EAN-8 / UPC-A / UPC-E) so QR codes and
 * other 2D symbologies don't produce spurious hits.
 *
 * Mirrors [TextRecognizer]'s shape: a reusable client + a callback→coroutine
 * bridge, consuming the shared [ImageData] (which wraps ML Kit's `InputImage`).
 */
actual class BarcodeScanner {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .build()
    )

    actual suspend fun detect(imageData: ImageData): BarcodeResult? {
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
        scanner.close()
    }

    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        else -> "OTHER"
    }
}
