package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.ErrorState
import com.together.newverse.ui.state.ErrorType
import com.together.newverse.ui.state.InitializationStep
import com.together.newverse.ui.state.UserRole
import com.together.newverse.ui.state.UserState
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * Initialization extension functions for BuyAppViewModel
 *
 * Handles app startup, authentication flow, and initial data loading.
 *
 * Extracted functions:
 * - initializeApp - Main initialization orchestration
 * - checkAuthenticationStatus - Check for persisted auth session
 * - signInAsGuest - Anonymous sign-in fallback
 * - observeAuthState - Reactive auth state monitoring
 * - loadOpenOrderAfterAuth - Load cart after login
 * - loadUserProfile - Load user profile during init
 * - loadCurrentOrder - Load most recent order during init
 * - formatDateKey - Helper to format timestamps for Firebase paths
 */

/**
 * Main initialization flow - executes sequentially based on auth state
 * Flow:
 * 1. Check Auth
 * 2. If signed in: Load Profile → Load Order
 * 3. Load Articles (for all users)
 */
internal fun BuyAppViewModel.initializeApp() {
    viewModelScope.launch {
        try {
            // Start initialization
            _state.update { it.copy(
                meta = it.meta.copy(
                    isInitializing = true,
                    initializationStep = InitializationStep.CheckingAuth
                )
            )}

            // Step 1: Check authentication status
            checkAuthenticationStatus()

            // Step 2: Wait for auth state to stabilize
            println("🚀 App Init: Waiting for authentication to complete...")
            val userId = authRepository.observeAuthState()
                .filterNotNull()
                .first()

            println("🚀 App Init: Authentication complete, user ID: $userId")

            // Step 3: Load user-specific data (we have a valid userId)
            println("🚀 App Init: User logged in, loading user-specific data...")

            // Step 3a: Load Profile
            _state.update { it.copy(
                meta = it.meta.copy(
                    initializationStep = InitializationStep.LoadingProfile
                )
            )}
            loadUserProfile()

            // Step 3b: Load current order
            _state.update { it.copy(
                meta = it.meta.copy(
                    initializationStep = InitializationStep.LoadingOrder
                )
            )}
            loadCurrentOrder()

            // Step 4: Load articles (for all users)
            _state.update { it.copy(
                meta = it.meta.copy(
                    initializationStep = InitializationStep.LoadingArticles
                )
            )}
            loadProducts()

            // Step 5: Mark initialization complete
            _state.update { it.copy(
                meta = it.meta.copy(
                    isInitializing = false,
                    isInitialized = true,
                    initializationStep = InitializationStep.Complete
                )
            )}

            println("🚀 App Init: Initialization complete!")

        } catch (e: Exception) {
            println("❌ App Init: Error during initialization: ${e.message}")
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
}

/**
 * Check if user has a persisted authentication session
 * This runs BEFORE any Firebase connections
 *
 * Buy flavor: If no session, automatically sign in as guest
 */
internal suspend fun BuyAppViewModel.checkAuthenticationStatus() {
    try {
        _state.update { current ->
            current.copy(
                meta = current.meta.copy(
                    initializationStep = InitializationStep.CheckingAuth
                )
            )
        }

        println("🔍 Buy App Startup: Checking authentication")

        // Check for persisted authentication session
        authRepository.checkPersistedAuth().fold(
            onSuccess = { userId ->
                if (userId != null) {
                    // User has valid persisted session
                    println("✅ Buy App Startup: Restored auth session for user: $userId")
                    _state.update { current ->
                        current.copy(
                            meta = current.meta.copy(
                                initializationStep = InitializationStep.LoadingProfile
                            )
                        )
                    }
                } else {
                    // No persisted session - sign in as guest
                    println("🛒 Buy App Startup: No auth - signing in as guest...")
                    signInAsGuest()
                }
            },
            onFailure = { error ->
                // Failed to check auth - sign in as guest
                println("⚠️ Buy App Startup: Failed to check auth - ${error.message}, signing in as guest...")
                signInAsGuest()
            }
        )
    } catch (e: Exception) {
        // Error checking auth - sign in as guest
        println("❌ Buy App Startup: Exception checking auth - ${e.message}, signing in as guest...")
        signInAsGuest()
    }
}

/**
 * Sign in anonymously as a guest user
 */
internal suspend fun BuyAppViewModel.signInAsGuest() {
    authRepository.signInAnonymously().fold(
        onSuccess = { userId ->
            println("App Startup: Guest sign-in successful, user ID: $userId")
            _state.update { current ->
                current.copy(
                    meta = current.meta.copy(
                        initializationStep = InitializationStep.CheckingAuth
                    )
                )
            }
        },
        onFailure = { error ->
            println("App Startup: Guest sign-in failed - ${error.message}")
            _state.update { current ->
                current.copy(
                    meta = current.meta.copy(
                        initializationStep = InitializationStep.Failed(
                            step = "authentication",
                            message = error.message ?: "Failed to create session"
                        )
                    )
                )
            }
        }
    )
}

internal fun BuyAppViewModel.observeAuthState() {
    viewModelScope.launch {
        authRepository.observeAuthState().collect { userId ->
            _state.update { current ->
                current.copy(
                    common = current.common.copy(
                        user = if (userId != null) {
                            UserState.LoggedIn(
                                id = userId,
                                email = "", // Will be populated from profile
                                name = "", // Will be populated from profile
                                role = UserRole.CUSTOMER
                            )
                        } else {
                            UserState.Guest
                        },
                        // Buy flavor: never require login (guest access allowed)
                        requiresLogin = false
                    )
                )
            }

            // Load open order after successful authentication
            if (userId != null) {
                loadOpenOrderAfterAuth()
            }
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
            println("🛒 UnifiedAppViewModel.loadOpenOrderAfterAuth: START")

            // Get buyer profile to get placed order IDs
            val profileResult = profileRepository.getBuyerProfile()
            profileResult.onSuccess { buyerProfile ->
                val placedOrderIds = buyerProfile.placedOrderIds

                if (placedOrderIds.isEmpty()) {
                    println("🛒 UnifiedAppViewModel.loadOpenOrderAfterAuth: No placed orders found")
                    return@launch
                }

                println("🛒 UnifiedAppViewModel.loadOpenOrderAfterAuth: Found ${placedOrderIds.size} placed orders")

                // Get the most recent editable order
                val sellerId = "" // Using empty seller ID for now
                val orderResult = orderRepository.getOpenEditableOrder(sellerId, placedOrderIds)

                orderResult.onSuccess { order ->
                    if (order != null) {
                        println("✅ UnifiedAppViewModel.loadOpenOrderAfterAuth: Loaded editable order - orderId=${order.id}, ${order.articles.size} items")

                        // Calculate date key
                        val dateKey = formatDateKey(order.pickUpDate)

                        // Load order items into BasketRepository with order metadata
                        basketRepository.loadOrderItems(order.articles, order.id, dateKey)

                        // Update state to store order info for later retrieval
                        _state.update { current ->
                            current.copy(
                                common = current.common.copy(
                                    basket = current.common.basket.copy(
                                        currentOrderId = order.id,
                                        currentOrderDate = dateKey
                                    )
                                )
                            )
                        }

                        val itemCount = order.articles.size
                        println("✅ UnifiedAppViewModel.loadOpenOrderAfterAuth: Cart badge updated with $itemCount items")
                    } else {
                        println("🛒 UnifiedAppViewModel.loadOpenOrderAfterAuth: No editable orders found")
                    }
                }.onFailure { error ->
                    println("❌ UnifiedAppViewModel.loadOpenOrderAfterAuth: Failed to load order - ${error.message}")
                }
            }.onFailure { error ->
                println("❌ UnifiedAppViewModel.loadOpenOrderAfterAuth: Failed to load buyer profile - ${error.message}")
            }
        } catch (e: Exception) {
            println("❌ UnifiedAppViewModel.loadOpenOrderAfterAuth: Exception - ${e.message}")
        }
    }
}

/**
 * Load user profile during initialization
 * Loads the profile data for the currently logged-in user
 */
internal suspend fun BuyAppViewModel.loadUserProfile() {
    try {
        val userId = (_state.value.common.user as? UserState.LoggedIn)?.id ?: return

        println("👤 Loading user profile for userId: $userId")

        val result = profileRepository.getBuyerProfile()

        result.onSuccess { profile ->
            println("✅ Profile loaded successfully: ${profile.displayName}")
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            profile = profile,
                            isLoading = false,
                            error = null
                        )
                    )
                )
            }
        }.onFailure { error ->
            println("❌ Failed to load profile: ${error.message}")
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            isLoading = false,
                            error = ErrorState(
                                message = "Failed to load profile: ${error.message}",
                                type = ErrorType.NETWORK
                            )
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
                println("🛒 loadCurrentOrder: Found draft basket with ${draftBasket.items.size} items, loading...")
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
            val sellerId = "" // Using empty seller ID for now
            val orderResult = orderRepository.getUpcomingOrder(sellerId, placedOrderIds)

            orderResult.onSuccess { order ->
                if (order != null) {
                    println("✅ loadCurrentOrder: Found upcoming order - orderId=${order.id}, ${order.articles.size} items")

                    // Calculate date key
                    val dateKey = formatDateKey(order.pickUpDate)

                    // Check if order is editable (before Tuesday 23:59:59 deadline)
                    val canEdit = OrderDateUtils.canEditOrder(
                        Instant.fromEpochMilliseconds(order.pickUpDate)
                    )

                    val now = Clock.System.now().toEpochMilliseconds()
                    println("📦 loadCurrentOrder: Order canEdit=$canEdit (pickup in ${(order.pickUpDate - now) / (24 * 60 * 60 * 1000)} days)")

                    // Load order items into BasketRepository with order metadata
                    basketRepository.loadOrderItems(order.articles, order.id, dateKey)

                    // Update state to store order info and editability
                    _state.update { current ->
                        current.copy(
                            common = current.common.copy(
                                basket = current.common.basket.copy(
                                    currentOrderId = order.id,
                                    currentOrderDate = dateKey
                                )
                            ),
                            screens = current.screens.copy(
                                mainScreen = current.screens.mainScreen.copy(
                                    canEditOrder = canEdit
                                )
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
