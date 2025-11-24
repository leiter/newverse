package com.together.newverse.android.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.together.newverse.android.utils.ACTION_START_SERVICE

/**
 * BroadcastReceiver that starts ListenerService when device boots up
 * Handles various boot completion intents from different manufacturers
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"

        // Various boot completion actions from different manufacturers
        private val BOOT_ACTIONS = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.action.QUICKBOOT_POWERON"
        )
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action

        if (action != null && action in BOOT_ACTIONS) {
            Log.d(TAG, "Boot completed, starting ListenerService")
            context?.let { handleService(it) }

            // Also reinitialize SwitchWorker if auto-scheduling is enabled
            context?.let { SwitchWorker.initialize(it) }
        }
    }

    private fun handleService(context: Context) {
        try {
            Intent(context, ListenerService::class.java).apply {
                this.action = ACTION_START_SERVICE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, this)
                } else {
                    context.startService(this)
                }
            }
            Log.d(TAG, "ListenerService started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ListenerService", e)
        }
    }
}
