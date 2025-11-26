package com.together.newverse.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.ui.navigation.NavRoutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

    init {
        // Initialize app on startup
        initializeApp()

        // Observe auth state changes
        observeAuthState()

        // Initialize MainScreen observers
        observeMainScreenBasket()
        observeMainScreenBuyerProfile()

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
                                currentProducts.add(article)
                                println("📦 UnifiedAppViewModel.loadProducts: ADDED article '${article.productName}' (id=${article.id})")
                            }
                            Article.MODE_CHANGED -> {
                                val index = currentProducts.indexOfFirst { it.id == article.id }
                                if (index >= 0) {
                                    currentProducts[index] = article
                                    println("📦 UnifiedAppViewModel.loadProducts: CHANGED article '${article.productName}' at index $index")
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
        showSnackbar("Added to basket", SnackbarType.SUCCESS)
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
                    showSnackbar("Signed in successfully", SnackbarType.SUCCESS)

                    println("🎯 Login complete - requiresLogin cleared, app will show main UI")
                }
                .onFailure { error ->
                    // Parse error message for user-friendly display
                    val errorMessage = when {
                        error.message?.contains("No account found", true) == true ->
                            "No account found with this email address"
                        error.message?.contains("Incorrect password", true) == true ->
                            "Incorrect password. Please try again"
                        error.message?.contains("Invalid email", true) == true ->
                            "Please enter a valid email address"
                        error.message?.contains("Network", true) == true ->
                            "Network error. Please check your connection"
                        error.message?.contains("too many", true) == true ->
                            "Too many failed attempts. Please try again later"
                        else -> error.message ?: "Sign in failed. Please try again"
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
                    showSnackbar("Signed out successfully", SnackbarType.SUCCESS)
                }
                .onFailure { error ->
                    showSnackbar(error.message ?: "Sign out failed", SnackbarType.ERROR)
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
                    showSnackbar("Account created successfully! Please check your email for verification.", SnackbarType.SUCCESS)

                    // Navigate to login after a short delay
                    delay(1500)
                    navigateTo(NavRoutes.Login)
                }
                .onFailure { error ->
                    // Provide user-friendly error messages
                    val errorMessage = when {
                        error.message?.contains("email-already-in-use") == true ->
                            "An account with this email already exists. Please sign in instead."
                        error.message?.contains("weak-password") == true ->
                            "Password is too weak. Please use at least 6 characters."
                        error.message?.contains("invalid-email") == true ->
                            "Invalid email address. Please check and try again."
                        error.message?.contains("network") == true ->
                            "Network error. Please check your connection and try again."
                        else ->
                            "Registration failed. Please try again."
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

                        // Check if order is editable (more than 3 days before pickup)
                        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        val threeDaysBeforePickup = order.pickUpDate - (3 * 24 * 60 * 60 * 1000)
                        val canEdit = now < threeDaysBeforePickup

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
                    println("🎬 UnifiedAppViewModel.loadMainScreenArticles: Received article event - mode=${article.mode}, id=${article.id}, name=${article.productName}")

                    val currentArticles = _state.value.screens.mainScreen.articles.toMutableList()
                    val beforeCount = currentArticles.size

                    when (article.mode) {
                        Article.MODE_ADDED -> {
                            currentArticles.add(article)
                            println("🎬 UnifiedAppViewModel.loadMainScreenArticles: ADDED article '${article.productName}' (id=${article.id})")
                        }
                        Article.MODE_CHANGED -> {
                            val index = currentArticles.indexOfFirst { it.id == article.id }
                            if (index >= 0) {
                                currentArticles[index] = article
                                println("🎬 UnifiedAppViewModel.loadMainScreenArticles: CHANGED article '${article.productName}' at index $index")
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

                    // Update articles list first
                    _state.update { current ->
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
                // Update favourite articles
                _state.update { current ->
                    current.copy(
                        screens = current.screens.copy(
                            mainScreen = current.screens.mainScreen.copy(
                                favouriteArticles = profile?.favouriteArticles ?: emptyList()
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
}