package com.together.newverse.data.config

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of [BuyerUUIDStorage] using NSUserDefaults.
 * Per-user storage: each userId gets their own NSUserDefaults suite.
 */
actual class BuyerUUIDStorage {

    private var defaults: NSUserDefaults? = null

    actual fun get(): String? = defaults?.stringForKey(KEY_BUYER_UUID)

    actual fun set(uuid: String) {
        defaults?.setObject(uuid, forKey = KEY_BUYER_UUID)
    }

    actual fun clear() {
        defaults?.removeObjectForKey(KEY_BUYER_UUID)
    }

    actual fun setActiveUserId(userId: String) {
        defaults = NSUserDefaults(suiteName = suiteName(userId))
    }

    actual fun clearActiveUserId() {
        defaults?.removeObjectForKey(KEY_BUYER_UUID)
        defaults = null
    }

    actual fun renameUserId(fromId: String, toId: String) {
        val from = NSUserDefaults(suiteName = suiteName(fromId))
        val to = NSUserDefaults(suiteName = suiteName(toId))
        from?.stringForKey(KEY_BUYER_UUID)?.let { uuid ->
            to?.setObject(uuid, forKey = KEY_BUYER_UUID)
        }
        from?.removeObjectForKey(KEY_BUYER_UUID)
    }

    companion object {
        private const val KEY_BUYER_UUID = "buyer_uuid"
        fun suiteName(userId: String) = "newverse_user_$userId"
    }
}
