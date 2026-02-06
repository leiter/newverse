package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.isActive
import com.together.newverse.domain.model.isFinalized
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * ViewModel for Seller Orders Management screen
 * Based on universe project's OrdersViewModel
 */
class OrdersViewModel(
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrdersUiState>(OrdersUiState.Loading)
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    private val _selectedFilter = MutableStateFlow(OrderFilter.ALL)
    val selectedFilter: StateFlow<OrderFilter> = _selectedFilter.asStateFlow()

    init {
        loadOrders()
    }

    private fun loadOrders() {
        viewModelScope.launch {
            _uiState.value = OrdersUiState.Loading

            try {
                // Get current seller ID (authenticated user)
                val sellerId = authRepository.getCurrentUserId() ?: ""

                println("📦 OrdersViewModel.loadOrders: sellerId='$sellerId'")

                // Observe seller orders in real-time
                orderRepository.observeSellerOrders(sellerId)
                    .catch { error ->
                        println("❌ OrdersViewModel.loadOrders: Error - ${error.message}")
                        _uiState.value = OrdersUiState.Error(
                            error.message ?: "Failed to load orders"
                        )
                    }
                    .collect { orders ->
                        println("✅ OrdersViewModel.loadOrders: Loaded ${orders.size} orders")

                        // Apply filter
                        val filteredOrders = when (_selectedFilter.value) {
                            OrderFilter.ALL -> orders
                            OrderFilter.PENDING -> orders.filter { it.status.isActive() && !it.status.isFinalized() }
                            OrderFilter.COMPLETED -> orders.filter { it.status == OrderStatus.COMPLETED }
                            OrderFilter.CANCELLED -> orders.filter { it.status == OrderStatus.CANCELLED }
                        }

                        // Sort by pickup date descending (most recent/upcoming first)
                        val sortedOrders = filteredOrders.sortedByDescending { it.pickUpDate }

                        _uiState.value = OrdersUiState.Success(sortedOrders)
                    }
            } catch (e: Exception) {
                println("❌ OrdersViewModel.loadOrders: Error - ${e.message}")
                _uiState.value = OrdersUiState.Error(
                    e.message ?: "Failed to load orders"
                )
            }
        }
    }

    fun setFilter(filter: OrderFilter) {
        _selectedFilter.value = filter
        loadOrders()
    }

    fun markAsFavorite(orderId: String) {
        // TODO: Implement with repository
    }

    fun refresh() {
        loadOrders()
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

sealed interface OrdersUiState {
    data object Loading : OrdersUiState
    data class Success(val orders: List<Order>) : OrdersUiState
    data class Error(val message: String) : OrdersUiState
}

enum class OrderFilter {
    ALL, PENDING, COMPLETED, CANCELLED
}
