package com.together.newverse.data.config

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of [BuyerUUIDStorage] using NSUserDefaults.
 */
actual class BuyerUUIDStorage {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun get(): String? = defaults.stringForKey(KEY_BUYER_UUID)

    actual fun set(uuid: String) {
        defaults.setObject(uuid, forKey = KEY_BUYER_UUID)
    }

    actual fun clear() {
        defaults.removeObjectForKey(KEY_BUYER_UUID)
    }

    companion object {
        private const val KEY_BUYER_UUID = "buyer_uuid"
    }
}
