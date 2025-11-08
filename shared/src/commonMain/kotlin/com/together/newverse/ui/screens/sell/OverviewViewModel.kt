package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Seller Overview/Dashboard screen
 */
class OverviewViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<OverviewUiState>(OverviewUiState.Loading)
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        loadOverview()
    }

    private fun loadOverview() {
        viewModelScope.launch {
            _uiState.value = OverviewUiState.Loading

            // TODO: Replace with actual repository calls when Firebase is integrated
            // For now, show empty state
            _uiState.value = OverviewUiState.Success(
                totalProducts = 0,
                activeOrders = 0,
                totalRevenue = 0.0,
                recentArticles = emptyList(),
                recentOrders = emptyList()
            )
        }
    }

    fun refresh() {
        loadOverview()
    }
}

sealed interface OverviewUiState {
    data object Loading : OverviewUiState
    data class Success(
        val totalProducts: Int,
        val activeOrders: Int,
        val totalRevenue: Double,
        val recentArticles: List<Article>,
        val recentOrders: List<Order>
    ) : OverviewUiState
    data class Error(val message: String) : OverviewUiState
}
