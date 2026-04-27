package ule.jescuj00.fridgey.domain.scanner

/** Platform-specific wrapper for a camera/image frame. */
expect class ImageData

/** On-device text recognizer backed by ML Kit (Android) or Vision (iOS). */
expect class TextRecognizer() {
    suspend fun recognizeText(imageData: ImageData): OcrResult
    fun close()
}
