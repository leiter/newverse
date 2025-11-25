package com.together.newverse.domain.repository

import com.together.newverse.domain.model.Order
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing order data
 */
interface OrderRepository {
    /**
     * Observe orders for a seller
     * @param sellerId The seller's ID
     * @return Flow of orders with real-time updates
     */
    fun observeSellerOrders(sellerId: String): Flow<List<Order>>

    /**
     * Get buyer's placed orders
     * @param sellerId The seller's ID
     * @param placedOrderIds Map of date to order ID
     * @return List of orders
     */
    suspend fun getBuyerOrders(sellerId: String, placedOrderIds: Map<String, String>): Result<List<Order>>

    /**
     * Place a new order
     * @param order The order to place
     * @return Updated order with ID
     */
    suspend fun placeOrder(order: Order): Result<Order>

    /**
     * Update an existing order
     * @param order The order to update
     * @return Success or failure result
     */
    suspend fun updateOrder(order: Order): Result<Unit>

    /**
     * Cancel an order
     * @param sellerId The seller's ID
     * @param date The order date
     * @param orderId The order ID
     * @return Success or failure result
     */
    suspend fun cancelOrder(sellerId: String, date: String, orderId: String): Result<Boolean>

    /**
     * Hide an order from seller's view
     * @param sellerId The seller's ID
     * @param date The order date
     * @param orderId The order ID
     * @return Success or failure result
     */
    suspend fun hideOrderForSeller(sellerId: String, date: String, orderId: String): Result<Boolean>

    /**
     * Hide an order from buyer's view
     * @param sellerId The seller's ID
     * @param date The order date
     * @param orderId The order ID
     * @return Success or failure result
     */
    suspend fun hideOrderForBuyer(sellerId: String, date: String, orderId: String): Result<Boolean>

    /**
     * Load a specific order
     * @param sellerId The seller's ID
     * @param orderId The order ID
     * @param orderPath The order path
     * @return Order details
     */
    suspend fun loadOrder(sellerId: String, orderId: String, orderPath: String): Result<Order>

    /**
     * Get the most recent open/editable order for the current buyer
     * An order is considered editable if it's more than 3 days before pickup
     * @param sellerId The seller's ID
     * @param placedOrderIds Map of date to order ID
     * @return The most recent editable order, or null if none found
     */
    suspend fun getOpenEditableOrder(sellerId: String, placedOrderIds: Map<String, String>): Result<Order?>

    /**
     * Get the most recent upcoming order for the current buyer (regardless of editability)
     * An order is considered upcoming if pickup date is in the future
     * @param sellerId The seller's ID
     * @param placedOrderIds Map of date to order ID
     * @return The most recent upcoming order, or null if none found
     */
    suspend fun getUpcomingOrder(sellerId: String, placedOrderIds: Map<String, String>): Result<Order?>
}
