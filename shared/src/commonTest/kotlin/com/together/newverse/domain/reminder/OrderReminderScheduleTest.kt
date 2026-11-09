package com.together.newverse.domain.reminder

import com.together.newverse.domain.config.OrderScheduleConfig
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.OrderReminderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlinx.datetime.TimeZone

class OrderReminderScheduleTest {

    private val default = OrderReminderSchedule.DEFAULT

    /** dayIndex counts Monday as 0, so 4 is Friday — the Ökomarkt's day. */
    private val friday = Market(id = "oeko", dayIndex = 4).scheduleConfig(default)
    private val saturday = Market(id = "fw", dayIndex = 5).scheduleConfig(default)

    // ===== the market's own week =====

    @Test
    fun `a Friday market has its deadline on Wednesday`() {
        assertEquals(FRIDAY, friday.pickupDay)
        assertEquals(WEDNESDAY, friday.deadlineDay)
    }

    @Test
    fun `selectable days run up to the deadline and end on it`() {
        assertEquals(listOf(SATURDAY, SUNDAY, MONDAY, TUESDAY), OrderReminderSchedule.selectableDays(default))
        assertEquals(listOf(SUNDAY, MONDAY, TUESDAY, WEDNESDAY), OrderReminderSchedule.selectableDays(friday))
    }

    @Test
    fun `default days are the day before the deadline and the deadline day`() {
        assertEquals(setOf(MONDAY, TUESDAY), OrderReminderSchedule.defaultDays(default))
        assertEquals(setOf(TUESDAY, WEDNESDAY), OrderReminderSchedule.defaultDays(friday))
    }

    @Test
    fun `the settings default matches the default schedule`() {
        assertEquals(OrderReminderSchedule.defaultDays(default), OrderReminderSettings.DEFAULT_DAYS)
    }

    // ===== moving days with the market =====

    @Test
    fun `days keep their distance to the deadline when the market changes`() {
        val moved = OrderReminderSchedule.shiftDays(setOf(MONDAY, TUESDAY), default, friday)
        assertEquals(setOf(TUESDAY, WEDNESDAY), moved)
    }

    @Test
    fun `shifting across the end of the week wraps round`() {
        // Saturday market: deadline Thursday, two days later than the default.
        val moved = OrderReminderSchedule.shiftDays(setOf(SATURDAY, SUNDAY), default, saturday)
        assertEquals(setOf(MONDAY, TUESDAY), moved)
    }

    @Test
    fun `shifting there and back returns the original days`() {
        val days = setOf(SATURDAY, TUESDAY)
        val there = OrderReminderSchedule.shiftDays(days, default, friday)
        assertEquals(days, OrderReminderSchedule.shiftDays(there, friday, default))
    }

    @Test
    fun `an unchanged deadline leaves the days alone`() {
        val days = setOf(SUNDAY, TUESDAY)
        assertEquals(days, OrderReminderSchedule.shiftDays(days, default, default))
    }

    // ===== text =====

    @Test
    fun `deadline time is zero padded`() {
        assertEquals("23:59", OrderReminderSchedule.deadlineTime(default))
        val early = object : OrderScheduleConfig {
            override val pickupDay = FRIDAY
            override val deadlineDay = WEDNESDAY
            override val deadlineHour = 8
            override val deadlineMinute = 5
        }
        assertEquals("08:05", OrderReminderSchedule.deadlineTime(early))
    }

    // ===== storage form =====

    @Test
    fun `a schedule survives being stored`() {
        val decoded = OrderReminderSchedule.decode(OrderReminderSchedule.encode(friday))
        assertTrue(decoded != null && OrderReminderSchedule.sameSchedule(friday, decoded))
    }

    @Test
    fun `anything not written by encode decodes to null`() {
        assertNull(OrderReminderSchedule.decode(null))
        assertNull(OrderReminderSchedule.decode(""))
        assertNull(OrderReminderSchedule.decode("5,3,23"))
        assertNull(OrderReminderSchedule.decode("8,3,23,59"))
        assertNull(OrderReminderSchedule.decode("5,3,24,0"))
        assertNull(OrderReminderSchedule.decode("a,b,c,d"))
    }

    @Test
    fun `different weeks are not the same schedule`() {
        assertFalse(OrderReminderSchedule.sameSchedule(default, friday))
    }

    // ===== the evaluator on a Friday market's week =====

    private val utc = TimeZone.UTC
    private val settings = OrderReminderSettings(
        enabled = true,
        days = setOf(TUESDAY, WEDNESDAY),
        hour = 18,
        minute = 0
    )

    /** Wednesday 10 January 2024, 18:00 — the Friday market's deadline day. */
    private val wednesdayAt1800 = Instant.parse("2024-01-10T18:00:00Z")

    @Test
    fun `the upcoming cycle of a Friday market is that Friday`() {
        assertEquals(
            "20240112",
            OrderReminderEvaluator.nextOpenPickupCycle(wednesdayAt1800, utc, friday)
        )
    }

    @Test
    fun `an order for the Friday counts as already ordered`() {
        // The bug this guards: judged against the default week, the worker looked for
        // an order on Thursday the 11th, never found the Friday one, and reminded a
        // buyer who had already ordered.
        assertFalse(
            OrderReminderEvaluator.isDue(settings, setOf("20240112"), wednesdayAt1800, utc, friday)
        )
        assertTrue(
            OrderReminderEvaluator.isDue(settings, emptySet(), wednesdayAt1800, utc, friday)
        )
    }

    @Test
    fun `on the default week the same Wednesday is already past the deadline`() {
        // Thursday's deadline was Tuesday, so the upcoming cycle is the next week's —
        // which is why the worker must not fall back to the default for a Friday buyer.
        assertEquals(
            "20240118",
            OrderReminderEvaluator.nextOpenPickupCycle(wednesdayAt1800, utc, default)
        )
    }
}
