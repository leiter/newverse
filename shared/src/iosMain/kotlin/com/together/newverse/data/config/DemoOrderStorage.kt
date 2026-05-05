package com.together.newverse.data.config

import platform.Foundation.NSUserDefaults

actual class DemoOrderStorage {

    private var defaults: NSUserDefaults? = null

    actual fun getDemoOrdersJson(): String =
        defaults?.stringForKey(KEY_DEMO_ORDERS) ?: ""

    actual fun setDemoOrdersJson(json: String) {
        defaults?.setObject(json, forKey = KEY_DEMO_ORDERS)
    }

    actual fun clearDemoOrders() {
        defaults?.removeObjectForKey(KEY_DEMO_ORDERS)
    }

    actual fun getFirebaseWriteCount(): Int =
        defaults?.integerForKey(KEY_FIREBASE_WRITE_COUNT)?.toInt() ?: 0

    actual fun setFirebaseWriteCount(count: Int) {
        defaults?.setInteger(count.toLong(), forKey = KEY_FIREBASE_WRITE_COUNT)
    }

    actual fun setActiveUserId(userId: String) {
        defaults = NSUserDefaults(suiteName = suiteName(userId))
    }

    actual fun clearActiveUserId() {
        defaults?.removeObjectForKey(KEY_DEMO_ORDERS)
        defaults?.removeObjectForKey(KEY_FIREBASE_WRITE_COUNT)
        defaults = null
    }

    actual fun renameUserId(fromId: String, toId: String) {
        val from = NSUserDefaults(suiteName = suiteName(fromId))
        val to = NSUserDefaults(suiteName = suiteName(toId))
        from?.stringForKey(KEY_DEMO_ORDERS)?.let { orders ->
            to?.setObject(orders, forKey = KEY_DEMO_ORDERS)
        }
        val count = from?.integerForKey(KEY_FIREBASE_WRITE_COUNT)?.toInt() ?: 0
        if (count > 0) {
            to?.setInteger(count.toLong(), forKey = KEY_FIREBASE_WRITE_COUNT)
        }
        from?.removeObjectForKey(KEY_DEMO_ORDERS)
        from?.removeObjectForKey(KEY_FIREBASE_WRITE_COUNT)
    }

    companion object {
        private const val KEY_DEMO_ORDERS = "demo_orders"
        private const val KEY_FIREBASE_WRITE_COUNT = "firebase_write_count"
        fun suiteName(userId: String) = "newverse_user_$userId"
    }
}
