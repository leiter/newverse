package com.together.newverse.domain.reminder

import com.together.newverse.domain.config.OrderScheduleConfig
import com.together.newverse.domain.model.OrderReminderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone

/**
 * The reference week is January 2024: Mon 8th, Tue 9th, Wed 10th, Thu 11th (pickup),
 * next Thursday the 18th. Deadline is Tue 23:59, so an order placed on Monday is for
 * cycle 20240111.
 */
class OrderReminderEvaluatorTest {

    private val utc = TimeZone.UTC
    private val berlin = TimeZone.of("Europe/Berlin")

    private val config = object : OrderScheduleConfig {
        override val pickupDay: DayOfWeek = DayOfWeek.THURSDAY
        override val deadlineDay: DayOfWeek = DayOfWeek.TUESDAY
        override val deadlineHour: Int = 23
        override val deadlineMinute: Int = 59
    }

    private val settings = OrderReminderSettings(
        enabled = true,
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
        hour = 18,
        minute = 0
    )

    private val mondayAt1800 = Instant.parse("2024-01-08T18:00:00Z")
    private val mondayAt1759 = Instant.parse("2024-01-08T17:59:00Z")
    private val mondayAt2000 = Instant.parse("2024-01-08T20:00:00Z")
    private val mondayAt2300 = Instant.parse("2024-01-08T23:00:00Z")
    private val wednesdayAt1800 = Instant.parse("2024-01-10T18:00:00Z")

    // ===== isDue =====

    @Test
    fun `due on a chosen day at the chosen time`() {
        assertTrue(OrderReminderEvaluator.isDue(settings, emptySet(), mondayAt1800, utc, config))
    }

    @Test
    fun `not due before the chosen time`() {
        assertFalse(OrderReminderEvaluator.isDue(settings, emptySet(), mondayAt1759, utc, config))
    }

    @Test
    fun `still due within the grace window when the worker wakes late`() {
        assertTrue(OrderReminderEvaluator.isDue(settings, emptySet(), mondayAt2000, utc, config))
    }

    @Test
    fun `dropped once the grace window has passed`() {
        // 23:00 is five hours after the slot — too late to be useful, and nobody wants a
        // reminder at that hour.
        assertFalse(OrderReminderEvaluator.isDue(settings, emptySet(), mondayAt2300, utc, config))
    }

    @Test
    fun `not due on a day that was not chosen`() {
        assertFalse(OrderReminderEvaluator.isDue(settings, emptySet(), wednesdayAt1800, utc, config))
    }

    @Test
    fun `not due when disabled`() {
        val off = settings.copy(enabled = false)
        assertFalse(OrderReminderEvaluator.isDue(off, emptySet(), mondayAt1800, utc, config))
    }

    @Test
    fun `not due when no day is selected`() {
        val noDays = settings.copy(days = emptySet())
        assertFalse(OrderReminderEvaluator.isDue(noDays, emptySet(), mondayAt1800, utc, config))
    }

    @Test
    fun `fires at most once a day`() {
        val alreadyFired = settings.copy(lastFiredDate = "2024-01-08")
        assertFalse(OrderReminderEvaluator.isDue(alreadyFired, emptySet(), mondayAt1800, utc, config))
        // The following chosen day is unaffected.
        val tuesday = Instant.parse("2024-01-09T18:00:00Z")
        assertTrue(OrderReminderEvaluator.isDue(alreadyFired, emptySet(), tuesday, utc, config))
    }

    @Test
    fun `suppressed when an order already exists for the upcoming pickup`() {
        assertFalse(
            OrderReminderEvaluator.isDue(settings, setOf("20240111"), mondayAt1800, utc, config)
        )
    }

    @Test
    fun `an order for a different cycle does not suppress it`() {
        // Last week's pickup; says nothing about the coming Thursday.
        assertTrue(
            OrderReminderEvaluator.isDue(settings, setOf("20240104"), mondayAt1800, utc, config)
        )
    }

    @Test
    fun `fires despite an existing order when the buyer asked for that`() {
        val always = settings.copy(onlyWhenNotOrdered = false)
        assertTrue(
            OrderReminderEvaluator.isDue(always, setOf("20240111"), mondayAt1800, utc, config)
        )
    }

    @Test
    fun `the chosen time is local, not UTC`() {
        // Berlin is UTC+1 in January, so the 18:00 Berlin slot falls at 17:00Z.
        val sixteenZ = Instant.parse("2024-01-08T16:00:00Z")   // 17:00 Berlin — too early
        val seventeenZ = Instant.parse("2024-01-08T17:00:00Z") // 18:00 Berlin — the slot
        assertFalse(OrderReminderEvaluator.isDue(settings, emptySet(), sixteenZ, berlin, config))
        assertTrue(OrderReminderEvaluator.isDue(settings, emptySet(), seventeenZ, berlin, config))
        // The same instant is not yet due in UTC, where the slot is an hour later.
        assertFalse(OrderReminderEvaluator.isDue(settings, emptySet(), seventeenZ, utc, config))
    }

    // ===== hasOrderedForUpcomingPickup =====

    @Test
    fun `the upcoming cycle rolls over once the deadline has passed`() {
        // Wednesday: this Thursday is closed, so the relevant cycle is the 18th.
        assertTrue(
            OrderReminderEvaluator.hasOrderedForUpcomingPickup(
                setOf("20240118"), wednesdayAt1800, utc, config
            )
        )
        assertFalse(
            OrderReminderEvaluator.hasOrderedForUpcomingPickup(
                setOf("20240111"), wednesdayAt1800, utc, config
            )
        )
    }

    // ===== nextOccurrence =====

    @Test
    fun `next occurrence is today when its time is still ahead`() {
        assertEquals(
            mondayAt1800,
            OrderReminderEvaluator.nextOccurrence(settings, mondayAt1759, utc)
        )
    }

    @Test
    fun `next occurrence skips today once its time has passed`() {
        assertEquals(
            Instant.parse("2024-01-09T18:00:00Z"),
            OrderReminderEvaluator.nextOccurrence(settings, mondayAt1800, utc)
        )
    }

    @Test
    fun `a single chosen day wraps to the same weekday next week`() {
        val mondayOnly = settings.copy(days = setOf(DayOfWeek.MONDAY))
        assertEquals(
            Instant.parse("2024-01-15T18:00:00Z"),
            OrderReminderEvaluator.nextOccurrence(mondayOnly, mondayAt1800, utc)
        )
    }

    @Test
    fun `no next occurrence when the reminder can never fire`() {
        assertNull(OrderReminderEvaluator.nextOccurrence(settings.copy(enabled = false), mondayAt1800, utc))
        assertNull(OrderReminderEvaluator.nextOccurrence(settings.copy(days = emptySet()), mondayAt1800, utc))
    }

    @Test
    fun `next occurrence is resolved in the given time zone`() {
        assertEquals(
            Instant.parse("2024-01-08T17:00:00Z"),
            OrderReminderEvaluator.nextOccurrence(settings, Instant.parse("2024-01-08T16:00:00Z"), berlin)
        )
    }
}
