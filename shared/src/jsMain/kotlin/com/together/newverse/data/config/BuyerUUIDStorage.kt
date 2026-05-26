package com.together.newverse.data.config

import kotlinx.browser.localStorage

actual class BuyerUUIDStorage {

    private var activeUserId: String? = null

    private fun key(k: String) = activeUserId?.let { "newverse_user_${it}_$k" } ?: k

    actual fun get(): String? = localStorage.getItem(key(KEY))
    actual fun set(uuid: String) { localStorage.setItem(key(KEY), uuid) }
    actual fun clear() { localStorage.removeItem(key(KEY)) }

    actual fun setActiveUserId(userId: String) { activeUserId = userId }

    actual fun clearActiveUserId() {
        localStorage.removeItem(key(KEY))
        activeUserId = null
    }

    actual fun renameUserId(fromId: String, toId: String) {
        val fromKey = "newverse_user_${fromId}_$KEY"
        val toKey = "newverse_user_${toId}_$KEY"
        localStorage.getItem(fromKey)?.let { localStorage.setItem(toKey, it) }
        localStorage.removeItem(fromKey)
    }

    companion object {
        private const val KEY = "buyer_uuid"
    }
}
