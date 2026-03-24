package com.together.newverse.data.config

import com.together.newverse.domain.config.MutableSellerConfig
import com.together.newverse.domain.model.DemoOrderDto
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.toDemoDto
import com.together.newverse.domain.model.toOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Buyer-side seller configuration that persists the connected seller ID.
 * Uses [SellerIdStorage] for platform-specific persistence (SharedPreferences on Android, NSUserDefaults on iOS).
 */
class BuyerSellerConfig(
    private val storage: SellerIdStorage
) : MutableSellerConfig {

    override val demoSellerId: String = DefaultSellerConfig().sellerId

    private val json = Json { ignoreUnknownKeys = true }

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

    override fun saveDemoOrder(order: Order) {
        val existing = loadDemoDtos()
        val updated = existing + order.toDemoDto()
        storage.setDemoOrders(json.encodeToString(updated))
    }

    override fun updateDemoOrder(order: Order) {
        val existing = loadDemoDtos()
        val updated = existing.map { if (it.id == order.id) order.toDemoDto() else it }
        storage.setDemoOrders(json.encodeToString(updated))
    }

    override fun loadDemoOrders(): List<Order> =
        loadDemoDtos().map { it.toOrder() }

    override fun clearDemoOrders() {
        storage.clearDemoOrders()
    }

    private fun loadDemoDtos(): List<DemoOrderDto> {
        val raw = storage.getDemoOrders()
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<DemoOrderDto>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
