package com.together.newverse.domain.config

import kotlinx.datetime.DayOfWeek

/**
 * Configuration for order scheduling business rules.
 * Controls which day is pickup day and when the edit deadline occurs.
 */
interface OrderScheduleConfig {
    /** The day of the week when orders are picked up */
    val pickupDay: DayOfWeek

    /** The day of the week when the edit deadline occurs (before pickup day) */
    val deadlineDay: DayOfWeek

    /** Hour of the deadline (0-23) */
    val deadlineHour: Int

    /** Minute of the deadline (0-59) */
    val deadlineMinute: Int
}
