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
import com.together.newverse.ui.state.buy.loginWithApple
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
import com.together.newverse.ui.state.buy.setAuthMode
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
 * Core ViewModel for the buyer/customer app implementing a Redux-like pattern.
 * Uses BuyAppState (flattened, buyer-only state) instead of the legacy UnifiedAppState.
 *
 * Domain logic is organized using Kotlin extension functions in the buy/ package.
 */
class BuyAppViewModel(
    internal val articleRepository: ArticleRepository,
    internal val orderRepository: OrderRepository,
    internal val profileRepository: ProfileRepository,
    internal val authRepository: AuthRepository,
    internal val basketRepository: BasketRepository
) : ViewModel() {

    /**
     * Internal state exposed for extension functions.
     */
    internal val _state = MutableStateFlow(BuyAppState())
    val state: StateFlow<BuyAppState> = _state.asStateFlow()

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

    // ===== Public Action Handlers =====

    /**
     * Main action dispatcher - all UI actions go through here
     */
    fun dispatch(action: BuyAction) {
        when (action) {
            // Navigation actions
            is BuyNavigationAction -> handleNavigationAction(action)

            // User actions
            is BuyUserAction -> handleUserAction(action)

            // Product actions
            is BuyProductAction -> handleProductAction(action)

            // Basket actions
            is BuyBasketAction -> handleBasketAction(action)

            // Order actions
            is BuyOrderAction -> handleOrderAction(action)

            // UI actions
            is BuyUiAction -> handleUiAction(action)

            // Profile actions
            is BuyProfileAction -> handleProfileAction(action)

            // Main Screen actions
            is BuyMainScreenAction -> handleMainScreenAction(action)

            // Basket Screen actions (checkout/order workflow)
            is BuyBasketScreenAction -> handleBasketScreenAction(action)

            // Account management actions (guest linking, logout warning, etc.)
            is BuyAccountAction -> handleAccountAction(action)
        }
    }

    // ===== Action Handlers =====

    private fun handleNavigationAction(action: BuyNavigationAction) {
        when (action) {
            is BuyNavigationAction.NavigateTo -> navigateTo(action.route)
            is BuyNavigationAction.NavigateBack -> navigateBack()
            is BuyNavigationAction.OpenDrawer -> openDrawer()
            is BuyNavigationAction.CloseDrawer -> closeDrawer()
        }
    }

    private fun handleUserAction(action: BuyUserAction) {
        when (action) {
            is BuyUserAction.Login -> login(action.email, action.password)
            is BuyUserAction.LoginWithGoogle -> loginWithGoogle()
            is BuyUserAction.LoginWithTwitter -> loginWithTwitter()
            is BuyUserAction.LoginWithApple -> loginWithApple()
            is BuyUserAction.Logout -> logout()
            is BuyUserAction.ContinueAsGuest -> continueAsGuest()
            is BuyUserAction.Register -> register(action.email, action.password, action.name)
            is BuyUserAction.UpdateProfile -> { /* Not implemented */ }
            is BuyUserAction.RequestPasswordReset -> sendPasswordResetEmail(action.email)
        }
    }

    private fun handleProductAction(action: BuyProductAction) {
        when (action) {
            is BuyProductAction.LoadProducts -> loadProducts()
            is BuyProductAction.RefreshProducts -> refreshProducts()
            is BuyProductAction.SelectProduct -> selectProduct(action.product)
            is BuyProductAction.ViewProductDetail -> { /* Not implemented */ }
        }
    }

    private fun handleBasketAction(action: BuyBasketAction) {
        when (action) {
            is BuyBasketAction.AddToBasket -> addToBasket(action.product, action.quantity)
            is BuyBasketAction.RemoveFromBasket -> removeFromBasket(action.productId)
            is BuyBasketAction.UpdateQuantity -> updateBasketQuantity(action.productId, action.quantity)
            is BuyBasketAction.ClearBasket -> clearBasket()
            is BuyBasketAction.ApplyPromoCode -> { /* Not implemented */ }
            is BuyBasketAction.StartCheckout -> { /* Use basketScreenCheckout() instead */ }
        }
    }

    private fun handleOrderAction(action: BuyOrderAction) {
        when (action) {
            is BuyOrderAction.LoadOrders -> { /* Use loadOrderHistory() instead */ }
            is BuyOrderAction.ViewOrderDetail -> { /* Not implemented */ }
            is BuyOrderAction.PlaceOrder -> { /* Use basketScreenCheckout() instead */ }
            is BuyOrderAction.CancelOrder -> { /* Use basketScreenCancelOrder() instead */ }
        }
    }

    private fun handleUiAction(action: BuyUiAction) {
        when (action) {
            is BuyUiAction.ShowSnackbar -> showSnackbar(action.message, action.type)
            is BuyUiAction.HideSnackbar -> hideSnackbar()
            is BuyUiAction.ShowDialog -> showDialog(action.dialog)
            is BuyUiAction.HideDialog -> hideDialog()
            is BuyUiAction.ShowBottomSheet -> showBottomSheet(action.sheet)
            is BuyUiAction.HideBottomSheet -> hideBottomSheet()
            is BuyUiAction.SetRefreshing -> setRefreshing(action.isRefreshing)
            is BuyUiAction.ShowPasswordResetDialog -> showPasswordResetDialog()
            is BuyUiAction.HidePasswordResetDialog -> hidePasswordResetDialog()
            is BuyUiAction.SetAuthMode -> setAuthMode(action.mode)
        }
    }

    private fun handleProfileAction(action: BuyProfileAction) {
        when (action) {
            is BuyProfileAction.LoadProfile -> loadProfile()
            is BuyProfileAction.UpdateProfileField -> { /* Not implemented */ }
            is BuyProfileAction.SaveProfile -> { /* Use saveBuyerProfile() instead */ }
            is BuyProfileAction.CancelProfileEdit -> { /* Not implemented */ }
            is BuyProfileAction.LoadCustomerProfile -> loadCustomerProfile()
            is BuyProfileAction.LoadOrderHistory -> loadOrderHistory()
            is BuyProfileAction.RefreshCustomerProfile -> refreshCustomerProfile()
            is BuyProfileAction.SaveBuyerProfile -> saveBuyerProfile(action.displayName, action.email, action.phone)
        }
    }

    internal fun loadProducts() {
        viewModelScope.launch {
            println("📦 BuyAppViewModel.loadProducts: START")

            // Update loading state
            _state.update { current ->
                current.copy(
                    products = current.products.copy(isLoading = true, error = null)
                )
            }

            println("📦 BuyAppViewModel.loadProducts: Set loading state to true")

            val sellerId = ""
            println("📦 BuyAppViewModel.loadProducts: Calling articleRepository.getArticles(sellerId='$sellerId')")

            articleRepository.getArticles(sellerId)
                .catch { e ->
                    println("❌ BuyAppViewModel.loadProducts: ERROR - ${e.message}")
                    e.printStackTrace()
                    _state.update { current ->
                        current.copy(
                            products = current.products.copy(
                                isLoading = false,
                                error = ErrorState(
                                    message = e.message ?: "Failed to load products",
                                    type = ErrorType.NETWORK
                                )
                            )
                        )
                    }
                }
                .collect { article ->
                    println("📦 BuyAppViewModel.loadProducts: Received article event - mode=${article.mode}, id=${article.id}, name=${article.productName}")

                    _state.update { current ->
                        val currentProducts = current.products.items.toMutableList()
                        val beforeCount = currentProducts.size

                        when (article.mode) {
                            Article.MODE_ADDED -> {
                                val existingIndex = currentProducts.indexOfFirst { it.id == article.id }
                                if (existingIndex >= 0) {
                                    currentProducts[existingIndex] = article
                                    println("📦 BuyAppViewModel.loadProducts: UPDATED existing article '${article.productName}' at index $existingIndex")
                                } else {
                                    currentProducts.add(article)
                                    println("📦 BuyAppViewModel.loadProducts: ADDED article '${article.productName}' (id=${article.id})")
                                }
                            }
                            Article.MODE_CHANGED -> {
                                val index = currentProducts.indexOfFirst { it.id == article.id }
                                if (index >= 0) {
                                    currentProducts[index] = article
                                    println("📦 BuyAppViewModel.loadProducts: CHANGED article '${article.productName}' at index $index, available=${article.available}")
                                } else {
                                    currentProducts.add(article)
                                    println("📦 BuyAppViewModel.loadProducts: CHANGED but not found, ADDED article '${article.productName}' (id=${article.id}), available=${article.available}")
                                }
                            }
                            Article.MODE_REMOVED -> {
                                currentProducts.removeAll { it.id == article.id }
                                println("📦 BuyAppViewModel.loadProducts: REMOVED article '${article.productName}' (id=${article.id})")
                            }
                        }

                        val afterCount = currentProducts.size
                        println("📦 BuyAppViewModel.loadProducts: Product count: $beforeCount → $afterCount")

                        current.copy(
                            products = current.products.copy(
                                isLoading = false,
                                items = currentProducts,
                                error = null
                            )
                        )
                    }
                }
        }
    }

    private fun refreshProducts() {
        _state.update { current ->
            current.copy(
                ui = current.ui.copy(isRefreshing = true)
            )
        }
        loadProducts()
        _state.update { current ->
            current.copy(
                ui = current.ui.copy(isRefreshing = false)
            )
        }
    }

    private fun selectProduct(product: Article) {
        _state.update { current ->
            current.copy(
                products = current.products.copy(selectedItem = product)
            )
        }
    }

    private fun addToBasket(product: Article, quantity: Double) {
        _state.update { current ->
            val currentItems = current.basket.items.toMutableList()
            val existingIndex = currentItems.indexOfFirst { it.id == product.id || it.productId == product.id }

            if (existingIndex >= 0) {
                val existingItem = currentItems[existingIndex]
                currentItems[existingIndex] = existingItem.copy(
                    amountCount = existingItem.amountCount + quantity
                )
            } else {
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
                basket = current.basket.copy(
                    items = currentItems,
                    totalAmount = newTotal,
                    itemCount = newCount
                )
            )
        }

        viewModelScope.launch {
            showSnackbar(getString(Res.string.snackbar_added_to_basket), SnackbarType.SUCCESS)
        }
    }

    private fun removeFromBasket(productId: String) {
        _state.update { current ->
            val newItems = current.basket.items.filter { it.id != productId && it.productId != productId }
            val newTotal = newItems.sumOf { it.price * it.amountCount }
            val newCount = newItems.sumOf { it.piecesCount }

            current.copy(
                basket = current.basket.copy(
                    items = newItems,
                    totalAmount = newTotal,
                    itemCount = newCount
                )
            )
        }
    }

    private fun updateBasketQuantity(productId: String, quantity: Double) {
        _state.update { current ->
            val newItems = current.basket.items.map { item ->
                if (item.id == productId || item.productId == productId) {
                    item.copy(amountCount = quantity, piecesCount = quantity.toInt())
                } else item
            }
            val newTotal = newItems.sumOf { it.price * it.amountCount }
            val newCount = newItems.sumOf { it.piecesCount }

            current.copy(
                basket = current.basket.copy(
                    items = newItems,
                    totalAmount = newTotal,
                    itemCount = newCount
                )
            )
        }
    }

    private fun clearBasket() {
        _state.update { current ->
            current.copy(
                basket = BasketState()
            )
        }
    }

    // Override interface methods - kept in core ViewModel (not extracted)
    fun resetGoogleSignInTrigger() {
        println("🔐 BuyAppViewModel.resetGoogleSignInTrigger: Resetting trigger")
        _state.update { current ->
            current.copy(triggerGoogleSignIn = false)
        }
    }

    fun resetTwitterSignInTrigger() {
        println("🔐 BuyAppViewModel.resetTwitterSignInTrigger: Resetting trigger")
        _state.update { current ->
            current.copy(triggerTwitterSignIn = false)
        }
    }

    fun resetAppleSignInTrigger() {
        println("🔐 BuyAppViewModel.resetAppleSignInTrigger: Resetting trigger")
        _state.update { current ->
            current.copy(triggerAppleSignIn = false)
        }
    }

    fun resetGoogleSignOutTrigger() {
        _state.update { current ->
            current.copy(triggerGoogleSignOut = false)
        }
    }

}
