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
import platform.Vision.VNBarcodeSymbologyQR
import platform.Vision.VNBarcodeSymbologyUPCE
import platform.Vision.VNDetectBarcodesRequest
import platform.Vision.VNImageRequestHandler

/**
 * iOS barcode detector backed by the Vision framework (same framework as the
 * OCR path in [TextRecognizer]). The symbology set is parameterized per
 * request, mirroring the Android actual:
 *  - [detect] (the common expect API) stays restricted to retail symbologies
 *    so QR codes don't produce spurious hits in the product scanner. UPC-A is
 *    reported by Vision under EAN-13 (with a leading 0), so we don't list it
 *    separately.
 *  - [detectQr] is an iOS-only extra member (NOT part of the common expect),
 *    the mirror of the Android-only `detectQr`, used by the "unirse con
 *    código" flow.
 *
 * Runs synchronously off the main thread, mirroring the OCR path. A fresh
 * [VNImageRequestHandler] is created per frame (Apple requires it not be
 * reused); `request.results` is snapshotted into an immutable value before
 * returning.
 */
actual class BarcodeScanner {

    actual suspend fun detect(imageData: ImageData): BarcodeResult? =
        withContext(Dispatchers.Default) {
            runVisionBarcode(imageData, SIMBOLOGIAS_PRODUCTO)
        }

    /**
     * iOS-only: first QR code found in the frame (invite-joining flow),
     * or `null` when the frame contains none.
     */
    suspend fun detectQr(imageData: ImageData): BarcodeResult? =
        withContext(Dispatchers.Default) {
            runVisionBarcode(imageData, listOf(VNBarcodeSymbologyQR))
        }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun runVisionBarcode(imageData: ImageData, symbologies: List<Any?>): BarcodeResult? {
        val cgImage = imageData.uiImage.CGImage ?: return null

        val request = VNDetectBarcodesRequest()
        request.symbologies = symbologies

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

    private companion object {
        val SIMBOLOGIAS_PRODUCTO = listOf(
            VNBarcodeSymbologyEAN13,
            VNBarcodeSymbologyEAN8,
            VNBarcodeSymbologyUPCE,
        )
    }
}
