package com.together.newverse.data.config

import android.content.Context
import android.content.SharedPreferences

actual class DemoOrderStorage(private val context: Context) {

    private var prefs: SharedPreferences = noopPrefs()

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

    actual fun setActiveUserId(userId: String) {
        prefs = context.getSharedPreferences(prefsName(userId), Context.MODE_PRIVATE)
    }

    actual fun clearActiveUserId() {
        prefs.edit().remove(KEY_DEMO_ORDERS).apply()
        prefs.edit().remove(KEY_FIREBASE_WRITE_COUNT).apply()
        prefs = noopPrefs()
    }

    actual fun renameUserId(fromId: String, toId: String) {
        val from = context.getSharedPreferences(prefsName(fromId), Context.MODE_PRIVATE)
        val to = context.getSharedPreferences(prefsName(toId), Context.MODE_PRIVATE)
        from.getString(KEY_DEMO_ORDERS, null)?.let { orders ->
            to.edit().putString(KEY_DEMO_ORDERS, orders).apply()
        }
        val count = from.getInt(KEY_FIREBASE_WRITE_COUNT, 0)
        if (count > 0) {
            to.edit().putInt(KEY_FIREBASE_WRITE_COUNT, count).apply()
        }
        from.edit().clear().apply()
    }

    private fun noopPrefs(): SharedPreferences =
        context.getSharedPreferences(NOOP_PREFS_FILE, Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEMO_ORDERS = "demo_orders"
        private const val KEY_FIREBASE_WRITE_COUNT = "firebase_write_count"
        private const val NOOP_PREFS_FILE = "newverse_noop"
        fun prefsName(userId: String) = "newverse_user_$userId"
    }
}
