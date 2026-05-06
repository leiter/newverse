package com.together.newverse.data.config

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of [SellerIdStorage] using NSUserDefaults.
 * Per-user storage: each userId gets their own NSUserDefaults suite.
 */
actual class SellerIdStorage {

    private var defaults: NSUserDefaults? = null

    actual fun getConnectedSellerId(): String? =
        defaults?.stringForKey(KEY_CONNECTED_SELLER_ID)

    actual fun setConnectedSellerId(sellerId: String) {
        defaults?.setObject(sellerId, forKey = KEY_CONNECTED_SELLER_ID)
    }

    actual fun clearConnectedSellerId() {
        defaults?.removeObjectForKey(KEY_CONNECTED_SELLER_ID)
    }

    actual fun getDemoOrders(): String =
        defaults?.stringForKey(KEY_DEMO_ORDERS) ?: ""

    actual fun setDemoOrders(json: String) {
        defaults?.setObject(json, forKey = KEY_DEMO_ORDERS)
    }

    actual fun clearDemoOrders() {
        defaults?.removeObjectForKey(KEY_DEMO_ORDERS)
    }

    actual fun setActiveUserId(userId: String) {
        defaults = NSUserDefaults(suiteName = suiteName(userId))
    }

    actual fun clearActiveUserId() {
        defaults?.removeObjectForKey(KEY_CONNECTED_SELLER_ID)
        defaults?.removeObjectForKey(KEY_DEMO_ORDERS)
        defaults = null
    }

    actual fun renameUserId(fromId: String, toId: String) {
        val from = NSUserDefaults(suiteName = suiteName(fromId))
        val to = NSUserDefaults(suiteName = suiteName(toId))
        from?.stringForKey(KEY_CONNECTED_SELLER_ID)?.let { id ->
            to?.setObject(id, forKey = KEY_CONNECTED_SELLER_ID)
        }
        from?.stringForKey(KEY_DEMO_ORDERS)?.let { orders ->
            to?.setObject(orders, forKey = KEY_DEMO_ORDERS)
        }
        from?.removeObjectForKey(KEY_CONNECTED_SELLER_ID)
        from?.removeObjectForKey(KEY_DEMO_ORDERS)
    }

    companion object {
        private const val KEY_CONNECTED_SELLER_ID = "connected_seller_id"
        private const val KEY_DEMO_ORDERS = "demo_orders"
        fun suiteName(userId: String) = "newverse_user_$userId"
    }
}
