package com.together.newverse.android.notification

import android.content.Context
import android.util.Log
import androidx.work.*
import com.together.newverse.android.utils.ACTION_START_SERVICE
import com.together.newverse.android.utils.ACTION_STOP_SERVICE
import com.together.newverse.android.utils.FILE_CONFIG_NOTIFICATION
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker that schedules automatic start/stop of ListenerService
 * Based on configured time periods stored in SharedPreferences
 */
class SwitchWorker(
    context: Context,
    parameters: WorkerParameters
) : Worker(context, parameters) {

    companion object {
        const val MODE = "mode"
        private const val TAG = "SwitchWorker"

        private const val START_KEY = "start"
        private const val STOP_KEY = "end"
        private const val START_DEFAULT = "08:00"
        private const val END_DEFAULT = "22:00"

        /**
         * Initialize the automatic scheduling system
         * Should be called once at app startup
         */
        fun initialize(context: Context) {
            val sharedPreferences = context.getSharedPreferences(
                FILE_CONFIG_NOTIFICATION,
                Context.MODE_PRIVATE
            )

            // Check if already initialized
            val isEnabled = sharedPreferences.getBoolean("auto_schedule_enabled", false)
            if (isEnabled) {
                // Schedule the first action (start or stop based on current time)
                val config = NotificationConfig(context)
                val now = Calendar.getInstance()
                val startCal = config.getStartCalendar()
                val stopCal = config.getStopCalendar()

                val nextAction = if (now.before(startCal)) {
                    ACTION_START_SERVICE
                } else if (now.before(stopCal)) {
                    ACTION_STOP_SERVICE
                } else {
                    ACTION_START_SERVICE
                }

                scheduleNextAction(context, nextAction)
            }
        }

        /**
         * Enable automatic scheduling
         */
        fun enable(context: Context) {
            val sharedPreferences = context.getSharedPreferences(
                FILE_CONFIG_NOTIFICATION,
                Context.MODE_PRIVATE
            )
            sharedPreferences.edit().putBoolean("auto_schedule_enabled", true).apply()
            initialize(context)
        }

        /**
         * Disable automatic scheduling
         */
        fun disable(context: Context) {
            val sharedPreferences = context.getSharedPreferences(
                FILE_CONFIG_NOTIFICATION,
                Context.MODE_PRIVATE
            )
            sharedPreferences.edit().putBoolean("auto_schedule_enabled", false).apply()
            WorkManager.getInstance(context).cancelAllWorkByTag("SwitchWorker")
        }

        /**
         * Schedule the next action (START or STOP)
         */
        private fun scheduleNextAction(context: Context, action: String) {
            val config = NotificationConfig(context)
            val delay = if (action == ACTION_START_SERVICE) {
                config.calculateDelayToStart()
            } else {
                config.calculateDelayToStop()
            }

            Log.d(TAG, "Scheduling $action in $delay seconds")

            val workRequest = OneTimeWorkRequestBuilder<SwitchWorker>()
                .setInputData(workDataOf(MODE to action))
                .addTag("SwitchWorker")
                .setInitialDelay(delay, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "SwitchWorker_$action",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }
    }

    private val notificationConfig = NotificationConfig(applicationContext)

    override fun doWork(): Result {
        val action = inputData.getString(MODE) ?: return Result.failure()

        Log.d(TAG, "doWork: $action")

        // Determine next action (toggle)
        val nextAction = if (action == ACTION_START_SERVICE) {
            ACTION_STOP_SERVICE
        } else {
            ACTION_START_SERVICE
        }

        // Schedule the next action
        scheduleNextAction(applicationContext, nextAction)

        return Result.success()
    }

    /**
     * Configuration for notification scheduling
     */
    class NotificationConfig(context: Context) {
        private val sharedPreferences = context.getSharedPreferences(
            FILE_CONFIG_NOTIFICATION,
            Context.MODE_PRIVATE
        )

        private val currentDate = Calendar.getInstance()

        /**
         * Get the start time from preferences
         */
        fun getStartTime(): String {
            return sharedPreferences.getString(START_KEY, START_DEFAULT) ?: START_DEFAULT
        }

        /**
         * Get the stop time from preferences
         */
        fun getStopTime(): String {
            return sharedPreferences.getString(STOP_KEY, END_DEFAULT) ?: END_DEFAULT
        }

        /**
         * Set the start time
         */
        fun setStartTime(time: String) {
            sharedPreferences.edit().putString(START_KEY, time).apply()
        }

        /**
         * Set the stop time
         */
        fun setStopTime(time: String) {
            sharedPreferences.edit().putString(STOP_KEY, time).apply()
        }

        /**
         * Get Calendar for start time
         */
        fun getStartCalendar(): Calendar {
            val time = getStartTime()
            return parseTimeToCalendar(time)
        }

        /**
         * Get Calendar for stop time
         */
        fun getStopCalendar(): Calendar {
            val time = getStopTime()
            return parseTimeToCalendar(time)
        }

        /**
         * Calculate delay in seconds until start time
         */
        fun calculateDelayToStart(): Long {
            return calculateDelay(getStartTime(), before = true)
        }

        /**
         * Calculate delay in seconds until stop time
         */
        fun calculateDelayToStop(): Long {
            return calculateDelay(getStopTime(), before = false)
        }

        private fun parseTimeToCalendar(timeString: String): Calendar {
            val parts = timeString.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            return Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        private fun calculateDelay(timeString: String, before: Boolean): Long {
            val dueDate = parseTimeToCalendar(timeString)

            if (before) {
                // If due time is before current time, schedule for tomorrow
                if (dueDate.before(currentDate)) {
                    dueDate.add(Calendar.DAY_OF_MONTH, 1)
                }
            } else {
                // If due time is after current time, schedule for tomorrow
                if (dueDate.after(currentDate)) {
                    dueDate.add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val delayMillis = dueDate.timeInMillis - currentDate.timeInMillis
            return delayMillis / 1000
        }
    }
}
