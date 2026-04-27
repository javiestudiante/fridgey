package ule.jescuj00.fridgey.domain.util

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DateParserTest {

    // --- parseDate ---

    @Test
    fun parsesSlashFullYear() {
        assertEquals(LocalDate(2026, 3, 15), DateParser.parseDate("15/03/2026"))
    }

    @Test
    fun parsesDashFullYear() {
        assertEquals(LocalDate(2026, 3, 15), DateParser.parseDate("15-03-2026"))
    }

    @Test
    fun parsesDotFullYear() {
        assertEquals(LocalDate(2026, 3, 15), DateParser.parseDate("15.03.2026"))
    }

    @Test
    fun parsesShortYear() {
        assertEquals(LocalDate(2026, 3, 15), DateParser.parseDate("15/03/26"))
    }

    @Test
    fun parsesShortYearDash() {
        assertEquals(LocalDate(2026, 3, 15), DateParser.parseDate("15-03-26"))
    }

    @Test
    fun shortYearOver50MapsTo1900s() {
        assertEquals(LocalDate(1999, 12, 31), DateParser.parseDate("31/12/99"))
    }

    @Test
    fun parsesDayMonthOnly() {
        val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
        assertEquals(LocalDate(currentYear, 3, 15), DateParser.parseDate("15/03"))
    }

    @Test
    fun parsesDayMonthDash() {
        val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
        assertEquals(LocalDate(currentYear, 6, 1), DateParser.parseDate("01-06"))
    }

    @Test
    fun returnsNullForGarbage() {
        assertNull(DateParser.parseDate("hello world"))
    }

    @Test
    fun returnsNullForInvalidDate() {
        // 32nd of January doesn't exist
        assertNull(DateParser.parseDate("32/01/2026"))
    }

    @Test
    fun euroFormatPrioritizedOverUs() {
        // 05/03/2026 → March 5th (EU), not May 3rd (US)
        val date = DateParser.parseDate("05/03/2026")
        assertNotNull(date)
        assertEquals(3, date.monthNumber) // March
        assertEquals(5, date.dayOfMonth)
    }

    @Test
    fun fallsBackToUsWhenDayOver12() {
        // 13 can't be a month, so 01/13/2026 → January 13th? No — EU first: day=01 month=13 invalid, swap to month=01 day=13
        // Actually 01/13/2026 → first=1 second=13: first(1) in 1..31, second(13) NOT in 1..12 → try US: first(1) in 1..12, second(13) in 1..31 → Jan 13
        val date = DateParser.parseDate("01/13/2026")
        assertNotNull(date)
        assertEquals(1, date.monthNumber)
        assertEquals(13, date.dayOfMonth)
    }

    // --- OCR normalization ---

    @Test
    fun normalizesOcrMisreads() {
        // "O1/O3/2O26" → "01/03/2026"
        val date = DateParser.parseDate("O1/O3/2O26")
        assertEquals(LocalDate(2026, 3, 1), date)
    }

    // --- extractDates ---

    @Test
    fun extractsDateAfterCadKeyword() {
        val dates = DateParser.extractDates("CAD: 15/03/2026")
        assertTrue(dates.isNotEmpty())
        assertEquals(LocalDate(2026, 3, 15), dates.first())
    }

    @Test
    fun extractsDateAfterConsumirAntes() {
        val dates = DateParser.extractDates("CONSUMIR ANTES 15-03-26")
        assertTrue(dates.isNotEmpty())
        assertEquals(LocalDate(2026, 3, 15), dates.first())
    }

    @Test
    fun extractsDateWithSurroundingText() {
        val dates = DateParser.extractDates("15.03.2026 LOTE A123")
        assertTrue(dates.isNotEmpty())
        assertEquals(LocalDate(2026, 3, 15), dates.first())
    }

    @Test
    fun extractsMultipleDates() {
        val dates = DateParser.extractDates("FAB: 01/01/2026 CAD: 15/06/2026")
        assertTrue(dates.size >= 2)
    }

    @Test
    fun returnsEmptyForNoDates() {
        val dates = DateParser.extractDates("No dates here at all")
        assertTrue(dates.isEmpty())
    }

    // --- isValidExpirationDate ---

    @Test
    fun futureDate5YearsIsValid() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val future = LocalDate(today.year + 3, today.monthNumber, today.dayOfMonth)
        assertTrue(DateParser.isValidExpirationDate(future))
    }

    @Test
    fun pastDateIsInvalid() {
        val past = LocalDate(2020, 1, 1)
        assertTrue(!DateParser.isValidExpirationDate(past))
    }

    @Test
    fun todayIsValid() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        assertTrue(DateParser.isValidExpirationDate(today))
    }

    // --- DatePatterns.expandYear ---

    @Test
    fun expandYearBelow50() {
        assertEquals(2026, DatePatterns.expandYear(26))
        assertEquals(2000, DatePatterns.expandYear(0))
        assertEquals(2049, DatePatterns.expandYear(49))
    }

    @Test
    fun expandYear50AndAbove() {
        assertEquals(1950, DatePatterns.expandYear(50))
        assertEquals(1999, DatePatterns.expandYear(99))
    }

    // --- DatePatterns.normalizeOcrText ---

    @Test
    fun normalizePreservesKeywords() {
        val result = DatePatterns.normalizeOcrText("CAD O1/O3/2O26")
        assertEquals("CAD 01/03/2026", result)
    }

    @Test
    fun normalizeIgnoresNonDateTokens() {
        val result = DatePatterns.normalizeOcrText("LOTE B123")
        assertEquals("LOTE B123", result)
    }
}
