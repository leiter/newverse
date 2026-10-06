package com.together.newverse.domain.reminder

/**
 * Arms the platform's background work so the order reminder fires at the time the buyer
 * chose, whether or not the app is open.
 *
 * Timing is best-effort by nature: both platforms batch background wake-ups to save
 * battery, so a reminder can arrive late. [OrderReminderEvaluator] drops one that is
 * too late rather than delivering it at an odd hour.
 */
interface OrderReminderScheduler {

    /**
     * Arm the next reminder from the stored settings, replacing anything pending.
     *
     * Safe and cheap to call repeatedly — on app start, after a settings change, and
     * after a reminder fires. With inactive settings it just cancels.
     */
    fun reschedule()

    /** Drop any pending reminder. */
    fun cancel()

    /**
     * Whether a reminder posted right now would actually reach the buyer.
     *
     * False when notifications are switched off for the app, so the settings UI can say
     * so rather than leaving a switch that looks on and never fires.
     */
    fun canDeliver(): Boolean
}
