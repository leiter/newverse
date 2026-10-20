package com.together.newverse.domain.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.together.newverse.data.config.StockReminderStorage
import com.together.newverse.util.Log
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import java.util.concurrent.TimeUnit

/**
 * WorkManager-backed [StockReminderScheduler].
 */
class AndroidStockReminderScheduler(
    private val context: Context,
    private val storage: StockReminderStorage
) : StockReminderScheduler {

    override fun reschedule() {
        StockReminderScheduling.enqueueNext(
            context = context,
            settings = storage.load(),
            snapshot = storage.loadSnapshot(),
            now = Clock.System.now()
        )
    }

    override fun cancel() {
        StockReminderScheduling.cancel(context)
    }

    override fun canDeliver(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Enqueueing lives here rather than on the scheduler so [StockReminderWorker] can
 * re-arm without constructing a scheduler, and so both paths share one unique work
 * name.
 */
internal object StockReminderScheduling {

    private const val TAG = "StockReminderSched"
    private const val WORK_NAME = "newverse_stock_reminder"

    fun enqueueNext(
        context: Context,
        settings: com.together.newverse.domain.model.StockReminderSettings,
        snapshot: com.together.newverse.domain.model.StockReminderSnapshot,
        now: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ) {
        val next = StockReminderEvaluator.nextOccurrence(settings, snapshot, now, timeZone)
        if (next == null) {
            Log.d(TAG) { "enqueueNext: nothing to schedule, cancelling" }
            cancel(context)
            return
        }

        val delayMillis = (next - now).inWholeMilliseconds.coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<StockReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            // Nothing to fetch and nothing worth deferring: the seller shops in the
            // morning, and a buy list held back to save battery arrives after they
            // have already driven to the wholesaler.
            .setConstraints(Constraints.Builder().build())
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)

        Log.d(TAG) { "enqueueNext: refill reminder in ${delayMillis / 60_000} min for ${snapshot.cycle}" }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
