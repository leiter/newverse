package com.together.newverse.domain.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.together.newverse.data.config.StockReminderStorage
import com.together.newverse.domain.model.ProductPricing
import com.together.newverse.domain.model.StockReminderSnapshot
import com.together.newverse.util.Log
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.stock_reminder_channel_description
import newverse.shared.generated.resources.stock_reminder_channel_name
import newverse.shared.generated.resources.stock_reminder_notification_more
import newverse.shared.generated.resources.stock_reminder_notification_title
import org.jetbrains.compose.resources.getString

/**
 * Posts the seller's refill list for the coming market when it is due, then arms the
 * next one.
 *
 * A self-rescheduling one-time chain rather than a `PeriodicWorkRequest`, as for
 * [OrderReminderWorker]: the reminder fires at an instant derived from the pickup
 * schedule, which a fixed interval cannot express. WorkManager restores its queue
 * after a reboot by itself — the sell flavor already holds `RECEIVE_BOOT_COMPLETED`.
 *
 * It reads nothing but [StockReminderStorage], which the app filled while it was
 * running: a worker can start with no Koin graph, no session and no network, and the
 * numbers in a buy list cannot be recomputed without the database. What it can still
 * do is refuse to post a list for a market that has already happened.
 */
class StockReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val storage = StockReminderStorage(applicationContext)
            val settings = storage.load()

            if (!settings.enabled) {
                Log.d(TAG) { "doWork: refill reminder switched off, chain ends" }
                return Result.success()
            }

            val snapshot = storage.loadSnapshot()
            val timeZone = TimeZone.currentSystemDefault()
            val now = Clock.System.now()

            if (StockReminderEvaluator.isDue(settings, snapshot, now, timeZone)) {
                if (notify(snapshot, timeZone)) {
                    // Recorded only once it was actually shown, so a seller who grants
                    // the notification permission later still gets this cycle's list.
                    storage.save(settings.copy(lastFiredCycle = snapshot.cycle))
                    Log.d(TAG) { "doWork: refill list posted for ${snapshot.cycle}" }
                }
            }

            // Re-arm from the freshly stored settings, so lastFiredCycle counts.
            StockReminderScheduling.enqueueNext(
                context = applicationContext,
                settings = storage.load(),
                snapshot = snapshot,
                now = Clock.System.now()
            )
            Result.success()
        } catch (e: Exception) {
            // Never retry: a buy list is stale by the time a retry would run, and the
            // app arms the next cycle as soon as it is opened again.
            Log.w(TAG) { "doWork: failed - ${e.message}" }
            Result.success()
        }
    }

    /** @return true when the notification was handed to the system. */
    private suspend fun notify(snapshot: StockReminderSnapshot, timeZone: TimeZone): Boolean {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) {
            Log.d(TAG) { "notify: notifications are disabled for this app" }
            return false
        }

        createChannel()

        val title = getString(Res.string.stock_reminder_notification_title, marketDay(snapshot, timeZone))
        val body = body(snapshot)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(body)
            // The point is the list, so it has to be readable without opening
            // anything: a seller standing at the wholesaler wants the names.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
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

    /** The pickup day as a date the seller reads, e.g. `23.10.`. */
    private fun marketDay(snapshot: StockReminderSnapshot, timeZone: TimeZone): String {
        val date = Instant.fromEpochMilliseconds(snapshot.pickUpDate).toLocalDateTime(timeZone).date
        return "${date.day.toString().padStart(2, '0')}.${date.month.number.toString().padStart(2, '0')}."
    }

    /**
     * The list itself, trimmed to what a notification shows before it is collapsed.
     *
     * The count of what is left is named rather than dropped silently: a seller who
     * reads four lines and buys four things should know there was a fifth.
     */
    private suspend fun body(snapshot: StockReminderSnapshot): String {
        val shown = snapshot.lines.take(MAX_LINES).joinToString("\n") { line ->
            "${line.productName}: ${ProductPricing.formatQuantity(line.shortfall)} ${line.unit}"
        }
        val rest = snapshot.lines.size - MAX_LINES
        if (rest <= 0) return shown
        return "$shown\n" + getString(Res.string.stock_reminder_notification_more, rest)
    }

    private suspend fun createChannel() {
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(Res.string.stock_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(Res.string.stock_reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    internal companion object {
        private const val TAG = "StockReminderWorker"
        const val CHANNEL_ID = "newverse_stock_reminder"
        const val NOTIFICATION_ID = 4712
        /** Beyond this a notification is scrolling, not reading. */
        const val MAX_LINES = 8
    }
}
