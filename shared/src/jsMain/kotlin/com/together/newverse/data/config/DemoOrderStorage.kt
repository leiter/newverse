package com.together.newverse.data.config

import kotlinx.browser.localStorage

actual class DemoOrderStorage {

    private var activeUserId: String? = null

    private fun key(k: String) = activeUserId?.let { "newverse_user_${it}_$k" } ?: k

    actual fun getDemoOrdersJson(): String = localStorage.getItem(key(KEY_DEMO_ORDERS)) ?: ""
    actual fun setDemoOrdersJson(json: String) { localStorage.setItem(key(KEY_DEMO_ORDERS), json) }
    actual fun clearDemoOrders() { localStorage.removeItem(key(KEY_DEMO_ORDERS)) }

    actual fun getFirebaseWriteCount(): Int =
        localStorage.getItem(key(KEY_WRITE_COUNT))?.toIntOrNull() ?: 0

    actual fun setFirebaseWriteCount(count: Int) {
        localStorage.setItem(key(KEY_WRITE_COUNT), count.toString())
    }

    actual fun setActiveUserId(userId: String) { activeUserId = userId }

    actual fun clearActiveUserId() {
        localStorage.removeItem(key(KEY_DEMO_ORDERS))
        localStorage.removeItem(key(KEY_WRITE_COUNT))
        activeUserId = null
    }

    actual fun renameUserId(fromId: String, toId: String) {
        for (k in listOf(KEY_DEMO_ORDERS, KEY_WRITE_COUNT)) {
            val fromKey = "newverse_user_${fromId}_$k"
            val toKey = "newverse_user_${toId}_$k"
            localStorage.getItem(fromKey)?.let { localStorage.setItem(toKey, it) }
            localStorage.removeItem(fromKey)
        }
    }

    companion object {
        private const val KEY_DEMO_ORDERS = "demo_orders"
        private const val KEY_WRITE_COUNT = "firebase_write_count"
    }
}
