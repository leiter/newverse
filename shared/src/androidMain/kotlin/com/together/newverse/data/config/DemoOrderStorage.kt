package com.together.newverse.data.config

import android.content.Context
import android.content.SharedPreferences

actual class DemoOrderStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    actual fun getDemoOrdersJson(): String =
        prefs.getString(KEY_DEMO_ORDERS, "") ?: ""

    actual fun setDemoOrdersJson(json: String) {
        prefs.edit().putString(KEY_DEMO_ORDERS, json).apply()
    }

    actual fun clearDemoOrders() {
        prefs.edit().remove(KEY_DEMO_ORDERS).apply()
    }

    actual fun getFirebaseWriteCount(): Int =
        prefs.getInt(KEY_FIREBASE_WRITE_COUNT, 0)

    actual fun setFirebaseWriteCount(count: Int) {
        prefs.edit().putInt(KEY_FIREBASE_WRITE_COUNT, count).apply()
    }

    companion object {
        private const val PREFS_FILE = "newverse_demo_orders"
        private const val KEY_DEMO_ORDERS = "demo_orders"
        private const val KEY_FIREBASE_WRITE_COUNT = "firebase_write_count"
    }
}
