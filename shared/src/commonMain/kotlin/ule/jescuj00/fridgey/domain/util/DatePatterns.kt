package ule.jescuj00.fridgey.domain.util

object DatePatterns {

    // --- Full-year patterns (4-digit year) ---
    val DD_MM_YYYY_SLASH = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""")
    val DD_MM_YYYY_DASH  = Regex("""(\d{1,2})-(\d{1,2})-(\d{4})""")
    val DD_MM_YYYY_DOT   = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""")

    // --- Short-year patterns (2-digit year) ---
    val DD_MM_YY_SLASH = Regex("""(\d{1,2})/(\d{1,2})/(\d{2})""")
    val DD_MM_YY_DASH  = Regex("""(\d{1,2})-(\d{1,2})-(\d{2})""")
    val DD_MM_YY_DOT   = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{2})""")

    // --- Day/month only (no year) ---
    val DD_MM_SLASH = Regex("""(\d{1,2})/(\d{1,2})""")
    val DD_MM_DASH  = Regex("""(\d{1,2})-(\d{1,2})""")

    /** Ordered longest-first so 4-digit year patterns match before 2-digit ones. */
    val FULL_YEAR_PATTERNS = listOf(DD_MM_YYYY_SLASH, DD_MM_YYYY_DASH, DD_MM_YYYY_DOT)
    val SHORT_YEAR_PATTERNS = listOf(DD_MM_YY_SLASH, DD_MM_YY_DASH, DD_MM_YY_DOT)
    val NO_YEAR_PATTERNS = listOf(DD_MM_SLASH, DD_MM_DASH)

    // --- OCR character substitution map ---
    private val OCR_REPLACEMENTS = mapOf(
        'O' to '0', 'o' to '0',
        'I' to '1', 'l' to '1',
        'S' to '5', 's' to '5',
        'B' to '8',
        'G' to '6',
        'Z' to '2', 'z' to '2',
        'T' to '7',
    )

    /**
     * Normalizes common OCR misreads in segments that look like dates.
     * Only applies substitutions inside digit-like clusters (e.g. "O1/O3/2O26"),
     * leaving alphabetic keywords ("CAD", "CONSUMIR") untouched.
     */
    fun normalizeOcrText(text: String): String {
        // Split on whitespace, process each token independently
        return text.split(Regex("\\s+")).joinToString(" ") { token ->
            if (looksLikeDateToken(token)) normalizeToken(token) else token
        }
    }

    /**
     * A token looks date-like if it contains at least one digit and a separator (/ - .)
     * or is entirely digit-like characters.
     */
    private fun looksLikeDateToken(token: String): Boolean {
        val hasSeparator = token.any { it == '/' || it == '-' || it == '.' }
        val hasDigitLike = token.any { it.isDigit() || it in OCR_REPLACEMENTS }
        return hasSeparator && hasDigitLike
    }

    private fun normalizeToken(token: String): String = buildString {
        for (ch in token) {
            append(if (ch in OCR_REPLACEMENTS && !ch.isDigit()) OCR_REPLACEMENTS[ch] else ch)
        }
    }

    /**
     * Expands a 2-digit year to 4 digits.
     * 00-49 → 2000-2049, 50-99 → 1950-1999.
     */
    fun expandYear(yy: Int): Int = if (yy < 50) 2000 + yy else 1900 + yy
}
