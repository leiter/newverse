package com.together.newverse.domain.reminder

import com.together.newverse.util.Log

/**
 * iOS placeholder so the seller's notification settings resolve their dependencies
 * and the choice is stored. **It schedules nothing yet.**
 *
 * The refill reminder fires at one computed instant per market cycle, which
 * `UNUserNotificationCenter` can express directly as a local notification — there is
 * no need for `BGTaskScheduler` here, unlike the buyer's reminder, because the text
 * is known when the reminder is armed rather than evaluated at fire time. What is
 * still missing is the permission prompt and the request-replacing logic. The
 * evaluator in `commonMain` is already platform-free, so that work is additive.
 */
class IosStockReminderScheduler : StockReminderScheduler {

    override fun reschedule() {
        Log.d(TAG) { "reschedule: no-op on iOS — local notifications not wired up yet" }
    }

    override fun cancel() {
        Log.d(TAG) { "cancel: no-op on iOS — nothing is ever scheduled" }
    }

    /** False until scheduling exists: nothing can be delivered, so say so. */
    override fun canDeliver(): Boolean = false
}

private const val TAG = "IosStockReminder"
