package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.domain.scanner.DateScanResult
import ule.jescuj00.fridgey.domain.scanner.ImageData
import ule.jescuj00.fridgey.domain.scanner.TextRecognizer
import ule.jescuj00.fridgey.domain.util.DateParser

class ScanExpirationDateUseCase(private val textRecognizer: TextRecognizer) {

    /**
     * Scans an image for text, extracts dates, filters to valid expiration dates,
     * and returns the best result.
     */
    suspend operator fun invoke(imageData: ImageData): DateScanResult {
        val ocrResult = try {
            textRecognizer.recognizeText(imageData)
        } catch (e: Exception) {
            return DateScanResult.Error("Error de reconocimiento: ${e.message}")
        }

        if (ocrResult.rawText.isBlank()) {
            return DateScanResult.NoDateFound("")
        }

        val validDates = ocrResult.detectedDates
            .filter { DateParser.isValidExpirationDate(it) }

        return when {
            validDates.isEmpty() -> DateScanResult.NoDateFound(ocrResult.rawText)
            validDates.size == 1 -> DateScanResult.Success(validDates.first())
            else -> DateScanResult.MultipleDatesFound(validDates)
        }
    }
}
