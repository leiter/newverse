package com.together.newverse.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.data.repository.GitLiveArticleRepository
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.util.OrderDateUtils
import kotlinx.datetime.Clock
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.ui.navigation.NavRoutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.getString

/**
 * Buy flavor ViewModel managing all app state for buyer/customer app
 *
 * This is the single ViewModel for the buy flavor, implementing
 * a Redux-like pattern with actions and reducers.
 */
class BuyAppViewModel(
    private val articleRepository: ArticleRepository,
    private val orderRepository: OrderRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val basketRepository: BasketRepository
) : ViewModel(), AppViewModel {

    private val _state = MutableStateFlow(UnifiedAppState())
    override val state: StateFlow<UnifiedAppState> = _state.asStateFlow()

    /**
     * New simplified state for buyer app screens.
     * Maps from UnifiedAppState to BuyerAppState for gradual migration.
     * Screens can use this instead of the full UnifiedAppState.
     */
    val buyerState: StateFlow<BuyerAppState> = _state.map { old ->
        mapToBuyerAppState(old)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BuyerAppState()
    )

    /**
     * Maps UnifiedAppState to the new simplified BuyerAppState
     */
    private fun mapToBuyerAppState(old: UnifiedAppState): BuyerAppState {
        return BuyerAppState(
            data = BuyerDataState(
                articles = old.screens.mainScreen.articles,
                isLoadingArticles = old.screens.mainScreen.isLoading,
                buyerProfile = old.screens.customerProfile.profile,
                isLoadingProfile = old.screens.customerProfile.isLoading,
                currentOrder = old.screens.basketScreen.orderId?.let { orderId ->
                    Order(
                        id = orderId,
                        pickUpDate = old.screens.basketScreen.pickupDate ?: 0L,
                        articles = old.screens.basketScreen.items,
                        createdDate = old.screens.basketScreen.createdDate ?: 0L
                    )
                },
                isLoadingOrder = old.screens.basketScreen.isLoadingOrder,
                orderHistory = old.screens.orderHistory.items,
                isLoadingOrderHistory = old.screens.orderHistory.isLoading,
                basketItems = old.common.basket.items,
                error = old.screens.mainScreen.error?.message
            ),
            ui = BuyerUiStates(
                mainScreen = MainScreenUiState(
                    selectedArticle = old.screens.mainScreen.selectedArticle,
                    selectedQuantity = old.screens.mainScreen.selectedQuantity,
                    activeFilter = old.screens.mainScreen.activeFilter,
                    showNewOrderSnackbar = old.screens.mainScreen.showNewOrderSnackbar
                ),
                basketScreen = BasketScreenUiState(
                    showDatePicker = old.screens.basketScreen.showDatePicker,
                    selectedPickupDate = old.screens.basketScreen.selectedPickupDate,
                    availablePickupDates = old.screens.basketScreen.availablePickupDates,
                    isSubmitting = old.screens.basketScreen.isCheckingOut,
                    submitSuccess = old.screens.basketScreen.orderSuccess,
                    submitError = old.screens.basketScreen.orderError,
                    isCancelling = old.screens.basketScreen.isCancelling,
                    cancelSuccess = old.screens.basketScreen.cancelSuccess,
                    showReorderDatePicker = old.screens.basketScreen.showReorderDatePicker,
                    isReordering = old.screens.basketScreen.isReordering,
                    reorderSuccess = old.screens.basketScreen.reorderSuccess,
                    showMergeDialog = old.screens.basketScreen.showMergeDialog,
                    existingOrderForMerge = old.screens.basketScreen.existingOrderForMerge,
                    mergeConflicts = old.screens.basketScreen.mergeConflicts.map { conflict ->
                        BuyerMergeConflict(
                            productId = conflict.productId,
                            productName = conflict.productName,
                            unit = conflict.unit,
                            existingQuantity = conflict.existingQuantity,
                            newQuantity = conflict.newQuantity,
                            existingPrice = conflict.existingPrice,
                            newPrice = conflict.newPrice,
                            resolution = when (conflict.resolution) {
                                MergeResolution.UNDECIDED -> BuyerMergeResolution.UNDECIDED
                                MergeResolution.ADD -> BuyerMergeResolution.ADD
                                MergeResolution.KEEP_EXISTING -> BuyerMergeResolution.KEEP_EXISTING
                                MergeResolution.USE_NEW -> BuyerMergeResolution.USE_NEW
                            }
                        )
                    },
                    isMerging = old.screens.basketScreen.isMerging
                ),
                profileScreen = ProfileScreenUiState(),
                global = GlobalBuyerUiState(
                    isRefreshing = old.common.ui.isRefreshing,
                    snackbar = old.common.ui.snackbar?.let { snack ->
                        BuyerSnackbar(
                            message = snack.message,
                            type = when (snack.type) {
                                SnackbarType.SUCCESS -> BuyerSnackbarType.SUCCESS
                                SnackbarType.ERROR -> BuyerSnackbarType.ERROR
                                SnackbarType.WARNING -> BuyerSnackbarType.WARNING
                                SnackbarType.INFO -> BuyerSnackbarType.INFO
                            },
                            actionLabel = snack.actionLabel
                        )
                    },
                    currentRoute = old.common.navigation.currentRoute,
                    isDrawerOpen = old.common.navigation.isDrawerOpen
                )
            ),
            auth = BuyerAuthState(
                user = when (val user = old.common.user) {
                    is UserState.Guest -> BuyerUserState.Guest
                    is UserState.Loading -> BuyerUserState.Loading
                    is UserState.LoggedIn -> BuyerUserState.LoggedIn(
                        id = user.id,
                        name = user.name,
                        email = user.email,
                        photoUrl = user.profileImageUrl
                    )
                },
                triggerGoogleSignIn = old.common.triggerGoogleSignIn,
                triggerGoogleSignOut = old.common.triggerGoogleSignOut
            ),
            meta = BuyerMetaState(
                isInitialized = old.meta.isInitialized,
                initializationStep = when (val step = old.meta.initializationStep) {
                    is InitializationStep.NotStarted -> BuyerInitStep.NotStarted
                    is InitializationStep.CheckingAuth -> BuyerInitStep.CheckingAuth
                    is InitializationStep.LoadingProfile -> BuyerInitStep.LoadingProfile
                    is InitializationStep.LoadingOrder -> BuyerInitStep.LoadingOrder
                    is InitializationStep.LoadingArticles -> BuyerInitStep.LoadingArticles
                    is InitializationStep.Complete -> BuyerInitStep.Complete
                    is InitializationStep.Failed -> BuyerInitStep.Failed(step.step, step.message)
                },
                devOrderDateOffsetDays = old.meta.devOrderDateOffsetDays
            )
        )
    }

    init {
        // Initialize app on startup
        initializeApp()

        // Observe auth state changes
        observeAuthState()

        // Initialize MainScreen observers
        observeMainScreenBasket()
        observeMainScreenBuyerProfile()

        // Initialize BasketScreen observers
        initializeBasketScreen()

        // Load MainScreen articles after auth is ready
        viewModelScope.launch {
            authRepository.observeAuthState()
                .filterNotNull()
                .first()
            loadMainScreenArticles()
        }
    }

    private fun observeAuthState() {
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
    private fun loadOpenOrderAfterAuth() {
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
     * Format timestamp to date key (yyyyMMdd) for Firebase paths
     */
    private fun formatDateKey(timestamp: Long): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val year = dateTime.year
        val month = dateTime.monthNumber.toString().padStart(2, '0')
        val day = dateTime.dayOfMonth.toString().padStart(2, '0')
        return "$year$month$day"
    }

    // ===== Public Action Handlers =====

    /**
     * Main action dispatcher - all UI actions go through here
     */
    override fun dispatch(action: UnifiedAppAction) {
        when (action) {
            // Navigation actions
            is UnifiedNavigationAction -> handleNavigationAction(action)

            // User actions
            is UnifiedUserAction -> handleUserAction(action)

            // Product actions
            is UnifiedProductAction -> handleProductAction(action)

            // Basket actions
            is UnifiedBasketAction -> handleBasketAction(action)

            // Order actions
            is UnifiedOrderAction -> handleOrderAction(action)

            // UI actions
            is UnifiedUiAction -> handleUiAction(action)

            // Profile actions
            is UnifiedProfileAction -> handleProfileAction(action)

            // Search actions
            is UnifiedSearchAction -> handleSearchAction(action)

            // Filter actions
            is UnifiedFilterAction -> handleFilterAction(action)

            // Main Screen actions
            is UnifiedMainScreenAction -> handleMainScreenAction(action)

            // Basket Screen actions (checkout/order workflow)
            is UnifiedBasketScreenAction -> handleBasketScreenAction(action)

            // Account management actions (guest linking, logout warning, etc.)
            is UnifiedAccountAction -> handleAccountAction(action)
        }
    }

    // ===== Action Handlers =====

    private fun handleNavigationAction(action: UnifiedNavigationAction) {
        when (action) {
            is UnifiedNavigationAction.NavigateTo -> navigateTo(action.route)
            is UnifiedNavigationAction.NavigateBack -> navigateBack()
            is UnifiedNavigationAction.OpenDrawer -> openDrawer()
            is UnifiedNavigationAction.CloseDrawer -> closeDrawer()
        }
    }

    private fun handleUserAction(action: UnifiedUserAction) {
        when (action) {
            is UnifiedUserAction.Login -> login(action.email, action.password)
            is UnifiedUserAction.LoginWithGoogle -> loginWithGoogle()
            is UnifiedUserAction.LoginWithTwitter -> loginWithTwitter()
            is UnifiedUserAction.Logout -> logout()
            is UnifiedUserAction.Register -> register(action.email, action.password, action.name)
            is UnifiedUserAction.UpdateProfile -> updateProfile(action.profile)
            is UnifiedUserAction.RequestPasswordReset -> sendPasswordResetEmail(action.email)
        }
    }

    private fun handleProductAction(action: UnifiedProductAction) {
        when (action) {
            is UnifiedProductAction.LoadProducts -> loadProducts()
            is UnifiedProductAction.RefreshProducts -> refreshProducts()
            is UnifiedProductAction.SelectProduct -> selectProduct(action.product)
            is UnifiedProductAction.ViewProductDetail -> viewProductDetail(action.productId)
            is UnifiedProductAction.CreateProduct -> { /* Seller-only action */ }
            is UnifiedProductAction.UpdateProduct -> { /* Seller-only action */ }
            is UnifiedProductAction.DeleteProduct -> { /* Seller-only action */ }
        }
    }

    private fun handleBasketAction(action: UnifiedBasketAction) {
        when (action) {
            is UnifiedBasketAction.AddToBasket -> addToBasket(action.product, action.quantity)
            is UnifiedBasketAction.RemoveFromBasket -> removeFromBasket(action.productId)
            is UnifiedBasketAction.UpdateQuantity -> updateBasketQuantity(action.productId, action.quantity)
            is UnifiedBasketAction.ClearBasket -> clearBasket()
            is UnifiedBasketAction.ApplyPromoCode -> applyPromoCode(action.code)
            is UnifiedBasketAction.StartCheckout -> startCheckout()
        }
    }

    private fun handleOrderAction(action: UnifiedOrderAction) {
        when (action) {
            is UnifiedOrderAction.LoadOrders -> loadOrders()
            is UnifiedOrderAction.ViewOrderDetail -> viewOrderDetail(action.orderId)
            is UnifiedOrderAction.PlaceOrder -> placeOrder(action.checkoutData)
            is UnifiedOrderAction.CancelOrder -> cancelOrder(action.orderId)
        }
    }

    private fun handleUiAction(action: UnifiedUiAction) {
        when (action) {
            is UnifiedUiAction.ShowSnackbar -> showSnackbar(action.message, action.type)
            is UnifiedUiAction.HideSnackbar -> hideSnackbar()
            is UnifiedUiAction.ShowDialog -> showDialog(action.dialog)
            is UnifiedUiAction.HideDialog -> hideDialog()
            is UnifiedUiAction.ShowBottomSheet -> showBottomSheet(action.sheet)
            is UnifiedUiAction.HideBottomSheet -> hideBottomSheet()
            is UnifiedUiAction.SetRefreshing -> setRefreshing(action.isRefreshing)
            is UnifiedUiAction.ShowPasswordResetDialog -> showPasswordResetDialog()
            is UnifiedUiAction.HidePasswordResetDialog -> hidePasswordResetDialog()
        }
    }

    private fun handleProfileAction(action: UnifiedProfileAction) {
        when (action) {
            is UnifiedProfileAction.LoadProfile -> loadProfile()
            is UnifiedProfileAction.UpdateProfileField -> updateProfileField(action.field, action.value)
            is UnifiedProfileAction.SaveProfile -> saveProfile()
            is UnifiedProfileAction.CancelProfileEdit -> cancelProfileEdit()
            is UnifiedProfileAction.LoadCustomerProfile -> loadCustomerProfile()
            is UnifiedProfileAction.LoadOrderHistory -> loadOrderHistory()
            is UnifiedProfileAction.RefreshCustomerProfile -> refreshCustomerProfile()
            is UnifiedProfileAction.SaveBuyerProfile -> saveBuyerProfile(action.displayName, action.email, action.phone)
        }
    }

    private fun handleSearchAction(action: UnifiedSearchAction) {
        when (action) {
            is UnifiedSearchAction.Search -> search(action.query)
            is UnifiedSearchAction.ClearSearch -> clearSearch()
            is UnifiedSearchAction.AddToSearchHistory -> addToSearchHistory(action.query)
        }
    }

    private fun handleFilterAction(action: UnifiedFilterAction) {
        when (action) {
            is UnifiedFilterAction.ApplyFilter -> applyFilter(action.key, action.value)
            is UnifiedFilterAction.RemoveFilter -> removeFilter(action.key)
            is UnifiedFilterAction.ClearFilters -> clearFilters()
            is UnifiedFilterAction.SaveFilter -> saveFilter(action.name)
            is UnifiedFilterAction.LoadSavedFilter -> loadSavedFilter(action.filterId)
        }
    }

    private fun handleMainScreenAction(action: UnifiedMainScreenAction) {
        when (action) {
            is UnifiedMainScreenAction.SelectArticle -> selectMainScreenArticle(action.article)
            is UnifiedMainScreenAction.UpdateQuantity -> handleUpdateQuantity(action.quantity)
            is UnifiedMainScreenAction.UpdateQuantityText -> handleUpdateQuantityFromText(action.text)
            UnifiedMainScreenAction.AddToCart -> handleAddToCart()
            UnifiedMainScreenAction.RemoveFromBasket -> handleRemoveFromBasket()
            is UnifiedMainScreenAction.ToggleFavourite -> toggleMainScreenFavourite(action.articleId)
            is UnifiedMainScreenAction.SetFilter -> setMainScreenFilter(action.filter)
            UnifiedMainScreenAction.Refresh -> refreshMainScreen()
            UnifiedMainScreenAction.DismissNewOrderSnackbar -> dismissNewOrderSnackbar()
            UnifiedMainScreenAction.StartNewOrder -> startNewOrder()
        }
    }

    private fun handleUpdateQuantity(quantity: Double) {
        if (!_state.value.screens.mainScreen.canEditOrder) {
            showNewOrderSnackbar()
            return
        }
        updateMainScreenQuantity(quantity)
    }

    private fun handleUpdateQuantityFromText(text: String) {
        if (!_state.value.screens.mainScreen.canEditOrder) {
            showNewOrderSnackbar()
            return
        }
        updateMainScreenQuantityFromText(text)
    }

    private fun handleAddToCart() {
        if (!_state.value.screens.mainScreen.canEditOrder) {
            showNewOrderSnackbar()
            return
        }
        addMainScreenToCart()
    }

    private fun handleRemoveFromBasket() {
        if (!_state.value.screens.mainScreen.canEditOrder) {
            showNewOrderSnackbar()
            return
        }
        removeMainScreenFromBasket()
    }

    private fun showNewOrderSnackbar() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    mainScreen = current.screens.mainScreen.copy(
                        showNewOrderSnackbar = true
                    )
                )
            )
        }
    }

    private fun dismissNewOrderSnackbar() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    mainScreen = current.screens.mainScreen.copy(
                        showNewOrderSnackbar = false
                    )
                )
            )
        }
    }

    private fun startNewOrder() {
        viewModelScope.launch {
            // Clear the basket to start fresh
            basketRepository.clearBasket()

            // Reset canEditOrder to true and hide snackbar
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        mainScreen = current.screens.mainScreen.copy(
                            canEditOrder = true,
                            showNewOrderSnackbar = false
                        )
                    ),
                    common = current.common.copy(
                        basket = current.common.basket.copy(
                            currentOrderId = null,
                            currentOrderDate = null
                        )
                    )
                )
            }
            println("🛒 Started new order - basket cleared")
        }
    }

    private fun setMainScreenFilter(filter: ProductFilter) {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    mainScreen = current.screens.mainScreen.copy(
                        activeFilter = filter
                    )
                )
            )
        }
    }

    // ===== Implementation Methods =====

    /**
     * Main initialization flow - executes sequentially based on auth state
     * Flow:
     * 1. Check Auth
     * 2. If signed in: Load Profile → Load Order
     * 3. Load Articles (for all users)
     */
    private fun initializeApp() {
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
    private suspend fun checkAuthenticationStatus() {
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
    private suspend fun signInAsGuest() {
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

    private fun navigateTo(route: NavRoutes) {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    navigation = current.common.navigation.copy(
                        previousRoute = current.common.navigation.currentRoute,
                        currentRoute = route,
                        backStack = current.common.navigation.backStack + route
                    )
                )
            )
        }
    }

    private fun navigateBack() {
        _state.update { current ->
            val backStack = current.common.navigation.backStack
            if (backStack.size > 1) {
                val newBackStack = backStack.dropLast(1)
                current.copy(
                    common = current.common.copy(
                        navigation = current.common.navigation.copy(
                            currentRoute = newBackStack.last(),
                            previousRoute = current.common.navigation.currentRoute,
                            backStack = newBackStack
                        )
                    )
                )
            } else current
        }
    }

    private fun openDrawer() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    navigation = current.common.navigation.copy(isDrawerOpen = true)
                )
            )
        }
    }

    private fun closeDrawer() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    navigation = current.common.navigation.copy(isDrawerOpen = false)
                )
            )
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            println("📦 UnifiedAppViewModel.loadProducts: START")

            // Update loading state
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        products = current.screens.products.copy(isLoading = true, error = null)
                    )
                )
            }

            println("📦 UnifiedAppViewModel.loadProducts: Set loading state to true")

            // Load products for demo - using empty sellerId for now
            // TODO: Get sellerId from user state
            val sellerId = ""
            println("📦 UnifiedAppViewModel.loadProducts: Calling articleRepository.getArticles(sellerId='$sellerId')")

            articleRepository.getArticles(sellerId)
                .catch { e ->
                    println("❌ UnifiedAppViewModel.loadProducts: ERROR - ${e.message}")
                    e.printStackTrace()
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                products = current.screens.products.copy(
                                    isLoading = false,
                                    error = ErrorState(
                                        message = e.message ?: "Failed to load products",
                                        type = ErrorType.NETWORK
                                    )
                                )
                            )
                        )
                    }
                }
                .collect { article ->
                    println("📦 UnifiedAppViewModel.loadProducts: Received article event - mode=${article.mode}, id=${article.id}, name=${article.productName}")

                    // Handle individual article events
                    _state.update { current ->
                        val currentProducts = current.screens.products.items.toMutableList()
                        val beforeCount = currentProducts.size

                        when (article.mode) {
                            Article.MODE_ADDED -> {
                                // Check if article already exists to avoid duplicates
                                val existingIndex = currentProducts.indexOfFirst { it.id == article.id }
                                if (existingIndex >= 0) {
                                    currentProducts[existingIndex] = article
                                    println("📦 UnifiedAppViewModel.loadProducts: UPDATED existing article '${article.productName}' at index $existingIndex")
                                } else {
                                    currentProducts.add(article)
                                    println("📦 UnifiedAppViewModel.loadProducts: ADDED article '${article.productName}' (id=${article.id})")
                                }
                            }
                            Article.MODE_CHANGED -> {
                                val index = currentProducts.indexOfFirst { it.id == article.id }
                                if (index >= 0) {
                                    currentProducts[index] = article
                                    println("📦 UnifiedAppViewModel.loadProducts: CHANGED article '${article.productName}' at index $index, available=${article.available}")
                                } else {
                                    // Article wasn't in list, add it now
                                    currentProducts.add(article)
                                    println("📦 UnifiedAppViewModel.loadProducts: CHANGED but not found, ADDED article '${article.productName}' (id=${article.id}), available=${article.available}")
                                }
                            }
                            Article.MODE_REMOVED -> {
                                currentProducts.removeAll { it.id == article.id }
                                println("📦 UnifiedAppViewModel.loadProducts: REMOVED article '${article.productName}' (id=${article.id})")
                            }
                        }

                        val afterCount = currentProducts.size
                        println("📦 UnifiedAppViewModel.loadProducts: Product count: $beforeCount → $afterCount")

                        current.copy(
                            screens = current.screens.copy(
                                products = current.screens.products.copy(
                                    isLoading = false,
                                    items = currentProducts,
                                    error = null
                                )
                            )
                        )
                    }
                }
        }
    }

    private fun refreshProducts() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(isRefreshing = true)
                )
            )
        }
        loadProducts()
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(isRefreshing = false)
                )
            )
        }
    }

    private fun selectProduct(product: Article) {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    products = current.screens.products.copy(selectedItem = product)
                )
            )
        }
    }

    private fun addToBasket(product: Article, quantity: Double) {
        _state.update { current ->
            val currentItems = current.common.basket.items.toMutableList()
            val existingIndex = currentItems.indexOfFirst { it.id == product.id || it.productId == product.id }

            if (existingIndex >= 0) {
                // Update quantity if product already in basket
                val existingItem = currentItems[existingIndex]
                currentItems[existingIndex] = existingItem.copy(
                    amountCount = existingItem.amountCount + quantity
                )
            } else {
                // Add new item to basket
                currentItems.add(
                    OrderedProduct(
                        productId = product.id,
                        productName = product.productName,
                        price = product.price,
                        amountCount = quantity,
                        piecesCount = quantity.toInt()
                    )
                )
            }

            val newTotal = currentItems.sumOf { it.price * it.amountCount }
            val newCount = currentItems.sumOf { it.piecesCount }

            current.copy(
                common = current.common.copy(
                    basket = current.common.basket.copy(
                        items = currentItems,
                        totalAmount = newTotal,
                        itemCount = newCount
                    )
                )
            )
        }

        // Show success snackbar
        viewModelScope.launch {
            showSnackbar(getString(Res.string.snackbar_added_to_basket), SnackbarType.SUCCESS)
        }
    }

    private fun removeFromBasket(productId: String) {
        _state.update { current ->
            val newItems = current.common.basket.items.filter { it.id != productId && it.productId != productId }
            val newTotal = newItems.sumOf { it.price * it.amountCount }
            val newCount = newItems.sumOf { it.piecesCount }

            current.copy(
                common = current.common.copy(
                    basket = current.common.basket.copy(
                        items = newItems,
                        totalAmount = newTotal,
                        itemCount = newCount
                    )
                )
            )
        }
    }

    private fun updateBasketQuantity(productId: String, quantity: Double) {
        _state.update { current ->
            val newItems = current.common.basket.items.map { item ->
                if (item.id == productId || item.productId == productId) {
                    item.copy(amountCount = quantity, piecesCount = quantity.toInt())
                } else item
            }
            val newTotal = newItems.sumOf { it.price * it.amountCount }
            val newCount = newItems.sumOf { it.piecesCount }

            current.copy(
                common = current.common.copy(
                    basket = current.common.basket.copy(
                        items = newItems,
                        totalAmount = newTotal,
                        itemCount = newCount
                    )
                )
            )
        }
    }

    private fun clearBasket() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    basket = BasketState()
                )
            )
        }
    }

    private fun showSnackbar(message: String, type: SnackbarType) {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(
                        snackbar = SnackbarState(message = message, type = type)
                    )
                )
            )
        }
    }

    private fun hideSnackbar() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(snackbar = null)
                )
            )
        }
    }

    private fun showDialog(dialog: DialogState) {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(dialog = dialog)
                )
            )
        }
    }

    private fun hideDialog() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(dialog = null)
                )
            )
        }
    }

    private fun showBottomSheet(sheet: BottomSheetState) {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(bottomSheet = sheet)
                )
            )
        }
    }

    private fun hideBottomSheet() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(bottomSheet = null)
                )
            )
        }
    }

    private fun setRefreshing(isRefreshing: Boolean) {
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    ui = current.common.ui.copy(isRefreshing = isRefreshing)
                )
            )
        }
    }

    // Placeholder implementations for other methods
    private fun login(email: String, password: String) {
        viewModelScope.launch {
            // Clear any previous errors and set loading state
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        auth = current.screens.auth.copy(
                            isLoading = true,
                            error = null,
                            isSuccess = false
                        )
                    )
                )
            }

            // Attempt sign in
            authRepository.signInWithEmail(email, password)
                .onSuccess { userId ->
                    println("✅ Buy App Login Success: userId=$userId")

                    // Success - just clear the forced login flag and let the app naturally navigate
                    // Don't try to navigate manually - let AppScaffold handle it
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    isSuccess = true,
                                    error = null
                                )
                            ),
                            common = current.common.copy(
                                requiresLogin = false // Clear forced login flag - this will hide ForcedLoginScreen
                            ),
                            meta = current.meta.copy(
                                isInitializing = false,
                                isInitialized = true,
                                initializationStep = InitializationStep.Complete
                            )
                        )
                    }

                    // Show success message
                    showSnackbar(getString(Res.string.snackbar_login_success), SnackbarType.SUCCESS)

                    println("🎯 Login complete - requiresLogin cleared, app will show main UI")
                }
                .onFailure { error ->
                    // Parse error message for user-friendly display
                    val errorMessage = when {
                        error.message?.contains("No account found", true) == true ->
                            getString(Res.string.error_no_account)
                        error.message?.contains("Incorrect password", true) == true ->
                            getString(Res.string.error_wrong_password)
                        error.message?.contains("Invalid email", true) == true ->
                            getString(Res.string.error_email_invalid)
                        error.message?.contains("Network", true) == true ->
                            getString(Res.string.error_no_internet)
                        error.message?.contains("too many", true) == true ->
                            getString(Res.string.error_too_many_attempts)
                        else -> error.message ?: getString(Res.string.error_login_failed)
                    }

                    // Show error snackbar
                    showSnackbar(errorMessage, SnackbarType.ERROR)

                    // Update state with error
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    error = errorMessage,
                                    isSuccess = false
                                )
                            )
                        )
                    }
                }
        }
    }

    private fun loginWithGoogle() {
        println("🔐 UnifiedAppViewModel.loginWithGoogle: Triggering Google Sign-In flow")
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    triggerGoogleSignIn = true
                )
            )
        }
    }

    private fun loginWithTwitter() {
        println("🔐 UnifiedAppViewModel.loginWithTwitter: Triggering Twitter Sign-In flow")
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    triggerTwitterSignIn = true
                )
            )
        }
    }

    /**
     * Send password reset email to the specified email address.
     */
    private fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            // Set loading state
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        auth = current.screens.auth.copy(
                            isLoading = true,
                            error = null,
                            passwordResetSent = false
                        )
                    )
                )
            }

            authRepository.sendPasswordResetEmail(email)
                .onSuccess {
                    println("✅ Password reset email sent to $email")
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    passwordResetSent = true,
                                    showPasswordResetDialog = false,
                                    error = null
                                )
                            )
                        )
                    }
                    showSnackbar(getString(Res.string.password_reset_sent), SnackbarType.SUCCESS)
                }
                .onFailure { error ->
                    println("❌ Password reset failed: ${error.message}")
                    val errorMessage = when {
                        error.message?.contains("No account found", true) == true ->
                            getString(Res.string.error_no_account)
                        error.message?.contains("Invalid email", true) == true ->
                            getString(Res.string.error_email_invalid)
                        else -> error.message ?: getString(Res.string.password_reset_failed)
                    }
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    error = errorMessage,
                                    passwordResetSent = false
                                )
                            )
                        )
                    }
                    showSnackbar(errorMessage, SnackbarType.ERROR)
                }
        }
    }

    /**
     * Show password reset dialog
     */
    fun showPasswordResetDialog() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    auth = current.screens.auth.copy(
                        showPasswordResetDialog = true,
                        passwordResetSent = false,
                        error = null
                    )
                )
            )
        }
    }

    /**
     * Hide password reset dialog
     */
    fun hidePasswordResetDialog() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    auth = current.screens.auth.copy(
                        showPasswordResetDialog = false,
                        error = null
                    )
                )
            )
        }
    }

    /**
     * Reset Google Sign-In trigger after it's been handled
     */
    override fun resetGoogleSignInTrigger() {
        println("🔐 UnifiedAppViewModel.resetGoogleSignInTrigger: Resetting trigger")
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    triggerGoogleSignIn = false
                )
            )
        }
    }

    /**
     * Reset Twitter Sign-In trigger after it's been handled
     */
    override fun resetTwitterSignInTrigger() {
        println("🔐 UnifiedAppViewModel.resetTwitterSignInTrigger: Resetting trigger")
        _state.update { current ->
            current.copy(
                common = current.common.copy(
                    triggerTwitterSignIn = false
                )
            )
        }
    }

    private fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
                .onSuccess {
                    // Clear basket and other user-specific data
                    _state.update { current ->
                        current.copy(
                            common = current.common.copy(
                                user = UserState.Guest,
                                basket = BasketState(),
                                triggerGoogleSignOut = true
                            )
                        )
                    }
                    showSnackbar(getString(Res.string.snackbar_logout_success), SnackbarType.SUCCESS)
                }
                .onFailure { error ->
                    showSnackbar(error.message ?: getString(Res.string.snackbar_logout_failed), SnackbarType.ERROR)
                }
        }
    }

    override fun resetGoogleSignOutTrigger() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(triggerGoogleSignOut = false)
            )
        }
    }

    // ===== Account Management Handlers =====

    private fun handleAccountAction(action: UnifiedAccountAction) {
        when (action) {
            is UnifiedAccountAction.ShowLogoutWarning -> showLogoutWarningDialog()
            is UnifiedAccountAction.DismissLogoutWarning -> dismissLogoutWarningDialog()
            is UnifiedAccountAction.ShowLinkAccountDialog -> showLinkAccountDialog()
            is UnifiedAccountAction.DismissLinkAccountDialog -> dismissLinkAccountDialog()
            is UnifiedAccountAction.ShowDeleteAccountDialog -> showDeleteAccountDialog()
            is UnifiedAccountAction.DismissDeleteAccountDialog -> dismissDeleteAccountDialog()
            is UnifiedAccountAction.ConfirmGuestLogout -> confirmGuestLogout()
            is UnifiedAccountAction.LinkWithGoogle -> linkWithGoogle()
            is UnifiedAccountAction.LinkWithEmail -> linkWithEmail(action.email, action.password)
            is UnifiedAccountAction.ConfirmDeleteAccount -> confirmDeleteAccount()
        }
    }

    private fun showLogoutWarningDialog() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    customerProfile = current.screens.customerProfile.copy(
                        showLogoutWarningDialog = true
                    )
                )
            )
        }
    }

    private fun dismissLogoutWarningDialog() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    customerProfile = current.screens.customerProfile.copy(
                        showLogoutWarningDialog = false
                    )
                )
            )
        }
    }

    private fun showLinkAccountDialog() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    customerProfile = current.screens.customerProfile.copy(
                        showLinkAccountDialog = true
                    )
                )
            )
        }
    }

    private fun dismissLinkAccountDialog() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    customerProfile = current.screens.customerProfile.copy(
                        showLinkAccountDialog = false
                    )
                )
            )
        }
    }

    private fun showDeleteAccountDialog() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    customerProfile = current.screens.customerProfile.copy(
                        showDeleteAccountDialog = true
                    )
                )
            )
        }
    }

    private fun dismissDeleteAccountDialog() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    customerProfile = current.screens.customerProfile.copy(
                        showDeleteAccountDialog = false
                    )
                )
            )
        }
    }

    /**
     * Confirm guest logout with immediate data deletion.
     * Deletes buyer profile from Firebase, clears local basket, signs out.
     */
    private fun confirmGuestLogout() {
        viewModelScope.launch {
            try {
                // Close dialogs first
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            customerProfile = current.screens.customerProfile.copy(
                                showLogoutWarningDialog = false,
                                isLinkingAccount = true // Use as loading state
                            )
                        )
                    )
                }

                val userId = getCurrentUserId()

                // Step 1: Delete buyer profile from Firebase
                if (userId != null) {
                    profileRepository.deleteBuyerProfile(userId)
                    println("🗑️ Deleted buyer profile for: $userId")
                }

                // Step 2: Clear local basket
                basketRepository.clearBasket()
                println("🗑️ Cleared local basket")

                // Step 3: Delete Firebase Auth account (this also signs out)
                authRepository.deleteAccount()
                    .onSuccess { println("🔐 Deleted Firebase Auth account") }
                    .onFailure { e -> println("⚠️ Failed to delete auth account: ${e.message}") }

                // Step 4: Clear all local state
                _state.update { current ->
                    current.copy(
                        common = current.common.copy(
                            user = UserState.Guest,
                            basket = BasketState(),
                            triggerGoogleSignOut = true,
                            requiresLogin = true // Show login screen
                        ),
                        screens = current.screens.copy(
                            customerProfile = CustomerProfileScreenState(),
                            mainScreen = current.screens.mainScreen.copy(
                                favouriteArticles = emptyList()
                            )
                        )
                    )
                }

                showSnackbar(getString(Res.string.logout_guest_success), SnackbarType.INFO)

            } catch (e: Exception) {
                println("❌ Error during guest logout: ${e.message}")
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            customerProfile = current.screens.customerProfile.copy(
                                isLinkingAccount = false
                            )
                        )
                    )
                }
                showSnackbar(getString(Res.string.logout_error, e.message ?: "Unknown error"), SnackbarType.ERROR)
            }
        }
    }

    /**
     * Link anonymous account with Google credentials.
     * Triggers platform-specific Google Sign-In for linking.
     */
    private fun linkWithGoogle() {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            isLinkingAccount = true,
                            linkAccountError = null
                        )
                    )
                )
            }

            // Trigger Google Sign-In for linking
            // The platform layer will handle this and call back with the ID token
            _state.update { current ->
                current.copy(
                    common = current.common.copy(triggerGoogleSignIn = true),
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            showLinkAccountDialog = false
                        )
                    )
                )
            }
        }
    }

    /**
     * Link anonymous account with email/password credentials.
     */
    private fun linkWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            isLinkingAccount = true,
                            linkAccountError = null
                        )
                    )
                )
            }

            // TODO: Implement email/password linking via AuthRepository
            // authRepository.linkWithEmail(email, password)
            //     .onSuccess { ... }
            //     .onFailure { ... }

            // For now, just navigate to register screen
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        customerProfile = current.screens.customerProfile.copy(
                            isLinkingAccount = false,
                            showLinkAccountDialog = false
                        )
                    )
                )
            }
            navigateTo(NavRoutes.Register)
        }
    }

    /**
     * Confirm account deletion for authenticated users.
     * - Future orders (pickup date > now) are CANCELLED
     * - Past orders are kept for seller records
     * - Buyer profile is deleted
     * - Firebase Auth account is deleted
     */
    private fun confirmDeleteAccount() {
        viewModelScope.launch {
            try {
                // Set loading state (keep dialog visible to show progress)
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            customerProfile = current.screens.customerProfile.copy(
                                isLoading = true
                            )
                        )
                    )
                }

                val userId = getCurrentUserId()
                var cancelledOrderCount = 0

                if (userId != null) {
                    // Get buyer profile to access placedOrderIds
                    val profileResult = profileRepository.getBuyerProfile()
                    val buyerProfile = profileResult.getOrNull()

                    if (buyerProfile != null) {
                        // Clear user data: cancel future orders, keep past orders, delete profile
                        val cleanUpResult = profileRepository.clearUserData(
                            sellerId = BASKET_SELLER_ID,
                            buyerProfile = buyerProfile
                        )

                        cleanUpResult.onSuccess { result ->
                            cancelledOrderCount = result.cancelledOrders.size
                            println("🔐 confirmDeleteAccount: Cleanup complete - cancelled=${result.cancelledOrders.size}, skipped=${result.skippedOrders.size}, profileDeleted=${result.profileDeleted}")
                            if (result.errors.isNotEmpty()) {
                                println("⚠️ confirmDeleteAccount: Cleanup had errors: ${result.errors}")
                            }
                        }.onFailure { e ->
                            println("⚠️ confirmDeleteAccount: Cleanup failed - ${e.message}")
                        }
                    } else {
                        // No profile found, just delete auth
                        println("🔐 confirmDeleteAccount: No buyer profile found, proceeding with auth deletion only")
                    }
                }

                // Clear local basket
                basketRepository.clearBasket()

                // Delete Firebase Auth account (this also signs out)
                authRepository.deleteAccount()
                    .onSuccess { println("🔐 Deleted Firebase Auth account for authenticated user") }
                    .onFailure { e -> println("⚠️ Failed to delete auth account: ${e.message}") }

                // Reset state and hide dialog
                _state.update { current ->
                    current.copy(
                        common = current.common.copy(
                            user = UserState.Guest,
                            basket = BasketState(),
                            triggerGoogleSignOut = true,
                            requiresLogin = true
                        ),
                        screens = current.screens.copy(
                            customerProfile = CustomerProfileScreenState()
                        )
                    )
                }

                // Show success message with cancelled order count
                val message = if (cancelledOrderCount > 0) {
                    getString(Res.string.account_deleted_with_cancellations, cancelledOrderCount)
                } else {
                    getString(Res.string.account_deleted_success)
                }
                showSnackbar(message, SnackbarType.INFO)

            } catch (e: Exception) {
                // Hide loading and dialog on error
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            customerProfile = current.screens.customerProfile.copy(
                                isLoading = false,
                                showDeleteAccountDialog = false
                            )
                        )
                    )
                }
                showSnackbar("Fehler beim Löschen: ${e.message}", SnackbarType.ERROR)
            }
        }
    }

    private fun getCurrentUserId(): String? {
        return when (val user = _state.value.common.user) {
            is UserState.LoggedIn -> user.id
            else -> null
        }
    }

    private fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            // Clear any previous errors and set loading state
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        auth = current.screens.auth.copy(
                            isLoading = true,
                            error = null,
                            isSuccess = false
                        )
                    )
                )
            }

            // Attempt sign up
            authRepository.signUpWithEmail(email, password)
                .onSuccess { userId ->
                    // Update user state with name
                    _state.update { current ->
                        current.copy(
                            common = current.common.copy(
                                user = UserState.LoggedIn(
                                    id = userId,
                                    name = name,
                                    email = email,
                                    role = UserRole.CUSTOMER // Default to customer for new registrations
                                )
                            ),
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    error = null,
                                    isSuccess = true
                                )
                            )
                        )
                    }

                    // Show success message
                    showSnackbar(getString(Res.string.snackbar_account_created), SnackbarType.SUCCESS)

                    // Navigate to login after a short delay
                    delay(1500)
                    navigateTo(NavRoutes.Login)
                }
                .onFailure { error ->
                    // Provide user-friendly error messages
                    val errorMessage = when {
                        error.message?.contains("email-already-in-use") == true ->
                            getString(Res.string.error_email_in_use)
                        error.message?.contains("weak-password") == true ->
                            getString(Res.string.error_weak_password)
                        error.message?.contains("invalid-email") == true ->
                            getString(Res.string.error_email_invalid)
                        error.message?.contains("network") == true ->
                            getString(Res.string.error_no_internet)
                        else ->
                            getString(Res.string.error_registration_failed)
                    }

                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    error = errorMessage,
                                    isSuccess = false
                                )
                            )
                        )
                    }

                    showSnackbar(errorMessage, SnackbarType.ERROR)
                }
        }
    }

    private fun updateProfile(profile: UserProfile) {
        // TODO: Implement update profile
    }

    /**
     * Load user profile during initialization
     * Loads the profile data for the currently logged-in user
     */
    private suspend fun loadUserProfile() {
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
     * Finds the most recent order in EDITABLE state for the user
     */
    private suspend fun loadCurrentOrder() {
        try {
            println("📦 loadCurrentOrder: Loading current order...")

            // Get profile to access placedOrderIds
            val profileResult = profileRepository.getBuyerProfile()

            profileResult.onSuccess { profile ->
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

    private fun loadProfile() {
        // Redirect to loadCustomerProfile for now
        loadCustomerProfile()
    }

    private fun loadCustomerProfile() {
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

    private fun loadOrderHistory() {
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

    private fun refreshCustomerProfile() {
        loadCustomerProfile()
        loadOrderHistory()
    }

    private fun updateProfileField(field: String, value: String) {
        // TODO: Implement update profile field
    }

    private fun saveProfile() {
        // Legacy method - use saveBuyerProfile instead
    }

    private fun saveBuyerProfile(displayName: String, email: String, phone: String) {
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

    private fun cancelProfileEdit() {
        // TODO: Implement cancel profile edit
    }

    private fun viewProductDetail(productId: String) {
        // TODO: Implement view product detail
    }

    private fun applyPromoCode(code: String) {
        // TODO: Implement apply promo code
    }

    private fun startCheckout() {
        // TODO: Implement start checkout
    }

    private fun loadOrders() {
        // TODO: Implement load orders
    }

    private fun viewOrderDetail(orderId: String) {
        // TODO: Implement view order detail
    }

    private fun placeOrder(checkoutData: CheckoutData) {
        // TODO: Implement place order
    }

    private fun cancelOrder(orderId: String) {
        // TODO: Implement cancel order
    }

    private fun search(query: String) {
        // TODO: Implement search
    }

    private fun clearSearch() {
        // TODO: Implement clear search
    }

    private fun addToSearchHistory(query: String) {
        // TODO: Implement add to search history
    }

    private fun applyFilter(key: String, value: FilterValue) {
        // TODO: Implement apply filter
    }

    private fun removeFilter(key: String) {
        // TODO: Implement remove filter
    }

    private fun clearFilters() {
        // TODO: Implement clear filters
    }

    private fun saveFilter(name: String) {
        // TODO: Implement save filter
    }

    private fun loadSavedFilter(filterId: String) {
        // TODO: Implement load saved filter
    }

    // ===== Main Screen Implementation Methods =====

    private fun selectMainScreenArticle(article: Article) {
        // Check if this product is already in the basket
        val basketItems = basketRepository.observeBasket().value

        println("🎯 selectMainScreenArticle: Looking for article.id=${article.id} in ${basketItems.size} basket items")
        basketItems.forEach { item ->
            println("🎯   Basket item: id='${item.id}', productId='${item.productId}', name='${item.productName}', qty=${item.amountCount}")
        }

        val existingItem = basketItems.find { it.id == article.id || it.productId == article.id }

        // If it exists, pre-populate the quantity with the existing amount
        val initialQuantity = existingItem?.amountCount ?: 0.0

        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    mainScreen = current.screens.mainScreen.copy(
                        selectedArticle = article,
                        selectedQuantity = initialQuantity
                    )
                )
            )
        }

        println("🎯 UnifiedAppViewModel.selectMainScreenArticle: Selected ${article.productName}, existingItem=${existingItem != null}, quantity: $initialQuantity")
    }

    private fun updateMainScreenQuantity(quantity: Double) {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    mainScreen = current.screens.mainScreen.copy(
                        selectedQuantity = quantity.coerceAtLeast(0.0)
                    )
                )
            )
        }
    }

    private fun updateMainScreenQuantityFromText(text: String) {
        val quantity = text.replace(",", ".").toDoubleOrNull() ?: 0.0
        updateMainScreenQuantity(quantity)
    }

    private fun addMainScreenToCart() {
        val selectedArticle = _state.value.screens.mainScreen.selectedArticle ?: return
        val quantity = _state.value.screens.mainScreen.selectedQuantity

        if (quantity <= 0.0) {
            // If quantity is 0, remove from basket if it exists
            viewModelScope.launch {
                basketRepository.removeItem(selectedArticle.id)
            }
            return
        }

        // Check if item already exists in basket
        val basketItems = basketRepository.observeBasket().value
        val existingItem = basketItems.find { it.id == selectedArticle.id || it.productId == selectedArticle.id }

        if (existingItem != null) {
            // Update existing item quantity
            viewModelScope.launch {
                basketRepository.updateQuantity(selectedArticle.id, quantity)
            }
            println("🛒 UnifiedAppViewModel.addMainScreenToCart: Updated ${selectedArticle.productName} to ${quantity} ${selectedArticle.unit}")
        } else {
            // Create new OrderedProduct from selected article and quantity
            val orderedProduct = OrderedProduct(
                id = "", // Will be generated when order is placed
                productId = selectedArticle.id,
                productName = selectedArticle.productName,
                unit = selectedArticle.unit,
                price = selectedArticle.price,
                amount = quantity.toString(),
                amountCount = quantity,
                piecesCount = if (selectedArticle.unit.lowercase() in listOf("kg", "g")) {
                    (quantity / selectedArticle.weightPerPiece).toInt()
                } else {
                    quantity.toInt()
                }
            )

            // Add to basket via BasketRepository
            viewModelScope.launch {
                basketRepository.addItem(orderedProduct)
            }
            println("🛒 UnifiedAppViewModel.addMainScreenToCart: Added ${selectedArticle.productName} (${quantity} ${selectedArticle.unit}) to basket")
        }
    }

    private fun removeMainScreenFromBasket() {
        val selectedArticle = _state.value.screens.mainScreen.selectedArticle ?: return

        viewModelScope.launch {
            basketRepository.removeItem(selectedArticle.id)
            // Reset quantity to 0 after removing
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        mainScreen = current.screens.mainScreen.copy(selectedQuantity = 0.0)
                    )
                )
            }
        }

        println("🗑️ UnifiedAppViewModel.removeMainScreenFromBasket: Removed ${selectedArticle.productName} from basket")
    }

    private fun toggleMainScreenFavourite(articleId: String) {
        viewModelScope.launch {
            try {
                println("⭐ UnifiedAppViewModel.toggleMainScreenFavourite: START - articleId=$articleId")

                // Get current buyer profile
                val profileResult = profileRepository.getBuyerProfile()
                val currentProfile = profileResult.getOrNull()

                if (currentProfile == null) {
                    println("❌ UnifiedAppViewModel.toggleMainScreenFavourite: No buyer profile found")
                    return@launch
                }

                // Check if article is already a favourite
                val isFavourite = currentProfile.favouriteArticles.contains(articleId)
                val updatedFavourites = if (isFavourite) {
                    // Remove from favourites
                    currentProfile.favouriteArticles.filter { it != articleId }
                } else {
                    // Add to favourites
                    currentProfile.favouriteArticles + articleId
                }

                println("⭐ UnifiedAppViewModel.toggleMainScreenFavourite: ${if (isFavourite) "Removing" else "Adding"} article")

                // Update profile with new favourites list
                val updatedProfile = currentProfile.copy(favouriteArticles = updatedFavourites)
                val saveResult = profileRepository.saveBuyerProfile(updatedProfile)

                saveResult.onSuccess {
                    println("✅ UnifiedAppViewModel.toggleMainScreenFavourite: Successfully updated favourites")
                    // Update local state immediately for instant UI feedback
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                mainScreen = current.screens.mainScreen.copy(
                                    favouriteArticles = updatedFavourites
                                )
                            )
                        )
                    }
                }.onFailure { error ->
                    println("❌ UnifiedAppViewModel.toggleMainScreenFavourite: Failed to save - ${error.message}")
                }

            } catch (e: Exception) {
                println("❌ UnifiedAppViewModel.toggleMainScreenFavourite: Exception - ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun refreshMainScreen() {
        loadMainScreenArticles()
    }

    /**
     * Load articles for MainScreen
     */
    private fun loadMainScreenArticles() {
        viewModelScope.launch {
            println("🎬 UnifiedAppViewModel.loadMainScreenArticles: START")

            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        mainScreen = current.screens.mainScreen.copy(
                            isLoading = true,
                            error = null
                        )
                    )
                )
            }
            println("🎬 UnifiedAppViewModel.loadMainScreenArticles: Set loading state to true")

            // Load articles for a specific seller or use empty string for current user
            val sellerId = "" // Empty string for current authenticated user
            println("🎬 UnifiedAppViewModel.loadMainScreenArticles: Calling articleRepository.getArticles(sellerId='$sellerId')")

            articleRepository.getArticles(sellerId)
                .catch { e ->
                    println("❌ UnifiedAppViewModel.loadMainScreenArticles: ERROR - ${e.message}")
                    e.printStackTrace()
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                mainScreen = current.screens.mainScreen.copy(
                                    isLoading = false,
                                    error = ErrorState(
                                        message = e.message ?: "Failed to load articles",
                                        type = ErrorType.NETWORK
                                    )
                                )
                            )
                        )
                    }
                }
                .collect { article ->
                    println("🎬 UnifiedAppViewModel.loadMainScreenArticles: Received article event - mode=${article.mode}, id=${article.id}, name=${article.productName}, available=${article.available}")

                    // Update state atomically to avoid race conditions
                    _state.update { current ->
                        val currentArticles = current.screens.mainScreen.articles.toMutableList()
                        val beforeCount = currentArticles.size

                        when (article.mode) {
                            Article.MODE_ADDED -> {
                                // Check if article already exists to avoid duplicates
                                val existingIndex = currentArticles.indexOfFirst { it.id == article.id }
                                if (existingIndex >= 0) {
                                    currentArticles[existingIndex] = article
                                    println("🎬 UnifiedAppViewModel.loadMainScreenArticles: UPDATED existing article '${article.productName}' at index $existingIndex")
                                } else {
                                    currentArticles.add(article)
                                    println("🎬 UnifiedAppViewModel.loadMainScreenArticles: ADDED article '${article.productName}' (id=${article.id})")
                                }
                            }
                            Article.MODE_CHANGED -> {
                                val index = currentArticles.indexOfFirst { it.id == article.id }
                                if (index >= 0) {
                                    currentArticles[index] = article
                                    println("🎬 UnifiedAppViewModel.loadMainScreenArticles: CHANGED article '${article.productName}' at index $index, available=${article.available}")
                                } else {
                                    // Article wasn't in list (maybe was filtered before), add it now
                                    currentArticles.add(article)
                                    println("🎬 UnifiedAppViewModel.loadMainScreenArticles: CHANGED but not found, ADDED article '${article.productName}' (id=${article.id}), available=${article.available}")
                                }
                            }
                            Article.MODE_REMOVED -> {
                                currentArticles.removeAll { it.id == article.id }
                                println("🎬 UnifiedAppViewModel.loadMainScreenArticles: REMOVED article '${article.productName}' (id=${article.id})")
                            }
                            // MODE_MOVED typically doesn't need special handling
                        }

                        val afterCount = currentArticles.size
                        println("🎬 UnifiedAppViewModel.loadMainScreenArticles: Article count: $beforeCount → $afterCount")

                        current.copy(
                            screens = current.screens.copy(
                                mainScreen = current.screens.mainScreen.copy(
                                    isLoading = false,
                                    articles = currentArticles,
                                    error = null
                                )
                            )
                        )
                    }

                    // Auto-select first article if none selected (using proper selection logic)
                    val currentSelectedArticle = _state.value.screens.mainScreen.selectedArticle
                    val currentArticles = _state.value.screens.mainScreen.articles
                    if (currentSelectedArticle == null && currentArticles.isNotEmpty()) {
                        val firstArticle = currentArticles.first()
                        println("🎬 UnifiedAppViewModel.loadMainScreenArticles: Auto-selecting first article: ${firstArticle.productName}")
                        selectMainScreenArticle(firstArticle)  // ✅ Use proper selection method
                    }
                }
        }
    }

    /**
     * Observe basket to update MainScreen cart item count and selected quantity
     */
    private fun observeMainScreenBasket() {
        viewModelScope.launch {
            basketRepository.observeBasket().collect { basketItems ->
                _state.update { current ->
                    // Check if currently selected article is in the basket
                    val selectedArticle = current.screens.mainScreen.selectedArticle
                    val existingItem = if (selectedArticle != null) {
                        basketItems.find { it.id == selectedArticle.id || it.productId == selectedArticle.id }
                    } else null

                    // Update quantity if the selected article is in basket
                    val updatedQuantity = existingItem?.amountCount
                        ?: current.screens.mainScreen.selectedQuantity

                    current.copy(
                        screens = current.screens.copy(
                            mainScreen = current.screens.mainScreen.copy(
                                cartItemCount = basketItems.size,
                                basketItems = basketItems,
                                selectedQuantity = updatedQuantity
                            )
                        )
                    )
                }
            }
        }
    }

    /**
     * Observe buyer profile to update MainScreen favourite articles
     * and reload order history when placedOrderIds changes
     */
    private fun observeMainScreenBuyerProfile() {
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

    // ===== Basket Screen Handler and Methods =====

    // Seller ID constant
    private val BASKET_SELLER_ID = GitLiveArticleRepository.DEFAULT_SELLER_ID

    private fun handleBasketScreenAction(action: UnifiedBasketScreenAction) {
        when (action) {
            is UnifiedBasketScreenAction.AddItem -> basketScreenAddItem(action.item)
            is UnifiedBasketScreenAction.RemoveItem -> basketScreenRemoveItem(action.productId)
            is UnifiedBasketScreenAction.UpdateItemQuantity -> basketScreenUpdateQuantity(action.productId, action.newQuantity)
            UnifiedBasketScreenAction.ClearBasket -> basketScreenClearBasket()
            UnifiedBasketScreenAction.Checkout -> basketScreenCheckout()
            is UnifiedBasketScreenAction.LoadOrder -> basketScreenLoadOrder(action.orderId, action.date, forceLoad = true)
            UnifiedBasketScreenAction.UpdateOrder -> basketScreenUpdateOrder()
            UnifiedBasketScreenAction.EnableEditing -> basketScreenEnableEditing()
            UnifiedBasketScreenAction.ResetOrderState -> basketScreenResetOrderState()
            UnifiedBasketScreenAction.ShowDatePicker -> basketScreenShowDatePicker()
            UnifiedBasketScreenAction.HideDatePicker -> basketScreenHideDatePicker()
            is UnifiedBasketScreenAction.SelectPickupDate -> basketScreenSelectPickupDate(action.date)
            UnifiedBasketScreenAction.LoadAvailableDates -> basketScreenLoadAvailableDates()
            UnifiedBasketScreenAction.CancelOrder -> basketScreenCancelOrder()
            UnifiedBasketScreenAction.ShowReorderDatePicker -> basketScreenShowReorderDatePicker()
            UnifiedBasketScreenAction.HideReorderDatePicker -> basketScreenHideReorderDatePicker()
            is UnifiedBasketScreenAction.ReorderWithNewDate -> basketScreenReorderWithNewDate(action.newPickupDate, action.currentArticles)
            UnifiedBasketScreenAction.HideMergeDialog -> basketScreenHideMergeDialog()
            is UnifiedBasketScreenAction.ResolveMergeConflict -> basketScreenResolveMergeConflict(action.productId, action.resolution)
            UnifiedBasketScreenAction.ConfirmMerge -> basketScreenConfirmMerge()
        }
    }

    /**
     * Initialize basket screen observers
     * Called from init block
     */
    fun initializeBasketScreen() {
        observeBasketScreenItems()
        basketScreenLoadAvailableDates()
        basketScreenLoadMostRecentEditableOrder()
    }

    private fun observeBasketScreenItems() {
        viewModelScope.launch {
            basketRepository.observeBasket().collect { items ->
                val hasChanges = basketScreenCheckIfHasChanges(items, _state.value.screens.basketScreen.originalOrderItems)
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                items = items,
                                total = basketRepository.getTotal(),
                                hasChanges = hasChanges
                            )
                        )
                    )
                }
            }
        }
    }

    private fun basketScreenCheckIfHasChanges(currentItems: List<OrderedProduct>, originalItems: List<OrderedProduct>): Boolean {
        if (originalItems.isEmpty() && currentItems.isEmpty()) return false
        if (originalItems.isEmpty()) return true
        if (currentItems.size != originalItems.size) return true

        currentItems.forEach { currentItem ->
            val originalItem = originalItems.find { it.productId == currentItem.productId }
            if (originalItem == null) return true
            if (originalItem.amountCount != currentItem.amountCount) return true
        }

        originalItems.forEach { originalItem ->
            val currentItem = currentItems.find { it.productId == originalItem.productId }
            if (currentItem == null) return true
        }

        return false
    }

    private fun basketScreenLoadMostRecentEditableOrder() {
        viewModelScope.launch {
            try {
                println("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: START")

                val loadedOrderInfo = basketRepository.getLoadedOrderInfo()
                if (loadedOrderInfo != null) {
                    val (orderId, orderDate) = loadedOrderInfo
                    println("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Order already loaded - orderId=$orderId, date=$orderDate")

                    val orderPath = "orders/$BASKET_SELLER_ID/$orderDate/$orderId"
                    val result = orderRepository.loadOrder(BASKET_SELLER_ID, orderId, orderPath)
                    result.onSuccess { order ->
                        // Check if order is still editable (not cancelled/completed)
                        if (order.status == com.together.newverse.domain.model.OrderStatus.CANCELLED ||
                            order.status == com.together.newverse.domain.model.OrderStatus.COMPLETED) {
                            println("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Order is finalized, clearing basket")
                            basketRepository.clearBasket()
                            return@onSuccess
                        }

                        val canEdit = OrderDateUtils.canEditOrder(
                            Instant.fromEpochMilliseconds(order.pickUpDate)
                        )
                        val currentBasketItems = basketRepository.observeBasket().value
                        val hasChanges = basketScreenCheckIfHasChanges(currentBasketItems, order.articles)

                        _state.update { current ->
                            current.copy(
                                screens = current.screens.copy(
                                    basketScreen = current.screens.basketScreen.copy(
                                        orderId = orderId,
                                        orderDate = orderDate,
                                        pickupDate = order.pickUpDate,
                                        createdDate = order.createdDate,
                                        isEditMode = false,
                                        canEdit = canEdit,
                                        originalOrderItems = order.articles,
                                        hasChanges = hasChanges
                                    )
                                )
                            )
                        }
                    }.onFailure { error ->
                        println("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Failed to load order - ${error.message}")
                        // Clear loaded order info since loading failed
                        basketRepository.clearBasket()
                    }
                    return@launch
                }

                val profileResult = profileRepository.getBuyerProfile()
                val buyerProfile = profileResult.getOrNull()

                if (buyerProfile == null || buyerProfile.placedOrderIds.isEmpty()) {
                    println("🛒 BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: No buyer profile or orders")
                    return@launch
                }

                val orderResult = orderRepository.getOpenEditableOrder(BASKET_SELLER_ID, buyerProfile.placedOrderIds)
                val order = orderResult.getOrNull()

                if (order != null) {
                    val dateKey = basketScreenFormatDateKey(order.pickUpDate)
                    basketScreenLoadOrder(order.id, dateKey)
                }
            } catch (e: Exception) {
                println("❌ BuyAppViewModel.basketScreenLoadMostRecentEditableOrder: Error - ${e.message}")
            }
        }
    }

    private fun basketScreenAddItem(item: OrderedProduct) {
        viewModelScope.launch {
            basketRepository.addItem(item)
        }
    }

    private fun basketScreenRemoveItem(productId: String) {
        viewModelScope.launch {
            basketRepository.removeItem(productId)
        }
    }

    private fun basketScreenUpdateQuantity(productId: String, newQuantity: Double) {
        viewModelScope.launch {
            basketRepository.updateQuantity(productId, newQuantity)
        }
    }

    private fun basketScreenClearBasket() {
        viewModelScope.launch {
            basketRepository.clearBasket()
        }
    }

    private fun basketScreenCheckout() {
        viewModelScope.launch {
            println("🛒 BuyAppViewModel.basketScreenCheckout: START")
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            isCheckingOut = true,
                            orderSuccess = false,
                            orderError = null
                        )
                    )
                )
            }

            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderError = "Bitte melden Sie sich an, um eine Bestellung aufzugeben"
                                )
                            )
                        )
                    }
                    return@launch
                }

                val items = _state.value.screens.basketScreen.items
                if (items.isEmpty()) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderError = "Warenkorb ist leer"
                                )
                            )
                        )
                    }
                    return@launch
                }

                val buyerProfile = try {
                    profileRepository.getBuyerProfile().getOrNull() ?: BuyerProfile(
                        id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false
                    )
                } catch (e: Exception) {
                    BuyerProfile(id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false)
                }

                val selectedDate = _state.value.screens.basketScreen.selectedPickupDate
                if (selectedDate == null) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderError = "Bitte wählen Sie ein Abholdatum",
                                    showDatePicker = true
                                )
                            )
                        )
                    }
                    return@launch
                }

                val isDateValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(selectedDate))
                if (!isDateValid) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderError = "Gewähltes Datum ist nicht mehr verfügbar.",
                                    selectedPickupDate = null,
                                    showDatePicker = true
                                )
                            )
                        )
                    }
                    basketScreenLoadAvailableDates()
                    return@launch
                }

                val dateKey = basketScreenFormatDateKey(selectedDate)
                val existingOrderId = buyerProfile.placedOrderIds[dateKey]

                if (existingOrderId != null) {
                    val existingOrderPath = "orders/$BASKET_SELLER_ID/$dateKey/$existingOrderId"
                    val existingOrderResult = orderRepository.loadOrder(BASKET_SELLER_ID, existingOrderId, existingOrderPath)
                    existingOrderResult.onSuccess { existingOrder ->
                        val conflicts = basketScreenCalculateMergeConflicts(items, existingOrder.articles)
                        _state.update { current ->
                            current.copy(
                                screens = current.screens.copy(
                                    basketScreen = current.screens.basketScreen.copy(
                                        isCheckingOut = false,
                                        showMergeDialog = true,
                                        existingOrderForMerge = existingOrder,
                                        mergeConflicts = conflicts
                                    )
                                )
                            )
                        }
                    }.onFailure { error ->
                        _state.update { current ->
                            current.copy(
                                screens = current.screens.copy(
                                    basketScreen = current.screens.basketScreen.copy(
                                        isCheckingOut = false,
                                        orderError = "Bestellung konnte nicht geladen werden: ${error.message}"
                                    )
                                )
                            )
                        }
                    }
                    return@launch
                }

                val order = Order(
                    buyerProfile = buyerProfile,
                    createdDate = Clock.System.now().toEpochMilliseconds(),
                    sellerId = BASKET_SELLER_ID,
                    marketId = "",
                    pickUpDate = selectedDate,
                    message = "",
                    articles = items
                )

                val result = orderRepository.placeOrder(order)
                result.onSuccess { placedOrder ->
                    val placedDateKey = basketScreenFormatDateKey(placedOrder.pickUpDate)
                    basketScreenLoadOrder(placedOrder.id, placedDateKey)
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderSuccess = true
                                )
                            )
                        )
                    }
                }.onFailure { error ->
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderError = error.message ?: "Bestellung fehlgeschlagen"
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = e.message ?: "Ein Fehler ist aufgetreten"
                            )
                        )
                    )
                }
            }
        }
    }

    private fun basketScreenResetOrderState() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        orderSuccess = false,
                        orderError = null
                    )
                )
            )
        }
    }

    private fun basketScreenLoadOrder(orderId: String, date: String, forceLoad: Boolean = false) {
        viewModelScope.launch {
            println("🛒 BuyAppViewModel.basketScreenLoadOrder: START - orderId=$orderId, date=$date")
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            isLoadingOrder = true,
                            orderError = null
                        )
                    )
                )
            }

            try {
                val orderPath = "orders/$BASKET_SELLER_ID/$date/$orderId"
                val result = orderRepository.loadOrder(BASKET_SELLER_ID, orderId, orderPath)

                result.onSuccess { order ->
                    val canEdit = OrderDateUtils.canEditOrder(
                        Instant.fromEpochMilliseconds(order.pickUpDate)
                    )
                    val currentBasketItems = basketRepository.observeBasket().value

                    val shouldLoadOrderItems = forceLoad ||
                        currentBasketItems.isEmpty() ||
                        basketRepository.getLoadedOrderInfo()?.first != orderId

                    if (shouldLoadOrderItems) {
                        basketRepository.loadOrderItems(order.articles, orderId, date)
                    }

                    val finalBasketItems = basketRepository.observeBasket().value
                    val hasChanges = basketScreenCheckIfHasChanges(finalBasketItems, order.articles)

                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    orderId = orderId,
                                    orderDate = date,
                                    pickupDate = order.pickUpDate,
                                    createdDate = order.createdDate,
                                    isEditMode = false,
                                    canEdit = canEdit,
                                    isLoadingOrder = false,
                                    items = finalBasketItems,
                                    total = finalBasketItems.sumOf { it.price * it.amountCount },
                                    originalOrderItems = order.articles,
                                    hasChanges = hasChanges
                                )
                            )
                        )
                    }
                }.onFailure { error ->
                    // Clear loaded order info since loading failed
                    basketRepository.clearBasket()
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = BasketScreenState(
                                    isLoadingOrder = false,
                                    orderError = "Bestellung konnte nicht geladen werden: ${error.message}",
                                    availablePickupDates = current.screens.basketScreen.availablePickupDates
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                basketRepository.clearBasket()
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = BasketScreenState(
                                isLoadingOrder = false,
                                orderError = "Fehler beim Laden der Bestellung: ${e.message}",
                                availablePickupDates = current.screens.basketScreen.availablePickupDates
                            )
                        )
                    )
                }
            }
        }
    }

    private fun basketScreenEnableEditing() {
        val canEdit = _state.value.screens.basketScreen.canEdit
        if (canEdit) {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(isEditMode = true)
                    )
                )
            }
        } else {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            orderError = "Bestellung kann nicht mehr bearbeitet werden (Frist: Dienstag 23:59)"
                        )
                    )
                )
            }
        }
    }

    private fun basketScreenUpdateOrder() {
        viewModelScope.launch {
            println("🛒 BuyAppViewModel.basketScreenUpdateOrder: START")
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            isCheckingOut = true,
                            orderError = null
                        )
                    )
                )
            }

            try {
                val basketState = _state.value.screens.basketScreen
                val orderId = basketState.orderId
                val pickupDate = basketState.pickupDate
                val createdDate = basketState.createdDate

                if (orderId == null || pickupDate == null || createdDate == null) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderError = "Bestellinformationen fehlen"
                                )
                            )
                        )
                    }
                    return@launch
                }

                val canEdit = OrderDateUtils.canEditOrder(Instant.fromEpochMilliseconds(pickupDate))
                if (!canEdit) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderError = "Bearbeitungsfrist abgelaufen (Dienstag 23:59)"
                                )
                            )
                        )
                    }
                    return@launch
                }

                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderError = "Benutzer nicht angemeldet"
                                )
                            )
                        )
                    }
                    return@launch
                }

                val items = basketState.items
                if (items.isEmpty()) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderError = "Warenkorb ist leer"
                                )
                            )
                        )
                    }
                    return@launch
                }

                val buyerProfile = try {
                    profileRepository.getBuyerProfile().getOrNull() ?: BuyerProfile(
                        id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false
                    )
                } catch (e: Exception) {
                    BuyerProfile(id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false)
                }

                val updatedOrder = Order(
                    id = orderId,
                    buyerProfile = buyerProfile,
                    createdDate = createdDate,
                    sellerId = BASKET_SELLER_ID,
                    marketId = "",
                    pickUpDate = pickupDate,
                    message = "",
                    articles = items
                )

                val result = orderRepository.updateOrder(updatedOrder)
                result.onSuccess {
                    val currentItems = _state.value.screens.basketScreen.items
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderSuccess = true,
                                    isEditMode = false,
                                    originalOrderItems = currentItems,
                                    hasChanges = false
                                )
                            )
                        )
                    }
                }.onFailure { error ->
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCheckingOut = false,
                                    orderError = error.message ?: "Aktualisierung fehlgeschlagen"
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCheckingOut = false,
                                orderError = e.message ?: "Ein Fehler ist aufgetreten"
                            )
                        )
                    )
                }
            }
        }
    }

    private fun basketScreenCancelOrder() {
        viewModelScope.launch {
            println("🛒 BuyAppViewModel.basketScreenCancelOrder: START")
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            isCancelling = true,
                            orderError = null,
                            cancelSuccess = false
                        )
                    )
                )
            }

            try {
                val basketState = _state.value.screens.basketScreen
                val orderId = basketState.orderId
                val orderDate = basketState.orderDate
                val pickupDate = basketState.pickupDate

                if (orderId == null || orderDate == null || pickupDate == null) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCancelling = false,
                                    orderError = "Bestellinformationen fehlen"
                                )
                            )
                        )
                    }
                    return@launch
                }

                val canEdit = OrderDateUtils.canEditOrder(Instant.fromEpochMilliseconds(pickupDate))
                if (!canEdit) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isCancelling = false,
                                    orderError = "Stornierung nicht mehr möglich (Frist: Dienstag 23:59)"
                                )
                            )
                        )
                    }
                    return@launch
                }

                println("🛒 BuyAppViewModel.basketScreenCancelOrder: Calling orderRepository.cancelOrder")
                println("🛒 BuyAppViewModel.basketScreenCancelOrder: sellerId=$BASKET_SELLER_ID")
                println("🛒 BuyAppViewModel.basketScreenCancelOrder: orderDate=$orderDate")
                println("🛒 BuyAppViewModel.basketScreenCancelOrder: orderId=$orderId")
                val result = orderRepository.cancelOrder(BASKET_SELLER_ID, orderDate, orderId)

                if (result.isSuccess) {
                    println("🛒 BuyAppViewModel.basketScreenCancelOrder: Cancel SUCCESS, clearing basket")
                    // Clear basket in proper suspend context
                    basketRepository.clearBasket()
                    println("🛒 BuyAppViewModel.basketScreenCancelOrder: Basket cleared, updating state")

                    val availableDates = _state.value.screens.basketScreen.availablePickupDates
                    _state.update { current ->
                        current.copy(
                            common = current.common.copy(
                                basket = current.common.basket.copy(
                                    currentOrderId = null,
                                    currentOrderDate = null
                                )
                            ),
                            screens = current.screens.copy(
                                basketScreen = BasketScreenState(
                                    cancelSuccess = true,
                                    availablePickupDates = availableDates
                                )
                            )
                        )
                    }
                    println("🛒 BuyAppViewModel.basketScreenCancelOrder: State updated to empty basket with cancelSuccess=true")
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: "Stornierung fehlgeschlagen"
                    println("🛒 BuyAppViewModel.basketScreenCancelOrder: Cancel FAILED - $errorMessage")

                    // If order not found, the order was already cancelled/deleted - clear basket and show empty state
                    if (errorMessage.contains("not found", ignoreCase = true)) {
                        println("🛒 BuyAppViewModel.basketScreenCancelOrder: Order not found, clearing basket")
                        basketRepository.clearBasket()

                        // Also remove from buyer profile's placedOrderIds
                        try {
                            val profile = profileRepository.getBuyerProfile().getOrNull()
                            if (profile != null && profile.placedOrderIds.containsValue(orderId)) {
                                val updatedOrderIds = profile.placedOrderIds.filterValues { it != orderId }
                                val updatedProfile = profile.copy(placedOrderIds = updatedOrderIds)
                                profileRepository.saveBuyerProfile(updatedProfile)
                                println("🛒 BuyAppViewModel.basketScreenCancelOrder: Removed order from buyer profile")
                            }
                        } catch (e: Exception) {
                            println("🛒 BuyAppViewModel.basketScreenCancelOrder: Failed to update profile - ${e.message}")
                        }

                        val availableDates = _state.value.screens.basketScreen.availablePickupDates
                        _state.update { current ->
                            current.copy(
                                common = current.common.copy(
                                    basket = current.common.basket.copy(
                                        currentOrderId = null,
                                        currentOrderDate = null
                                    )
                                ),
                                screens = current.screens.copy(
                                    basketScreen = BasketScreenState(
                                        availablePickupDates = availableDates
                                    )
                                )
                            )
                        }
                    } else {
                        _state.update { current ->
                            current.copy(
                                screens = current.screens.copy(
                                    basketScreen = current.screens.basketScreen.copy(
                                        isCancelling = false,
                                        orderError = errorMessage
                                    )
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isCancelling = false,
                                orderError = e.message ?: "Ein Fehler ist aufgetreten"
                            )
                        )
                    )
                }
            }
        }
    }

    private fun basketScreenLoadAvailableDates() {
        println("📅 BuyAppViewModel.basketScreenLoadAvailableDates: START")
        val dates = OrderDateUtils.getAvailablePickupDates(count = 5)
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        availablePickupDates = dates.map { it.toEpochMilliseconds() }
                    )
                )
            )
        }
    }

    private fun basketScreenShowDatePicker() {
        if (_state.value.screens.basketScreen.availablePickupDates.isEmpty()) {
            basketScreenLoadAvailableDates()
        }
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(showDatePicker = true)
                )
            )
        }
    }

    private fun basketScreenHideDatePicker() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(showDatePicker = false)
                )
            )
        }
    }

    private fun basketScreenSelectPickupDate(date: Long) {
        val isValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(date))
        if (!isValid) {
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            orderError = "Gewähltes Datum ist nicht mehr verfügbar.",
                            selectedPickupDate = null,
                            showDatePicker = true
                        )
                    )
                )
            }
            basketScreenLoadAvailableDates()
            return
        }

        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        selectedPickupDate = date,
                        showDatePicker = false,
                        orderError = null
                    )
                )
            )
        }
    }

    private fun basketScreenShowReorderDatePicker() {
        if (_state.value.screens.basketScreen.availablePickupDates.isEmpty()) {
            basketScreenLoadAvailableDates()
        }
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(showReorderDatePicker = true)
                )
            )
        }
    }

    private fun basketScreenHideReorderDatePicker() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(showReorderDatePicker = false)
                )
            )
        }
    }

    private fun basketScreenReorderWithNewDate(newPickupDate: Long, currentArticles: List<Article>) {
        viewModelScope.launch {
            println("🛒 BuyAppViewModel.basketScreenReorderWithNewDate: START")
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(
                            isReordering = true,
                            showReorderDatePicker = false,
                            orderError = null,
                            reorderSuccess = false
                        )
                    )
                )
            }

            try {
                val isDateValid = OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(newPickupDate))
                if (!isDateValid) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isReordering = false,
                                    orderError = "Gewähltes Datum ist nicht mehr verfügbar.",
                                    showReorderDatePicker = true
                                )
                            )
                        )
                    }
                    basketScreenLoadAvailableDates()
                    return@launch
                }

                val currentItems = _state.value.screens.basketScreen.items
                if (currentItems.isEmpty()) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isReordering = false,
                                    orderError = "Keine Artikel zum Nachbestellen"
                                )
                            )
                        )
                    }
                    return@launch
                }

                val updatedItems = mutableListOf<OrderedProduct>()
                for (item in currentItems) {
                    val article = currentArticles.find { it.id == item.id }
                    if (article != null && article.available) {
                        updatedItems.add(item.copy(
                            price = article.price,
                            productName = article.productName,
                            unit = article.unit
                        ))
                    } else {
                        updatedItems.add(item)
                    }
                }

                basketRepository.clearBasket()
                for (item in updatedItems) {
                    basketRepository.addItem(item)
                }

                val newTotal = updatedItems.sumOf { it.price * it.amountCount }

                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                items = updatedItems,
                                total = newTotal,
                                orderId = null,
                                orderDate = null,
                                pickupDate = null,
                                createdDate = null,
                                isEditMode = false,
                                canEdit = true,
                                originalOrderItems = emptyList(),
                                hasChanges = false,
                                selectedPickupDate = newPickupDate,
                                isReordering = false,
                                reorderSuccess = true
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isReordering = false,
                                orderError = e.message ?: "Ein Fehler ist aufgetreten"
                            )
                        )
                    )
                }
            }
        }
    }

    private fun basketScreenCalculateMergeConflicts(
        newItems: List<OrderedProduct>,
        existingItems: List<OrderedProduct>
    ): List<MergeConflict> {
        val conflicts = mutableListOf<MergeConflict>()
        for (newItem in newItems) {
            val existingItem = existingItems.find { it.productId == newItem.productId }
            if (existingItem != null && existingItem.amountCount != newItem.amountCount) {
                conflicts.add(MergeConflict(
                    productId = newItem.productId,
                    productName = newItem.productName,
                    unit = newItem.unit,
                    existingQuantity = existingItem.amountCount,
                    newQuantity = newItem.amountCount,
                    existingPrice = existingItem.price,
                    newPrice = newItem.price,
                    resolution = MergeResolution.UNDECIDED
                ))
            }
        }
        return conflicts
    }

    private fun basketScreenHideMergeDialog() {
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(
                        showMergeDialog = false,
                        existingOrderForMerge = null,
                        mergeConflicts = emptyList()
                    )
                )
            )
        }
    }

    private fun basketScreenResolveMergeConflict(productId: String, resolution: MergeResolution) {
        val updatedConflicts = _state.value.screens.basketScreen.mergeConflicts.map { conflict ->
            if (conflict.productId == productId) conflict.copy(resolution = resolution) else conflict
        }
        _state.update { current ->
            current.copy(
                screens = current.screens.copy(
                    basketScreen = current.screens.basketScreen.copy(mergeConflicts = updatedConflicts)
                )
            )
        }
    }

    private fun basketScreenConfirmMerge() {
        viewModelScope.launch {
            println("🔀 BuyAppViewModel.basketScreenConfirmMerge: START")
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        basketScreen = current.screens.basketScreen.copy(isMerging = true)
                    )
                )
            }

            try {
                val basketState = _state.value.screens.basketScreen
                val existingOrder = basketState.existingOrderForMerge

                if (existingOrder == null) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isMerging = false,
                                    orderError = "Keine bestehende Bestellung zum Zusammenführen"
                                )
                            )
                        )
                    }
                    return@launch
                }

                val conflicts = basketState.mergeConflicts
                val newItems = basketState.items

                val mergedItems = mutableListOf<OrderedProduct>()
                val processedProductIds = mutableSetOf<String>()

                for (existingItem in existingOrder.articles) {
                    val conflict = conflicts.find { it.productId == existingItem.productId }
                    val newItem = newItems.find { it.productId == existingItem.productId }

                    val finalItem = when {
                        conflict != null -> when (conflict.resolution) {
                            MergeResolution.ADD -> existingItem.copy(
                                amountCount = existingItem.amountCount + (newItem?.amountCount ?: 0.0),
                                price = newItem?.price ?: existingItem.price
                            )
                            MergeResolution.KEEP_EXISTING -> existingItem
                            MergeResolution.USE_NEW -> newItem ?: existingItem
                            MergeResolution.UNDECIDED -> existingItem
                        }
                        newItem != null -> newItem
                        else -> existingItem
                    }
                    mergedItems.add(finalItem)
                    processedProductIds.add(existingItem.productId)
                }

                for (newItem in newItems) {
                    if (newItem.productId !in processedProductIds) {
                        mergedItems.add(newItem)
                    }
                }

                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isMerging = false,
                                    orderError = "Benutzer nicht angemeldet"
                                )
                            )
                        )
                    }
                    return@launch
                }

                val buyerProfile = try {
                    profileRepository.getBuyerProfile().getOrNull() ?: BuyerProfile(
                        id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false
                    )
                } catch (e: Exception) {
                    BuyerProfile(id = currentUserId, displayName = "Kunde", emailAddress = "", anonymous = false)
                }

                val mergedOrder = existingOrder.copy(
                    buyerProfile = buyerProfile,
                    articles = mergedItems
                )

                val result = orderRepository.updateOrder(mergedOrder)
                result.onSuccess {
                    val dateKey = basketScreenFormatDateKey(existingOrder.pickUpDate)
                    basketRepository.loadOrderItems(mergedItems, existingOrder.id, dateKey)

                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    showMergeDialog = false,
                                    existingOrderForMerge = null,
                                    mergeConflicts = emptyList(),
                                    isMerging = false,
                                    orderSuccess = true,
                                    orderId = existingOrder.id,
                                    orderDate = dateKey,
                                    pickupDate = existingOrder.pickUpDate,
                                    createdDate = existingOrder.createdDate,
                                    items = mergedItems,
                                    total = mergedItems.sumOf { it.price * it.amountCount },
                                    originalOrderItems = mergedItems,
                                    hasChanges = false
                                )
                            )
                        )
                    }
                }.onFailure { error ->
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                basketScreen = current.screens.basketScreen.copy(
                                    isMerging = false,
                                    orderError = "Zusammenführung fehlgeschlagen: ${error.message}"
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            basketScreen = current.screens.basketScreen.copy(
                                isMerging = false,
                                orderError = "Ein Fehler ist aufgetreten: ${e.message}"
                            )
                        )
                    )
                }
            }
        }
    }

    private fun basketScreenFormatDateKey(timestamp: Long): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val year = dateTime.year
        val month = dateTime.monthNumber.toString().padStart(2, '0')
        val day = dateTime.dayOfMonth.toString().padStart(2, '0')
        return "$year$month$day"
    }

    /**
     * Format timestamp to readable date string for BasketScreen
     */
    fun basketScreenFormatDate(timestamp: Long): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val day = dateTime.dayOfMonth.toString().padStart(2, '0')
        val month = dateTime.monthNumber.toString().padStart(2, '0')
        val year = dateTime.year
        return "$day.$month.$year"
    }

    /**
     * Check if order can be edited based on pickup date
     */
    fun basketScreenCanEditOrder(pickupDate: Long): Boolean {
        return OrderDateUtils.canEditOrder(Instant.fromEpochMilliseconds(pickupDate))
    }

    /**
     * Get days until pickup for BasketScreen
     */
    fun basketScreenGetDaysUntilPickup(pickupDate: Long): Long {
        val diff = pickupDate - Clock.System.now().toEpochMilliseconds()
        return diff / (24 * 60 * 60 * 1000)
    }
}