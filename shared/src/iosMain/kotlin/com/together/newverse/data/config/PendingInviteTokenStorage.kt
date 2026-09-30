package com.together.newverse.data.config

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of [PendingInviteTokenStorage] using NSUserDefaults.
 * Per-user storage: each userId gets their own NSUserDefaults suite.
 */
actual class PendingInviteTokenStorage {

    private var defaults: NSUserDefaults? = null

    actual fun get(): String? = defaults?.stringForKey(KEY_PENDING_TOKEN)

    actual fun set(token: String) {
        defaults?.setObject(token, forKey = KEY_PENDING_TOKEN)
    }

    actual fun clear() {
        defaults?.removeObjectForKey(KEY_PENDING_TOKEN)
    }

    actual fun setActiveUserId(userId: String) {
        defaults = NSUserDefaults(suiteName = suiteName(userId))
    }

    actual fun clearActiveUserId() {
        defaults?.removeObjectForKey(KEY_PENDING_TOKEN)
        defaults = null
    }

    actual fun renameUserId(fromId: String, toId: String) {
        val from = NSUserDefaults(suiteName = suiteName(fromId))
        val to = NSUserDefaults(suiteName = suiteName(toId))
        from?.stringForKey(KEY_PENDING_TOKEN)?.let { token ->
            to?.setObject(token, forKey = KEY_PENDING_TOKEN)
        }
        from?.removeObjectForKey(KEY_PENDING_TOKEN)
    }

    companion object {
        private const val KEY_PENDING_TOKEN = "pending_invite_token"
        fun suiteName(userId: String) = "newverse_user_$userId"
    }
}
