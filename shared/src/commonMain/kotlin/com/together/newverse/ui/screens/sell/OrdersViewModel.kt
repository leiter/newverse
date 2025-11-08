package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Seller Orders Management screen
 */
class OrdersViewModel : ViewModel() {

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

            // TODO: Replace with actual OrderRepository call when Firebase is integrated
            // For now, show empty state
            _uiState.value = OrdersUiState.Success(emptyList())
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
}

sealed interface OrdersUiState {
    data object Loading : OrdersUiState
    data class Success(val orders: List<Order>) : OrdersUiState
    data class Error(val message: String) : OrdersUiState
}

enum class OrderFilter {
    ALL, PENDING, COMPLETED, CANCELLED
}
