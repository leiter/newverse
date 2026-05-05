package com.together.newverse.data.config

/**
 * Platform-specific storage for the connected seller ID.
 * Per-user storage: each userId gets their own prefs container.
 * Android: SharedPreferences file `newverse_user_<userId>`, iOS: NSUserDefaults suite.
 */
expect class SellerIdStorage {
    fun getConnectedSellerId(): String?
    fun setConnectedSellerId(sellerId: String)
    fun clearConnectedSellerId()
    fun getDemoOrders(): String
    fun setDemoOrders(json: String)
    fun clearDemoOrders()

    fun setActiveUserId(userId: String)
    fun clearActiveUserId()
    fun renameUserId(fromId: String, toId: String)
}
