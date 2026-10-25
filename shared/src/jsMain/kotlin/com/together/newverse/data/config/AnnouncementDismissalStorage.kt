package com.together.newverse.data.config

import kotlinx.browser.localStorage

actual class AnnouncementDismissalStorage {

    actual fun dismissed(): Set<String> = stored().toSet()

    actual fun dismiss(key: String) {
        if (key.isBlank()) return
        val current = stored()
        if (key in current) return
        val next = (current + key).takeLast(MAX_DISMISSAL_KEYS)
        localStorage.setItem(KEY_DISMISSED, next.joinToString(DISMISSAL_SEPARATOR))
    }

    private fun stored(): List<String> =
        localStorage.getItem(KEY_DISMISSED)
            ?.split(DISMISSAL_SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    companion object {
        private const val KEY_DISMISSED = "dismissed_announcements"
    }
}
