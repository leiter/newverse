package com.together.newverse.data.config

/**
 * Platform-specific storage for the buyer UUID assigned by the seller via deep link.
 * Per-user storage: each userId gets their own prefs container.
 * Android: SharedPreferences file `newverse_user_<userId>`, iOS: NSUserDefaults suite.
 */
expect class BuyerUUIDStorage {
    fun get(): String?
    fun set(uuid: String)
    fun clear()

    fun setActiveUserId(userId: String)
    fun clearActiveUserId()
    fun renameUserId(fromId: String, toId: String)
}
