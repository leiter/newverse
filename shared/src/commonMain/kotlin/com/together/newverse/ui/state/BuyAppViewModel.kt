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
import com.together.newverse.ui.state.buy.*
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

    // Main Screen action handler and helpers moved to BuyAppViewModelMainScreen.kt
    // - handleMainScreenAction(action)
    // - handleUpdateQuantity(quantity), handleUpdateQuantityFromText(text)
    // - handleAddToCart(), handleRemoveFromBasket()
    // - showNewOrderSnackbar(), dismissNewOrderSnackbar(), startNewOrder()
    // - setMainScreenFilter(filter)

    // ===== Implementation Methods =====
    // Initialization functions moved to BuyAppViewModelInitialization.kt
    // - initializeApp(), checkAuthenticationStatus(), signInAsGuest()

    // Navigation functions moved to BuyAppViewModelNavigation.kt
    // - navigateTo(route)
    // - navigateBack()
    // - openDrawer()
    // - closeDrawer()

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

    // UI Management functions moved to BuyAppViewModelUi.kt
    // - showSnackbar(message, type)
    // - hideSnackbar()
    // - showDialog(dialog)
    // - hideDialog()
    // - showBottomSheet(sheet)
    // - hideBottomSheet()
    // - setRefreshing(isRefreshing)

    // ===== Authentication & Account Management moved to BuyAppViewModelAuth.kt =====
    // - login(email, password), loginWithGoogle(), loginWithTwitter(), logout()
    // - register(email, password, name), sendPasswordResetEmail(email)
    // - handleAccountAction(action)
    // - Dialog management: showLogoutWarningDialog(), showLinkAccountDialog(), showDeleteAccountDialog() (and dismiss variants)
    // - Account operations: confirmGuestLogout(), linkWithGoogle(), linkWithEmail(), confirmDeleteAccount()
    // - Helper: getCurrentUserId()

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

    // updateProfile, loadUserProfile, loadCurrentOrder moved to their respective extension files

    // Profile functions moved to BuyAppViewModelProfile.kt
    // - loadProfile()
    // - loadCustomerProfile()
    // - loadOrderHistory()
    // - refreshCustomerProfile()
    // - updateProfileField(field, value)
    // - saveProfile()
    // - saveBuyerProfile(displayName, email, phone)
    // - cancelProfileEdit()

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

    // ===== Main Screen Implementation Methods moved to BuyAppViewModelMainScreen.kt =====
    // - selectMainScreenArticle(article)
    // - updateMainScreenQuantity(quantity), updateMainScreenQuantityFromText(text)
    // - addMainScreenToCart(), removeMainScreenFromBasket()
    // - toggleMainScreenFavourite(articleId)
    // - refreshMainScreen(), loadMainScreenArticles()
    // - observeMainScreenBasket()

    private fun viewProductDetail(productId: String) {
        // TODO: Implement view product detail
    }

    private fun applyPromoCode(code: String) {
        // TODO: Implement apply promo code
    }

    private fun startCheckout() {
        // TODO: Implement start checkout
    }

    // observeMainScreenBuyerProfile moved to BuyAppViewModelProfile.kt

    // ===== Basket Screen Handler and Methods moved to BuyAppViewModelBasket.kt =====
    // - BASKET_SELLER_ID constant
    // - handleBasketScreenAction(action)
    // - initializeBasketScreen(), observeBasketScreenItems(), basketScreenCheckIfHasChanges()
    // - Item management: basketScreenAddItem(), basketScreenRemoveItem(), basketScreenUpdateQuantity(), basketScreenClearBasket()
    // - Checkout: basketScreenCheckout()
    // - Order loading: basketScreenLoadMostRecentEditableOrder(), basketScreenLoadOrder()
    // - Order editing: basketScreenEnableEditing(), basketScreenUpdateOrder(), basketScreenCancelOrder(), basketScreenResetOrderState()
    // - Date handling: basketScreenLoadAvailableDates(), basketScreenShowDatePicker(), basketScreenHideDatePicker(), basketScreenSelectPickupDate()
    // - Reorder: basketScreenShowReorderDatePicker(), basketScreenHideReorderDatePicker(), basketScreenReorderWithNewDate()
    // - Merge: basketScreenCalculateMergeConflicts(), basketScreenHideMergeDialog(), basketScreenResolveMergeConflict(), basketScreenConfirmMerge()
    // - Helpers: basketScreenFormatDateKey(), basketScreenFormatDate(), basketScreenCanEditOrder(), basketScreenGetDaysUntilPickup()
}