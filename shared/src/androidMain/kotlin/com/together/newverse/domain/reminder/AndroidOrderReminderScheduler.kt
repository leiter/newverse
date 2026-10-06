package com.together.newverse.domain.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.together.newverse.data.config.OrderReminderStorage
import com.together.newverse.domain.model.OrderReminderSettings
import com.together.newverse.util.Log
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import java.util.concurrent.TimeUnit

/**
 * WorkManager-backed [OrderReminderScheduler].
 */
class AndroidOrderReminderScheduler(
    private val context: Context,
    private val storage: OrderReminderStorage
) : OrderReminderScheduler {

    override fun reschedule() {
        OrderReminderScheduling.enqueueNext(context, storage.load(), Clock.System.now())
    }

    override fun cancel() {
        OrderReminderScheduling.cancel(context)
    }

    override fun canDeliver(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Enqueueing lives here rather than on the scheduler so [OrderReminderWorker] can re-arm
 * itself without constructing a scheduler, and so both paths share one unique work name.
 */
internal object OrderReminderScheduling {

    private const val TAG = "OrderReminderSched"
    private const val WORK_NAME = "newverse_order_reminder"

    fun enqueueNext(
        context: Context,
        settings: OrderReminderSettings,
        now: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ) {
        val next = OrderReminderEvaluator.nextOccurrence(settings, now, timeZone)
        if (next == null) {
            Log.d(TAG) { "enqueueNext: nothing to schedule, cancelling" }
            cancel(context)
            return
        }

        val delayMillis = (next - now).inWholeMilliseconds.coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<OrderReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            // No network needed: everything the worker evaluates is on the device. Not
            // battery-gated either — a reminder suppressed to save power is a reminder
            // that arrives after the deadline, which is worse than useless.
            .setConstraints(Constraints.Builder().build())
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)

        Log.d(TAG) { "enqueueNext: next reminder in ${delayMillis / 60_000} min" }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
