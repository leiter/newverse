package com.together.newverse.data.config

/**
 * Platform-specific storage for the connected seller ID.
 * Android: SharedPreferences, iOS: NSUserDefaults.
 */
expect class SellerIdStorage {
    fun getConnectedSellerId(): String?
    fun setConnectedSellerId(sellerId: String)
    fun clearConnectedSellerId()
}
