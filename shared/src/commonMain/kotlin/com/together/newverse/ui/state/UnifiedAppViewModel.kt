package com.together.newverse.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.ui.navigation.NavRoutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Unified ViewModel managing all app state
 *
 * This is the single ViewModel for the entire app, implementing
 * a Redux-like pattern with actions and reducers.
 */
class UnifiedAppViewModel(
    private val articleRepository: ArticleRepository,
    private val orderRepository: OrderRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(UnifiedAppState())
    val state: StateFlow<UnifiedAppState> = _state.asStateFlow()

    init {
        // Initialize app on startup
        initializeApp()

        // Observe auth state changes
        observeAuthState()
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
                                    role = UserRole.CUSTOMER // Default role
                                )
                            } else {
                                UserState.Guest
                            }
                        )
                    )
                }
            }
        }
    }

    // ===== Public Action Handlers =====

    /**
     * Main action dispatcher - all UI actions go through here
     */
    fun dispatch(action: UnifiedAppAction) {
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
            is UnifiedProductAction.CreateProduct -> createProduct(action.productData)
            is UnifiedProductAction.UpdateProduct -> updateProduct(action.productId, action.productData)
            is UnifiedProductAction.DeleteProduct -> deleteProduct(action.productId)
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

    // ===== Implementation Methods =====

    private fun initializeApp() {
        viewModelScope.launch {
            // Set app to initializing state
            _state.update { current ->
                current.copy(
                    meta = current.meta.copy(
                        isInitializing = true,
                        initializationStep = "Checking authentication..."
                    )
                )
            }

            // Step 1: Check authentication status FIRST
            checkAuthenticationStatus()

            // Step 2: Wait a bit to ensure auth state is propagated
            kotlinx.coroutines.delay(100)

            // Step 3: Load data based on auth status
            val isAuthenticated = _state.value.common.user is UserState.LoggedIn

            if (isAuthenticated) {
                // User is logged in, load user-specific data
                _state.update { current ->
                    current.copy(
                        meta = current.meta.copy(
                            initializationStep = "Loading user data..."
                        )
                    )
                }
                loadUserProfile()
                loadProducts()
            } else {
                // Guest mode, load public data only
                _state.update { current ->
                    current.copy(
                        meta = current.meta.copy(
                            initializationStep = "Loading products..."
                        )
                    )
                }
                loadProducts()
            }

            // Step 4: Mark initialization complete
            _state.update { current ->
                current.copy(
                    meta = current.meta.copy(
                        isInitializing = false,
                        isInitialized = true,
                        initializationStep = ""
                    )
                )
            }
        }
    }

    /**
     * Check if user has a persisted authentication session
     * This runs BEFORE any Firebase connections
     */
    private suspend fun checkAuthenticationStatus() {
        try {
            // Check for persisted authentication session
            authRepository.checkPersistedAuth().fold(
                onSuccess = { userId ->
                    if (userId != null) {
                        // User has valid persisted session
                        println("App Startup: Restored auth session for user: $userId")
                        _state.update { current ->
                            current.copy(
                                meta = current.meta.copy(
                                    initializationStep = "Loading user data..."
                                )
                            )
                        }
                    } else {
                        // No persisted session, user is guest
                        println("App Startup: No persisted auth, starting as guest")
                        _state.update { current ->
                            current.copy(
                                meta = current.meta.copy(
                                    initializationStep = "Loading products..."
                                )
                            )
                        }
                    }
                },
                onFailure = { error ->
                    // Failed to check auth, default to guest
                    println("App Startup: Failed to check auth - ${error.message}")
                    _state.update { current ->
                        current.copy(
                            meta = current.meta.copy(
                                initializationStep = "Starting in guest mode..."
                            )
                        )
                    }
                }
            )
        } catch (e: Exception) {
            // Error checking auth, proceed as guest
            println("App Startup: Exception checking auth - ${e.message}")
        }
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
            // Update loading state
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        products = current.screens.products.copy(isLoading = true, error = null)
                    )
                )
            }

            // Load products for demo - using empty sellerId for now
            // TODO: Get sellerId from user state
            articleRepository.getArticles("")
                .catch { e ->
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
                    // Handle individual article events
                    _state.update { current ->
                        val currentProducts = current.screens.products.items.toMutableList()

                        when (article.mode) {
                            Article.MODE_ADDED -> currentProducts.add(article)
                            Article.MODE_CHANGED -> {
                                val index = currentProducts.indexOfFirst { it.id == article.id }
                                if (index >= 0) currentProducts[index] = article
                            }
                            Article.MODE_REMOVED -> {
                                currentProducts.removeAll { it.id == article.id }
                            }
                        }

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
            val existingIndex = currentItems.indexOfFirst { it.productId == product.id }

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
            val newItems = current.common.basket.items.filter { it.productId != productId }
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
                if (item.productId == productId) {
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
                    // Success - auth state will be updated via observeAuthState()
                    showSnackbar("Signed in successfully", SnackbarType.SUCCESS)

                    // Set success state and clear loading
                    _state.update { current ->
                        current.copy(
                            screens = current.screens.copy(
                                auth = current.screens.auth.copy(
                                    isLoading = false,
                                    isSuccess = true,
                                    error = null
                                )
                            )
                        )
                    }

                    // Navigate to home after a short delay
                    kotlinx.coroutines.delay(500)
                    navigateTo(NavRoutes.Home)
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

    private fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
                .onSuccess {
                    // Clear basket and other user-specific data
                    _state.update { current ->
                        current.copy(
                            common = current.common.copy(
                                user = UserState.Guest,
                                basket = BasketState()
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

    private fun loadUserProfile() {
        // TODO: Implement load profile
    }

    private fun loadProfile() {
        // TODO: Implement load profile
    }

    private fun updateProfileField(field: String, value: String) {
        // TODO: Implement update profile field
    }

    private fun saveProfile() {
        // TODO: Implement save profile
    }

    private fun cancelProfileEdit() {
        // TODO: Implement cancel profile edit
    }

    private fun viewProductDetail(productId: String) {
        // TODO: Implement view product detail
    }

    private fun createProduct(productData: ProductFormData) {
        // TODO: Implement create product
    }

    private fun updateProduct(productId: String, productData: ProductFormData) {
        // TODO: Implement update product
    }

    private fun deleteProduct(productId: String) {
        // TODO: Implement delete product
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
}