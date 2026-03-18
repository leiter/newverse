package com.together.newverse.data.config

import com.together.newverse.domain.config.MutableSellerConfig

/**
 * Buyer-side seller configuration that persists the connected seller ID.
 * Uses [SellerIdStorage] for platform-specific persistence (SharedPreferences on Android, NSUserDefaults on iOS).
 */
class BuyerSellerConfig(
    private val storage: SellerIdStorage
) : MutableSellerConfig {

    override val demoSellerId: String = DefaultSellerConfig().sellerId

    init {
        // Clear any stale seller ID from a previous version — only one seller is supported.
        val stored = storage.getConnectedSellerId()
        if (stored != null && stored != demoSellerId) {
            storage.clearConnectedSellerId()
        }
    }

    override val sellerId: String
        get() = storage.getConnectedSellerId() ?: demoSellerId

    override val isDemoMode: Boolean
        get() = sellerId == demoSellerId

    override fun setSellerId(id: String) {
        if (id == demoSellerId) {
            storage.clearConnectedSellerId()
        } else {
            storage.setConnectedSellerId(id)
        }
    }

    override fun resetToDemo() {
        storage.clearConnectedSellerId()
    }

    override fun getDemoOrderCount(): Int = storage.getDemoOrderCount()

    override fun incrementDemoOrderCount() = storage.incrementDemoOrderCount()
}
