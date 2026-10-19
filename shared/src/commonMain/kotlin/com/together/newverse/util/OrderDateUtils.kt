package com.together.newverse.util

import com.together.newverse.data.config.DefaultOrderScheduleConfig
import com.together.newverse.domain.config.OrderScheduleConfig
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.MarketClosures
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.*

/**
 * Utility functions for order date calculations
 *
 * Business Rules (configurable via OrderScheduleConfig):
 * - Pickup day: Configurable (default: Thursday)
 * - Edit deadline: Configurable (default: Tuesday 23:59:59 before pickup)
 * - If today is past the deadline day, order is for NEXT pickup day
 */
object OrderDateUtils {

    private val defaultConfig: OrderScheduleConfig = DefaultOrderScheduleConfig()

    /**
     * Calculate the number of days from [fromDay] to [targetDay], always positive (1-7).
     * Returns 7 if they are the same day (next week's occurrence).
     */
    private fun daysUntil(fromDay: DayOfWeek, targetDay: DayOfWeek): Int {
        val diff = (targetDay.isoDayNumber - fromDay.isoDayNumber + 7) % 7
        return if (diff == 0) 7 else diff
    }

    /**
     * Calculate the number of days between two days of week (can be 0 for same day).
     */
    private fun daysBetween(fromDay: DayOfWeek, toDay: DayOfWeek): Int {
        return (toDay.isoDayNumber - fromDay.isoDayNumber + 7) % 7
    }

    /**
     * Calculate the next pickup date from a given date.
     *
     * Rules:
     * - If today is on or before the deadline day (and pickup day is still ahead): this week's pickup day
     * - If today is past the deadline day: next week's pickup day
     * - On pickup day itself: next week's pickup day
     *
     * @param fromDate Starting date (defaults to now)
     * @param timeZone Time zone for calculations (defaults to system)
     * @param config Order schedule configuration (defaults to Thursday pickup, Tuesday 23:59 deadline)
     * @return Instant representing pickup day at 00:00 local time
     */
    fun calculateNextPickupDate(
        fromDate: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        config: OrderScheduleConfig = defaultConfig
    ): Instant {
        val localDate = fromDate.toLocalDateTime(timeZone).date
        val currentDayOfWeek = localDate.dayOfWeek

        // How many days from deadline day to pickup day
        val deadlineToPickup = daysBetween(config.deadlineDay, config.pickupDay)

        // How many days from current day to pickup day (1-7, with same day = 7)
        val daysToPickup = daysUntil(currentDayOfWeek, config.pickupDay)

        // The deadline for the upcoming pickup is: pickup_date - deadlineToPickup days
        // If we're already past the deadline day (i.e., daysToPickup < deadlineToPickup),
        // we need to go to next week's pickup instead.
        // On the deadline day itself (daysToPickup == deadlineToPickup), the deadline
        // hasn't passed yet, so we still target this week's pickup.
        val actualDaysToPickup = if (daysToPickup < deadlineToPickup) {
            // We're past the deadline for this week's pickup, go to next week
            daysToPickup + 7
        } else {
            daysToPickup
        }

        val pickupDate = localDate.plus(actualDaysToPickup, DateTimeUnit.DAY)

        // Return as Instant at midnight (start of day) in the given timezone
        return pickupDate.atTime(0, 0)
            .toInstant(timeZone)
    }

    /**
     * Calculate the edit deadline for a given pickup date.
     *
     * The deadline is a fixed offset before pickup — the distance from
     * [OrderScheduleConfig.deadlineDay] to [OrderScheduleConfig.pickupDay] — so it is
     * well defined for *any* date, not only the configured pickup day.
     *
     * This used to `require` the pickup day and was commented out in `cb0884c` without
     * the reason being written down. The reason is sound: four of the call sites are
     * inside composables in `BasketScreen`, so a stored order carrying an unexpected
     * weekday — legacy data, or a timezone shift moving it a day — would not be
     * rejected, it would crash the screen that displays it. Tolerating the date and
     * returning a sensible deadline is the behaviour the app depends on, and it becomes
     * mandatory once each market has its own pickup day
     * (`doc/market-pricing-and-access-design.md` §4).
     *
     * @param pickupDate The pickup date; need not fall on [OrderScheduleConfig.pickupDay]
     * @param timeZone Time zone for calculations (defaults to system)
     * @param config Order schedule configuration
     * @return Instant representing the deadline day at the configured time
     */
    fun calculateEditDeadline(
        pickupDate: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        config: OrderScheduleConfig = defaultConfig
    ): Instant {
        val pickupLocalDate = pickupDate.toLocalDateTime(timeZone).date

        // Calculate days from deadline day to pickup day
        val deadlineToPickup = daysBetween(config.deadlineDay, config.pickupDay)

        // Deadline is deadlineToPickup days before pickup
        val deadlineDate = pickupLocalDate.minus(deadlineToPickup, DateTimeUnit.DAY)

        // Return at configured deadline time
        return deadlineDate.atTime(config.deadlineHour, config.deadlineMinute, 59, 999_000_000)
            .toInstant(timeZone)
    }

    /**
     * Check if an order can be edited based on the current time and pickup date
     *
     * @param pickupDate The order's pickup date
     * @param now Current time (defaults to now)
     * @param timeZone Time zone for calculations (defaults to system)
     * @param config Order schedule configuration
     * @return true if order can be edited, false otherwise
     */
    fun canEditOrder(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        config: OrderScheduleConfig = defaultConfig
    ): Boolean {
        val deadline = calculateEditDeadline(pickupDate, timeZone, config)
        return now <= deadline
    }

    /**
     * The last moment of the pickup day: pickup day at 23:59:59.999 local time.
     *
     * [calculateNextPickupDate] returns the pickup day at 00:00, so comparing a
     * timestamp against the pickup instant itself treats the order as picked up
     * from midnight on — for the whole of the day the pickup actually happens.
     */
    private fun endOfPickupDay(
        pickupDate: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Instant {
        val pickupLocalDate = pickupDate.toLocalDateTime(timeZone).date
        return pickupLocalDate.atTime(23, 59, 59, 999_000_000)
            .toInstant(timeZone)
    }

    /**
     * Get the status of an order window based on current time
     */
    fun getOrderWindowStatus(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        config: OrderScheduleConfig = defaultConfig
    ): OrderWindowStatus {
        val deadline = calculateEditDeadline(pickupDate, timeZone, config)

        return when {
            now <= deadline -> OrderWindowStatus.OPEN
            now > endOfPickupDay(pickupDate, timeZone) -> OrderWindowStatus.PICKUP_PASSED
            else -> OrderWindowStatus.DEADLINE_PASSED
        }
    }

    /**
     * Check if a given pickup date is for the current ordering cycle
     * (i.e., the next pickup date from today)
     *
     * @param pickupDate The pickup date to check
     * @param now Current time (defaults to now)
     * @param timeZone Time zone for calculations (defaults to system)
     * @param config Order schedule configuration
     * @return true if pickup date matches the next pickup date
     */
    fun isCurrentOrderingCycle(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        config: OrderScheduleConfig = defaultConfig
    ): Boolean {
        val nextPickup = calculateNextPickupDate(now, timeZone, config)
        val pickupLocalDate = pickupDate.toLocalDateTime(timeZone).date
        val nextPickupLocalDate = nextPickup.toLocalDateTime(timeZone).date

        return pickupLocalDate == nextPickupLocalDate
    }

    /**
     * Calculate time remaining until edit deadline
     *
     * @param pickupDate The order's pickup date
     * @param now Current time (defaults to now)
     * @param timeZone Time zone for calculations (defaults to system)
     * @param config Order schedule configuration
     * @return DateTimePeriod representing remaining time, or null if deadline passed
     */
    fun timeUntilDeadline(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        config: OrderScheduleConfig = defaultConfig
    ): DateTimePeriod? {
        val deadline = calculateEditDeadline(pickupDate, timeZone, config)

        if (now > deadline) return null

        // Use Instant.periodUntil instead of LocalDateTime.periodUntil
        return now.periodUntil(deadline, timeZone)
    }

    /**
     * Format time remaining until deadline as human-readable string
     *
     * @param pickupDate The order's pickup date
     * @param now Current time (defaults to now)
     * @param timeZone Time zone for calculations (defaults to system)
     * @param config Order schedule configuration
     * @return Formatted string like "2 days, 5 hours" or "Deadline passed"
     */
    fun formatTimeUntilDeadline(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        config: OrderScheduleConfig = defaultConfig
    ): String {
        val period = timeUntilDeadline(pickupDate, now, timeZone, config) ?: return "Deadline passed"

        val days = period.days
        val hours = period.hours
        val minutes = period.minutes

        return when {
            days > 1 -> "$days days, $hours hours"
            days == 1 -> "1 day, $hours hours"
            hours > 1 -> "$hours hours, $minutes minutes"
            hours == 1 -> "1 hour, $minutes minutes"
            minutes > 1 -> "$minutes minutes"
            minutes == 1 -> "1 minute"
            else -> "Less than 1 minute"
        }
    }

    /**
     * Get a warning level based on time remaining until deadline
     */
    fun getDeadlineWarningLevel(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        config: OrderScheduleConfig = defaultConfig
    ): DeadlineWarningLevel {
        val period = timeUntilDeadline(pickupDate, now, timeZone, config)
            ?: return DeadlineWarningLevel.EXPIRED

        val totalHours = period.days * 24 + period.hours

        return when {
            totalHours > 48 -> DeadlineWarningLevel.NONE
            totalHours > 24 -> DeadlineWarningLevel.INFO
            totalHours > 6 -> DeadlineWarningLevel.WARNING
            totalHours > 1 -> DeadlineWarningLevel.URGENT
            else -> DeadlineWarningLevel.CRITICAL
        }
    }

    /**
     * Format a LocalDate to date key string (yyyyMMdd) for Firebase paths
     */
    fun formatDateKey(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val dateTime = instant.toLocalDateTime(timeZone)
        val year = dateTime.year
        val month = dateTime.month.number.toString().padStart(2, '0')
        val day = dateTime.day.toString().padStart(2, '0')
        return "$year$month$day"
    }

    /**
     * Get list of available pickup dates where deadline hasn't passed
     *
     * @param count How many future dates to return (default: 5)
     * @param now Current time (defaults to now)
     * @param timeZone Time zone for calculations (defaults to system)
     * @param config Order schedule configuration
     * @return List of Instants representing available pickup dates
     */
    /** [market]'s own schedule, falling back to the app default when it has no day. */
    fun marketConfig(market: Market): OrderScheduleConfig = market.scheduleConfig(defaultConfig)

    fun getAvailablePickupDates(
        count: Int = 5,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        config: OrderScheduleConfig = defaultConfig,
        closures: MarketClosures = MarketClosures.NONE,
        marketId: String? = null
    ): List<Instant> {
        val dates = mutableListOf<Instant>()
        var currentDate = now
        var candidates = 0

        // Bounded on purpose. Every pickup day used to qualify, so walking until
        // `count` were found always terminated; a seller who can close days can
        // close every candidate, and an unbounded loop would then hang the app
        // instead of simply offering fewer dates.
        while (dates.size < count && candidates < MAX_PICKUP_CANDIDATES) {
            candidates++
            val nextPickup = calculateNextPickupDate(currentDate, timeZone, config)

            // Check if deadline has passed for this pickup date
            val withinWindow = canEditOrder(nextPickup, now, timeZone, config)
            val open = !closures.isClosed(nextPickup, timeZone, marketId)
            if (withinWindow && open) {
                dates.add(nextPickup)
            }

            // Move to day after this pickup date to find next one
            val nextPickupLocal = nextPickup.toLocalDateTime(timeZone)
            currentDate = nextPickupLocal.date
                .plus(1, DateTimeUnit.DAY)
                .atStartOfDayIn(timeZone)
        }

        return dates
    }

    /**
     * How many pickup days ahead [getAvailablePickupDates] will look before giving
     * up. Half a year of weekly markets — far beyond any order window, and short
     * enough that an all-closed calendar returns promptly rather than spinning.
     */
    const val MAX_PICKUP_CANDIDATES = 26

    /**
     * Check if a selected pickup date is still valid
     * (deadline hasn't passed and date is in future)
     *
     * @param pickupDate The selected pickup date
     * @param now Current time (defaults to now)
     * @param timeZone Time zone for calculations (defaults to system)
     * @param config Order schedule configuration
     * @return true if date is still valid for ordering
     */
    fun isPickupDateValid(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        config: OrderScheduleConfig = defaultConfig,
        closures: MarketClosures = MarketClosures.NONE,
        marketId: String? = null
    ): Boolean {
        // Must be in future
        if (pickupDate <= now) return false

        // A date the buyer picked before the seller closed that day is no longer
        // selectable. Re-checked here rather than only at generation time, because
        // a draft basket can hold a date chosen days ago.
        if (closures.isClosed(pickupDate, timeZone, marketId)) return false

        // Deadline must not have passed
        return canEditOrder(pickupDate, now, timeZone, config)
    }

    /**
     * Format date for display (dd.MM.yyyy)
     */
    fun formatDisplayDate(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val dateTime = instant.toLocalDateTime(timeZone)
        val day = dateTime.day.toString().padStart(2, '0')
        val month = dateTime.month.number.toString().padStart(2, '0')
        val year = dateTime.year
        return "$day.$month.$year"
    }

    /**
     * Format date and time for display (dd.MM.yyyy HH:mm)
     */
    fun formatDisplayDateTime(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val dateTime = instant.toLocalDateTime(timeZone)
        val day = dateTime.day.toString().padStart(2, '0')
        val month = dateTime.month.number.toString().padStart(2, '0')
        val year = dateTime.year
        val hour = dateTime.hour.toString().padStart(2, '0')
        val minute = dateTime.minute.toString().padStart(2, '0')
        return "$day.$month.$year $hour:$minute"
    }
}

/**
 * Status of an order window relative to current time
 */
enum class OrderWindowStatus {
    OPEN,              // Can place or edit order (before deadline)
    DEADLINE_PASSED,   // Cannot edit (after deadline, before pickup)
    PICKUP_PASSED      // Order completed (after pickup day)
}

/**
 * Warning level for deadline proximity
 */
enum class DeadlineWarningLevel {
    NONE,       // More than 48 hours
    INFO,       // 24-48 hours
    WARNING,    // 6-24 hours
    URGENT,     // 1-6 hours
    CRITICAL,   // Less than 1 hour
    EXPIRED     // Deadline passed
}
