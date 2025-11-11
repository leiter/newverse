package com.together.newverse

import com.together.newverse.shared.BuildKonfig

/**
 * Build flavor configuration
 * Now uses BuildKonfig for compile-time flavor detection (no initialization needed)
 */
object BuildFlavor {
    /**
     * Returns true if this is the "buy" flavor (customer/buyer app)
     */
    val isBuyFlavor: Boolean
        get() = BuildKonfig.IS_BUY_APP

    /**
     * Returns true if this is the "sell" flavor (merchant/seller app)
     */
    val isSellFlavor: Boolean
        get() = BuildKonfig.IS_SELL_APP

    /**
     * Returns the flavor name ("buy" or "sell")
     */
    val flavorName: String
        get() = BuildKonfig.USER_TYPE

    /**
     * Returns the app name for the current flavor
     */
    val appName: String
        get() = BuildKonfig.APP_NAME

    /**
     * @deprecated No longer needed - BuildKonfig provides compile-time configuration
     * Kept for backward compatibility but does nothing
     */
    @Deprecated("No longer needed - BuildKonfig provides compile-time configuration")
    fun initialize(flavor: String) {
        // No-op: BuildKonfig handles this at compile time
    }
}
