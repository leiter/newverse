package com.together.newverse.data.config

import android.content.Context
import androidx.core.content.edit

/**
 * Android implementation of [AnnouncementDismissalStorage].
 *
 * One SharedPreferences file for the whole install, holding the keys as a single
 * newline-joined string. A joined string rather than a `StringSet` because
 * `getStringSet` has no order, and the eviction in [dismiss] needs to know which key
 * is oldest.
 */
actual class AnnouncementDismissalStorage(private val context: Context) {

    private val prefs get() = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    actual fun dismissed(): Set<String> = stored().toSet()

    actual fun dismiss(key: String) {
        if (key.isBlank()) return
        val current = stored()
        if (key in current) return
        // Oldest first, so dropping from the front drops the oldest.
        val next = (current + key).takeLast(MAX_DISMISSAL_KEYS)
        prefs.edit { putString(KEY_DISMISSED, next.joinToString(DISMISSAL_SEPARATOR)) }
    }

    actual fun restore(key: String) {
        if (key.isBlank()) return
        val current = stored()
        if (key !in current) return
        prefs.edit {
            putString(KEY_DISMISSED, (current - key).joinToString(DISMISSAL_SEPARATOR))
        }
    }

    actual fun collapseHintsShown(): Int = prefs.getInt(KEY_COLLAPSE_HINTS, 0)

    actual fun noteCollapseHintShown() {
        prefs.edit { putInt(KEY_COLLAPSE_HINTS, collapseHintsShown() + 1) }
    }

    private fun stored(): List<String> =
        prefs.getString(KEY_DISMISSED, null)
            ?.split(DISMISSAL_SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    companion object {
        private const val PREFS_FILE = "newverse_announcements"
        private const val KEY_DISMISSED = "dismissed_keys"
        private const val KEY_COLLAPSE_HINTS = "collapse_hints_shown"
    }
}
