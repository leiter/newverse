package com.together.newverse.data.config

import com.together.newverse.domain.config.OrderScheduleConfig
import kotlinx.datetime.DayOfWeek

/**
 * Default order schedule: pickup on Thursday, edit deadline Tuesday 23:59.
 */
class DefaultOrderScheduleConfig : OrderScheduleConfig {
    override val pickupDay: DayOfWeek = DayOfWeek.THURSDAY
    override val deadlineDay: DayOfWeek = DayOfWeek.TUESDAY
    override val deadlineHour: Int = 23
    override val deadlineMinute: Int = 59
}
