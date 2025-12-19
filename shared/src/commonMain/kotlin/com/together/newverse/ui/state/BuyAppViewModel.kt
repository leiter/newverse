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