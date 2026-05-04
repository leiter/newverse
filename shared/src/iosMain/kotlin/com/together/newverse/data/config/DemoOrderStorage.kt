package com.together.newverse.data.config

import platform.Foundation.NSUserDefaults

actual class DemoOrderStorage {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getDemoOrdersJson(): String =
        defaults.stringForKey(KEY_DEMO_ORDERS) ?: ""

    actual fun setDemoOrdersJson(json: String) {
        defaults.setObject(json, forKey = KEY_DEMO_ORDERS)
    }

    actual fun clearDemoOrders() {
        defaults.removeObjectForKey(KEY_DEMO_ORDERS)
    }

    actual fun getFirebaseWriteCount(): Int =
        defaults.integerForKey(KEY_FIREBASE_WRITE_COUNT).toInt()

    actual fun setFirebaseWriteCount(count: Int) {
        defaults.setInteger(count.toLong(), forKey = KEY_FIREBASE_WRITE_COUNT)
    }

    companion object {
        private const val KEY_DEMO_ORDERS = "newverse_demo_orders.demo_orders"
        private const val KEY_FIREBASE_WRITE_COUNT = "newverse_demo_orders.firebase_write_count"
    }
}
