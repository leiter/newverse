package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.isActive
import com.together.newverse.domain.model.isFinalized
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.ui.state.core.asAsyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * ViewModel for Seller Orders Management screen.
 *
 * Uses AsyncState<List<Order>> for type-safe loading/success/error handling.
 * Orders are automatically filtered and sorted based on the selected filter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModel(
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(OrderFilter.ALL)
    val selectedFilter: StateFlow<OrderFilter> = _selectedFilter.asStateFlow()

    // Trigger for refreshing orders
    private val _refreshTrigger = MutableStateFlow(0)

    /**
     * Orders state using AsyncState for type-safe loading/success/error.
     * Automatically updates when filter changes or refresh is triggered.
     */
    val ordersState: StateFlow<AsyncState<List<Order>>> = combine(
        _selectedFilter,
        _refreshTrigger
    ) { filter, _ -> filter }
        .flatMapLatest { filter ->
            val sellerId = authRepository.getCurrentUserId() ?: ""
            println("📦 OrdersViewModel: Loading orders for sellerId='$sellerId', filter=$filter")

            if (sellerId.isEmpty()) {
                flowOf(AsyncState.Error("Not authenticated", retryable = true))
            } else {
                orderRepository.observeSellerOrders(sellerId)
                    .map { orders ->
                        // Apply filter
                        val filteredOrders = when (filter) {
                            OrderFilter.ALL -> orders
                            OrderFilter.PENDING -> orders.filter { it.status.isActive() && !it.status.isFinalized() }
                            OrderFilter.COMPLETED -> orders.filter { it.status == OrderStatus.COMPLETED }
                            OrderFilter.CANCELLED -> orders.filter { it.status == OrderStatus.CANCELLED }
                        }
                        // Sort by pickup date descending (most recent/upcoming first)
                        filteredOrders.sortedByDescending { it.pickUpDate }
                    }
                    .asAsyncState()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AsyncState.Loading
        )

    fun setFilter(filter: OrderFilter) {
        _selectedFilter.value = filter
    }

    fun refresh() {
        _refreshTrigger.value++
    }

    fun markAsFavorite(orderId: String) {
        // TODO: Implement with repository
    }

    /**
     * Hide an order from seller's view
     */
    fun hideOrder(
        order: Order,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                println("📦 OrdersViewModel.hideOrder: orderId='${order.id}'")

                // Format date key from pickUpDate
                val date = formatDateKey(order.pickUpDate)

                val result = orderRepository.hideOrderForSeller(
                    sellerId = order.sellerId,
                    date = date,
                    orderId = order.id
                )

                result.onSuccess {
                    println("✅ OrdersViewModel.hideOrder: Success")
                    onSuccess()
                }.onFailure { error ->
                    println("❌ OrdersViewModel.hideOrder: Error - ${error.message}")
                    onError(error.message ?: "Failed to hide order")
                }
            } catch (e: Exception) {
                println("❌ OrdersViewModel.hideOrder: Error - ${e.message}")
                onError(e.message ?: "Failed to hide order")
            }
        }
    }

    /**
     * Delete demo orders older than 1 month from demo_orders Firebase path
     */
    fun clearOldDemoOrders(onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            orderRepository.deleteOldDemoOrders(sellerId).fold(
                onSuccess = { count -> onSuccess(count) },
                onFailure = { e -> onError(e.message ?: "Failed to clear demo orders") }
            )
        }
    }

    /**
     * Format timestamp to date key (yyyyMMdd)
     */
    private fun formatDateKey(timestamp: Long): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        val year = localDateTime.year
        val month = localDateTime.month.number.toString().padStart(2, '0')
        val day = localDateTime.day.toString().padStart(2, '0')

        return "$year$month$day"
    }
}

enum class OrderFilter {
    ALL, PENDING, COMPLETED, CANCELLED
}
