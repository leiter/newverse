package com.together.newverse.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Android implementation of [PendingInviteTokenStorage] using SharedPreferences.
 * Per-user storage: each userId gets their own prefs file `newverse_user_<userId>`.
 */
actual class PendingInviteTokenStorage(private val context: Context) {

    private var prefs: SharedPreferences = noopPrefs()

    actual fun get(): String? = prefs.getString(KEY_PENDING_TOKEN, null)

    actual fun set(token: String) {
        prefs.edit { putString(KEY_PENDING_TOKEN, token) }
    }

    actual fun clear() {
        prefs.edit { remove(KEY_PENDING_TOKEN) }
    }

    actual fun setActiveUserId(userId: String) {
        prefs = context.getSharedPreferences(prefsName(userId), Context.MODE_PRIVATE)
    }

    actual fun clearActiveUserId() {
        prefs.edit { remove(KEY_PENDING_TOKEN) }
        prefs = noopPrefs()
    }

    actual fun renameUserId(fromId: String, toId: String) {
        val from = context.getSharedPreferences(prefsName(fromId), Context.MODE_PRIVATE)
        val to = context.getSharedPreferences(prefsName(toId), Context.MODE_PRIVATE)
        from.getString(KEY_PENDING_TOKEN, null)?.let { token ->
            to.edit { putString(KEY_PENDING_TOKEN, token) }
        }
        from.edit { clear() }
    }

    private fun noopPrefs(): SharedPreferences =
        context.getSharedPreferences(NOOP_PREFS_FILE, Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PENDING_TOKEN = "pending_invite_token"
        private const val NOOP_PREFS_FILE = "newverse_noop"
        fun prefsName(userId: String) = "newverse_user_$userId"
    }
}
