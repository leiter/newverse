package com.together.newverse.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookingPeriodTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    // --- ISO weeks (German KW) ---

    @Test
    fun `a week runs Monday to Sunday`() {
        val week = BookingPeriod.weekOf(LocalDate(2026, 9, 24))   // Thursday

        assertEquals(BookingPeriod.Week(2026, 39), week)
        assertEquals(LocalDate(2026, 9, 21), week.start)
        assertEquals(LocalDate(2026, 9, 28), week.endExclusive)
    }

    @Test
    fun `Monday and Sunday belong to the same week`() {
        assertEquals(BookingPeriod.weekOf(LocalDate(2026, 9, 21)), BookingPeriod.weekOf(LocalDate(2026, 9, 27)))
    }

    @Test
    fun `early January can belong to the previous year's last week`() {
        // 1 Jan 2027 is a Friday: its week's Thursday is 31 Dec 2026.
        assertEquals(BookingPeriod.Week(2026, 53), BookingPeriod.weekOf(LocalDate(2027, 1, 1)))
        assertEquals(BookingPeriod.Week(2027, 1), BookingPeriod.weekOf(LocalDate(2027, 1, 4)))
    }

    @Test
    fun `late December can belong to the next year's first week`() {
        // 1 Jan 2026 is a Thursday, so KW 1/2026 starts on Monday 29 Dec 2025.
        assertEquals(BookingPeriod.Week(2026, 1), BookingPeriod.weekOf(LocalDate(2025, 12, 29)))
        assertEquals(LocalDate(2025, 12, 29), BookingPeriod.Week(2026, 1).start)
    }

    @Test
    fun `stepping through weeks crosses the year end`() {
        val kw53 = BookingPeriod.Week(2026, 53)

        assertEquals(BookingPeriod.Week(2027, 1), kw53.next())
        assertEquals(kw53, BookingPeriod.Week(2027, 1).previous())
    }

    @Test
    fun `a week out of range is refused`() {
        assertFailsWith<IllegalArgumentException> { BookingPeriod.Week(2026, 54) }
    }

    // --- months ---

    @Test
    fun `a month runs from the first to the first of the next`() {
        val september = BookingPeriod.monthOf(LocalDate(2026, 9, 24))

        assertEquals(BookingPeriod.Month(2026, 9), september)
        assertEquals(LocalDate(2026, 9, 1), september.start)
        assertEquals(LocalDate(2026, 10, 1), september.endExclusive)
    }

    @Test
    fun `stepping through months crosses the year end`() {
        assertEquals(BookingPeriod.Month(2027, 1), BookingPeriod.Month(2026, 12).next())
        assertEquals(BookingPeriod.Month(2026, 12), BookingPeriod.Month(2027, 1).previous())
    }

    // --- boundaries in time ---

    @Test
    fun `a period runs from local midnight to local midnight`() {
        val september = BookingPeriod.Month(2026, 9)
        val lastMinute = LocalDateTime(2026, 9, 30, 23, 59).toInstant(berlin).toEpochMilliseconds()
        val firstMinute = LocalDateTime(2026, 10, 1, 0, 0).toInstant(berlin).toEpochMilliseconds()

        assertTrue(lastMinute in september.startMillis(berlin) until september.endMillis(berlin))
        assertFalse(firstMinute in september.startMillis(berlin) until september.endMillis(berlin))
    }

    @Test
    fun `a week across the end of summer time has one hour more`() {
        // Summer time ends on Sunday 25 Oct 2026: that week has one hour more.
        val week = BookingPeriod.weekOf(LocalDate(2026, 10, 25))
        val hours = (week.endMillis(berlin) - week.startMillis(berlin)) / 3_600_000

        assertEquals(7 * 24 + 1, hours.toInt())
    }

    @Test
    fun `contains uses the local date`() {
        val week = BookingPeriod.Week(2026, 39)

        assertTrue(LocalDate(2026, 9, 27) in week)
        assertFalse(LocalDate(2026, 9, 28) in week)
    }
}
