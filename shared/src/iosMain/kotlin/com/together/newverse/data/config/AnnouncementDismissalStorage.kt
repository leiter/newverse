package com.together.newverse.data.config

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of [AnnouncementDismissalStorage].
 *
 * The standard defaults rather than a per-user suite, deliberately — see the expect
 * declaration. Stored as one newline-joined string so the order survives, which the
 * eviction in [dismiss] relies on.
 */
actual class AnnouncementDismissalStorage {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun dismissed(): Set<String> = stored().toSet()

    actual fun dismiss(key: String) {
        if (key.isBlank()) return
        val current = stored()
        if (key in current) return
        val next = (current + key).takeLast(MAX_DISMISSAL_KEYS)
        defaults.setObject(next.joinToString(DISMISSAL_SEPARATOR), forKey = KEY_DISMISSED)
    }

    private fun stored(): List<String> =
        defaults.stringForKey(KEY_DISMISSED)
            ?.split(DISMISSAL_SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    companion object {
        private const val KEY_DISMISSED = "dismissed_announcements"
    }
}
