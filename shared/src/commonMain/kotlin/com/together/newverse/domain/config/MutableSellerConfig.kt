package com.together.newverse.domain.config

/**
 * Mutable seller configuration for the buyer app.
 * Allows changing the connected seller at runtime (e.g., via QR code scan or manual input).
 */
interface MutableSellerConfig : SellerConfig {
    /** The demo seller ID used when no seller is explicitly connected. */
    val demoSellerId: String

    /** Whether the buyer is currently in demo mode (using the demo seller). */
    val isDemoMode: Boolean

    /** Set the connected seller ID. Pass [demoSellerId] to reset to demo mode. */
    fun setSellerId(id: String)

    /** Reset to the demo seller. */
    fun resetToDemo()
}
