package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.repository.AuthUserInfo
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

            // Step 2: Check if user needs to authenticate (no persisted session)
            if (_state.value.common.user is UserState.NotAuthenticated) {
                println("🔐 App Init: No auth - waiting for user to login or continue as guest")
                return@launch  // Stop - wait for user to authenticate via LoginScreen
            }

            // Step 3: Wait for auth state to stabilize
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
 * Buy flavor: If no session, show login screen (NotAuthenticated state)
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

                    // Get user info from Firebase Auth
                    val userInfo = authRepository.getCurrentUserInfo()
                    println("📧 Buy App Startup: User info - email=${userInfo?.email}, name=${userInfo?.displayName}")

                    // Set LoggedIn state immediately (don't wait for observeAuthState)
                    _state.update { current ->
                        current.copy(
                            common = current.common.copy(
                                user = UserState.LoggedIn(
                                    id = userId,
                                    email = userInfo?.email ?: "",
                                    name = userInfo?.displayName ?: "",
                                    role = UserRole.CUSTOMER
                                )
                            ),
                            meta = current.meta.copy(
                                initializationStep = InitializationStep.LoadingProfile
                            )
                        )
                    }
                } else {
                    // No persisted session - show login screen
                    println("🔐 Buy App Startup: No auth - showing login screen...")
                    setNotAuthenticatedState()
                }
            },
            onFailure = { error ->
                // Failed to check auth - show login screen
                println("⚠️ Buy App Startup: Failed to check auth - ${error.message}, showing login screen...")
                setNotAuthenticatedState()
            }
        )
    } catch (e: Exception) {
        // Error checking auth - show login screen
        println("❌ Buy App Startup: Exception checking auth - ${e.message}, showing login screen...")
        setNotAuthenticatedState()
    }
}

/**
 * Set state to NotAuthenticated - user must choose to login or continue as guest
 */
internal fun BuyAppViewModel.setNotAuthenticatedState() {
    _state.update { current ->
        current.copy(
            common = current.common.copy(user = UserState.NotAuthenticated),
            meta = current.meta.copy(
                isInitializing = false,
                isInitialized = false,
                initializationStep = InitializationStep.NotStarted
            )
        )
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
            val previousUserState = _state.value.common.user

            // Get user info from Firebase Auth (email, displayName, etc.)
            val userInfo = if (userId != null) authRepository.getCurrentUserInfo() else null

            _state.update { current ->
                val newUserState = if (userId != null) {
                    UserState.LoggedIn(
                        id = userId,
                        email = userInfo?.email ?: "",
                        name = userInfo?.displayName ?: "",
                        role = UserRole.CUSTOMER
                    )
                } else {
                    // Keep NotAuthenticated if that's current state (user hasn't chosen yet)
                    if (current.common.user is UserState.NotAuthenticated) {
                        UserState.NotAuthenticated
                    } else {
                        UserState.Guest
                    }
                }

                current.copy(
                    common = current.common.copy(
                        user = newUserState,
                        // Buy flavor: never require login (guest access allowed)
                        requiresLogin = false
                    )
                )
            }

            // Handle transitions when user signs in
            if (userId != null) {
                // If coming from NotAuthenticated (first-time auth), run full initialization
                if (previousUserState is UserState.NotAuthenticated) {
                    println("🔐 Auth state changed: NotAuthenticated -> LoggedIn, resuming initialization...")
                    resumeInitializationAfterAuth(userInfo)
                } else {
                    // Already initialized, just refresh the order
                    loadOpenOrderAfterAuth()
                }
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
        val userId = (_state.value.common.user as? UserState.LoggedIn)?.id ?: return

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
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            profile = updatedProfile,
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
            val sellerId = "" // Using empty seller ID for now
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
