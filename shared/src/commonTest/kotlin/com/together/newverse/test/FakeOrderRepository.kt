package com.together.newverse.test

import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake implementation of OrderRepository for testing.
 * Allows controlling order emissions and tracking operations.
 */
class FakeOrderRepository : OrderRepository {

    // StateFlow for emitting orders - tests can set this
    private val _ordersFlow = MutableStateFlow<List<Order>>(emptyList())

    // Track operations for verification
    private val _statusUpdates = mutableListOf<StatusUpdate>()
    val statusUpdates: List<StatusUpdate> get() = _statusUpdates.toList()

    private val _hiddenOrders = mutableListOf<HiddenOrder>()
    val hiddenOrders: List<HiddenOrder> get() = _hiddenOrders.toList()

    // Configuration for test scenarios
    var shouldFailUpdateStatus = false
    var shouldFailHideOrder = false
    var failureMessage = "Test error"

    data class StatusUpdate(
        val sellerId: String,
        val date: String,
        val orderId: String,
        val status: OrderStatus
    )

    data class HiddenOrder(
        val sellerId: String,
        val date: String,
        val orderId: String,
        val forSeller: Boolean
    )

    /**
     * Set the orders to be emitted
     */
    fun setOrders(orders: List<Order>) {
        _ordersFlow.value = orders
    }

    /**
     * Reset repository state for fresh test
     */
    fun reset() {
        _ordersFlow.value = emptyList()
        _statusUpdates.clear()
        _hiddenOrders.clear()
        shouldFailUpdateStatus = false
        shouldFailHideOrder = false
        failureMessage = "Test error"
    }

    override fun observeSellerOrders(sellerId: String): Flow<List<Order>> {
        return _ordersFlow.asStateFlow()
    }

    override suspend fun getBuyerOrders(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Result<List<Order>> {
        val filteredOrders = _ordersFlow.value.filter { order ->
            placedOrderIds.values.contains(order.id)
        }
        return Result.success(filteredOrders)
    }

    override fun observeBuyerOrders(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Flow<List<Order>> {
        return _ordersFlow.asStateFlow()
    }

    override suspend fun placeOrder(order: Order): Result<Order> {
        val newOrder = order.copy(id = "order_${System.currentTimeMillis()}")
        val currentOrders = _ordersFlow.value.toMutableList()
        currentOrders.add(0, newOrder)
        _ordersFlow.value = currentOrders
        return Result.success(newOrder)
    }

    override suspend fun updateOrder(order: Order): Result<Unit> {
        val currentOrders = _ordersFlow.value.toMutableList()
        val index = currentOrders.indexOfFirst { it.id == order.id }
        if (index >= 0) {
            currentOrders[index] = order
            _ordersFlow.value = currentOrders
            return Result.success(Unit)
        }
        return Result.failure(Exception("Order not found"))
    }

    override suspend fun cancelOrder(
        sellerId: String,
        date: String,
        orderId: String
    ): Result<Boolean> {
        val currentOrders = _ordersFlow.value.toMutableList()
        val index = currentOrders.indexOfFirst { it.id == orderId }
        if (index >= 0) {
            currentOrders.removeAt(index)
            _ordersFlow.value = currentOrders
            return Result.success(true)
        }
        return Result.failure(Exception("Order not found"))
    }

    override suspend fun hideOrderForSeller(
        sellerId: String,
        date: String,
        orderId: String
    ): Result<Boolean> {
        if (shouldFailHideOrder) {
            return Result.failure(Exception(failureMessage))
        }

        _hiddenOrders.add(HiddenOrder(sellerId, date, orderId, forSeller = true))

        val currentOrders = _ordersFlow.value.toMutableList()
        val index = currentOrders.indexOfFirst { it.id == orderId }
        if (index >= 0) {
            currentOrders[index] = currentOrders[index].copy(hiddenBySeller = true)
            _ordersFlow.value = currentOrders
        }
        return Result.success(true)
    }

    override suspend fun hideOrderForBuyer(
        sellerId: String,
        date: String,
        orderId: String
    ): Result<Boolean> {
        if (shouldFailHideOrder) {
            return Result.failure(Exception(failureMessage))
        }

        _hiddenOrders.add(HiddenOrder(sellerId, date, orderId, forSeller = false))

        val currentOrders = _ordersFlow.value.toMutableList()
        val index = currentOrders.indexOfFirst { it.id == orderId }
        if (index >= 0) {
            currentOrders[index] = currentOrders[index].copy(hiddenByBuyer = true)
            _ordersFlow.value = currentOrders
        }
        return Result.success(true)
    }

    override suspend fun loadOrder(
        sellerId: String,
        orderId: String,
        orderPath: String
    ): Result<Order> {
        val order = _ordersFlow.value.find { it.id == orderId }
        return if (order != null) {
            Result.success(order)
        } else {
            Result.failure(Exception("Order not found"))
        }
    }

    override suspend fun getOpenEditableOrder(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Result<Order?> {
        val orders = _ordersFlow.value.filter { order ->
            placedOrderIds.values.contains(order.id)
        }
        return Result.success(orders.firstOrNull())
    }

    override suspend fun getUpcomingOrder(
        sellerId: String,
        placedOrderIds: Map<String, String>
    ): Result<Order?> {
        val orders = _ordersFlow.value.filter { order ->
            placedOrderIds.values.contains(order.id)
        }
        return Result.success(orders.firstOrNull())
    }

    override suspend fun updateOrderStatus(
        sellerId: String,
        date: String,
        orderId: String,
        status: OrderStatus
    ): Result<Unit> {
        if (shouldFailUpdateStatus) {
            return Result.failure(Exception(failureMessage))
        }

        _statusUpdates.add(StatusUpdate(sellerId, date, orderId, status))

        val currentOrders = _ordersFlow.value.toMutableList()
        val index = currentOrders.indexOfFirst { it.id == orderId }
        if (index >= 0) {
            currentOrders[index] = currentOrders[index].copy(status = status)
            _ordersFlow.value = currentOrders
            return Result.success(Unit)
        }
        return Result.failure(Exception("Order not found"))
    }
}
