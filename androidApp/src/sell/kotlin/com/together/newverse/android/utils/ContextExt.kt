package com.together.newverse.android.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.together.newverse.android.notification.ListenerService

/**
 * Check if a specific service is currently running
 */
fun Context.isThisServiceRunning(clazz: Class<*>): Boolean {
    val manager = getSystemService<ActivityManager>()
    return manager?.let { activityManager ->
        @Suppress("DEPRECATION")
        activityManager.getRunningServices(Integer.MAX_VALUE).forEach {
            if (clazz.name == it.service.className) {
                return@let true
            }
        }
        return@let false
    } == true
}

/**
 * Check if ListenerService is currently running
 */
fun Context.isListenerServiceRunning(): Boolean {
    return isThisServiceRunning(ListenerService::class.java)
//    val manager = getSystemService<ActivityManager>()
//    return manager?.let { activityManager ->
//        @Suppress("DEPRECATION")
//        activityManager.getRunningServices(Integer.MAX_VALUE).forEach {
//            if (ListenerService::class.java.name == it.service.className) {
//                return@let true
//            }
//        }
//        return@let false
//    } == true
}

/**
 * Start or stop the ListenerService based on action
 * Only starts the service if it's not already running
 */
fun Context.handleService(action: String) {
    if (!applicationContext.isListenerServiceRunning()) {
        Intent(applicationContext, ListenerService::class.java).also {
            it.action = action
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (action == ACTION_START_SERVICE) {
                    applicationContext.startForegroundService(it)
                } else {
                    applicationContext.stopService(it)
                }
            } else {
                applicationContext.startService(it)
            }
        }
    }
}

/**
 * Start the ListenerService
 */
fun Context.startListenerService() {
    Intent(this, ListenerService::class.java).apply {
        action = ACTION_START_SERVICE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(this)
        } else {
            startService(this)
        }
    }
}

/**
 * Stop the ListenerService
 */
fun Context.stopListenerService() {
    Intent(this, ListenerService::class.java).apply {
        action = ACTION_STOP_SERVICE
        startService(this)
    }
}
