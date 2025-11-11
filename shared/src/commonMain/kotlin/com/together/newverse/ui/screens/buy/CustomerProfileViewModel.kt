package com.together.newverse.ui.screens.buy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Customer Profile screen
 */
class CustomerProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _profile = MutableStateFlow<BuyerProfile?>(null)
    val profile: StateFlow<BuyerProfile?> = _profile.asStateFlow()

    private val _orderHistory = MutableStateFlow<List<Order>>(emptyList())
    val orderHistory: StateFlow<List<Order>> = _orderHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _photoUrl = MutableStateFlow<String?>(null)
    val photoUrl: StateFlow<String?> = _photoUrl.asStateFlow()

    init {
        loadProfile()
        observeProfile()
    }

    private fun observeProfile() {
        viewModelScope.launch {
            profileRepository.observeBuyerProfile().collect { profile ->
                _profile.value = profile
                // Photo URL comes from the profile (synced from Firebase Auth)
                _photoUrl.value = profile?.photoUrl
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                println("👤 CustomerProfileViewModel.loadProfile: START")

                // Get buyer profile from repository
                val result = profileRepository.getBuyerProfile()
                result.onSuccess { profile ->
                    _profile.value = profile
                    _photoUrl.value = profile.photoUrl
                    println("✅ CustomerProfileViewModel.loadProfile: Success - ${profile.displayName}, photoUrl=${profile.photoUrl}")
                }.onFailure { error ->
                    println("❌ CustomerProfileViewModel.loadProfile: Error - ${error.message}")
                    // Set a default guest profile
                    _profile.value = BuyerProfile(
                        displayName = "Guest User",
                        anonymous = true
                    )
                }
            } catch (e: Exception) {
                println("❌ CustomerProfileViewModel.loadProfile: Exception - ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadOrderHistory() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                println("📋 CustomerProfileViewModel.loadOrderHistory: START")

                val profile = _profile.value
                if (profile != null && profile.placedOrderIds.isNotEmpty()) {
                    // Load orders using the placedOrderIds from profile
                    val result = orderRepository.getBuyerOrders("", profile.placedOrderIds)
                    result.onSuccess { orders ->
                        _orderHistory.value = orders
                        println("✅ CustomerProfileViewModel.loadOrderHistory: Loaded ${orders.size} orders")
                    }.onFailure { error ->
                        println("❌ CustomerProfileViewModel.loadOrderHistory: Error - ${error.message}")
                        _orderHistory.value = emptyList()
                    }
                } else {
                    println("⚠️ CustomerProfileViewModel.loadOrderHistory: No orders to load")
                    _orderHistory.value = emptyList()
                }
            } catch (e: Exception) {
                println("❌ CustomerProfileViewModel.loadOrderHistory: Exception - ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateProfile(profile: BuyerProfile) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                println("💾 CustomerProfileViewModel.updateProfile: START - ${profile.displayName}")

                val result = profileRepository.saveBuyerProfile(profile)
                result.onSuccess {
                    _profile.value = profile
                    _photoUrl.value = profile.photoUrl
                    println("✅ CustomerProfileViewModel.updateProfile: Success")
                }.onFailure { error ->
                    println("❌ CustomerProfileViewModel.updateProfile: Error - ${error.message}")
                }
            } catch (e: Exception) {
                println("❌ CustomerProfileViewModel.updateProfile: Exception - ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadProfile()
        loadOrderHistory()
    }
}
