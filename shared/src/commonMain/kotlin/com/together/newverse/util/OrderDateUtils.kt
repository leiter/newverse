package com.together.newverse.util

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.*

/**
 * Utility functions for order date calculations
 *
 * Business Rules:
 * - Pickup day: Always Thursday
 * - Edit deadline: Tuesday 23:59:59 before pickup Thursday
 * - If today is Wednesday or later in the week, order is for NEXT Thursday
 */
object OrderDateUtils {

    /**
     * Calculate the next pickup date (Thursday) from a given date
     *
     * Rules:
     * - Monday/Tuesday: This week's Thursday
     * - Wednesday onwards: Next week's Thursday
     * - Thursday: Next week's Thursday (not today)
     *
     * @param fromDate Starting date (defaults to now)
     * @param timeZone Time zone for calculations (defaults to system)
     * @return Instant representing Thursday at 00:00 local time
     */
    fun calculateNextPickupDate(
        fromDate: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Instant {
        val localDate = fromDate.toLocalDateTime(timeZone).date
        val currentDayOfWeek = localDate.dayOfWeek

        val daysUntilNextThursday = when (currentDayOfWeek) {
            DayOfWeek.MONDAY -> 3      // Mon → Thu (this week)
            DayOfWeek.TUESDAY -> 2     // Tue → Thu (this week)
            DayOfWeek.WEDNESDAY -> 8   // Wed → Thu (next week, skip this Thu)
            DayOfWeek.THURSDAY -> 7    // Thu → Thu (next week)
            DayOfWeek.FRIDAY -> 6      // Fri → Thu (next week)
            DayOfWeek.SATURDAY -> 5    // Sat → Thu (next week)
            DayOfWeek.SUNDAY -> 4      // Sun → Thu (next week)
        }

        val pickupDate = localDate.plus(daysUntilNextThursday, DateTimeUnit.DAY)

        // Return as Instant at midnight (start of day) in the given timezone
        return pickupDate.atTime(0, 0)
            .toInstant(timeZone)
    }

    /**
     * Calculate the edit deadline (Tuesday 23:59:59) for a given pickup date
     *
     * @param pickupDate The Thursday pickup date
     * @param timeZone Time zone for calculations (defaults to system)
     * @return Instant representing Tuesday 23:59:59 before the pickup
     */
    fun calculateEditDeadline(
        pickupDate: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Instant {
        val pickupLocalDate = pickupDate.toLocalDateTime(timeZone).date

        // Verify it's a Thursday
        require(pickupLocalDate.dayOfWeek == DayOfWeek.THURSDAY) {
            "Pickup date must be a Thursday, got ${pickupLocalDate.dayOfWeek}"
        }

        // Tuesday is 2 days before Thursday
        val tuesdayDate = pickupLocalDate.minus(2, DateTimeUnit.DAY)

        // Return Tuesday at 23:59:59
        return tuesdayDate.atTime(23, 59, 59, 999_000_000)
            .toInstant(timeZone)
    }

    /**
     * Check if an order can be edited based on the current time and pickup date
     *
     * @param pickupDate The order's pickup date
     * @param now Current time (defaults to now)
     * @param timeZone Time zone for calculations (defaults to system)
     * @return true if order can be edited, false otherwise
     */
    fun canEditOrder(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        val deadline = calculateEditDeadline(pickupDate, timeZone)
        return now <= deadline
    }

    /**
     * Get the status of an order window based on current time
     */
    fun getOrderWindowStatus(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): OrderWindowStatus {
        val deadline = calculateEditDeadline(pickupDate, timeZone)

        return when {
            now <= deadline -> OrderWindowStatus.OPEN
            now > pickupDate -> OrderWindowStatus.PICKUP_PASSED
            else -> OrderWindowStatus.DEADLINE_PASSED
        }
    }

    /**
     * Check if a given pickup date is for the current ordering cycle
     * (i.e., the next Thursday from today)
     *
     * @param pickupDate The pickup date to check
     * @param now Current time (defaults to now)
     * @param timeZone Time zone for calculations (defaults to system)
     * @return true if pickup date matches the next Thursday
     */
    fun isCurrentOrderingCycle(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        val nextPickup = calculateNextPickupDate(now, timeZone)
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
     * @return DateTimePeriod representing remaining time, or null if deadline passed
     */
    fun timeUntilDeadline(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): DateTimePeriod? {
        val deadline = calculateEditDeadline(pickupDate, timeZone)

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
     * @return Formatted string like "2 days, 5 hours" or "Deadline passed"
     */
    fun formatTimeUntilDeadline(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        val period = timeUntilDeadline(pickupDate, now, timeZone) ?: return "Deadline passed"

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
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): DeadlineWarningLevel {
        val period = timeUntilDeadline(pickupDate, now, timeZone)
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
     * Get list of available pickup dates (Thursdays) where deadline hasn't passed
     *
     * @param count How many future dates to return (default: 5)
     * @param now Current time (defaults to now)
     * @param timeZone Time zone for calculations (defaults to system)
     * @return List of Instants representing available Thursdays
     */
    fun getAvailablePickupDates(
        count: Int = 5,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): List<Instant> {
        val dates = mutableListOf<Instant>()
        var currentDate = now

        while (dates.size < count) {
            val nextThursday = calculateNextPickupDate(currentDate, timeZone)

            // Check if deadline has passed for this Thursday
            if (canEditOrder(nextThursday, now, timeZone)) {
                dates.add(nextThursday)
            }

            // Move to day after this Thursday to find next one
            val nextThursdayLocal = nextThursday.toLocalDateTime(timeZone)
            currentDate = nextThursdayLocal.date
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
     * @return true if date is still valid for ordering
     */
    fun isPickupDateValid(
        pickupDate: Instant,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        // Must be in future
        if (pickupDate <= now) return false

        // Deadline must not have passed
        return canEditOrder(pickupDate, now, timeZone)
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
    OPEN,              // Can place or edit order (before Tuesday 23:59)
    DEADLINE_PASSED,   // Cannot edit (after Tuesday 23:59, before Thursday)
    PICKUP_PASSED      // Order completed (after Thursday)
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
