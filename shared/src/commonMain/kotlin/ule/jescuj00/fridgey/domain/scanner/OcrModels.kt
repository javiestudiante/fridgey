package ule.jescuj00.fridgey.domain.scanner

import kotlinx.datetime.LocalDate

/** Raw output from the platform text recognizer. */
data class OcrResult(
    val rawText: String,
    val detectedDates: List<LocalDate>,
    val confidence: Float,
    val processingTimeMs: Long
)

/** High-level result of scanning for an expiration date. */
sealed class DateScanResult {
    data class Success(val date: LocalDate) : DateScanResult()
    data class MultipleDatesFound(val dates: List<LocalDate>) : DateScanResult()
    data class NoDateFound(val rawText: String) : DateScanResult()
    data class Error(val message: String) : DateScanResult()
}
