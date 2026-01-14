package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.ErrorState
import com.together.newverse.ui.state.ErrorType
import com.together.newverse.ui.state.UnifiedUiAction
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        println("👤 UnifiedAppViewModel.loadCustomerProfile: START")

        // Set loading state
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    customerProfile = current.screens.customerProfile.copy(
                        isLoading = true,
                        error = null
                    )
                )
            )
        }

        try {
            // Get buyer profile from repository
            val result = profileRepository.getBuyerProfile()
            result.onSuccess { profile ->
                println("✅ UnifiedAppViewModel.loadCustomerProfile: Success - ${profile.displayName}, photoUrl=${profile.photoUrl}")

                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            customerProfile = current.screens.customerProfile.copy(
                                isLoading = false,
                                profile = profile,
                                photoUrl = profile.photoUrl,
                                error = null
                            )
                        )
                    )
                }
            }.onFailure { error ->
                println("❌ UnifiedAppViewModel.loadCustomerProfile: Error - ${error.message}")

                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            customerProfile = current.screens.customerProfile.copy(
                                isLoading = false,
                                error = ErrorState(
                                    message = error.message ?: "Failed to load profile",
                                    type = ErrorType.GENERAL
                                )
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            println("❌ UnifiedAppViewModel.loadCustomerProfile: Exception - ${e.message}")
            e.printStackTrace()

            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            isLoading = false,
                            error = ErrorState(
                                message = e.message ?: "Failed to load profile",
                                type = ErrorType.GENERAL
                            )
                        )
                    )
                )
            }
        }
    }
}

internal fun BuyAppViewModel.loadOrderHistory() {
    viewModelScope.launch {
        println("📋 UnifiedAppViewModel.loadOrderHistory: START (reactive)")

        // Set loading state
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    orderHistory = current.screens.orderHistory.copy(
                        isLoading = true,
                        error = null
                    )
                )
            )
        }

        try {
            // Get profile from state, or fetch it if not available
            var profile = _state.value.screens.customerProfile.profile
            if (profile == null) {
                println("📋 UnifiedAppViewModel.loadOrderHistory: Profile not in state, fetching from repository")
                val profileResult = profileRepository.getBuyerProfile()
                profile = profileResult.getOrNull()
            }

            if (profile != null && profile.placedOrderIds.isNotEmpty()) {
                // Observe orders reactively using the placedOrderIds from profile
                orderRepository.observeBuyerOrders("", profile.placedOrderIds)
                    .catch { e ->
                        println("❌ UnifiedAppViewModel.loadOrderHistory: Error - ${e.message}")
                        _state.update { current ->
                            current.copy(
                                screens = current.screens.copy(
                                    orderHistory = current.screens.orderHistory.copy(
                                        isLoading = false,
                                        error = ErrorState(
                                            message = e.message ?: "Failed to load order history",
                                            type = ErrorType.GENERAL
                                        )
                                    )
                                )
                            )
                        }
                    }
                    .collect { orders ->
                        println("✅ UnifiedAppViewModel.loadOrderHistory: Received ${orders.size} orders (reactive update)")

                        _state.update { current ->
                            current.copy(
                                screens = current.screens.copy(
                                    orderHistory = current.screens.orderHistory.copy(
                                        isLoading = false,
                                        items = orders,
                                        error = null
                                    )
                                )
                            )
                        }
                    }
            } else {
                println("⚠️ UnifiedAppViewModel.loadOrderHistory: No orders to load")

                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            orderHistory = current.screens.orderHistory.copy(
                                isLoading = false,
                                items = emptyList(),
                                error = null
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            println("❌ UnifiedAppViewModel.loadOrderHistory: Exception - ${e.message}")
            e.printStackTrace()

            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        orderHistory = current.screens.orderHistory.copy(
                            isLoading = false,
                            error = ErrorState(
                                message = e.message ?: "Failed to load order history",
                                type = ErrorType.GENERAL
                            )
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
        println("💾 BuyAppViewModel.saveBuyerProfile: START - displayName=$displayName, email=$email, phone=$phone")

        try {
            val currentProfile = _state.value.screens.customerProfile.profile
            if (currentProfile == null) {
                println("❌ BuyAppViewModel.saveBuyerProfile: No current profile to update")
                dispatch(UnifiedUiAction.ShowSnackbar("Fehler: Kein Profil vorhanden"))
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
                println("✅ BuyAppViewModel.saveBuyerProfile: Success")

                // Update state with saved profile
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            customerProfile = current.screens.customerProfile.copy(
                                profile = savedProfile
                            )
                        )
                    )
                }

                dispatch(UnifiedUiAction.ShowSnackbar("Profil gespeichert"))
            }.onFailure { error ->
                println("❌ BuyAppViewModel.saveBuyerProfile: Error - ${error.message}")
                dispatch(UnifiedUiAction.ShowSnackbar("Fehler beim Speichern: ${error.message}"))
            }

        } catch (e: Exception) {
            println("❌ BuyAppViewModel.saveBuyerProfile: Exception - ${e.message}")
            e.printStackTrace()
            dispatch(UnifiedUiAction.ShowSnackbar("Fehler beim Speichern"))
        }
    }
}

internal fun BuyAppViewModel.observeMainScreenBuyerProfile() {
    viewModelScope.launch {
        var previousPlacedOrderIds: Map<String, String>? = null

        profileRepository.observeBuyerProfile().collect { profile ->
            val newFavourites = profile?.favouriteArticles ?: emptyList()
            val currentFavourites = _state.value.screens.mainScreen.favouriteArticles

            println("⭐ observeMainScreenBuyerProfile: profile=${profile != null}, newFavourites=${newFavourites.size}, currentFavourites=${currentFavourites.size}")

            // Don't clear favourites if profile comes back with empty favourites but we had some before
            // This prevents transient Firebase updates from clearing favourites
            val favouritesToUse = if (newFavourites.isEmpty() && currentFavourites.isNotEmpty()) {
                println("⭐ observeMainScreenBuyerProfile: Keeping existing favourites (new was empty)")
                currentFavourites
            } else {
                newFavourites
            }

            // Update favourite articles
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        mainScreen = current.screens.mainScreen.copy(
                            favouriteArticles = favouritesToUse
                        ),
                        // Also update customer profile so loadOrderHistory has access to it
                        customerProfile = current.screens.customerProfile.copy(
                            profile = profile
                        )
                    )
                )
            }

            // Check if placedOrderIds changed - if so, reload order history
            val currentPlacedOrderIds = profile?.placedOrderIds
            if (previousPlacedOrderIds != null && currentPlacedOrderIds != previousPlacedOrderIds) {
                println("📋 observeMainScreenBuyerProfile: placedOrderIds changed, reloading order history")
                loadOrderHistory()
            }
            previousPlacedOrderIds = currentPlacedOrderIds
        }
    }
}
