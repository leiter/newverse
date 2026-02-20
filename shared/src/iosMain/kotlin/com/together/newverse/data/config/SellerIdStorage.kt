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

    companion object {
        private const val KEY_CONNECTED_SELLER_ID = "connected_seller_id"
    }
}
