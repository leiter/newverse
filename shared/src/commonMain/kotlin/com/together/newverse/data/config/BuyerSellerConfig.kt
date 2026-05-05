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
    private val storage: SellerIdStorage,
    private val demoStorage: DemoOrderStorage,
) : MutableSellerConfig {

    override val demoSellerId: String = DefaultSellerConfig().sellerId

    private val json = Json { ignoreUnknownKeys = true }

    init {
        // Clear any stale seller ID from a previous version — only one seller is supported.
        val stored = storage.getConnectedSellerId()
        if (stored != null && stored != demoSellerId) {
            storage.clearConnectedSellerId()
        }

        // One-shot migration: move legacy demo orders out of newverse_seller_config
        // into the dedicated newverse_demo_orders prefs file.
        if (demoStorage.getDemoOrdersJson().isBlank()) {
            val legacy = storage.getDemoOrders()
            if (legacy.isNotBlank()) {
                demoStorage.setDemoOrdersJson(legacy)
                storage.clearDemoOrders()
            }
        }
    }

    companion object {
        const val DEMO_FIREBASE_WRITE_LIMIT = 3
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
        demoStorage.setDemoOrdersJson(json.encodeToString(updated))
    }

    override fun updateDemoOrder(order: Order) {
        val existing = loadDemoDtos()
        val updated = existing.map { if (it.id == order.id) order.toDemoDto() else it }
        demoStorage.setDemoOrdersJson(json.encodeToString(updated))
    }

    override fun loadDemoOrders(): List<Order> =
        loadDemoDtos().map { it.toOrder() }

    override fun clearDemoOrders() {
        demoStorage.clearDemoOrders()
    }

    override fun firebaseDemoWritesRemaining(): Int =
        (DEMO_FIREBASE_WRITE_LIMIT - demoStorage.getFirebaseWriteCount()).coerceAtLeast(0)

    override fun recordFirebaseDemoWrite() {
        demoStorage.setFirebaseWriteCount(demoStorage.getFirebaseWriteCount() + 1)
    }

    private fun loadDemoDtos(): List<DemoOrderDto> {
        val raw = demoStorage.getDemoOrdersJson()
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<DemoOrderDto>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setActiveUserId(userId: String) {
        storage.setActiveUserId(userId)
        demoStorage.setActiveUserId(userId)
    }

    fun clearActiveUser() {
        storage.clearActiveUserId()
        demoStorage.clearActiveUserId()
    }

    fun migrateAnonymousUser(fromId: String, toId: String) {
        storage.renameUserId(fromId, toId)
        demoStorage.renameUserId(fromId, toId)
        setActiveUserId(toId)
    }
}
