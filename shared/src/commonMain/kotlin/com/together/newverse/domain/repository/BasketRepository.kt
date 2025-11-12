package com.together.newverse.domain.repository

import com.together.newverse.domain.model.OrderedProduct
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing shopping basket/cart
 */
interface BasketRepository {
    /**
     * Observe basket items
     * @return StateFlow of current basket items
     */
    fun observeBasket(): StateFlow<List<OrderedProduct>>

    /**
     * Add item to basket
     * @param item The product to add
     */
    suspend fun addItem(item: OrderedProduct)

    /**
     * Remove item from basket
     * @param productId The product ID to remove
     */
    suspend fun removeItem(productId: String)

    /**
     * Update item quantity in basket
     * @param productId The product ID
     * @param newQuantity The new quantity
     */
    suspend fun updateQuantity(productId: String, newQuantity: Double)

    /**
     * Clear all items from basket
     */
    suspend fun clearBasket()

    /**
     * Get current basket total
     * @return Total price of all items
     */
    fun getTotal(): Double

    /**
     * Get current basket item count
     * @return Number of items in basket
     */
    fun getItemCount(): Int

    /**
     * Load items from an existing order
     * Clears basket and adds all order items
     * @param items The order items to load
     * @param orderId The order ID
     * @param orderDate The order date key
     */
    suspend fun loadOrderItems(items: List<OrderedProduct>, orderId: String, orderDate: String)

    /**
     * Get current loaded order info
     * @return Pair of orderId and orderDate, or null if no order is loaded
     */
    fun getLoadedOrderInfo(): Pair<String, String>?
}
