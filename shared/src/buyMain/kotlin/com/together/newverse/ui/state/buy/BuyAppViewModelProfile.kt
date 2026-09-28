package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.ErrorState
import com.together.newverse.ui.state.ErrorType
import com.together.newverse.ui.state.BuyUiAction
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.together.newverse.util.Log


private const val TAG = "BuyVMProfile"

/**
 * Profile management extension functions for BuyAppViewModel
 *
 * Handles buyer profile loading, saving, and order history.
 *
 * Extracted functions:
 * - loadProfile
 * - loadCustomerProfile
 * - loadOrderHistory
 * - refreshCustomerProfile
 * - saveBuyerProfile
 * - observeMainScreenBuyerProfile
 */

internal fun BuyAppViewModel.loadProfile() {
    // Redirect to loadCustomerProfile for now
    loadCustomerProfile()
}

internal fun BuyAppViewModel.loadCustomerProfile() {
    viewModelScope.launch {
        Log.d(TAG) { "loadCustomerProfile: START" }

        // Set loading state
        _state.update { current ->
            current.copy(
                customerProfile = current.customerProfile.copy(
                    isLoading = true,
                    error = null
                )
            )
        }

        try {
            // Get buyer profile from repository
            val result = profileRepository.getBuyerProfile()
            result.onSuccess { profile ->
                Log.d(TAG) { "loadCustomerProfile: Success - ${profile.displayName}, photoUrl=${profile.photoUrl}" }

                _state.update { current ->
                    current.copy(
                        customerProfile = current.customerProfile.copy(
                            isLoading = false,
                            profile = profile,
                            photoUrl = profile.photoUrl,
                            error = null
                        )
                    )
                }
            }.onFailure { error ->
                Log.e(TAG) { "loadCustomerProfile: Error - ${error.message}" }

                _state.update { current ->
                    current.copy(
                        customerProfile = current.customerProfile.copy(
                            isLoading = false,
                            error = ErrorState(
                                message = error.message ?: "Failed to load profile",
                                type = ErrorType.GENERAL
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG) { "loadCustomerProfile: Exception - ${e.message}" }
            e.printStackTrace()

            _state.update { current ->
                current.copy(
                    customerProfile = current.customerProfile.copy(
                        isLoading = false,
                        error = ErrorState(
                            message = e.message ?: "Failed to load profile",
                            type = ErrorType.GENERAL
                        )
                    )
                )
            }
        }
    }
}

internal fun BuyAppViewModel.loadOrderHistory() {
    viewModelScope.launch {
        Log.d(TAG) { "loadOrderHistory: START (reactive)" }

        // Set loading state
        _state.update { current ->
            current.copy(
                orderHistory = current.orderHistory.copy(
                    isLoading = true,
                    error = null
                )
            )
        }

        try {
            // In demo mode, load orders from both local storage AND Firebase demo_orders
            if (_state.value.isDemoMode) {
                // 1. Get local orders
                val localOrders = sellerConfig.loadDemoOrders().map { order ->
                    order.transitionStatusIfNeeded() ?: order
                }

                // 2. Get Firebase demo orders
                val profileResult = profileRepository.getBuyerProfile()
                val profile = profileResult.getOrNull()

                if (profile != null && profile.placedOrderIds.isNotEmpty()) {
                    // Observe combined orders reactively
                    orderRepository.observeBuyerOrders(sellerConfig.sellerId, profile.placedOrderIds, isDemo = true)
                        .collect { firebaseOrders ->
                            val combinedOrders = (localOrders + firebaseOrders)
                                .distinctBy { it.id }
                                .sortedByDescending { it.createdDate }

                            _state.update { current ->
                                current.copy(
                                    orderHistory = current.orderHistory.copy(
                                        isLoading = false,
                                        items = combinedOrders,
                                        error = null
                                    )
                                )
                            }
                        }
                } else {
                    Log.d(TAG) { "loadOrderHistory: Loaded ${localOrders.size} local demo orders" }
                    _state.update { current ->
                        current.copy(
                            orderHistory = current.orderHistory.copy(
                                isLoading = false,
                                items = localOrders.sortedByDescending { it.createdDate },
                                error = null
                            )
                        )
                    }
                }
                return@launch
            }

            // Get profile from state, or fetch it if not available
            var profile = _state.value.customerProfile.profile
            if (profile == null) {
                Log.d(TAG) { "loadOrderHistory: Profile not in state, fetching from repository" }
                val profileResult = profileRepository.getBuyerProfile()
                profile = profileResult.getOrNull()
            }

            if (profile != null && profile.placedOrderIds.isNotEmpty()) {
                // Observe orders reactively using the placedOrderIds from profile
                orderRepository.observeBuyerOrders(
                    sellerId = sellerConfig.sellerId,
                    placedOrderIds = profile.placedOrderIds,
                    isDemo = _state.value.isDemoMode
                )
                    .catch { e ->
                        Log.e(TAG) { "loadOrderHistory: Error - ${e.message}" }
                        _state.update { current ->
                            current.copy(
                                orderHistory = current.orderHistory.copy(
                                    isLoading = false,
                                    error = ErrorState(
                                        message = e.message ?: "Failed to load order history",
                                        type = ErrorType.GENERAL
                                    )
                                )
                            )
                        }
                    }
                    .collect { orders ->
                        Log.d(TAG) { "loadOrderHistory: Received ${orders.size} orders (reactive update)" }

                        _state.update { current ->
                            current.copy(
                                orderHistory = current.orderHistory.copy(
                                    isLoading = false,
                                    items = orders,
                                    error = null
                                )
                            )
                        }
                    }
            } else {
                Log.w(TAG) { "loadOrderHistory: No orders to load" }

                _state.update { current ->
                    current.copy(
                        orderHistory = current.orderHistory.copy(
                            isLoading = false,
                            items = emptyList(),
                            error = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG) { "loadOrderHistory: Exception - ${e.message}" }
            e.printStackTrace()

            _state.update { current ->
                current.copy(
                    orderHistory = current.orderHistory.copy(
                        isLoading = false,
                        error = ErrorState(
                            message = e.message ?: "Failed to load order history",
                            type = ErrorType.GENERAL
                        )
                    )
                )
            }
        }
    }
}

internal fun BuyAppViewModel.refreshCustomerProfile() {
    loadCustomerProfile()
    loadOrderHistory()
}

internal fun BuyAppViewModel.saveBuyerProfile(displayName: String, email: String, phone: String) {
    viewModelScope.launch {
        Log.d(TAG) { "saveBuyerProfile: START - displayName=$displayName, email=$email, phone=$phone" }

        try {
            val currentProfile = _state.value.customerProfile.profile
            if (currentProfile == null) {
                Log.e(TAG) { "saveBuyerProfile: No current profile to update" }
                dispatch(BuyUiAction.ShowSnackbar("Fehler: Kein Profil vorhanden"))
                return@launch
            }

            // Create updated profile
            val updatedProfile = currentProfile.copy(
                displayName = displayName,
                emailAddress = email,
                telephoneNumber = phone
            )

            // Save to repository
            val result = profileRepository.saveBuyerProfile(updatedProfile)

            result.onSuccess { savedProfile ->
                Log.d(TAG) { "saveBuyerProfile: Success" }

                // Update state with saved profile
                _state.update { current ->
                    current.copy(
                        customerProfile = current.customerProfile.copy(
                            profile = savedProfile
                        )
                    )
                }

                dispatch(BuyUiAction.ShowSnackbar("Profil gespeichert"))
            }.onFailure { error ->
                Log.e(TAG) { "saveBuyerProfile: Error - ${error.message}" }
                dispatch(BuyUiAction.ShowSnackbar("Fehler beim Speichern: ${error.message}"))
            }

        } catch (e: Exception) {
            Log.e(TAG) { "saveBuyerProfile: Exception - ${e.message}" }
            e.printStackTrace()
            dispatch(BuyUiAction.ShowSnackbar("Fehler beim Speichern"))
        }
    }
}

internal fun BuyAppViewModel.observeMainScreenBuyerProfile() {
    viewModelScope.launch {
        var previousPlacedOrderIds: Map<String, String>? = null

        profileRepository.observeBuyerProfile().collect { profile ->
            val newFavourites = profile?.favouriteArticles ?: emptyList()
            val currentFavourites = _state.value.mainScreen.favouriteArticles

            Log.d(TAG) { "observeMainScreenBuyerProfile: profile=${profile != null}, newFavourites=${newFavourites.size}, currentFavourites=${currentFavourites.size}" }

            // A transient Firebase update can arrive with empty favourites, so an
            // empty list on its own is not taken as "the user cleared them".
            // A null profile is different: there is no user, and holding on to
            // the favourites there is what showed a signed-out user the previous
            // one's marked products.
            val favouritesToUse = when {
                profile == null -> {
                    Log.d(TAG) { "observeMainScreenBuyerProfile: No profile - clearing favourites" }
                    emptyList()
                }
                newFavourites.isEmpty() && currentFavourites.isNotEmpty() -> {
                    Log.d(TAG) { "observeMainScreenBuyerProfile: Keeping existing favourites (new was empty)" }
                    currentFavourites
                }
                else -> newFavourites
            }

            // Update favourite articles
            _state.update { current ->
                current.copy(
                    mainScreen = current.mainScreen.copy(
                        favouriteArticles = favouritesToUse
                    ),
                    // Also update customer profile so loadOrderHistory has access to it
                    customerProfile = current.customerProfile.copy(
                        profile = profile
                    )
                )
            }

            // Check if placedOrderIds changed - if so, reload order history
            val currentPlacedOrderIds = profile?.placedOrderIds
            if (previousPlacedOrderIds != null && currentPlacedOrderIds != previousPlacedOrderIds) {
                Log.d(TAG) { "observeMainScreenBuyerProfile: placedOrderIds changed, reloading order history" }
                loadOrderHistory()
            }
            previousPlacedOrderIds = currentPlacedOrderIds
        }
    }
}
