package com.together.newverse.test

import com.together.newverse.domain.config.MutableSellerConfig

/**
 * Fake implementation of MutableSellerConfig for testing.
 */
class FakeSellerConfig(
    override val demoSellerId: String = "demo_seller_id"
) : MutableSellerConfig {

    private var _sellerId: String = demoSellerId

    override val sellerId: String
        get() = _sellerId

    override val isDemoMode: Boolean
        get() = _sellerId == demoSellerId

    override fun setSellerId(id: String) {
        _sellerId = id
    }

    override fun resetToDemo() {
        _sellerId = demoSellerId
    }

    private var _demoOrderCount: Int = 0

    override fun getDemoOrderCount(): Int = _demoOrderCount

    override fun incrementDemoOrderCount() {
        _demoOrderCount++
    }

    fun reset() {
        _sellerId = demoSellerId
        _demoOrderCount = 0
    }
}
