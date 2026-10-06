package com.together.newverse.domain.reminder

import com.together.newverse.data.config.DefaultOrderScheduleConfig
import com.together.newverse.domain.config.OrderScheduleConfig
import com.together.newverse.domain.model.OrderReminderSettings
import com.together.newverse.util.OrderDateUtils
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Decides whether an order reminder is due, and when the next one should be scheduled.
 *
 * Pure: every input is a parameter, so the whole thing is unit-testable without a
 * clock, a device or a network. Both platforms' schedulers delegate here rather than
 * reimplementing the rules.
 *
 * It deliberately needs no access to the order repository. "Has this buyer ordered for
 * the upcoming pickup?" is answered from the cycle keys of
 * `BuyerProfile.placedOrderIds`, which are already `yyyyMMdd` of the pickup date — the
 * same key the reminder derives from the clock. That keeps the evaluation local and
 * offline-safe, which matters because the worker runs when the app is not open.
 */
object OrderReminderEvaluator {

    private val defaultConfig: OrderScheduleConfig = DefaultOrderScheduleConfig()

    /**
     * How late a reminder may still fire after its chosen time. Background work is
     * batched by the OS, so the worker can wake well after the slot; a reminder that
     * arrives at 19:30 instead of 18:00 is still useful. Past this it is dropped
     * rather than delivered at an odd hour, and the next chosen day takes over.
     */
    private const val GRACE_HOURS = 4

    /**
     * Whether a reminder should be posted at [now].
     *
     * @param placedCycles cycle keys (`yyyyMMdd`) the buyer has already placed an order
     *   for — `BuyerProfile.placedOrderIds.keys`.
     */
    fun isDue(
        settings: OrderReminderSettings,
        placedCycles: Set<String>,
        now: Instant,
        timeZone: TimeZone,
        config: OrderScheduleConfig = defaultConfig
    ): Boolean {
        if (!settings.isActive) return false

        val localNow = now.toLocalDateTime(timeZone)
        val today = localNow.date
        if (today.dayOfWeek !in settings.days) return false

        // Already fired today. Two chosen days in one week still give two reminders;
        // several worker runs on one day give one.
        if (settings.lastFiredDate == today.toString()) return false

        val slot = today.atTime(settings.hour, settings.minute).toInstant(timeZone)
        if (now < slot) return false
        if (now >= slot + GRACE_HOURS.hours) return false

        if (settings.onlyWhenNotOrdered && hasOrderedForUpcomingPickup(placedCycles, now, timeZone, config)) {
            return false
        }
        return true
    }

    /**
     * Whether an order already exists for the pickup this reminder would be about.
     *
     * [OrderDateUtils.calculateNextPickupDate] rolls to the following week once the
     * deadline has passed, so the "upcoming" cycle is always one the buyer can still
     * order for — no separate deadline check is needed here.
     */
    fun hasOrderedForUpcomingPickup(
        placedCycles: Set<String>,
        now: Instant,
        timeZone: TimeZone,
        config: OrderScheduleConfig = defaultConfig
    ): Boolean {
        val upcoming = OrderDateUtils.calculateNextPickupDate(now, timeZone, config)
        return OrderDateUtils.formatDateKey(upcoming, timeZone) in placedCycles
    }

    /**
     * The next instant a reminder could fire, or null when the settings can never fire.
     *
     * Used to arm the next piece of background work. Today counts when its slot is
     * still ahead; a slot already passed does not, so re-arming right after a reminder
     * moves to the next chosen day rather than looping on the same one.
     */
    fun nextOccurrence(
        settings: OrderReminderSettings,
        now: Instant,
        timeZone: TimeZone
    ): Instant? {
        if (!settings.isActive) return null

        val today = now.toLocalDateTime(timeZone).date
        val time = LocalTime(settings.hour, settings.minute)

        // 0..7 so a single chosen day lands on the same weekday next week.
        for (offset in 0..7) {
            val date: LocalDate = today.plus(offset, DateTimeUnit.DAY)
            if (date.dayOfWeek !in settings.days) continue
            val candidate = date.atTime(time).toInstant(timeZone)
            if (candidate > now) return candidate
        }
        return null
    }
}
