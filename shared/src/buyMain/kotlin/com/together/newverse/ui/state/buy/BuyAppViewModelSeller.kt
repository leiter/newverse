package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.ui.state.BasketScreenState
import com.together.newverse.ui.state.BasketState
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BuySellerAction
import com.together.newverse.ui.state.MainScreenState
import com.together.newverse.ui.state.OrderHistoryScreenState
import com.together.newverse.ui.state.ProductsScreenState
import com.together.newverse.ui.state.SnackbarType
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Seller connection extension functions for BuyAppViewModel.
 *
 * Handles connecting to a new seller (via QR code or manual input),
 * resetting to demo mode, and reloading all seller-dependent data.
 */

internal fun BuyAppViewModel.handleSellerAction(action: BuySellerAction) {
    when (action) {
        is BuySellerAction.ConnectToSeller -> connectToSeller(action.sellerId)
        is BuySellerAction.ResetToDemo -> resetToDemo()
    }
}

internal fun BuyAppViewModel.connectToSeller(sellerId: String) {
    if (sellerId.isBlank()) {
        viewModelScope.launch {
            showSnackbar("Seller ID cannot be empty", SnackbarType.ERROR)
        }
        return
    }

    // If same seller, no-op
    if (sellerId == sellerConfig.sellerId) {
        viewModelScope.launch {
            showSnackbar("Already connected to this seller", SnackbarType.INFO)
        }
        return
    }

    viewModelScope.launch {
        try {
            // Check if buyer is blocked by this seller
            val buyerId = authRepository.getCurrentUserId()
            if (buyerId != null) {
                val isBlocked = profileRepository.isClientBlocked(sellerId, buyerId)
                if (isBlocked) {
                    showSnackbar("You have been blocked by this seller", SnackbarType.ERROR)
                    return@launch
                }

                // Register as known client
                profileRepository.addKnownClient(sellerId, buyerId)
            }

            // Persist new seller ID
            sellerConfig.setSellerId(sellerId)

            // Clear basket and reset screen states
            _state.update { current ->
                current.copy(
                    connectedSellerId = sellerConfig.sellerId,
                    isDemoMode = sellerConfig.isDemoMode,
                    basket = BasketState(),
                    products = ProductsScreenState(),
                    mainScreen = MainScreenState(),
                    basketScreen = BasketScreenState(),
                    orderHistory = OrderHistoryScreenState()
                )
            }

            // Reload all seller-dependent data
            loadProducts()
            loadMainScreenArticles()

            val message = if (sellerConfig.isDemoMode) {
                "Switched to demo mode"
            } else {
                "Connected to seller"
            }
            showSnackbar(message, SnackbarType.SUCCESS)

        } catch (e: Exception) {
            println("❌ BuyAppViewModel.connectToSeller: Error - ${e.message}")
            showSnackbar("Failed to connect: ${e.message}", SnackbarType.ERROR)
        }
    }
}

private fun BuyAppViewModel.resetToDemo() {
    connectToSeller(sellerConfig.demoSellerId)
}
