package com.together.newverse.test

import com.together.newverse.domain.model.DraftBasket
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.repository.BasketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake implementation of BasketRepository for testing.
 * Allows controlling basket state and tracking operations.
 */
class FakeBasketRepository : BasketRepository {

    private val _basketItems = MutableStateFlow<List<OrderedProduct>>(emptyList())

    // Track operations for verification
    private val _addedItems = mutableListOf<OrderedProduct>()
    val addedItems: List<OrderedProduct> get() = _addedItems.toList()

    private val _removedProductIds = mutableListOf<String>()
    val removedProductIds: List<String> get() = _removedProductIds.toList()

    private val _quantityUpdates = mutableListOf<Pair<String, Double>>()
    val quantityUpdates: List<Pair<String, Double>> get() = _quantityUpdates.toList()

    var clearBasketCalled = false
        private set

    // Loaded order tracking
    var loadedOrderId: String? = null
        private set
    var loadedOrderDate: String? = null
        private set

    // Configuration for test scenarios
    var shouldSimulateDraft: Boolean = false

    /**
     * Set the basket items directly for testing
     */
    fun setBasketItems(items: List<OrderedProduct>) {
        _basketItems.value = items
    }

    /**
     * Reset repository state for fresh test
     */
    fun reset() {
        _basketItems.value = emptyList()
        _addedItems.clear()
        _removedProductIds.clear()
        _quantityUpdates.clear()
        clearBasketCalled = false
        loadedOrderId = null
        loadedOrderDate = null
        shouldSimulateDraft = false
    }

    override fun observeBasket(): StateFlow<List<OrderedProduct>> {
        return _basketItems.asStateFlow()
    }

    override suspend fun addItem(item: OrderedProduct) {
        _addedItems.add(item)
        val currentItems = _basketItems.value.toMutableList()
        val existingIndex = currentItems.indexOfFirst { it.productId == item.productId }
        if (existingIndex >= 0) {
            currentItems[existingIndex] = item
        } else {
            currentItems.add(item)
        }
        _basketItems.value = currentItems
    }

    override suspend fun removeItem(productId: String) {
        _removedProductIds.add(productId)
        _basketItems.value = _basketItems.value.filter { it.productId != productId }
    }

    override suspend fun updateQuantity(productId: String, newQuantity: Double) {
        _quantityUpdates.add(productId to newQuantity)
        _basketItems.value = _basketItems.value.map { item ->
            if (item.productId == productId) {
                item.copy(amountCount = newQuantity, piecesCount = newQuantity.toInt())
            } else item
        }
    }

    override suspend fun clearBasket() {
        clearBasketCalled = true
        _basketItems.value = emptyList()
        loadedOrderId = null
        loadedOrderDate = null
    }

    override fun getTotal(): Double {
        return _basketItems.value.sumOf { it.price * it.amountCount }
    }

    override fun getItemCount(): Int {
        return _basketItems.value.sumOf { it.piecesCount }
    }

    override suspend fun loadOrderItems(items: List<OrderedProduct>, orderId: String, orderDate: String) {
        loadedOrderId = orderId
        loadedOrderDate = orderDate
        _basketItems.value = items
    }

    override fun getLoadedOrderInfo(): Pair<String, String>? {
        return if (loadedOrderId != null && loadedOrderDate != null) {
            loadedOrderId!! to loadedOrderDate!!
        } else null
    }

    override fun hasDraftBasket(): Boolean {
        return shouldSimulateDraft || (_basketItems.value.isNotEmpty() && loadedOrderId == null)
    }

    override suspend fun loadFromProfile(draftBasket: DraftBasket) {
        _basketItems.value = draftBasket.items
    }

    override fun toDraftBasket(selectedPickupDate: String?): DraftBasket {
        return DraftBasket(
            items = _basketItems.value,
            selectedPickupDate = selectedPickupDate,
            lastModified = System.currentTimeMillis()
        )
    }
}
