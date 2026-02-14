package com.together.newverse.util

import com.together.newverse.domain.config.OrderScheduleConfig
import kotlinx.datetime.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Comprehensive unit tests for OrderDateUtils - the critical business logic
 * for order pickup dates, edit deadlines, and order window status.
 */
class OrderDateUtilsTest {

    // Using UTC for predictable test results
    private val utc = TimeZone.UTC

    // Berlin timezone for DST tests
    private val berlinTz = TimeZone.of("Europe/Berlin")

    // Default config: Thursday pickup, Tuesday 23:59 deadline
    private val defaultConfig = object : OrderScheduleConfig {
        override val pickupDay: DayOfWeek = DayOfWeek.THURSDAY
        override val deadlineDay: DayOfWeek = DayOfWeek.TUESDAY
        override val deadlineHour: Int = 23
        override val deadlineMinute: Int = 59
    }

    // ===== Test Week Reference (January 2024) =====
    // Monday Jan 8, Tuesday Jan 9, Wednesday Jan 10, Thursday Jan 11
    // Next week: Monday Jan 15, Tuesday Jan 16, Wednesday Jan 17, Thursday Jan 18

    // Fixed test instants
    private val mondayMorning = Instant.parse("2024-01-08T10:00:00Z")
    private val tuesdayMorning = Instant.parse("2024-01-09T10:00:00Z")
    private val tuesdayBeforeDeadline = Instant.parse("2024-01-09T22:00:00Z")
    private val tuesdayAtDeadline = Instant.parse("2024-01-09T22:59:59Z")
    private val tuesdayAfterDeadline = Instant.parse("2024-01-09T23:00:00Z")
    private val wednesdayMorning = Instant.parse("2024-01-10T10:00:00Z")
    private val thursdayMorning = Instant.parse("2024-01-11T10:00:00Z")
    private val fridayMorning = Instant.parse("2024-01-12T10:00:00Z")
    private val saturdayMorning = Instant.parse("2024-01-13T10:00:00Z")
    private val sundayMorning = Instant.parse("2024-01-14T10:00:00Z")

    // Expected pickup dates
    private val thisThursday = Instant.parse("2024-01-11T00:00:00Z")
    private val nextThursday = Instant.parse("2024-01-18T00:00:00Z")

    // =========================================================================
    // A. Core Calculation Tests - calculateNextPickupDate
    // =========================================================================

    @Test
    fun `calculateNextPickupDate on Monday returns this Thursday`() {
        val result = OrderDateUtils.calculateNextPickupDate(mondayMorning, utc, defaultConfig)
        assertEquals(thisThursday, result)
    }

    @Test
    fun `calculateNextPickupDate on Tuesday before deadline returns this Thursday`() {
        val result = OrderDateUtils.calculateNextPickupDate(tuesdayBeforeDeadline, utc, defaultConfig)
        assertEquals(thisThursday, result)
    }

    @Test
    fun `calculateNextPickupDate on Tuesday at deadline returns this Thursday`() {
        // At 22:59:59 UTC, deadline is 23:59 UTC, so we're still before
        val result = OrderDateUtils.calculateNextPickupDate(tuesdayAtDeadline, utc, defaultConfig)
        assertEquals(thisThursday, result)
    }

    @Test
    fun `calculateNextPickupDate on Wednesday returns next Thursday`() {
        val result = OrderDateUtils.calculateNextPickupDate(wednesdayMorning, utc, defaultConfig)
        assertEquals(nextThursday, result)
    }

    @Test
    fun `calculateNextPickupDate on Thursday returns next Thursday`() {
        val result = OrderDateUtils.calculateNextPickupDate(thursdayMorning, utc, defaultConfig)
        assertEquals(nextThursday, result)
    }

    @Test
    fun `calculateNextPickupDate on Friday returns next Thursday`() {
        val result = OrderDateUtils.calculateNextPickupDate(fridayMorning, utc, defaultConfig)
        assertEquals(nextThursday, result)
    }

    @Test
    fun `calculateNextPickupDate on Saturday returns next Thursday`() {
        val result = OrderDateUtils.calculateNextPickupDate(saturdayMorning, utc, defaultConfig)
        assertEquals(nextThursday, result)
    }

    @Test
    fun `calculateNextPickupDate on Sunday returns next Thursday`() {
        val result = OrderDateUtils.calculateNextPickupDate(sundayMorning, utc, defaultConfig)
        assertEquals(nextThursday, result)
    }

    // =========================================================================
    // B. calculateEditDeadline Tests
    // =========================================================================

    @Test
    fun `calculateEditDeadline returns Tuesday 23_59_59 for Thursday pickup`() {
        val deadline = OrderDateUtils.calculateEditDeadline(thisThursday, utc, defaultConfig)

        val deadlineLocal = deadline.toLocalDateTime(utc)
        assertEquals(DayOfWeek.TUESDAY, deadlineLocal.dayOfWeek)
        assertEquals(23, deadlineLocal.hour)
        assertEquals(59, deadlineLocal.minute)
        assertEquals(59, deadlineLocal.second)
    }

    @Test
    fun `calculateEditDeadline with custom config uses custom deadline day`() {
        val fridayPickupConfig = object : OrderScheduleConfig {
            override val pickupDay: DayOfWeek = DayOfWeek.FRIDAY
            override val deadlineDay: DayOfWeek = DayOfWeek.WEDNESDAY
            override val deadlineHour: Int = 18
            override val deadlineMinute: Int = 0
        }

        // Friday Jan 12 pickup
        val fridayPickup = Instant.parse("2024-01-12T00:00:00Z")
        val deadline = OrderDateUtils.calculateEditDeadline(fridayPickup, utc, fridayPickupConfig)

        val deadlineLocal = deadline.toLocalDateTime(utc)
        assertEquals(DayOfWeek.WEDNESDAY, deadlineLocal.dayOfWeek)
        assertEquals(18, deadlineLocal.hour)
        assertEquals(0, deadlineLocal.minute)
    }

    @Test
    fun `calculateEditDeadline throws for non-pickup day`() {
        val wednesday = Instant.parse("2024-01-10T00:00:00Z")

        assertFailsWith<IllegalArgumentException> {
            OrderDateUtils.calculateEditDeadline(wednesday, utc, defaultConfig)
        }
    }

    // =========================================================================
    // C. Order Window Status Tests
    // =========================================================================

    @Test
    fun `getOrderWindowStatus OPEN before deadline`() {
        val status = OrderDateUtils.getOrderWindowStatus(thisThursday, mondayMorning, utc, defaultConfig)
        assertEquals(OrderWindowStatus.OPEN, status)
    }

    @Test
    fun `getOrderWindowStatus OPEN on Tuesday before deadline time`() {
        val status = OrderDateUtils.getOrderWindowStatus(thisThursday, tuesdayBeforeDeadline, utc, defaultConfig)
        assertEquals(OrderWindowStatus.OPEN, status)
    }

    @Test
    fun `getOrderWindowStatus DEADLINE_PASSED on Wednesday before pickup`() {
        val status = OrderDateUtils.getOrderWindowStatus(thisThursday, wednesdayMorning, utc, defaultConfig)
        assertEquals(OrderWindowStatus.DEADLINE_PASSED, status)
    }

    @Test
    fun `getOrderWindowStatus PICKUP_PASSED after Thursday pickup`() {
        val status = OrderDateUtils.getOrderWindowStatus(thisThursday, fridayMorning, utc, defaultConfig)
        assertEquals(OrderWindowStatus.PICKUP_PASSED, status)
    }

    // =========================================================================
    // D. canEditOrder Tests
    // =========================================================================

    @Test
    fun `canEditOrder true on Monday`() {
        val canEdit = OrderDateUtils.canEditOrder(thisThursday, mondayMorning, utc, defaultConfig)
        assertTrue(canEdit)
    }

    @Test
    fun `canEditOrder true on Tuesday morning`() {
        val canEdit = OrderDateUtils.canEditOrder(thisThursday, tuesdayMorning, utc, defaultConfig)
        assertTrue(canEdit)
    }

    @Test
    fun `canEditOrder true at exact deadline`() {
        // Deadline is Tuesday 23:59:59.999
        val deadline = OrderDateUtils.calculateEditDeadline(thisThursday, utc, defaultConfig)
        val canEdit = OrderDateUtils.canEditOrder(thisThursday, deadline, utc, defaultConfig)
        assertTrue(canEdit)
    }

    @Test
    fun `canEditOrder false on Wednesday`() {
        val canEdit = OrderDateUtils.canEditOrder(thisThursday, wednesdayMorning, utc, defaultConfig)
        assertFalse(canEdit)
    }

    @Test
    fun `canEditOrder false after pickup`() {
        val canEdit = OrderDateUtils.canEditOrder(thisThursday, fridayMorning, utc, defaultConfig)
        assertFalse(canEdit)
    }

    // =========================================================================
    // E. Deadline Warning Level Tests
    // =========================================================================

    @Test
    fun `getDeadlineWarningLevel NONE more than 48 hours remaining`() {
        // Monday 10:00, deadline is Tuesday 23:59 - ~38 hours remaining
        // Need to go back further - Sunday
        val sunday = Instant.parse("2024-01-07T10:00:00Z")
        val level = OrderDateUtils.getDeadlineWarningLevel(thisThursday, sunday, utc, defaultConfig)
        assertEquals(DeadlineWarningLevel.NONE, level)
    }

    @Test
    fun `getDeadlineWarningLevel INFO 24 to 48 hours remaining`() {
        // Monday 10:00, deadline is Tuesday 23:59 - ~38 hours remaining
        val level = OrderDateUtils.getDeadlineWarningLevel(thisThursday, mondayMorning, utc, defaultConfig)
        assertEquals(DeadlineWarningLevel.INFO, level)
    }

    @Test
    fun `getDeadlineWarningLevel WARNING 6 to 24 hours remaining`() {
        // Tuesday 10:00, deadline is Tuesday 23:59 - ~14 hours remaining
        val level = OrderDateUtils.getDeadlineWarningLevel(thisThursday, tuesdayMorning, utc, defaultConfig)
        assertEquals(DeadlineWarningLevel.WARNING, level)
    }

    @Test
    fun `getDeadlineWarningLevel URGENT 1 to 6 hours remaining`() {
        // Tuesday 19:00, deadline is Tuesday 23:59 - ~5 hours remaining
        val tuesdayEvening = Instant.parse("2024-01-09T19:00:00Z")
        val level = OrderDateUtils.getDeadlineWarningLevel(thisThursday, tuesdayEvening, utc, defaultConfig)
        assertEquals(DeadlineWarningLevel.URGENT, level)
    }

    @Test
    fun `getDeadlineWarningLevel CRITICAL less than 1 hour remaining`() {
        // Tuesday 23:30, deadline is Tuesday 23:59 - ~30 minutes remaining
        val tuesdayLate = Instant.parse("2024-01-09T23:30:00Z")
        val level = OrderDateUtils.getDeadlineWarningLevel(thisThursday, tuesdayLate, utc, defaultConfig)
        assertEquals(DeadlineWarningLevel.CRITICAL, level)
    }

    @Test
    fun `getDeadlineWarningLevel EXPIRED after deadline`() {
        val level = OrderDateUtils.getDeadlineWarningLevel(thisThursday, wednesdayMorning, utc, defaultConfig)
        assertEquals(DeadlineWarningLevel.EXPIRED, level)
    }

    // =========================================================================
    // F. Time Formatting Tests - formatTimeUntilDeadline
    // =========================================================================

    @Test
    fun `formatTimeUntilDeadline days and hours`() {
        // Sunday 10:00, deadline is Tuesday 23:59 - 2 days, ~14 hours
        val sunday = Instant.parse("2024-01-07T10:00:00Z")
        val formatted = OrderDateUtils.formatTimeUntilDeadline(thisThursday, sunday, utc, defaultConfig)
        assertTrue(formatted.contains("days") && formatted.contains("hours"))
    }

    @Test
    fun `formatTimeUntilDeadline hours and minutes`() {
        // Tuesday 19:00, deadline is Tuesday 23:59 - ~5 hours
        val tuesdayEvening = Instant.parse("2024-01-09T19:00:00Z")
        val formatted = OrderDateUtils.formatTimeUntilDeadline(thisThursday, tuesdayEvening, utc, defaultConfig)
        assertTrue(formatted.contains("hour") && formatted.contains("minute"))
    }

    @Test
    fun `formatTimeUntilDeadline minutes only`() {
        // Tuesday 23:30, deadline is Tuesday 23:59 - ~30 minutes
        val tuesdayLate = Instant.parse("2024-01-09T23:30:00Z")
        val formatted = OrderDateUtils.formatTimeUntilDeadline(thisThursday, tuesdayLate, utc, defaultConfig)
        assertTrue(formatted.contains("minute"))
        assertFalse(formatted.contains("hour"))
    }

    @Test
    fun `formatTimeUntilDeadline less than minute`() {
        // Tuesday 23:59:30, deadline is Tuesday 23:59:59
        val almostDeadline = Instant.parse("2024-01-09T23:59:30Z")
        val formatted = OrderDateUtils.formatTimeUntilDeadline(thisThursday, almostDeadline, utc, defaultConfig)
        assertEquals("Less than 1 minute", formatted)
    }

    @Test
    fun `formatTimeUntilDeadline expired`() {
        val formatted = OrderDateUtils.formatTimeUntilDeadline(thisThursday, wednesdayMorning, utc, defaultConfig)
        assertEquals("Deadline passed", formatted)
    }

    // =========================================================================
    // G. timeUntilDeadline Tests
    // =========================================================================

    @Test
    fun `timeUntilDeadline returns period when before deadline`() {
        val period = OrderDateUtils.timeUntilDeadline(thisThursday, mondayMorning, utc, defaultConfig)
        assertNotNull(period, "Period should not be null when before deadline")
        // Verify period has valid time components
        assertTrue(period.days >= 0 || period.hours >= 0 || period.minutes >= 0)
    }

    @Test
    fun `timeUntilDeadline returns null when after deadline`() {
        val period = OrderDateUtils.timeUntilDeadline(thisThursday, wednesdayMorning, utc, defaultConfig)
        assertNull(period)
    }

    // =========================================================================
    // H. Available Dates Tests
    // =========================================================================

    @Test
    fun `getAvailablePickupDates returns correct count`() {
        val dates = OrderDateUtils.getAvailablePickupDates(5, mondayMorning, utc, defaultConfig)
        assertEquals(5, dates.size)
    }

    @Test
    fun `getAvailablePickupDates all are Thursdays`() {
        val dates = OrderDateUtils.getAvailablePickupDates(5, mondayMorning, utc, defaultConfig)

        dates.forEach { date ->
            val localDate = date.toLocalDateTime(utc).date
            assertEquals(DayOfWeek.THURSDAY, localDate.dayOfWeek)
        }
    }

    @Test
    fun `getAvailablePickupDates spaced 7 days apart`() {
        val dates = OrderDateUtils.getAvailablePickupDates(3, mondayMorning, utc, defaultConfig)

        for (i in 0 until dates.size - 1) {
            val current = dates[i].toLocalDateTime(utc).date
            val next = dates[i + 1].toLocalDateTime(utc).date
            val daysBetween = current.daysUntil(next)
            assertEquals(7, daysBetween)
        }
    }

    @Test
    fun `getAvailablePickupDates filters past deadlines`() {
        // On Wednesday after deadline, should not include this Thursday
        val dates = OrderDateUtils.getAvailablePickupDates(3, wednesdayMorning, utc, defaultConfig)

        dates.forEach { date ->
            assertTrue(OrderDateUtils.canEditOrder(date, wednesdayMorning, utc, defaultConfig))
        }
    }

    // =========================================================================
    // I. Display Formatting Tests
    // =========================================================================

    @Test
    fun `formatDateKey returns yyyyMMdd format`() {
        val result = OrderDateUtils.formatDateKey(thisThursday, utc)
        assertEquals("20240111", result)
    }

    @Test
    fun `formatDateKey pads single digit month and day`() {
        // January 5, 2024
        val jan5 = Instant.parse("2024-01-05T00:00:00Z")
        val result = OrderDateUtils.formatDateKey(jan5, utc)
        assertEquals("20240105", result)
    }

    @Test
    fun `formatDisplayDate returns dd_MM_yyyy format`() {
        val result = OrderDateUtils.formatDisplayDate(thisThursday, utc)
        assertEquals("11.01.2024", result)
    }

    @Test
    fun `formatDisplayDateTime returns full format`() {
        val instant = Instant.parse("2024-01-11T10:30:00Z")
        val result = OrderDateUtils.formatDisplayDateTime(instant, utc)
        assertEquals("11.01.2024 10:30", result)
    }

    @Test
    fun `formatDisplayDateTime pads single digit hours and minutes`() {
        val instant = Instant.parse("2024-01-05T05:05:00Z")
        val result = OrderDateUtils.formatDisplayDateTime(instant, utc)
        assertEquals("05.01.2024 05:05", result)
    }

    // =========================================================================
    // J. isPickupDateValid Tests
    // =========================================================================

    @Test
    fun `isPickupDateValid true for valid future date before deadline`() {
        val isValid = OrderDateUtils.isPickupDateValid(thisThursday, mondayMorning, utc, defaultConfig)
        assertTrue(isValid)
    }

    @Test
    fun `isPickupDateValid false for past date`() {
        // Check last Thursday from this Monday's perspective
        val lastThursday = Instant.parse("2024-01-04T00:00:00Z")
        val isValid = OrderDateUtils.isPickupDateValid(lastThursday, mondayMorning, utc, defaultConfig)
        assertFalse(isValid)
    }

    @Test
    fun `isPickupDateValid false when deadline has passed`() {
        // This Thursday, but checked on Wednesday (after deadline)
        val isValid = OrderDateUtils.isPickupDateValid(thisThursday, wednesdayMorning, utc, defaultConfig)
        assertFalse(isValid)
    }

    // =========================================================================
    // K. isCurrentOrderingCycle Tests
    // =========================================================================

    @Test
    fun `isCurrentOrderingCycle true for next pickup date`() {
        val isCurrent = OrderDateUtils.isCurrentOrderingCycle(thisThursday, mondayMorning, utc, defaultConfig)
        assertTrue(isCurrent)
    }

    @Test
    fun `isCurrentOrderingCycle false for future pickup date`() {
        val isCurrent = OrderDateUtils.isCurrentOrderingCycle(nextThursday, mondayMorning, utc, defaultConfig)
        assertFalse(isCurrent)
    }

    // =========================================================================
    // L. Edge Case Tests
    // =========================================================================

    @Test
    fun `leap year February 29 handled correctly`() {
        // 2024 is a leap year, Feb 29 exists
        val feb29 = Instant.parse("2024-02-29T10:00:00Z")

        // Should work without throwing
        val result = OrderDateUtils.calculateNextPickupDate(feb29, utc, defaultConfig)

        val resultDate = result.toLocalDateTime(utc).date
        assertEquals(DayOfWeek.THURSDAY, resultDate.dayOfWeek)
    }

    @Test
    fun `year boundary December to January works`() {
        // Sunday Dec 31, 2023 - next Thursday is Jan 4, 2024
        val dec31 = Instant.parse("2023-12-31T10:00:00Z")
        val result = OrderDateUtils.calculateNextPickupDate(dec31, utc, defaultConfig)

        val resultDate = result.toLocalDateTime(utc).date
        assertEquals(2024, resultDate.year)
        assertEquals(Month.JANUARY, resultDate.month)
        assertEquals(DayOfWeek.THURSDAY, resultDate.dayOfWeek)
    }

    @Test
    fun `DST spring forward March transition in Berlin`() {
        // DST in Europe starts last Sunday of March
        // March 31, 2024 is a Sunday, clocks go forward at 2:00 -> 3:00
        val marchBeforeDST = Instant.parse("2024-03-30T10:00:00Z")

        val result = OrderDateUtils.calculateNextPickupDate(marchBeforeDST, berlinTz, defaultConfig)
        val resultDate = result.toLocalDateTime(berlinTz).date
        assertEquals(DayOfWeek.THURSDAY, resultDate.dayOfWeek)
    }

    @Test
    fun `DST fall back October transition in Berlin`() {
        // DST in Europe ends last Sunday of October
        // October 27, 2024 is a Sunday, clocks go back at 3:00 -> 2:00
        val octBeforeDST = Instant.parse("2024-10-26T10:00:00Z")

        val result = OrderDateUtils.calculateNextPickupDate(octBeforeDST, berlinTz, defaultConfig)
        val resultDate = result.toLocalDateTime(berlinTz).date
        assertEquals(DayOfWeek.THURSDAY, resultDate.dayOfWeek)
    }

    // =========================================================================
    // M. Custom Configuration Tests
    // =========================================================================

    @Test
    fun `custom pickup day Monday works`() {
        val mondayPickupConfig = object : OrderScheduleConfig {
            override val pickupDay: DayOfWeek = DayOfWeek.MONDAY
            override val deadlineDay: DayOfWeek = DayOfWeek.SATURDAY
            override val deadlineHour: Int = 18
            override val deadlineMinute: Int = 0
        }

        // From Wednesday, next Monday is 5 days away
        val result = OrderDateUtils.calculateNextPickupDate(wednesdayMorning, utc, mondayPickupConfig)
        val resultDate = result.toLocalDateTime(utc).date
        assertEquals(DayOfWeek.MONDAY, resultDate.dayOfWeek)
    }

    @Test
    fun `custom deadline day Friday works`() {
        val fridayDeadlineConfig = object : OrderScheduleConfig {
            override val pickupDay: DayOfWeek = DayOfWeek.SUNDAY
            override val deadlineDay: DayOfWeek = DayOfWeek.FRIDAY
            override val deadlineHour: Int = 17
            override val deadlineMinute: Int = 30
        }

        val sundayPickup = Instant.parse("2024-01-14T00:00:00Z")
        val deadline = OrderDateUtils.calculateEditDeadline(sundayPickup, utc, fridayDeadlineConfig)

        val deadlineLocal = deadline.toLocalDateTime(utc)
        assertEquals(DayOfWeek.FRIDAY, deadlineLocal.dayOfWeek)
        assertEquals(17, deadlineLocal.hour)
        assertEquals(30, deadlineLocal.minute)
    }

    @Test
    fun `custom deadline time 18_00 works`() {
        val earlyDeadlineConfig = object : OrderScheduleConfig {
            override val pickupDay: DayOfWeek = DayOfWeek.THURSDAY
            override val deadlineDay: DayOfWeek = DayOfWeek.TUESDAY
            override val deadlineHour: Int = 18
            override val deadlineMinute: Int = 0
        }

        val deadline = OrderDateUtils.calculateEditDeadline(thisThursday, utc, earlyDeadlineConfig)
        val deadlineLocal = deadline.toLocalDateTime(utc)

        assertEquals(18, deadlineLocal.hour)
        assertEquals(0, deadlineLocal.minute)
    }

    // =========================================================================
    // N. Boundary Condition Tests
    // =========================================================================

    @Test
    fun `exactly at midnight pickup day goes to next week`() {
        val thursdayMidnight = Instant.parse("2024-01-11T00:00:00Z")
        val result = OrderDateUtils.calculateNextPickupDate(thursdayMidnight, utc, defaultConfig)

        // On Thursday itself, should get next Thursday
        assertEquals(nextThursday, result)
    }

    @Test
    fun `one second before midnight Tuesday still open`() {
        // Tuesday 23:58:59 - still before 23:59:59 deadline
        val beforeMidnight = Instant.parse("2024-01-09T23:58:59Z")
        val canEdit = OrderDateUtils.canEditOrder(thisThursday, beforeMidnight, utc, defaultConfig)
        assertTrue(canEdit)
    }

    @Test
    fun `one second after deadline cannot edit`() {
        // Get deadline, then add 1 second
        val deadline = OrderDateUtils.calculateEditDeadline(thisThursday, utc, defaultConfig)
        val afterDeadline = Instant.fromEpochMilliseconds(deadline.toEpochMilliseconds() + 1000)

        val canEdit = OrderDateUtils.canEditOrder(thisThursday, afterDeadline, utc, defaultConfig)
        assertFalse(canEdit)
    }

    // =========================================================================
    // O. One Day Singular Form Tests
    // =========================================================================

    @Test
    fun `formatTimeUntilDeadline one day singular`() {
        // Need exactly ~1 day remaining
        // Deadline is Tuesday 23:59, so Monday 23:59 would be 24 hours
        val oneDayBefore = Instant.parse("2024-01-08T23:59:00Z")
        val formatted = OrderDateUtils.formatTimeUntilDeadline(thisThursday, oneDayBefore, utc, defaultConfig)
        assertTrue(formatted.startsWith("1 day"))
    }

    @Test
    fun `formatTimeUntilDeadline one hour singular`() {
        // Need exactly ~1 hour remaining
        val oneHourBefore = Instant.parse("2024-01-09T22:59:00Z")
        val formatted = OrderDateUtils.formatTimeUntilDeadline(thisThursday, oneHourBefore, utc, defaultConfig)
        assertTrue(formatted.startsWith("1 hour"))
    }

    @Test
    fun `formatTimeUntilDeadline one minute singular`() {
        // Need exactly ~1 minute remaining
        val oneMinuteBefore = Instant.parse("2024-01-09T23:58:59Z")
        val formatted = OrderDateUtils.formatTimeUntilDeadline(thisThursday, oneMinuteBefore, utc, defaultConfig)
        assertEquals("1 minute", formatted)
    }
}
