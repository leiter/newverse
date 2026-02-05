package com.together.newverse.data.repository

import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.preview.PreviewData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * Mock implementation of OrderRepository for development and testing
 */
class MockOrderRepository : OrderRepository {

    private val _orders = MutableStateFlow(PreviewData.sampleOrders)

    override fun observeSellerOrders(sellerId: String): Flow<List<Order>> {
        return _orders.asStateFlow()
    }

    override suspend fun getBuyerOrders(sellerId: String, placedOrderIds: Map<String, String>): Result<List<Order>> {
        return try {
            delay(500)
            // Filter orders based on placed order IDs
            val filteredOrders = PreviewData.sampleOrders.filter { order ->
                placedOrderIds.values.contains(order.id)
            }
            Result.success(filteredOrders.ifEmpty { PreviewData.sampleOrders })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeBuyerOrders(sellerId: String, placedOrderIds: Map<String, String>): Flow<List<Order>> {
        // Return orders flow filtered by placed order IDs
        return _orders.asStateFlow()
    }

    override suspend fun placeOrder(order: Order): Result<Order> {
        return try {
            delay(500)
            val newOrder = order.copy(
                id = "order_${Clock.System.now().toEpochMilliseconds()}",
                createdDate = Clock.System.now().toEpochMilliseconds()
            )
            val currentOrders = _orders.value.toMutableList()
            currentOrders.add(0, newOrder) // Add to beginning
            _orders.value = currentOrders
            Result.success(newOrder)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateOrder(order: Order): Result<Unit> {
        return try {
            delay(300)
            val currentOrders = _orders.value.toMutableList()
            val index = currentOrders.indexOfFirst { it.id == order.id }
            if (index >= 0) {
                currentOrders[index] = order
                _orders.value = currentOrders
                Result.success(Unit)
            } else {
                Result.failure(Exception("Order not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelOrder(sellerId: String, date: String, orderId: String): Result<Boolean> {
        return try {
            delay(300)
            val currentOrders = _orders.value.toMutableList()
            val index = currentOrders.indexOfFirst { it.id == orderId }
            if (index >= 0) {
                // Remove the order from the list
                currentOrders.removeAt(index)
                _orders.value = currentOrders
                Result.success(true)
            } else {
                Result.failure(Exception("Order not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadOrder(sellerId: String, orderId: String, orderPath: String): Result<Order> {
        return try {
            delay(300)
            val order = _orders.value.find { it.id == orderId }
            if (order != null) {
                Result.success(order)
            } else {
                Result.failure(Exception("Order not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getOpenEditableOrder(sellerId: String, placedOrderIds: Map<String, String>): Result<Order?> {
        return try {
            delay(300)
            // Get all orders
            val orders = _orders.value.filter { order ->
                placedOrderIds.values.contains(order.id)
            }

            // Filter for editable orders (more than 3 days before pickup)
            val now = Clock.System.now().toEpochMilliseconds()
            val editableOrders = orders.filter { order ->
                val threeDaysBeforePickup = order.pickUpDate - (3 * 24 * 60 * 60 * 1000)
                now < threeDaysBeforePickup
            }

            // Return the most recent editable order
            val mostRecent = editableOrders.maxByOrNull { it.pickUpDate }
            Result.success(mostRecent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUpcomingOrder(sellerId: String, placedOrderIds: Map<String, String>): Result<Order?> {
        return try {
            delay(300)
            // Get all orders
            val orders = _orders.value.filter { order ->
                placedOrderIds.values.contains(order.id)
            }

            // Filter for upcoming orders (pickup date in the future)
            val now = Clock.System.now().toEpochMilliseconds()
            val upcomingOrders = orders.filter { order ->
                order.pickUpDate > now
            }

            // Return the most recent upcoming order
            val mostRecent = upcomingOrders.maxByOrNull { it.pickUpDate }
            Result.success(mostRecent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun hideOrderForSeller(sellerId: String, date: String, orderId: String): Result<Boolean> {
        return try {
            delay(300)
            val currentOrders = _orders.value.toMutableList()
            val index = currentOrders.indexOfFirst { it.id == orderId }
            if (index >= 0) {
                // Update order to set hiddenBySeller flag
                currentOrders[index] = currentOrders[index].copy(hiddenBySeller = true)
                _orders.value = currentOrders
                Result.success(true)
            } else {
                Result.failure(Exception("Order not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun hideOrderForBuyer(sellerId: String, date: String, orderId: String): Result<Boolean> {
        return try {
            delay(300)
            val currentOrders = _orders.value.toMutableList()
            val index = currentOrders.indexOfFirst { it.id == orderId }
            if (index >= 0) {
                // Update order to set hiddenByBuyer flag
                currentOrders[index] = currentOrders[index].copy(hiddenByBuyer = true)
                _orders.value = currentOrders
                Result.success(true)
            } else {
                Result.failure(Exception("Order not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateOrderStatus(
        sellerId: String,
        date: String,
        orderId: String,
        status: OrderStatus
    ): Result<Unit> {
        return try {
            delay(100)
            val currentOrders = _orders.value.toMutableList()
            val index = currentOrders.indexOfFirst { it.id == orderId }
            if (index >= 0) {
                currentOrders[index] = currentOrders[index].copy(status = status)
                _orders.value = currentOrders
                Result.success(Unit)
            } else {
                Result.failure(Exception("Order not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}