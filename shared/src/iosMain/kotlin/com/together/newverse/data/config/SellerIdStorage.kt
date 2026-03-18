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

    actual fun getDemoOrderCount(): Int =
        defaults.integerForKey(KEY_DEMO_ORDER_COUNT).toInt()

    actual fun incrementDemoOrderCount() {
        defaults.setInteger((getDemoOrderCount() + 1).toLong(), forKey = KEY_DEMO_ORDER_COUNT)
    }

    companion object {
        private const val KEY_CONNECTED_SELLER_ID = "connected_seller_id"
        private const val KEY_DEMO_ORDER_COUNT = "demo_order_count"
    }
}
