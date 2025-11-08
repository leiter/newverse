package com.together.newverse.ui.screens.buy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Customer Profile screen
 */
class CustomerProfileViewModel : ViewModel() {

    private val _profile = MutableStateFlow<BuyerProfile?>(null)
    val profile: StateFlow<BuyerProfile?> = _profile.asStateFlow()

    private val _orderHistory = MutableStateFlow<List<Order>>(emptyList())
    val orderHistory: StateFlow<List<Order>> = _orderHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true

            // TODO: Replace with actual ProfileRepository call when Firebase is integrated
            // For now, show empty/default profile
            _profile.value = BuyerProfile(
                displayName = "Guest User",
                anonymous = true
            )

            _isLoading.value = false
        }
    }

    fun loadOrderHistory() {
        viewModelScope.launch {
            _isLoading.value = true

            // TODO: Replace with actual OrderRepository call when Firebase is integrated
            _orderHistory.value = emptyList()

            _isLoading.value = false
        }
    }

    fun updateProfile(profile: BuyerProfile) {
        viewModelScope.launch {
            _isLoading.value = true

            // TODO: Replace with actual ProfileRepository call when Firebase is integrated
            _profile.value = profile

            _isLoading.value = false
        }
    }

    fun refresh() {
        loadProfile()
        loadOrderHistory()
    }
}
