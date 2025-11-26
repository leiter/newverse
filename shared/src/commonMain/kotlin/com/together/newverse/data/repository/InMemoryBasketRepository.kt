package com.together.newverse.data.repository

import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.repository.BasketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory implementation of BasketRepository
 * Stores basket items in memory during app session
 */
class InMemoryBasketRepository : BasketRepository {

    private val _basket = MutableStateFlow<List<OrderedProduct>>(emptyList())
    private var _loadedOrderId: String? = null
    private var _loadedOrderDate: String? = null

    override fun observeBasket(): StateFlow<List<OrderedProduct>> {
        return _basket.asStateFlow()
    }

    override suspend fun addItem(item: OrderedProduct) {
        val currentItems = _basket.value.toMutableList()
        // Match by id (Firebase key when loaded from order) or productId (when freshly added)
        val existingIndex = currentItems.indexOfFirst {
            (item.id.isNotEmpty() && it.id == item.id) || it.productId == item.productId
        }

        if (existingIndex >= 0) {
            // Update quantity if item already exists
            val existing = currentItems[existingIndex]
            currentItems[existingIndex] = existing.copy(
                amountCount = existing.amountCount + item.amountCount,
                amount = (existing.amountCount + item.amountCount).toString()
            )
        } else {
            currentItems.add(item)
        }

        _basket.value = currentItems
        println("🛒 BasketRepository.addItem: Added ${item.productName}, basket now has ${currentItems.size} items")
    }

    override suspend fun removeItem(productId: String) {
        _basket.value = _basket.value.filter { it.id != productId && it.productId != productId }
        println("🛒 BasketRepository.removeItem: Removed product $productId, basket now has ${_basket.value.size} items")
    }

    override suspend fun updateQuantity(productId: String, newQuantity: Double) {
        val currentItems = _basket.value.toMutableList()
        val index = currentItems.indexOfFirst { it.id == productId || it.productId == productId }

        if (index >= 0) {
            val item = currentItems[index]
            // Note: We don't have access to weightPerPiece here, so we keep the existing piecesCount ratio
            val newPiecesCount = if (item.amountCount > 0) {
                ((item.piecesCount / item.amountCount) * newQuantity).toInt()
            } else {
                newQuantity.toInt()
            }

            currentItems[index] = item.copy(
                amountCount = newQuantity,
                amount = newQuantity.toString(),
                piecesCount = newPiecesCount
            )
            _basket.value = currentItems
            println("🛒 BasketRepository.updateQuantity: Updated product $productId to quantity $newQuantity (pieces: $newPiecesCount)")
        }
    }

    override suspend fun clearBasket() {
        _basket.value = emptyList()
        _loadedOrderId = null
        _loadedOrderDate = null
        println("🛒 BasketRepository.clearBasket: Basket cleared")
    }

    override fun getTotal(): Double {
        return _basket.value.sumOf { it.price * it.amountCount }
    }

    override fun getItemCount(): Int {
        return _basket.value.size
    }

    override suspend fun loadOrderItems(items: List<OrderedProduct>, orderId: String, orderDate: String) {
        _basket.value = items
        _loadedOrderId = orderId
        _loadedOrderDate = orderDate
        println("🛒 BasketRepository.loadOrderItems: Loaded ${items.size} items from order $orderId (date: $orderDate)")
    }

    override fun getLoadedOrderInfo(): Pair<String, String>? {
        return if (_loadedOrderId != null && _loadedOrderDate != null) {
            Pair(_loadedOrderId!!, _loadedOrderDate!!)
        } else {
            null
        }
    }
}
