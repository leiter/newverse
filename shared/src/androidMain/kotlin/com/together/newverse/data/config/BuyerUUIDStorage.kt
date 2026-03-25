package com.together.newverse.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Android implementation of [BuyerUUIDStorage] using SharedPreferences.
 */
actual class BuyerUUIDStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    actual fun get(): String? = prefs.getString(KEY_BUYER_UUID, null)

    actual fun set(uuid: String) {
        prefs.edit { putString(KEY_BUYER_UUID, uuid) }
    }

    actual fun clear() {
        prefs.edit { remove(KEY_BUYER_UUID) }
    }

    companion object {
        private const val PREFS_FILE = "newverse_seller_config"
        private const val KEY_BUYER_UUID = "buyer_uuid"
    }
}
