package com.together.newverse.util

import com.together.newverse.data.config.DefaultOrderScheduleConfig
import com.together.newverse.domain.config.OrderScheduleConfig
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
     * @param pickupDate The pickup date
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

        // Verify it's the configured pickup day
        require(pickupLocalDate.dayOfWeek == config.pickupDay) {
            "Pickup date must be a ${config.pickupDay}, got ${pickupLocalDate.dayOfWeek}"
        }

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
            now > pickupDate -> OrderWindowStatus.PICKUP_PASSED
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
    fun getAvailablePickupDates(
        count: Int = 5,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        config: OrderScheduleConfig = defaultConfig
    ): List<Instant> {
        val dates = mutableListOf<Instant>()
        var currentDate = now

        while (dates.size < count) {
            val nextPickup = calculateNextPickupDate(currentDate, timeZone, config)

            // Check if deadline has passed for this pickup date
            if (canEditOrder(nextPickup, now, timeZone, config)) {
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
        config: OrderScheduleConfig = defaultConfig
    ): Boolean {
        // Must be in future
        if (pickupDate <= now) return false

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
