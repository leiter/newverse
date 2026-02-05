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
import com.together.newverse.ui.state.buy.closeDrawer
import com.together.newverse.ui.state.buy.handleAccountAction
import com.together.newverse.ui.state.buy.handleBasketScreenAction
import com.together.newverse.ui.state.buy.handleMainScreenAction
import com.together.newverse.ui.state.buy.hideBottomSheet
import com.together.newverse.ui.state.buy.hideDialog
import com.together.newverse.ui.state.buy.hidePasswordResetDialog
import com.together.newverse.ui.state.buy.hideSnackbar
import com.together.newverse.ui.state.buy.initializeApp
import com.together.newverse.ui.state.buy.initializeBasketScreen
import com.together.newverse.ui.state.buy.loadCustomerProfile
import com.together.newverse.ui.state.buy.loadMainScreenArticles
import com.together.newverse.ui.state.buy.loadOrderHistory
import com.together.newverse.ui.state.buy.loadProfile
import com.together.newverse.ui.state.buy.continueAsGuest
import com.together.newverse.ui.state.buy.login
import com.together.newverse.ui.state.buy.loginWithGoogle
import com.together.newverse.ui.state.buy.loginWithTwitter
import com.together.newverse.ui.state.buy.logout
import com.together.newverse.ui.state.buy.navigateBack
import com.together.newverse.ui.state.buy.navigateTo
import com.together.newverse.ui.state.buy.observeAuthState
import com.together.newverse.ui.state.buy.observeMainScreenBasket
import com.together.newverse.ui.state.buy.observeMainScreenBuyerProfile
import com.together.newverse.ui.state.buy.openDrawer
import com.together.newverse.ui.state.buy.refreshCustomerProfile
import com.together.newverse.ui.state.buy.register
import com.together.newverse.ui.state.buy.saveBuyerProfile
import com.together.newverse.ui.state.buy.sendPasswordResetEmail
import com.together.newverse.ui.state.buy.setRefreshing
import com.together.newverse.ui.state.buy.showBottomSheet
import com.together.newverse.ui.state.buy.showDialog
import com.together.newverse.ui.state.buy.showPasswordResetDialog
import com.together.newverse.ui.state.buy.showSnackbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.snackbar_added_to_basket
import org.jetbrains.compose.resources.getString

/**
 * Buy flavor ViewModel - Extension-Based Architecture
 *
 * Core ViewModel for the buyer/customer app implementing a Redux-like pattern
 * with actions and reducers. Domain logic is organized using Kotlin extension
 * functions in separate files for maintainability and team collaboration.
 *
 * ## Architecture Overview
 *
 * This ViewModel has been refactored from a monolithic 3,697-line file into a
 * modular architecture:
 * - **Core ViewModel** (~728 lines): State management, action dispatching, interface implementations
 * - **Extension Functions** (~2,900 lines): Domain-specific logic organized by feature area
 *
 * ## Extension Function Organization
 *
 * Domain logic is extracted to extension functions in the `ui/state/buy/` package:
 *
 * | File | Domain | Functions | Lines |
 * |------|--------|-----------|-------|
 * | **BuyAppViewModelBasket.kt** | Basket/Checkout | 30 functions | ~1200 |
 * | **BuyAppViewModelAuth.kt** | Authentication & Account | 18 functions | ~600 |
 * | **BuyAppViewModelMainScreen.kt** | Product Browsing | 14 functions | ~400 |
 * | **BuyAppViewModelInitialization.kt** | App Startup | 8 functions | ~345 |
 * | **BuyAppViewModelProfile.kt** | User Profile | 9 functions | ~200 |
 * | **BuyAppViewModelUi.kt** | UI Management | 9 functions | ~100 |
 * | **BuyAppViewModelNavigation.kt** | Navigation & Drawer | 4 functions | ~50 |
 *
 * ### Extension Function Domains
 *
 * **Basket/Checkout** (`BuyAppViewModelBasket.kt`):
 * - Basket initialization and observers
 * - Item management (add, remove, update, clear)
 * - Checkout workflow
 * - Order loading and editing
 * - Pickup date selection
 * - Reorder functionality
 * - Merge conflict resolution
 *
 * **Authentication & Account** (`BuyAppViewModelAuth.kt`):
 * - Email/password login
 * - Social login (Google, Twitter)
 * - User registration
 * - Password reset
 * - Guest account linking
 * - Account deletion
 *
 * **Product Browsing** (`BuyAppViewModelMainScreen.kt`):
 * - Article selection and quantity management
 * - Add to cart functionality
 * - Favorites management
 * - Product filtering
 * - Edit lock guards (prevent editing locked orders)
 *
 * **App Startup** (`BuyAppViewModelInitialization.kt`):
 * - App initialization flow
 * - Authentication checking
 * - Guest sign-in fallback
 * - Profile and order loading
 * - Auth state observation
 *
 * **User Profile** (`BuyAppViewModelProfile.kt`):
 * - Profile loading and saving
 * - Order history
 * - Profile observation
 * - Favorite articles sync
 *
 * **UI Management** (`BuyAppViewModelUi.kt`):
 * - Snackbar display
 * - Dialog management
 * - Bottom sheet control
 * - Refresh state
 *
 * **Navigation** (`BuyAppViewModelNavigation.kt`):
 * - Screen navigation
 * - Back stack management
 * - Drawer control
 *
 * ## Extension Function Access Pattern
 *
 * Extension functions have `internal` visibility and can access:
 * - `_state: MutableStateFlow<UnifiedAppState>` - State updates
 * - All repository dependencies (article, order, profile, auth, basket)
 * - `viewModelScope` - Coroutine launching
 *
 * Example:
 * ```kotlin
 * // In BuyAppViewModelAuth.kt
 * internal fun BuyAppViewModel.login(email: String, password: String) {
 *     viewModelScope.launch {
 *         _state.update { current -> /* ... */ }
 *         authRepository.signInWithEmail(email, password)
 *     }
 * }
 * ```
 *
 * ## Core ViewModel Responsibilities
 *
 * This core file handles:
 * 1. **Dependency injection** - Repository dependencies via constructor
 * 2. **State management** - UnifiedAppState flow
 * 3. **Action dispatching** - Main `dispatch()` method routing actions
 * 4. **Interface implementations** - AppViewModel interface overrides
 * 5. **Initialization** - init block calling extension functions
 *
 * ## Benefits
 *
 * - **Maintainability**: Find functions by domain, not line number
 * - **Team Collaboration**: Multiple developers can work on different domains
 * - **Code Organization**: Clear separation of concerns
 * - **Testing**: Domain logic isolated for easier testing
 * - **Migration Path**: Easy transition to use case classes in Phase 2
 *
 * @see com.together.newverse.ui.state.buy Extension function packages
 */
class BuyAppViewModel(
    internal val articleRepository: ArticleRepository,
    internal val orderRepository: OrderRepository,
    internal val profileRepository: ProfileRepository,
    internal val authRepository: AuthRepository,
    internal val basketRepository: BasketRepository
) : ViewModel(), AppViewModel {

    /**
     * Internal state exposed for extension functions.
     * Extension functions are organized in the buy/ package by domain.
     */
    internal val _state = MutableStateFlow(UnifiedAppState())
    override val state: StateFlow<UnifiedAppState> = _state.asStateFlow()

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

    // Initialization functions moved to BuyAppViewModelInitialization.kt
    // - observeAuthState(), loadOpenOrderAfterAuth(), formatDateKey()

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
            is UnifiedUserAction.ContinueAsGuest -> continueAsGuest()
            is UnifiedUserAction.Register -> register(action.email, action.password, action.name)
            is UnifiedUserAction.UpdateProfile -> { /* Not implemented */ }
            is UnifiedUserAction.RequestPasswordReset -> sendPasswordResetEmail(action.email)
        }
    }

    private fun handleProductAction(action: UnifiedProductAction) {
        when (action) {
            is UnifiedProductAction.LoadProducts -> loadProducts()
            is UnifiedProductAction.RefreshProducts -> refreshProducts()
            is UnifiedProductAction.SelectProduct -> selectProduct(action.product)
            is UnifiedProductAction.ViewProductDetail -> { /* Not implemented */ }
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
            is UnifiedBasketAction.ApplyPromoCode -> { /* Not implemented */ }
            is UnifiedBasketAction.StartCheckout -> { /* Use basketScreenCheckout() instead */ }
        }
    }

    private fun handleOrderAction(action: UnifiedOrderAction) {
        when (action) {
            is UnifiedOrderAction.LoadOrders -> { /* Use loadOrderHistory() instead */ }
            is UnifiedOrderAction.ViewOrderDetail -> { /* Not implemented */ }
            is UnifiedOrderAction.PlaceOrder -> { /* Use basketScreenCheckout() instead */ }
            is UnifiedOrderAction.CancelOrder -> { /* Use basketScreenCancelOrder() instead */ }
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
            is UnifiedProfileAction.UpdateProfileField -> { /* Not implemented */ }
            is UnifiedProfileAction.SaveProfile -> { /* Use saveBuyerProfile() instead */ }
            is UnifiedProfileAction.CancelProfileEdit -> { /* Not implemented */ }
            is UnifiedProfileAction.LoadCustomerProfile -> loadCustomerProfile()
            is UnifiedProfileAction.LoadOrderHistory -> loadOrderHistory()
            is UnifiedProfileAction.RefreshCustomerProfile -> refreshCustomerProfile()
            is UnifiedProfileAction.SaveBuyerProfile -> saveBuyerProfile(action.displayName, action.email, action.phone)
        }
    }

    private fun handleSearchAction(action: UnifiedSearchAction) {
        when (action) {
            is UnifiedSearchAction.Search -> { /* Not implemented */ }
            is UnifiedSearchAction.ClearSearch -> { /* Not implemented */ }
            is UnifiedSearchAction.AddToSearchHistory -> { /* Not implemented */ }
        }
    }

    private fun handleFilterAction(action: UnifiedFilterAction) {
        when (action) {
            is UnifiedFilterAction.ApplyFilter -> { /* Not implemented */ }
            is UnifiedFilterAction.RemoveFilter -> { /* Not implemented */ }
            is UnifiedFilterAction.ClearFilters -> { /* Not implemented */ }
            is UnifiedFilterAction.SaveFilter -> { /* Not implemented */ }
            is UnifiedFilterAction.LoadSavedFilter -> { /* Not implemented */ }
        }
    }

    internal fun loadProducts() {
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

    // Override interface methods - kept in core ViewModel (not extracted)
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

    override fun resetGoogleSignOutTrigger() {
        _state.update { current ->
            current.copy(
                common = current.common.copy(triggerGoogleSignOut = false)
            )
        }
    }

}