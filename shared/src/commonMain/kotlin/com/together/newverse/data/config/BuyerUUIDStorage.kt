package com.together.newverse.data.config

/**
 * Platform-specific storage for the buyer UUID assigned by the seller via deep link.
 * Android: SharedPreferences, iOS: NSUserDefaults.
 */
expect class BuyerUUIDStorage {
    fun get(): String?
    fun set(uuid: String)
    fun clear()
}
