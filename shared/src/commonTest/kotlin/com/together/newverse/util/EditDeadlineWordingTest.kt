package com.together.newverse.util

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * The edit deadline belongs to the order, not to a weekday named in a string.
 *
 * It sits a fixed distance before pickup, so a Thursday market and a Saturday market get
 * different deadline days from the same rule. The basket's error messages used to name
 * Tuesday outright, which was wrong for every market that is not Thursday's.
 */
class EditDeadlineWordingTest {

    private val zone = TimeZone.of("Europe/Berlin")

    private fun deadlineDayOf(pickup: LocalDate): DayOfWeek =
        OrderDateUtils.calculateEditDeadline(
            pickup.atTime(LocalTime(0, 0)).toInstant(zone),
            timeZone = zone
        ).toLocalDateTime(zone).date.dayOfWeek

    @Test
    fun `a Thursday market has a Tuesday deadline`() {
        // 2026-10-29 is a Thursday — the case the hardcoded string happened to get right.
        assertTrue(deadlineDayOf(LocalDate(2026, 10, 29)) == DayOfWeek.TUESDAY)
    }

    @Test
    fun `a Saturday market has a Thursday deadline, not a Tuesday one`() {
        // 2026-10-31 is a Saturday. The old wording told this buyer "Dienstag 23:59",
        // which is two days earlier than their actual deadline.
        assertTrue(deadlineDayOf(LocalDate(2026, 10, 31)) == DayOfWeek.THURSDAY)
    }

    @Test
    fun `a Friday market has a Wednesday deadline`() {
        assertTrue(deadlineDayOf(LocalDate(2026, 10, 30)) == DayOfWeek.WEDNESDAY)
    }

    @Test
    fun `the deadline is always two days before the pickup day`() {
        for (day in 1..28) {
            val pickup = LocalDate(2026, 11, day)
            val deadline = OrderDateUtils.calculateEditDeadline(
                pickup.atTime(LocalTime(0, 0)).toInstant(zone),
                timeZone = zone
            ).toLocalDateTime(zone).date
            assertTrue(
                pickup.toEpochDays() - deadline.toEpochDays() == 2L,
                "pickup $pickup -> deadline $deadline"
            )
        }
    }
}
