package com.together.newverse.domain.reminder

import com.together.newverse.data.config.DefaultOrderScheduleConfig
import com.together.newverse.domain.config.OrderScheduleConfig
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

/**
 * The parts of the order reminder that depend on which market the buyer collects at.
 *
 * Each market keeps its own pickup day ([com.together.newverse.domain.model.Market.scheduleConfig]),
 * so "remind me on Monday and Tuesday" is only right for a Thursday market. Everything
 * here is expressed relative to the market's deadline day instead, which is what the
 * buyer actually acts against.
 *
 * Pure, like [OrderReminderEvaluator], so the rules are testable without a device.
 */
object OrderReminderSchedule {

    /** The schedule used before any market is known: Thursday pickup, Tuesday 23:59. */
    val DEFAULT: OrderScheduleConfig = DefaultOrderScheduleConfig()

    /**
     * How many days before the deadline a reminder can be set. Earlier than that it
     * would be about a pickup more than a week away, which is not what this is for.
     */
    private const val DAYS_BEFORE_DEADLINE = 3

    /**
     * The days the buyer can choose from, earliest first, ending on the deadline day.
     * For the default schedule: Saturday to Tuesday.
     */
    fun selectableDays(config: OrderScheduleConfig): List<DayOfWeek> =
        (DAYS_BEFORE_DEADLINE downTo 0).map { config.deadlineDay.minusDays(it) }

    /**
     * The day before the deadline and the deadline day itself: the last two days on
     * which a reminder can still be acted on. Monday and Tuesday for the default.
     */
    fun defaultDays(config: OrderScheduleConfig): Set<DayOfWeek> =
        setOf(config.deadlineDay.minusDays(1), config.deadlineDay)

    /**
     * Moves the chosen days along with the deadline when the buyer's market changes.
     *
     * A buyer who picked "the day before the deadline" still means that at a market
     * whose deadline is a day later, so each day keeps its distance to the deadline
     * rather than its weekday.
     */
    fun shiftDays(
        days: Set<DayOfWeek>,
        from: OrderScheduleConfig,
        to: OrderScheduleConfig
    ): Set<DayOfWeek> {
        val offset = to.deadlineDay.isoDayNumber - from.deadlineDay.isoDayNumber
        if (offset == 0) return days
        return days.map { it.plusDays(offset) }.toSet()
    }

    /** The deadline's time of day, `HH:mm`. */
    fun deadlineTime(config: OrderScheduleConfig): String =
        "${config.deadlineHour.toString().padStart(2, '0')}:" +
            config.deadlineMinute.toString().padStart(2, '0')

    /** Whether two schedules describe the same week. Configs have no `equals` of their own. */
    fun sameSchedule(a: OrderScheduleConfig, b: OrderScheduleConfig): Boolean =
        a.pickupDay == b.pickupDay &&
            a.deadlineDay == b.deadlineDay &&
            a.deadlineHour == b.deadlineHour &&
            a.deadlineMinute == b.deadlineMinute

    /**
     * Stable text form for the reminder storage: ISO pickup day, ISO deadline day,
     * hour, minute — `"5,3,23,59"` for a Friday market.
     */
    fun encode(config: OrderScheduleConfig): String = listOf(
        config.pickupDay.isoDayNumber,
        config.deadlineDay.isoDayNumber,
        config.deadlineHour,
        config.deadlineMinute
    ).joinToString(",")

    /** The inverse of [encode], or null for anything it did not write. */
    fun decode(encoded: String?): OrderScheduleConfig? {
        val parts = encoded?.split(",")?.map { it.trim().toIntOrNull() ?: return null } ?: return null
        if (parts.size != 4) return null
        val (pickup, deadline, hour, minute) = parts
        if (pickup !in 1..7 || deadline !in 1..7 || hour !in 0..23 || minute !in 0..59) return null
        return StoredScheduleConfig(DayOfWeek(pickup), DayOfWeek(deadline), hour, minute)
    }

    private fun DayOfWeek.plusDays(days: Int): DayOfWeek =
        DayOfWeek(((isoDayNumber - 1 + days) % 7 + 7) % 7 + 1)

    private fun DayOfWeek.minusDays(days: Int): DayOfWeek = plusDays(-days)

    private data class StoredScheduleConfig(
        override val pickupDay: DayOfWeek,
        override val deadlineDay: DayOfWeek,
        override val deadlineHour: Int,
        override val deadlineMinute: Int,
    ) : OrderScheduleConfig
}
