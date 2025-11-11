package com.together.newverse.data.repository

import com.together.newverse.domain.model.Order
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.preview.PreviewData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

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
}