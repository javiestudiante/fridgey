package ule.jescuj00.fridgey.domain.scanner

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import ule.jescuj00.fridgey.domain.util.DateParser

/**
 * Wraps a platform image. On iOS, pass a UIImage from the camera/gallery.
 * The actual ML Kit processing is done in Swift via a helper that calls back
 * into Kotlin, since GoogleMLKit CocoaPods cannot be consumed directly from
 * Kotlin/Native. See IosTextRecognizerHelper.swift in the iosApp target.
 */
actual class ImageData(val image: Any)

actual class TextRecognizer {

    /**
     * iOS text recognition is delegated to Swift via [IosOcrBridge].
     * Set this callback from the iOS app at startup:
     *   TextRecognizer.iosOcrBridge = { image -> rawText }
     */
    actual suspend fun recognizeText(imageData: ImageData): OcrResult {
        val bridge = iosOcrBridge
            ?: return OcrResult(rawText = "", detectedDates = emptyList(), confidence = 0f, processingTimeMs = 0)

        val startTime = currentTimeMs()
        val rawText = bridge(imageData.image)
        val elapsed = currentTimeMs() - startTime
        val dates = DateParser.extractDates(rawText)

        return OcrResult(
            rawText = rawText,
            detectedDates = dates,
            confidence = if (rawText.isNotEmpty()) 0.8f else 0f,
            processingTimeMs = elapsed
        )
    }

    actual fun close() {
        // No-op; ML Kit lifecycle managed by Swift side
    }

    companion object {
        /**
         * Bridge function set from Swift. Takes a platform image (UIImage)
         * and returns the recognized text string.
         */
        var iosOcrBridge: (suspend (Any) -> String)? = null
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun currentTimeMs(): Long =
        (NSDate().timeIntervalSince1970 * 1000).toLong()
}
