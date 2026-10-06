package com.together.newverse.domain.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.together.newverse.data.config.OrderReminderStorage
import com.together.newverse.util.Log
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.order_reminder_channel_description
import newverse.shared.generated.resources.order_reminder_channel_name
import newverse.shared.generated.resources.order_reminder_notification_text
import newverse.shared.generated.resources.order_reminder_notification_title
import org.jetbrains.compose.resources.getString

/**
 * Posts the buyer's "place your order" reminder when it is due, then arms the next one.
 *
 * A self-rescheduling one-time chain rather than a `PeriodicWorkRequest`: the reminder
 * fires at a time the buyer chose, which a periodic worker's fixed interval cannot
 * express. Each run re-arms the next, so the chain survives as long as the settings are
 * active. WorkManager restores its queue after a reboot by itself, which is why the buy
 * flavor needs `RECEIVE_BOOT_COMPLETED` back in its manifest.
 *
 * It builds [OrderReminderStorage] directly instead of going through Koin: a worker can
 * start with no Application-scoped graph warmed up, and the storage only needs a
 * Context. Everything it decides is local — no network, no signed-in session.
 */
class OrderReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val storage = OrderReminderStorage(applicationContext)
            val settings = storage.load()

            if (!settings.isActive) {
                Log.d(TAG) { "doWork: reminder inactive, chain ends" }
                return Result.success()
            }

            val timeZone = TimeZone.currentSystemDefault()
            val now = Clock.System.now()

            if (OrderReminderEvaluator.isDue(settings, storage.cachedPlacedCycles(), now, timeZone)) {
                val today = now.toLocalDateTime(timeZone).date.toString()
                if (notify()) {
                    // Only record the fire when it was actually shown, so a buyer who
                    // grants the notification permission later still gets reminded.
                    storage.save(settings.copy(lastFiredDate = today))
                    Log.d(TAG) { "doWork: reminder posted for $today" }
                }
            }

            // Re-arm from the freshly stored settings, so lastFiredDate is accounted for.
            OrderReminderScheduling.enqueueNext(applicationContext, storage.load(), Clock.System.now())
            Result.success()
        } catch (e: Exception) {
            // Never retry: a missed reminder is stale by the time a retry would run, and
            // the daily chain will come round again.
            Log.w(TAG) { "doWork: failed - ${e.message}" }
            Result.success()
        }
    }

    /** @return true when the notification was handed to the system. */
    private suspend fun notify(): Boolean {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) {
            Log.d(TAG) { "notify: notifications are disabled for this app" }
            return false
        }

        createChannel()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(getString(Res.string.order_reminder_notification_title))
            .setContentText(getString(Res.string.order_reminder_notification_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(getString(Res.string.order_reminder_notification_text))
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted; areNotificationsEnabled can disagree.
            Log.w(TAG) { "notify: not permitted - ${e.message}" }
            false
        }
    }

    private suspend fun createChannel() {
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(Res.string.order_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(Res.string.order_reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    internal companion object {
        private const val TAG = "OrderReminderWorker"
        const val CHANNEL_ID = "newverse_order_reminder"
        const val NOTIFICATION_ID = 4711
    }
}
