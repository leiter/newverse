package com.together.newverse.data.config

/**
 * Dedicated platform storage for demo orders and their Firebase write counter.
 * Android: SharedPreferences file `newverse_demo_orders`. iOS: NSUserDefaults.
 */
expect class DemoOrderStorage {
    fun getDemoOrdersJson(): String
    fun setDemoOrdersJson(json: String)
    fun clearDemoOrders()
    fun getFirebaseWriteCount(): Int
    fun setFirebaseWriteCount(count: Int)
}
