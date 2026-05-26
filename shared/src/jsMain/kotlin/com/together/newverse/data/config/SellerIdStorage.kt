package com.together.newverse.data.config

import kotlinx.browser.localStorage

actual class SellerIdStorage {

    private var activeUserId: String? = null

    private fun key(k: String) = activeUserId?.let { "newverse_user_${it}_$k" } ?: k

    actual fun getConnectedSellerId(): String? = localStorage.getItem(key(KEY_SELLER_ID))
    actual fun setConnectedSellerId(sellerId: String) { localStorage.setItem(key(KEY_SELLER_ID), sellerId) }
    actual fun clearConnectedSellerId() { localStorage.removeItem(key(KEY_SELLER_ID)) }

    actual fun getDemoOrders(): String = localStorage.getItem(key(KEY_DEMO_ORDERS)) ?: ""
    actual fun setDemoOrders(json: String) { localStorage.setItem(key(KEY_DEMO_ORDERS), json) }
    actual fun clearDemoOrders() { localStorage.removeItem(key(KEY_DEMO_ORDERS)) }

    actual fun setActiveUserId(userId: String) { activeUserId = userId }

    actual fun clearActiveUserId() {
        localStorage.removeItem(key(KEY_SELLER_ID))
        localStorage.removeItem(key(KEY_DEMO_ORDERS))
        activeUserId = null
    }

    actual fun renameUserId(fromId: String, toId: String) {
        for (k in listOf(KEY_SELLER_ID, KEY_DEMO_ORDERS)) {
            val fromKey = "newverse_user_${fromId}_$k"
            val toKey = "newverse_user_${toId}_$k"
            localStorage.getItem(fromKey)?.let { localStorage.setItem(toKey, it) }
            localStorage.removeItem(fromKey)
        }
    }

    companion object {
        private const val KEY_SELLER_ID = "connected_seller_id"
        private const val KEY_DEMO_ORDERS = "demo_orders"
    }
}
