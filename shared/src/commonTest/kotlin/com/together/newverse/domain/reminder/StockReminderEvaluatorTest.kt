package com.together.newverse.domain.reminder

import com.together.newverse.domain.config.OrderScheduleConfig
import com.together.newverse.domain.model.StockReminderLine
import com.together.newverse.domain.model.StockReminderSettings
import com.together.newverse.domain.model.StockReminderSnapshot
import com.together.newverse.util.OrderDateUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/**
 * The refill reminder fires once per market cycle, after the edit deadline has frozen
 * the demand it reports. `doc/design/stock-driven-availability-design.md` §3.
 */
class StockReminderEvaluatorTest {

    private val zone = TimeZone.of("Europe/Berlin")

    /** Thursday 22 October 2026 — a pickup day under the default schedule. */
    private val pickupDate = LocalDate(2026, 10, 22)
    private val pickUp = pickupDate.atTime(LocalTime(10, 0)).toInstant(zone)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        date.atTime(LocalTime(hour, minute)).toInstant(zone)

    private fun snapshot(
        lines: List<StockReminderLine> = listOf(StockReminderLine("Äpfel", 12.0, "kg")),
        pickUpMillis: Long = pickUp.toEpochMilliseconds()
    ) = StockReminderSnapshot(
        cycle = OrderDateUtils.formatDateKey(Instant.fromEpochMilliseconds(pickUpMillis), zone),
        pickUpDate = pickUpMillis,
        lines = lines
    )

    private val on = StockReminderSettings(enabled = true)

    // --- when it is posted ----------------------------------------------------

    @Test
    fun `a night deadline is posted the next morning`() {
        // The deadline is Tuesday 23:59; a shopping list that buzzes at 23:59 is one
        // the seller switches off. Wednesday 07:00, before the wholesaler.
        val post = StockReminderEvaluator.postAt(pickUp, zone)

        assertEquals(at(LocalDate(2026, 10, 21), StockReminderEvaluator.POST_HOUR), post)
    }

    @Test
    fun `a deadline earlier than the posting hour is posted on its own day`() {
        // A seller whose deadline falls in the morning should not wait a whole night
        // for a list that is already final.
        val earlyDeadline = object : OrderScheduleConfig {
            override val pickupDay = DayOfWeek.THURSDAY
            override val deadlineDay = DayOfWeek.TUESDAY
            override val deadlineHour = 5
            override val deadlineMinute = 0
        }

        val post = StockReminderEvaluator.postAt(pickUp, zone, earlyDeadline)

        // Tuesday 07:00, not Wednesday: the deadline had already gone by.
        assertEquals(at(LocalDate(2026, 10, 20), StockReminderEvaluator.POST_HOUR), post)
    }

    // --- whether it is due ----------------------------------------------------

    @Test
    fun `nothing is due before the deadline has passed`() {
        // Monday: buyers can still add to their orders, so the list can still grow.
        assertTrue(!StockReminderEvaluator.isDue(on, snapshot(), at(LocalDate(2026, 10, 19), 9), zone))
        // Tuesday morning, hours before the 23:59 deadline: still not final.
        assertTrue(!StockReminderEvaluator.isDue(on, snapshot(), at(LocalDate(2026, 10, 20), 9), zone))
    }

    @Test
    fun `it is due once the deadline has passed`() {
        assertTrue(
            StockReminderEvaluator.isDue(on, snapshot(), at(LocalDate(2026, 10, 21), 7), zone)
        )
    }

    @Test
    fun `it is still due later that morning`() {
        assertTrue(
            StockReminderEvaluator.isDue(on, snapshot(), at(LocalDate(2026, 10, 21), 11), zone)
        )
    }

    @Test
    fun `it is not due in the middle of the night after`() {
        // Past the grace window: a list posted at 03:00 wakes the seller for nothing.
        assertTrue(
            !StockReminderEvaluator.isDue(on, snapshot(), at(LocalDate(2026, 10, 22), 3), zone)
        )
    }

    @Test
    fun `a switched-off reminder is never due`() {
        assertTrue(
            !StockReminderEvaluator.isDue(
                StockReminderSettings(enabled = false),
                snapshot(),
                at(LocalDate(2026, 10, 21), 7),
                zone
            )
        )
    }

    @Test
    fun `nothing short means silence`() {
        // The good case does not deserve a notification saying so.
        assertTrue(
            !StockReminderEvaluator.isDue(
                on,
                snapshot(lines = emptyList()),
                at(LocalDate(2026, 10, 21), 7),
                zone
            )
        )
    }

    @Test
    fun `an empty snapshot means silence`() {
        assertTrue(
            !StockReminderEvaluator.isDue(on, StockReminderSnapshot(), at(LocalDate(2026, 10, 21), 7), zone)
        )
    }

    @Test
    fun `it fires once per cycle however often the worker runs`() {
        val fired = on.copy(lastFiredCycle = snapshot().cycle)

        assertTrue(!StockReminderEvaluator.isDue(fired, snapshot(), at(LocalDate(2026, 10, 21), 7), zone))
        assertTrue(!StockReminderEvaluator.isDue(fired, snapshot(), at(LocalDate(2026, 10, 21), 12), zone))
    }

    @Test
    fun `the next cycle fires again`() {
        val fired = on.copy(lastFiredCycle = snapshot().cycle)
        val nextWeek = snapshot(pickUpMillis = (pickUp + 7.days).toEpochMilliseconds())

        // Pickup Thursday the 29th, deadline Tuesday the 27th, posted Wednesday.
        assertTrue(
            StockReminderEvaluator.isDue(fired, nextWeek, at(LocalDate(2026, 10, 28), 7), zone)
        )
    }

    @Test
    fun `a list for a market that has already happened is not posted`() {
        // This is what stops a stale snapshot surfacing weeks later.
        assertTrue(!StockReminderEvaluator.isDue(on, snapshot(), pickUp + 1.hours, zone))
    }

    // --- what gets armed ------------------------------------------------------

    @Test
    fun `the next occurrence is the posting moment while it is ahead`() {
        val next = StockReminderEvaluator.nextOccurrence(on, snapshot(), at(LocalDate(2026, 10, 19), 9), zone)

        assertEquals(at(LocalDate(2026, 10, 21), StockReminderEvaluator.POST_HOUR), next)
    }

    @Test
    fun `a moment just missed runs now rather than never`() {
        // The device was probably asleep when it came round.
        val now = at(LocalDate(2026, 10, 21), 10)

        assertEquals(now, StockReminderEvaluator.nextOccurrence(on, snapshot(), now, zone))
    }

    @Test
    fun `nothing is armed when there is nothing to say`() {
        val now = at(LocalDate(2026, 10, 19), 9)

        assertNull(StockReminderEvaluator.nextOccurrence(on, snapshot(lines = emptyList()), now, zone))
        assertNull(StockReminderEvaluator.nextOccurrence(on, StockReminderSnapshot(), now, zone))
        assertNull(
            StockReminderEvaluator.nextOccurrence(
                StockReminderSettings(enabled = false), snapshot(), now, zone
            )
        )
    }

    @Test
    fun `nothing is armed for a cycle already fired`() {
        // The next snapshot the app writes arms the cycle after this one.
        assertNull(
            StockReminderEvaluator.nextOccurrence(
                on.copy(lastFiredCycle = snapshot().cycle),
                snapshot(),
                at(LocalDate(2026, 10, 19), 9),
                zone
            )
        )
    }

    @Test
    fun `nothing is armed for a market that has passed`() {
        assertNull(StockReminderEvaluator.nextOccurrence(on, snapshot(), pickUp + 1.hours, zone))
    }

    @Test
    fun `nothing is armed once the whole window has gone by`() {
        // Wednesday 22:00: past 07:00 + 14h, and the market is the next morning.
        assertNull(
            StockReminderEvaluator.nextOccurrence(on, snapshot(), at(LocalDate(2026, 10, 21), 22), zone)
        )
    }
}
