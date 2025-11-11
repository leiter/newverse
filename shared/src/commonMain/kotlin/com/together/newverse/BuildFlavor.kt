package com.together.newverse

/**
 * Build flavor configuration
 * Initialized at app startup with the current flavor
 */
object BuildFlavor {
    private var _flavorName: String = "buy" // Default to buy

    /**
     * Initialize the flavor (call this from the Android app's onCreate)
     */
    fun initialize(flavor: String) {
        _flavorName = flavor
    }

    /**
     * Returns true if this is the "buy" flavor (customer/buyer app)
     */
    val isBuyFlavor: Boolean
        get() = _flavorName == "buy"

    /**
     * Returns true if this is the "sell" flavor (merchant/seller app)
     */
    val isSellFlavor: Boolean
        get() = _flavorName == "sell"

    /**
     * Returns the flavor name ("buy" or "sell")
     */
    val flavorName: String
        get() = _flavorName
}
