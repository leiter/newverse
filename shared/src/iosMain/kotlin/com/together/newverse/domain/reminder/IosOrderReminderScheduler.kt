package com.together.newverse.domain.reminder

import com.together.newverse.util.Log

/**
 * iOS placeholder so the reminder settings UI resolves its dependencies and the buyer's
 * choices are stored. **It schedules nothing yet.**
 *
 * Real delivery needs `BGTaskScheduler` for the background wake-up plus
 * `UNUserNotificationCenter` for the local notification — and a permission prompt, which
 * iOS requires before the first notification rather than at install time. See
 * `doc/order-ready-notification-design.md` C5. The evaluator in `commonMain` is already
 * platform-free, so that work is additive.
 */
class IosOrderReminderScheduler : OrderReminderScheduler {

    override fun reschedule() {
        Log.d(TAG) { "reschedule: no-op on iOS — background scheduling not implemented yet" }
    }

    override fun cancel() {
        Log.d(TAG) { "cancel: no-op on iOS — nothing is ever scheduled" }
    }

    /** False until scheduling exists: nothing can be delivered, so say so. */
    override fun canDeliver(): Boolean = false
}

private const val TAG = "IosOrderReminder"
