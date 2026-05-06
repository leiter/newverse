package com.together.newverse.data.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of [SellerIdStorage] using SharedPreferences.
 * Per-user storage: each userId gets their own prefs file `newverse_user_<userId>`.
 */
actual class SellerIdStorage(private val context: Context) {

    private var prefs: SharedPreferences = noopPrefs()

    actual fun getConnectedSellerId(): String? =
        prefs.getString(KEY_CONNECTED_SELLER_ID, null)

    actual fun setConnectedSellerId(sellerId: String) {
        prefs.edit().putString(KEY_CONNECTED_SELLER_ID, sellerId).apply()
    }

    actual fun clearConnectedSellerId() {
        prefs.edit().remove(KEY_CONNECTED_SELLER_ID).apply()
    }

    actual fun getDemoOrders(): String =
        prefs.getString(KEY_DEMO_ORDERS, "") ?: ""

    actual fun setDemoOrders(json: String) {
        prefs.edit().putString(KEY_DEMO_ORDERS, json).apply()
    }

    actual fun clearDemoOrders() {
        prefs.edit().remove(KEY_DEMO_ORDERS).apply()
    }

    actual fun setActiveUserId(userId: String) {
        prefs = context.getSharedPreferences(prefsName(userId), Context.MODE_PRIVATE)
    }

    actual fun clearActiveUserId() {
        prefs.edit().remove(KEY_CONNECTED_SELLER_ID).apply()
        prefs.edit().remove(KEY_DEMO_ORDERS).apply()
        prefs = noopPrefs()
    }

    actual fun renameUserId(fromId: String, toId: String) {
        val from = context.getSharedPreferences(prefsName(fromId), Context.MODE_PRIVATE)
        val to = context.getSharedPreferences(prefsName(toId), Context.MODE_PRIVATE)
        from.getString(KEY_CONNECTED_SELLER_ID, null)?.let { id ->
            to.edit().putString(KEY_CONNECTED_SELLER_ID, id).apply()
        }
        from.getString(KEY_DEMO_ORDERS, null)?.let { orders ->
            to.edit().putString(KEY_DEMO_ORDERS, orders).apply()
        }
        from.edit().clear().apply()
    }

    private fun noopPrefs(): SharedPreferences =
        context.getSharedPreferences(NOOP_PREFS_FILE, Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CONNECTED_SELLER_ID = "connected_seller_id"
        private const val KEY_DEMO_ORDERS = "demo_orders"
        private const val NOOP_PREFS_FILE = "newverse_noop"
        fun prefsName(userId: String) = "newverse_user_$userId"
    }
}
