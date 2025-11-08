package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.SellerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Seller Profile screen
 */
class SellerProfileViewModel : ViewModel() {

    private val _profile = MutableStateFlow<SellerProfile?>(null)
    val profile: StateFlow<SellerProfile?> = _profile.asStateFlow()

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
            _profile.value = SellerProfile(
                displayName = "My Business",
                markets = emptyList()
            )

            _isLoading.value = false
        }
    }

    fun updateProfile(profile: SellerProfile) {
        viewModelScope.launch {
            _isLoading.value = true

            // TODO: Replace with actual ProfileRepository call when Firebase is integrated
            _profile.value = profile

            _isLoading.value = false
        }
    }

    fun addMarket(market: Market) {
        val currentProfile = _profile.value ?: return
        val updatedMarkets = currentProfile.markets + market
        updateProfile(currentProfile.copy(markets = updatedMarkets))
    }

    fun removeMarket(marketId: String) {
        val currentProfile = _profile.value ?: return
        val updatedMarkets = currentProfile.markets.filter { it.id != marketId }
        updateProfile(currentProfile.copy(markets = updatedMarkets))
    }

    fun refresh() {
        loadProfile()
    }
}
