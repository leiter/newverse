package com.together.newverse.config

/**
 * Build flavor configuration
 *
 * Determines which app flavor is currently running:
 * - BUY: Customer/buyer app (allows guest access)
 * - SELL: Seller/vendor app (requires authentication)
 */
enum class AppFlavor {
    /**
     * Customer/buyer app flavor
     * - Allows guest/anonymous access
     * - Shows product catalog and ordering features
     */
    BUY,

    /**
     * Seller/vendor app flavor
     * - Requires authentication (no guest access)
     * - Shows seller profile, product management, and order management
     * - Requires seller profile to be created before app access
     */
    SELL
}

/**
 * Build flavor detector using expect/actual pattern
 */
expect object BuildFlavorDetector {
    /**
     * Get the current build flavor
     * Implementation is platform-specific based on build configuration
     */
    fun getCurrentFlavor(): AppFlavor
}

/**
 * Global accessor for current build flavor
 */
object BuildFlavor {
    val current: AppFlavor by lazy {
        BuildFlavorDetector.getCurrentFlavor()
    }

    /**
     * Check if current flavor is SELL
     */
    val isSeller: Boolean
        get() = current == AppFlavor.SELL

    /**
     * Check if current flavor is BUY
     */
    val isBuyer: Boolean
        get() = current == AppFlavor.BUY

    /**
     * Check if guest access is allowed for current flavor
     */
    val allowsGuestAccess: Boolean
        get() = current == AppFlavor.BUY
}
