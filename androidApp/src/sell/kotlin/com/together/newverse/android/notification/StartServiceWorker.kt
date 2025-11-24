package com.together.newverse.android.notification

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.together.newverse.android.utils.handleService

/**
 * WorkManager Worker to start/stop ListenerService
 * Used for scheduled service management
 */
class StartServiceWorker(
    context: Context,
    parameters: WorkerParameters
) : Worker(context, parameters) {

    companion object {
        const val ACTION = "mode"
    }

    override fun doWork(): Result {
        val action = inputData.getString(ACTION)
            ?: return Result.failure()

        applicationContext.handleService(action)
        return Result.success()
    }
}
