package com.together.newverse.data.config

/**
 * Dedicated platform storage for demo orders and their Firebase write counter.
 * Per-user storage: each userId gets their own prefs container.
 * Android: SharedPreferences file `newverse_user_<userId>`, iOS: NSUserDefaults suite.
 */
expect class DemoOrderStorage {
    fun getDemoOrdersJson(): String
    fun setDemoOrdersJson(json: String)
    fun clearDemoOrders()
    fun getFirebaseWriteCount(): Int
    fun setFirebaseWriteCount(count: Int)

    fun setActiveUserId(userId: String)
    fun clearActiveUserId()
    fun renameUserId(fromId: String, toId: String)
}
