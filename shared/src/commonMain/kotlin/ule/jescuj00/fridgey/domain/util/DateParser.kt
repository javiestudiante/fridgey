package ule.jescuj00.fridgey.domain.util

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

object DateParser {

    /** Max years into the future an expiration date can be. */
    private const val MAX_FUTURE_YEARS = 5

    /** Keywords that appear near expiration dates on product packaging. */
    private val EXPIRATION_KEYWORDS = listOf(
        // Spanish
        "CAD", "CADUCIDAD", "CONSUMIR ANTES", "FECHA DE CADUCIDAD",
        "CONSUMIR PREFERENTEMENTE", "VENC", "VENCIMIENTO",
        // English
        "EXP", "EXPIRY", "EXPIRATION", "BEST BEFORE", "BB",
        "USE BY", "SELL BY", "BEST BY",
        // French
        "DLC", "DDM", "À CONSOMMER",
        // Portuguese
        "VALIDADE", "VAL",
    )

    /**
     * Tries to parse a single date string in common EU/US formats.
     * Prioritizes DD/MM/YYYY (European) over MM/DD/YYYY (US).
     * Returns null if the string doesn't match any known pattern.
     */
    fun parseDate(text: String): LocalDate? {
        val normalized = DatePatterns.normalizeOcrText(text.trim())

        // Try full-year patterns first (DD/MM/YYYY)
        for (pattern in DatePatterns.FULL_YEAR_PATTERNS) {
            pattern.find(normalized)?.let { match ->
                return buildDateEuropean(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt()
                )
            }
        }

        // Try short-year patterns (DD/MM/YY)
        for (pattern in DatePatterns.SHORT_YEAR_PATTERNS) {
            pattern.find(normalized)?.let { match ->
                return buildDateEuropean(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    DatePatterns.expandYear(match.groupValues[3].toInt())
                )
            }
        }

        // Try day-month only patterns (DD/MM → assume current year)
        val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
        for (pattern in DatePatterns.NO_YEAR_PATTERNS) {
            pattern.find(normalized)?.let { match ->
                return buildDateEuropean(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    currentYear
                )
            }
        }

        return null
    }

    /**
     * Extracts all possible dates from a block of OCR text.
     * Scans around expiration keywords first, then falls back to scanning all text.
     */
    fun extractDates(ocrText: String): List<LocalDate> {
        val normalized = DatePatterns.normalizeOcrText(ocrText)
        val dates = mutableListOf<LocalDate>()

        // First pass: dates near expiration keywords (higher confidence)
        val keywordDates = extractDatesNearKeywords(normalized)
        dates.addAll(keywordDates)

        // Second pass: all date-like patterns in the text
        val allDates = extractAllDates(normalized)
        for (date in allDates) {
            if (date !in dates) dates.add(date)
        }

        return dates
    }

    /**
     * Returns true if the date is a plausible expiration date:
     * not in the past and not more than [MAX_FUTURE_YEARS] years ahead.
     */
    fun isValidExpirationDate(date: LocalDate): Boolean {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val maxDate = LocalDate(today.year + MAX_FUTURE_YEARS, today.monthNumber, today.dayOfMonth)
        return date >= today && date <= maxDate
    }

    // --- Private helpers ---

    /**
     * Looks for expiration keywords and extracts dates from the text surrounding them.
     */
    private fun extractDatesNearKeywords(text: String): List<LocalDate> {
        val upper = text.uppercase()
        val dates = mutableListOf<LocalDate>()

        for (keyword in EXPIRATION_KEYWORDS) {
            val idx = upper.indexOf(keyword)
            if (idx == -1) continue

            // Grab a window of text after the keyword (dates usually follow)
            val start = idx + keyword.length
            val end = (start + 30).coerceAtMost(text.length)
            val window = text.substring(start, end)

            extractAllDates(window).forEach { date ->
                if (date !in dates) dates.add(date)
            }
        }
        return dates
    }

    /**
     * Scans the entire text and returns every parseable date found.
     */
    private fun extractAllDates(text: String): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year

        // Full-year patterns
        for (pattern in DatePatterns.FULL_YEAR_PATTERNS) {
            for (match in pattern.findAll(text)) {
                buildDateEuropean(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt()
                )?.let { if (it !in dates) dates.add(it) }
            }
        }

        // Short-year patterns — only match substrings NOT already consumed by full-year
        for (pattern in DatePatterns.SHORT_YEAR_PATTERNS) {
            for (match in pattern.findAll(text)) {
                // Skip if this position was inside a 4-digit-year match
                if (isInsideFullYearMatch(text, match.range)) continue
                buildDateEuropean(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    DatePatterns.expandYear(match.groupValues[3].toInt())
                )?.let { if (it !in dates) dates.add(it) }
            }
        }

        // No-year patterns — only match if not part of a longer date
        for (pattern in DatePatterns.NO_YEAR_PATTERNS) {
            for (match in pattern.findAll(text)) {
                if (isInsideLongerDate(text, match.range)) continue
                buildDateEuropean(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    currentYear
                )?.let { if (it !in dates) dates.add(it) }
            }
        }

        return dates
    }

    /**
     * Checks if a short-year match overlaps with a full-year match at the same position.
     */
    private fun isInsideFullYearMatch(text: String, range: IntRange): Boolean {
        for (pattern in DatePatterns.FULL_YEAR_PATTERNS) {
            for (match in pattern.findAll(text)) {
                if (range.first >= match.range.first && range.last <= match.range.last) return true
            }
        }
        return false
    }

    /**
     * Checks if a no-year match is actually the prefix of a longer date pattern.
     */
    private fun isInsideLongerDate(text: String, range: IntRange): Boolean {
        val allLonger = DatePatterns.FULL_YEAR_PATTERNS + DatePatterns.SHORT_YEAR_PATTERNS
        for (pattern in allLonger) {
            for (match in pattern.findAll(text)) {
                if (range.first >= match.range.first && range.last <= match.range.last) return true
            }
        }
        return false
    }

    /**
     * Builds a LocalDate interpreting the first two groups as DD/MM (European format).
     * If day > 12 and month <= 12, it's unambiguously DD/MM.
     * If day <= 12 and month > 12, swaps to MM/DD (US).
     * If both <= 12, assumes DD/MM (European priority).
     * Returns null for invalid dates.
     */
    private fun buildDateEuropean(first: Int, second: Int, year: Int): LocalDate? {
        // Try DD/MM first (European priority)
        if (first in 1..31 && second in 1..12) {
            return tryBuildDate(year, second, first)
        }
        // Fall back to MM/DD (US format)
        if (first in 1..12 && second in 1..31) {
            return tryBuildDate(year, first, second)
        }
        return null
    }

    private fun tryBuildDate(year: Int, month: Int, day: Int): LocalDate? {
        return try {
            LocalDate(year, month, day)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
