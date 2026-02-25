package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.repository.AuthUserInfo
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.ErrorState
import com.together.newverse.ui.state.ErrorType
import com.together.newverse.ui.state.InitializationStep
import com.together.newverse.ui.state.UserRole
import com.together.newverse.ui.state.UserState
import com.together.newverse.ui.state.core.AuthState
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Initialization extension functions for BuyAppViewModel
 *
 * Handles app startup, authentication flow, and initial data loading.
 * Uses AuthFlowCoordinator for centralized auth state management.
 *
 * Extracted functions:
 * - initializeApp - Main initialization orchestration
 * - observeAuthStateChanges - Reactive auth state monitoring via AuthFlowCoordinator
 * - loadOpenOrderAfterAuth - Load cart after login
 * - loadUserProfile - Load user profile during init
 * - loadCurrentOrder - Load most recent order during init
 * - formatDateKey - Helper to format timestamps for Firebase paths
 */

/**
 * Converts AuthState to UserState for backward compatibility with existing UI.
 */
internal fun AuthState.toUserState(): UserState = when (this) {
    is AuthState.Initializing -> UserState.Loading
    is AuthState.NotAuthenticated -> UserState.NotAuthenticated
    is AuthState.Authenticated -> UserState.LoggedIn(
        id = userId,
        name = displayName ?: "",
        email = email ?: "",
        role = UserRole.CUSTOMER,
        profileImageUrl = photoUrl
    )
}

/**
 * Main initialization flow - executes sequentially based on auth state.
 * AuthFlowCoordinator handles auth checking automatically.
 *
 * Flow:
 * 1. Set initializing state
 * 2. AuthFlowCoordinator handles auth check
 * 3. observeAuthStateChanges handles loading user data when authenticated
 */
internal fun BuyAppViewModel.initializeApp() {
    println("[NV_BuyAppVM] initializeApp: START")
    viewModelScope.launch {
        try {
            // Only set initializing state if auth hasn't already resolved
            // This prevents overwriting the state if observeAuthStateChanges already updated it
            val currentAuthState = authFlowCoordinator.authState.value
            println("[NV_BuyAppVM] initializeApp: Current auth state = $currentAuthState")

            if (currentAuthState is AuthState.Initializing) {
                println("[NV_BuyAppVM] initializeApp: Auth still initializing, setting loading state")
                _state.update { it.copy(
                    meta = it.meta.copy(
                        isInitializing = true,
                        initializationStep = InitializationStep.CheckingAuth
                    )
                )}
            } else {
                println("[NV_BuyAppVM] initializeApp: Auth already resolved, skipping loading state")
            }

            // AuthFlowCoordinator handles auth checking automatically
            // observeAuthStateChanges will trigger loading when authenticated
            println("[NV_BuyAppVM] initializeApp: Waiting for auth state from coordinator...")

        } catch (e: Exception) {
            println("[NV_BuyAppVM] initializeApp: ERROR - ${e.message}")
            e.printStackTrace()
            _state.update { it.copy(
                meta = it.meta.copy(
                    isInitializing = false,
                    isInitialized = false,
                    initializationStep = InitializationStep.Failed(
                        step = "initialization",
                        message = e.message ?: "Unknown error"
                    )
                )
            )}
        }
    }
    println("[NV_BuyAppVM] initializeApp: END (coroutine launched)")
}

/**
 * Observes AuthFlowCoordinator's auth state and syncs to BuyAppState.
 * This bridges the new AuthState to the existing UserState for backward compatibility.
 * Also handles loading user data when authentication changes.
 */
internal fun BuyAppViewModel.observeAuthStateChanges() {
    println("[NV_BuyAppVM] observeAuthStateChanges: Setting up auth state collection")
    viewModelScope.launch {
        var previousAuthState: AuthState? = null
        println("[NV_BuyAppVM] observeAuthStateChanges: Coroutine started, collecting authState...")

        authFlowCoordinator.authState.collect { authState ->
            println("[NV_BuyAppVM] observeAuthStateChanges: Collected authState=$authState (previous=$previousAuthState)")
            val previousUserState = _state.value.user

            // Update state with new auth info
            _state.update { current ->
                current.copy(
                    user = authState.toUserState(),
                    requiresLogin = false, // Buy flavor: guest access allowed
                    meta = when (authState) {
                        is AuthState.Initializing -> {
                            println("[NV_BuyAppVM] observeAuthStateChanges: Setting meta to Initializing/CheckingAuth")
                            current.meta.copy(
                                isInitializing = true,
                                initializationStep = InitializationStep.CheckingAuth
                            )
                        }
                        is AuthState.NotAuthenticated -> {
                            println("[NV_BuyAppVM] observeAuthStateChanges: Setting meta to NotAuthenticated/NotStarted")
                            current.meta.copy(
                                isInitializing = false,
                                isInitialized = false,
                                initializationStep = InitializationStep.NotStarted
                            )
                        }
                        is AuthState.Authenticated -> {
                            println("[NV_BuyAppVM] observeAuthStateChanges: Keeping current meta (Authenticated)")
                            current.meta // Keep current, will be updated below
                        }
                    }
                )
            }

            // Handle auth state transitions
            when {
                // Finished initializing (from checking to authenticated) - persisted session
                // Must be checked before the general "just became authenticated" case
                authState is AuthState.Authenticated && (previousAuthState is AuthState.Initializing || previousAuthState == null) -> {
                    println("[NV_BuyAppVM] observeAuthStateChanges: Auth initialized with existing session")
                    val userInfo = AuthUserInfo(
                        id = authState.userId,
                        email = authState.email,
                        displayName = authState.displayName,
                        photoUrl = authState.photoUrl,
                        isAnonymous = authState.isAnonymous
                    )
                    resumeInitializationAfterAuth(userInfo)
                }

                // Just became authenticated (from NotAuthenticated - login/register)
                authState is AuthState.Authenticated && previousAuthState is AuthState.NotAuthenticated -> {
                    println("[NV_BuyAppVM] observeAuthStateChanges: First-time auth, running full initialization...")
                    val userInfo = AuthUserInfo(
                        id = authState.userId,
                        email = authState.email,
                        displayName = authState.displayName,
                        photoUrl = authState.photoUrl,
                        isAnonymous = authState.isAnonymous
                    )
                    resumeInitializationAfterAuth(userInfo)
                }

                // Just became not authenticated (logged out)
                authState is AuthState.NotAuthenticated && previousAuthState is AuthState.Authenticated -> {
                    println("[NV_BuyAppVM] observeAuthStateChanges: Logged out (Authenticated -> NotAuthenticated)")
                }
            }

            previousAuthState = authState
        }
    }
}

/**
 * Load the most recent open/editable order after successful authentication
 * This populates the cart badge with the order item count
 */
internal fun BuyAppViewModel.loadOpenOrderAfterAuth() {
    viewModelScope.launch {
        try {
            println("🛒 BuyAppViewModel.loadOpenOrderAfterAuth: START")

            // Get buyer profile to get placed order IDs
            val profileResult = profileRepository.getBuyerProfile()
            profileResult.onSuccess { buyerProfile ->
                val placedOrderIds = buyerProfile.placedOrderIds

                if (placedOrderIds.isEmpty()) {
                    println("🛒 BuyAppViewModel.loadOpenOrderAfterAuth: No placed orders found")
                    return@launch
                }

                println("🛒 BuyAppViewModel.loadOpenOrderAfterAuth: Found ${placedOrderIds.size} placed orders")

                // Get the most recent editable order
                val sellerId = sellerConfig.sellerId
                val orderResult = orderRepository.getOpenEditableOrder(sellerId, placedOrderIds)

                orderResult.onSuccess { order ->
                    if (order != null) {
                        println("✅ BuyAppViewModel.loadOpenOrderAfterAuth: Loaded editable order - orderId=${order.id}, ${order.articles.size} items")

                        // Calculate date key
                        val dateKey = formatDateKey(order.pickUpDate)

                        // Load order items into BasketRepository with order metadata
                        basketRepository.loadOrderItems(order.articles, order.id, dateKey)

                        // Update state to store order info for later retrieval
                        _state.update { current ->
                            current.copy(
                                basket = current.basket.copy(
                                    currentOrderId = order.id,
                                    currentOrderDate = dateKey
                                )
                            )
                        }

                        val itemCount = order.articles.size
                        println("✅ BuyAppViewModel.loadOpenOrderAfterAuth: Cart badge updated with $itemCount items")
                    } else {
                        println("🛒 BuyAppViewModel.loadOpenOrderAfterAuth: No editable orders found")
                    }
                }.onFailure { error ->
                    println("❌ BuyAppViewModel.loadOpenOrderAfterAuth: Failed to load order - ${error.message}")
                }
            }.onFailure { error ->
                println("❌ BuyAppViewModel.loadOpenOrderAfterAuth: Failed to load buyer profile - ${error.message}")
            }
        } catch (e: Exception) {
            println("❌ BuyAppViewModel.loadOpenOrderAfterAuth: Exception - ${e.message}")
        }
    }
}

/**
 * Load user profile during initialization.
 * Loads the profile data for the currently logged-in user.
 *
 * If authUserInfo is provided (e.g., from Google sign-in), updates the profile
 * with the auth provider's email and display name when the profile has empty fields.
 *
 * @param authUserInfo Optional user info from auth provider to populate profile
 */
internal suspend fun BuyAppViewModel.loadUserProfile(authUserInfo: AuthUserInfo? = null) {
    try {
        val userId = (_state.value.user as? UserState.LoggedIn)?.id ?: return

        println("👤 Loading user profile for userId: $userId")

        val result = profileRepository.getBuyerProfile()

        result.onSuccess { profile ->
            println("✅ Profile loaded successfully: ${profile.displayName}")

            // Check if we need to update the profile with auth provider info
            // This handles Google sign-in where profile is created with empty/default fields
            var updatedProfile = profile
            if (authUserInfo != null && !authUserInfo.isAnonymous) {
                // Check if displayName needs update (empty, default English, or default German)
                val isDefaultName = profile.displayName.isBlank() ||
                    profile.displayName == "New User" ||
                    profile.displayName == "Neuer Kunde"
                val hasAuthDisplayName = !authUserInfo.displayName.isNullOrBlank()
                val needsDisplayNameUpdate = isDefaultName && hasAuthDisplayName

                // Check if other fields need update
                val needsEmailUpdate = profile.emailAddress.isBlank() && !authUserInfo.email.isNullOrBlank()
                val needsPhotoUpdate = profile.photoUrl.isBlank() && !authUserInfo.photoUrl.isNullOrBlank()

                val needsUpdate = needsDisplayNameUpdate || needsEmailUpdate || needsPhotoUpdate

                if (needsUpdate) {
                    println("📝 Updating profile with auth provider info...")
                    println("   - Current displayName: '${profile.displayName}', Auth displayName: '${authUserInfo.displayName}'")
                    println("   - needsDisplayNameUpdate: $needsDisplayNameUpdate, needsEmailUpdate: $needsEmailUpdate")

                    updatedProfile = profile.copy(
                        displayName = if (needsDisplayNameUpdate) {
                            authUserInfo.displayName!!
                        } else {
                            profile.displayName
                        },
                        emailAddress = if (needsEmailUpdate) {
                            authUserInfo.email!!
                        } else {
                            profile.emailAddress
                        },
                        photoUrl = if (needsPhotoUpdate) {
                            authUserInfo.photoUrl!!
                        } else {
                            profile.photoUrl
                        },
                        anonymous = false
                    )

                    // Save the updated profile to Firebase
                    profileRepository.saveBuyerProfile(updatedProfile)
                    println("✅ Profile updated with auth provider info: ${updatedProfile.displayName}, ${updatedProfile.emailAddress}")
                }
            }

            _state.update { current ->
                current.copy(
                    customerProfile = current.customerProfile.copy(
                        profile = updatedProfile,
                        isLoading = false,
                        error = null
                    )
                )
            }
        }.onFailure { error ->
            println("❌ Failed to load profile: ${error.message}")
            _state.update { current ->
                current.copy(
                    customerProfile = current.customerProfile.copy(
                        isLoading = false,
                        error = ErrorState(
                            message = "Failed to load profile: ${error.message}",
                            type = ErrorType.NETWORK
                        )
                    )
                )
            }
        }
    } catch (e: Exception) {
        println("❌ Exception loading profile: ${e.message}")
    }
}

/**
 * Load current editable order during initialization
 * Priority:
 * 1. If profile has a draft basket with items, load that (user's unsaved work)
 * 2. Otherwise, load the most recent upcoming order
 *
 * Also applies client-side status transitions (PLACED->LOCKED, LOCKED->COMPLETED)
 * and clears expired draft pickup dates.
 */
internal suspend fun BuyAppViewModel.loadCurrentOrder() {
    try {
        println("📦 loadCurrentOrder: Loading current order...")

        // Get profile to access placedOrderIds and draftBasket
        val profileResult = profileRepository.getBuyerProfile()

        profileResult.onSuccess { profile ->
            // Check for draft basket first (user's unsaved work takes priority)
            val draftBasket = profile.draftBasket
            if (draftBasket != null && draftBasket.items.isNotEmpty()) {
                println("🛒 loadCurrentOrder: Found draft basket with ${draftBasket.items.size} items")

                // Check if the draft's selected pickup date has expired
                val selectedDateKey = draftBasket.selectedPickupDate
                if (selectedDateKey != null) {
                    val pickupTimestamp = parsePickupDateFromKey(selectedDateKey)
                    if (pickupTimestamp != null) {
                        val pickupInstant = Instant.fromEpochMilliseconds(pickupTimestamp)
                        if (!OrderDateUtils.canEditOrder(pickupInstant)) {
                            // Deadline passed - clear the pickup date from draft
                            println("⚠️ loadCurrentOrder: Draft pickup date expired, clearing...")
                            val clearedDraft = draftBasket.copy(selectedPickupDate = null)
                            profileRepository.saveDraftBasket(clearedDraft)
                            basketRepository.loadFromProfile(clearedDraft)
                            println("✅ loadCurrentOrder: Loaded draft basket with cleared pickup date")
                            return@onSuccess
                        }
                    }
                }

                basketRepository.loadFromProfile(draftBasket)
                println("✅ loadCurrentOrder: Loaded draft basket")
                return@onSuccess
            }

            // No draft basket - check for placed orders
            val placedOrderIds = profile.placedOrderIds

            if (placedOrderIds.isEmpty()) {
                println("ℹ️ loadCurrentOrder: No placed orders found in profile")
                return@onSuccess
            }

            println("📦 loadCurrentOrder: Found ${placedOrderIds.size} placed orders, looking for upcoming order...")

            // Get the most recent upcoming order (not just editable)
            val sellerId = sellerConfig.sellerId
            val orderResult = orderRepository.getUpcomingOrder(sellerId, placedOrderIds)

            orderResult.onSuccess { loadedOrder ->
                if (loadedOrder != null) {
                    println("✅ loadCurrentOrder: Found upcoming order - orderId=${loadedOrder.id}, ${loadedOrder.articles.size} items")

                    // Check if pickup date has already passed
                    val now = Clock.System.now()
                    val pickupInstant = Instant.fromEpochMilliseconds(loadedOrder.pickUpDate)
                    if (now > pickupInstant) {
                        println("⏰ loadCurrentOrder: Order pickup date has passed, skipping...")
                        // Transition to COMPLETED and update Firebase
                        val dateKey = formatDateKey(loadedOrder.pickUpDate)
                        orderRepository.updateOrderStatus(sellerId, dateKey, loadedOrder.id, com.together.newverse.domain.model.OrderStatus.COMPLETED)
                        return@onSuccess
                    }

                    // Apply status transition if needed (PLACED->LOCKED)
                    val order = loadedOrder.transitionStatusIfNeeded()?.let { updatedOrder ->
                        println("🔄 loadCurrentOrder: Status transition ${loadedOrder.status} -> ${updatedOrder.status}")
                        // Update Firebase with new status
                        val dateKey = formatDateKey(updatedOrder.pickUpDate)
                        orderRepository.updateOrderStatus(sellerId, dateKey, updatedOrder.id, updatedOrder.status)

                        // If order transitioned to COMPLETED, don't load it
                        if (updatedOrder.status == com.together.newverse.domain.model.OrderStatus.COMPLETED) {
                            println("⏰ loadCurrentOrder: Order transitioned to COMPLETED, skipping...")
                            return@onSuccess
                        }
                        updatedOrder
                    } ?: loadedOrder

                    // Calculate date key
                    val dateKey = formatDateKey(order.pickUpDate)

                    // Check if order is editable (before Tuesday 23:59:59 deadline)
                    val canEdit = order.canEdit()

                    val nowMs = now.toEpochMilliseconds()
                    println("📦 loadCurrentOrder: Order canEdit=$canEdit (pickup in ${(order.pickUpDate - nowMs) / (24 * 60 * 60 * 1000)} days)")

                    // Load order items into BasketRepository with order metadata
                    basketRepository.loadOrderItems(order.articles, order.id, dateKey)

                    // Update state to store order info and editability
                    _state.update { current ->
                        current.copy(
                            basket = current.basket.copy(
                                currentOrderId = order.id,
                                currentOrderDate = dateKey
                            ),
                            mainScreen = current.mainScreen.copy(
                                canEditOrder = canEdit
                            )
                        )
                    }

                    println("✅ loadCurrentOrder: Loaded ${order.articles.size} items into basket")
                } else {
                    println("ℹ️ loadCurrentOrder: No upcoming orders found")
                }
            }.onFailure { error ->
                println("❌ loadCurrentOrder: Failed to load order - ${error.message}")
            }
        }.onFailure { error ->
            println("❌ loadCurrentOrder: Failed to load profile - ${error.message}")
        }
    } catch (e: Exception) {
        println("❌ loadCurrentOrder: Exception - ${e.message}")
    }
}

/**
 * Parse a pickup date key (yyyyMMdd) back to timestamp
 * Returns null if parsing fails
 */
private fun parsePickupDateFromKey(dateKey: String): Long? {
    return try {
        if (dateKey.length != 8) return null
        val year = dateKey.substring(0, 4).toInt()
        val month = dateKey.substring(4, 6).toInt()
        val day = dateKey.substring(6, 8).toInt()
        val localDate = kotlinx.datetime.LocalDate(year, month, day)
        localDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } catch (e: Exception) {
        println("⚠️ parsePickupDateFromKey: Failed to parse '$dateKey': ${e.message}")
        null
    }
}

/**
 * Format timestamp to date key (yyyyMMdd) for Firebase paths
 */
internal fun BuyAppViewModel.formatDateKey(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val year = dateTime.year
    val month = dateTime.month.number.toString().padStart(2, '0')
    val day = dateTime.day.toString().padStart(2, '0')
    return "$year$month$day"
}
