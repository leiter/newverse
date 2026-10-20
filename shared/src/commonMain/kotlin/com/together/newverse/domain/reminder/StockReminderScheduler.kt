package com.together.newverse.domain.reminder

/**
 * Arms the platform's background work so the refill reminder reaches the seller
 * whether or not the app is open.
 *
 * Mirrors [OrderReminderScheduler], for the same reason: what has to happen at a
 * given time on a device the app does not control is platform work, and what should
 * happen is [StockReminderEvaluator]'s business.
 */
interface StockReminderScheduler {

    /**
     * Arm the reminder for the snapshot the app last stored, replacing anything
     * pending.
     *
     * Cheap and safe to call repeatedly — on every recalculation of the refill list,
     * which is what keeps the armed reminder in step with what it would say. With the
     * reminder switched off, or nothing short, it just cancels.
     */
    fun reschedule()

    /** Drop any pending reminder. */
    fun cancel()

    /**
     * Whether a reminder posted now would actually reach the seller.
     *
     * False when notifications are switched off for the app, so the settings UI can
     * say so rather than leaving a switch that looks on and never fires.
     */
    fun canDeliver(): Boolean
}
