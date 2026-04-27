package ule.jescuj00.fridgey.domain.scanner

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import ule.jescuj00.fridgey.domain.util.DateParser
import kotlin.coroutines.resume

actual class ImageData(val inputImage: InputImage)

actual class TextRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    actual suspend fun recognizeText(imageData: ImageData): OcrResult {
        val startTime = System.currentTimeMillis()

        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(imageData.inputImage)
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { e -> cont.resume(null) }
        }

        val elapsed = System.currentTimeMillis() - startTime
        val rawText = visionText?.text.orEmpty()
        val dates = DateParser.extractDates(rawText)
        val confidence = if (visionText != null) {
            visionText.textBlocks.mapNotNull { block ->
                block.lines.mapNotNull { it.confidence }.average().takeIf { !it.isNaN() }
            }.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f
        } else 0f

        return OcrResult(
            rawText = rawText,
            detectedDates = dates,
            confidence = confidence,
            processingTimeMs = elapsed
        )
    }

    actual fun close() {
        recognizer.close()
    }
}
