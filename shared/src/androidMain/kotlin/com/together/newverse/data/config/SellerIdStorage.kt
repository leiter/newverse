package com.together.newverse.data.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of [SellerIdStorage] using SharedPreferences.
 */
actual class SellerIdStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    actual fun getConnectedSellerId(): String? =
        prefs.getString(KEY_CONNECTED_SELLER_ID, null)

    actual fun setConnectedSellerId(sellerId: String) {
        prefs.edit().putString(KEY_CONNECTED_SELLER_ID, sellerId).apply()
    }

    actual fun clearConnectedSellerId() {
        prefs.edit().remove(KEY_CONNECTED_SELLER_ID).apply()
    }

    companion object {
        private const val PREFS_FILE = "newverse_seller_config"
        private const val KEY_CONNECTED_SELLER_ID = "connected_seller_id"
    }
}
