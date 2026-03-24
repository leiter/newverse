package com.together.newverse.test

import com.together.newverse.domain.config.MutableSellerConfig
import com.together.newverse.domain.model.Order

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

    private val _demoOrders = mutableListOf<Order>()

    override fun saveDemoOrder(order: Order) {
        _demoOrders.add(order)
    }

    override fun updateDemoOrder(order: Order) {
        val index = _demoOrders.indexOfFirst { it.id == order.id }
        if (index >= 0) _demoOrders[index] = order
    }

    override fun loadDemoOrders(): List<Order> = _demoOrders.toList()

    override fun clearDemoOrders() {
        _demoOrders.clear()
    }

    fun reset() {
        _sellerId = demoSellerId
        _demoOrders.clear()
    }
}
