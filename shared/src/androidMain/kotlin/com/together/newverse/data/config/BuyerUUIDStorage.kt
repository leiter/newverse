package com.together.newverse.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Android implementation of [BuyerUUIDStorage] using SharedPreferences.
 * Per-user storage: each userId gets their own prefs file `newverse_user_<userId>`.
 */
actual class BuyerUUIDStorage(private val context: Context) {

    private var prefs: SharedPreferences = noopPrefs()

    actual fun get(): String? = prefs.getString(KEY_BUYER_UUID, null)

    actual fun set(uuid: String) {
        prefs.edit { putString(KEY_BUYER_UUID, uuid) }
    }

    actual fun clear() {
        prefs.edit { remove(KEY_BUYER_UUID) }
    }

    actual fun setActiveUserId(userId: String) {
        prefs = context.getSharedPreferences(prefsName(userId), Context.MODE_PRIVATE)
    }

    actual fun clearActiveUserId() {
        prefs.edit { remove(KEY_BUYER_UUID) }
        prefs = noopPrefs()
    }

    actual fun renameUserId(fromId: String, toId: String) {
        val from = context.getSharedPreferences(prefsName(fromId), Context.MODE_PRIVATE)
        val to = context.getSharedPreferences(prefsName(toId), Context.MODE_PRIVATE)
        from.getString(KEY_BUYER_UUID, null)?.let { uuid ->
            to.edit { putString(KEY_BUYER_UUID, uuid) }
        }
        from.edit { clear() }
    }

    private fun noopPrefs(): SharedPreferences =
        context.getSharedPreferences(NOOP_PREFS_FILE, Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BUYER_UUID = "buyer_uuid"
        private const val NOOP_PREFS_FILE = "newverse_noop"
        fun prefsName(userId: String) = "newverse_user_$userId"
    }
}
