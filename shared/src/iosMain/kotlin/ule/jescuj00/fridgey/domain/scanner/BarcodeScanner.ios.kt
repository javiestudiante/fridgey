package ule.jescuj00.fridgey.domain.scanner

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Vision.VNBarcodeObservation
import platform.Vision.VNBarcodeSymbologyEAN13
import platform.Vision.VNBarcodeSymbologyEAN8
import platform.Vision.VNBarcodeSymbologyUPCE
import platform.Vision.VNDetectBarcodesRequest
import platform.Vision.VNImageRequestHandler

/**
 * iOS barcode detector backed by the Vision framework (same framework as the
 * OCR path in [TextRecognizer]). Restricted to retail symbologies. UPC-A is
 * reported by Vision under EAN-13 (with a leading 0), so we don't list it
 * separately. Runs synchronously off the main thread, mirroring the OCR path.
 *
 * A fresh [VNImageRequestHandler] is created per frame (Apple requires it not
 * be reused); `request.results` is snapshotted into an immutable value before
 * returning.
 */
actual class BarcodeScanner {

    actual suspend fun detect(imageData: ImageData): BarcodeResult? =
        withContext(Dispatchers.Default) {
            runVisionBarcode(imageData)
        }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun runVisionBarcode(imageData: ImageData): BarcodeResult? {
        val cgImage = imageData.uiImage.CGImage ?: return null

        val request = VNDetectBarcodesRequest()
        request.symbologies = listOf(
            VNBarcodeSymbologyEAN13,
            VNBarcodeSymbologyEAN8,
            VNBarcodeSymbologyUPCE,
        )

        val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any>())
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            handler.performRequests(listOf(request), errorPtr.ptr)
        }

        val observation = (request.results ?: emptyList<Any?>())
            .filterIsInstance<VNBarcodeObservation>()
            .firstOrNull { !it.payloadStringValue.isNullOrBlank() }
            ?: return null

        return BarcodeResult(
            rawValue = observation.payloadStringValue!!,
            format = observation.symbology.toString(),
        )
    }

    actual fun close() {
        // No long-lived Vision state to release (handler + request are
        // created per call). Symmetric with the Android client's close().
    }
}
