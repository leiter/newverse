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
}
