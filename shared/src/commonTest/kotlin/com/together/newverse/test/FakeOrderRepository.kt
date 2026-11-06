package com.together.newverse.test

import com.together.newverse.domain.model.DemoTransitionResult
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.canTransitionToProduction
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.util.OrderDateUtils
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
        cancelCalls.clear()
        deleteCalls.clear()
        deleteDemoOrdersFails = false
        _placedOrders.clear()
        _updatedOrders.clear()
        _ordersFlow.value = emptyList()
        _statusUpdates.clear()
        _hiddenOrders.clear()
        shouldFailUpdateStatus = false
        shouldFailHideOrder = false
        shouldFailTransition = false
        transitions.clear()
        failureMessage = "Test error"
    }

    override fun observeSellerOrders(sellerId: String): Flow<List<Order>> {
        return _ordersFlow.asStateFlow()
    }

    override suspend fun getBuyerOrders(
        sellerId: String,
        placedOrderIds: Map<String, String>,
        isDemo: Boolean
    ): Result<List<Order>> {
        val filteredOrders = _ordersFlow.value.filter { order ->
            placedOrderIds.values.contains(order.id)
        }
        return Result.success(filteredOrders)
    }

    override fun observeBuyerOrders(
        sellerId: String,
        placedOrderIds: Map<String, String>,
        isDemo: Boolean
    ): Flow<List<Order>> {
        return _ordersFlow.asStateFlow()
    }

    override suspend fun placeOrder(order: Order): Result<Order> {
        _placedOrders.add(order)
        val newOrder = order.copy(id = "order_${System.currentTimeMillis()}")
        val currentOrders = _ordersFlow.value.toMutableList()
        currentOrders.add(0, newOrder)
        _ordersFlow.value = currentOrders
        return Result.success(newOrder)
    }

    override suspend fun updateOrder(order: Order): Result<Unit> {
        _updatedOrders.add(order)
        val currentOrders = _ordersFlow.value.toMutableList()
        val index = currentOrders.indexOfFirst { it.id == order.id }
        if (index >= 0) {
            currentOrders[index] = order
            _ordersFlow.value = currentOrders
            return Result.success(Unit)
        }
        return Result.failure(Exception("Order not found"))
    }

    /** One call to [cancelOrder], so a test can say which trunk it was aimed at. */
    data class CancelCall(val date: String, val orderId: String, val isDemo: Boolean)

    val cancelCalls = mutableListOf<CancelCall>()

    override suspend fun cancelOrder(
        sellerId: String,
        date: String,
        orderId: String,
        isDemo: Boolean
    ): Result<Boolean> {
        cancelCalls.add(CancelCall(date, orderId, isDemo))
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
        orderId: String,
        isDemo: Boolean
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

    /**
     * Honours the trunk the path names, which is the whole point of the path.
     *
     * A record under `demo_orders/` is a demo order and a record under `orders/` is
     * not — the same rule `historyOrigin` applies to the real thing. Without this a
     * fake answers every path with every order, and a caller that asks the wrong trunk
     * looks correct here and fails on a device, which is exactly what happened to a
     * freshly approved buyer cancelling a leftover demo order.
     */
    override suspend fun loadOrder(
        sellerId: String,
        orderId: String,
        orderPath: String
    ): Result<Order> {
        val order = _ordersFlow.value.find { it.id == orderId }
        val trunkMatches = when {
            order == null -> false
            orderPath.startsWith("demo_orders/") -> order.origin.isDemo
            orderPath.startsWith("orders/") -> !order.origin.isDemo
            else -> true
        }
        return if (order != null && trunkMatches) {
            Result.success(order)
        } else {
            Result.failure(Exception("Order not found"))
        }
    }

    override suspend fun getOpenEditableOrder(
        sellerId: String,
        placedOrderIds: Map<String, String>,
        isDemo: Boolean
    ): Result<Order?> {
        val orders = _ordersFlow.value.filter { order ->
            placedOrderIds.values.contains(order.id)
        }
        return Result.success(orders.firstOrNull())
    }

    override suspend fun getUpcomingOrder(
        sellerId: String,
        placedOrderIds: Map<String, String>,
        isDemo: Boolean
    ): Result<Order?> {
        val orders = _ordersFlow.value.filter { order ->
            placedOrderIds.values.contains(order.id)
        }
        return Result.success(orders.firstOrNull())
    }

    override suspend fun deleteOldDemoOrders(sellerId: String): Result<Int> {
        return Result.success(0)
    }

    /** One `date to orderId` pair handed to [deleteDemoOrders]. */
    data class DeleteCall(val date: String, val orderId: String)

    /**
     * Every demo order the code under test asked to have deleted.
     *
     * A demo order is removed rather than marked `CANCELLED` (see
     * `Order.isRemovedNotCancelled`), so this — not [cancelCalls] — is where discarding
     * one shows up.
     */
    val deleteCalls = mutableListOf<DeleteCall>()

    /** Set to make the next [deleteDemoOrders] fail, for the "nothing was removed" path. */
    var deleteDemoOrdersFails = false

    override suspend fun deleteDemoOrders(sellerId: String, dateToOrderId: Map<String, String>): Result<Unit> {
        if (deleteDemoOrdersFails) return Result.failure(Exception("delete refused"))
        dateToOrderId.forEach { (date, orderId) -> deleteCalls.add(DeleteCall(date, orderId)) }
        // Actually gone, so a test can read the trunk back and find nothing — the fake
        // reporting success while keeping the record is how a delete that never happened
        // passes a test and fails on a device.
        _ordersFlow.value = _ordersFlow.value.filterNot { it.id in dateToOrderId.values }
        return Result.success(Unit)
    }

    private val _placedOrders = mutableListOf<Order>()
    /**
     * Every order handed to [placeOrder], as it was handed over.
     *
     * Kept separately from the orders flow so a test can assert on what was *written*
     * — the market, the pricing revision, the article prices — rather than on what the
     * fake chose to hand back.
     */
    val placedOrders: List<Order> get() = _placedOrders.toList()

    private val _updatedOrders = mutableListOf<Order>()
    /** Every order handed to [updateOrder], for the same reason as [placedOrders]. */
    val updatedOrders: List<Order> get() = _updatedOrders.toList()

    /** Records every transition call so tests can assert what was moved in one update. */
    val transitions = mutableListOf<Transition>()
    var shouldFailTransition: Boolean = false

    data class Transition(
        val sellerId: String,
        val remoteOrderIds: Map<String, String>,
        val localOrders: List<Order>,
        val allowedMarkets: List<String> = emptyList()
    )

    override suspend fun transitionDemoOrdersToProduction(
        sellerId: String,
        remoteOrderIds: Map<String, String>,
        localOrders: List<Order>,
        allowedMarkets: List<String>
    ): Result<DemoTransitionResult> {
        transitions.add(Transition(sellerId, remoteOrderIds, localOrders, allowedMarkets))
        if (shouldFailTransition) return Result.failure(Exception(failureMessage))

        // Empty means all, as it does in BuyerAccess and in the real implementation.
        fun mayCollectAt(marketId: String) =
            allowedMarkets.isEmpty() || marketId.isEmpty() || marketId in allowedMarkets

        val now = kotlin.time.Clock.System.now()
        val notEligible = mutableSetOf<String>()

        val moved = remoteOrderIds.filterValues { orderId ->
            val order = _ordersFlow.value.find { it.id == orderId }
            when {
                order == null -> true
                !mayCollectAt(order.marketId) -> {
                    notEligible += order.marketId
                    false
                }
                else -> order.canTransitionToProduction(now)
            }
        }.toMutableMap()

        localOrders.forEach { order ->
            if (!mayCollectAt(order.marketId)) {
                notEligible += order.marketId
                return@forEach
            }
            if (!order.canTransitionToProduction(now)) return@forEach
            moved[OrderDateUtils.formatDateKey(
                kotlin.time.Instant.fromEpochMilliseconds(order.pickUpDate)
            )] = "transitioned_${order.id}"
        }

        return Result.success(
            DemoTransitionResult(
                transitioned = moved,
                notEligibleMarketIds = notEligible.toList()
            )
        )
    }

    override suspend fun updateOrderStatus(
        sellerId: String,
        date: String,
        orderId: String,
        status: OrderStatus,
        isDemo: Boolean
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
