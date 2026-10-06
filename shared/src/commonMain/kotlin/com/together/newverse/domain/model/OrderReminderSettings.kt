package com.together.newverse.domain.model

import kotlinx.datetime.DayOfWeek

/**
 * A buyer's settings for the "place your order" reminder.
 *
 * The ordering week is fixed (pickup Thursday, deadline Tuesday 23:59), so the
 * reminder is expressed the way a buyer thinks about their week — on these days, at
 * this time — rather than as a lead time before a deadline they would have to work
 * out for themselves.
 *
 * @param days Weekdays the reminder may fire on. Empty means never, whatever
 *   [enabled] says.
 * @param onlyWhenNotOrdered Skip the reminder when an order already exists for the
 *   upcoming pickup. On by default: being nagged about something already done is the
 *   fastest way to have notifications switched off for good.
 * @param lastFiredDate ISO date (yyyy-MM-dd) the reminder last fired on, so it fires
 *   at most once a day however often the worker runs. Deliberately per *date* and not
 *   per pickup cycle: a buyer who picks both Monday and Tuesday wants two reminders in
 *   the same cycle.
 */
data class OrderReminderSettings(
    val enabled: Boolean = false,
    val days: Set<DayOfWeek> = DEFAULT_DAYS,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = 0,
    val onlyWhenNotOrdered: Boolean = true,
    val lastFiredDate: String = ""
) {
    /** True when this could ever fire, regardless of today's date or order state. */
    val isActive: Boolean get() = enabled && days.isNotEmpty()

    companion object {
        /**
         * Monday and Tuesday: the deadline is Tuesday 23:59, so these are the last two
         * days on which a reminder can still be acted on for the coming Thursday.
         */
        val DEFAULT_DAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)

        /** Early evening — home from work, before the evening is over. */
        const val DEFAULT_HOUR = 18
    }
}
