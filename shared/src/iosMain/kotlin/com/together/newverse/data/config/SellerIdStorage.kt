package com.together.newverse.data.config

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of [SellerIdStorage] using NSUserDefaults.
 */
actual class SellerIdStorage {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getConnectedSellerId(): String? =
        defaults.stringForKey(KEY_CONNECTED_SELLER_ID)

    actual fun setConnectedSellerId(sellerId: String) {
        defaults.setObject(sellerId, forKey = KEY_CONNECTED_SELLER_ID)
    }

    actual fun clearConnectedSellerId() {
        defaults.removeObjectForKey(KEY_CONNECTED_SELLER_ID)
    }

    actual fun getDemoOrders(): String =
        defaults.stringForKey(KEY_DEMO_ORDERS) ?: ""

    actual fun setDemoOrders(json: String) {
        defaults.setObject(json, forKey = KEY_DEMO_ORDERS)
    }

    actual fun clearDemoOrders() {
        defaults.removeObjectForKey(KEY_DEMO_ORDERS)
    }

    companion object {
        private const val KEY_CONNECTED_SELLER_ID = "connected_seller_id"
        private const val KEY_DEMO_ORDERS = "demo_orders"
    }
}
