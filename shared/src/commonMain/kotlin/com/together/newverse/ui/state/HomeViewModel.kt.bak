package com.together.newverse.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.preview.PreviewData
import com.together.newverse.ui.navigation.NavRoutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Unified ViewModel for the Home screen
 *
 * This demonstrates how to manage complex state with a single ViewModel
 * using a reducer-like pattern. This could eventually evolve into an
 * AppViewModel managing the entire app state.
 */
class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeScreenState())
    val state: StateFlow<HomeScreenState> = _state.asStateFlow()

    init {
        // Initialize with some data
        loadInitialData()
    }

    /**
     * Single entry point for all actions
     * This makes it easy to add logging, analytics, or middleware
     */
    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.Navigation -> handleNavigationAction(action)
            is HomeAction.User -> handleUserAction(action)
            is HomeAction.Catalog -> handleCatalogAction(action)
            is HomeAction.Basket -> handleBasketAction(action)
            is HomeAction.Search -> handleSearchAction(action)
            is HomeAction.UI -> handleUiAction(action)
            is HomeAction.Connection -> handleConnectionAction(action)
        }
    }

    private fun handleNavigationAction(action: HomeAction.Navigation) {
        when (action) {
            is HomeAction.Navigation.NavigateTo -> {
                _state.update { state ->
                    state.copy(
                        navigation = state.navigation.copy(
                            currentRoute = action.route,
                            navigationStack = state.navigation.navigationStack + action.route
                        )
                    )
                }
            }
            HomeAction.Navigation.OpenDrawer -> {
                _state.update { state ->
                    state.copy(
                        navigation = state.navigation.copy(isDrawerOpen = true)
                    )
                }
            }
            HomeAction.Navigation.CloseDrawer -> {
                _state.update { state ->
                    state.copy(
                        navigation = state.navigation.copy(isDrawerOpen = false)
                    )
                }
            }
            HomeAction.Navigation.NavigateBack -> {
                _state.update { state ->
                    val stack = state.navigation.navigationStack
                    if (stack.size > 1) {
                        state.copy(
                            navigation = state.navigation.copy(
                                currentRoute = stack[stack.size - 2],
                                navigationStack = stack.dropLast(1)
                            )
                        )
                    } else state
                }
            }
            is HomeAction.Navigation.HandleDeepLink -> {
                // Handle deep link navigation
                _state.update { state ->
                    state.copy(
                        navigation = state.navigation.copy(
                            pendingDeepLink = action.url
                        )
                    )
                }
            }
        }
    }

    private fun handleUserAction(action: HomeAction.User) {
        when (action) {
            is HomeAction.User.Login -> {
                viewModelScope.launch {
                    _state.update { it.copy(user = UserState.Loading) }

                    // Simulate login
                    delay(1000)

                    _state.update {
                        it.copy(
                            user = UserState.LoggedIn(
                                id = "user-001",
                                name = "John Doe",
                                email = action.email,
                                role = UserRole.CUSTOMER
                            )
                        )
                    }

                    showSnackbar("Welcome back!", SnackbarType.SUCCESS)
                }
            }
            HomeAction.User.Logout -> {
                _state.update {
                    it.copy(
                        user = UserState.Guest,
                        basket = BasketState() // Clear basket on logout
                    )
                }
                showSnackbar("Logged out successfully", SnackbarType.INFO)
            }
            HomeAction.User.RefreshProfile -> {
                // Refresh user profile from server
                viewModelScope.launch {
                    // TODO: Implement profile refresh
                }
            }
            is HomeAction.User.UpdateProfile -> {
                // Update user profile
                val currentUser = _state.value.user as? UserState.LoggedIn ?: return
                _state.update {
                    it.copy(
                        user = currentUser.copy(
                            name = action.name ?: currentUser.name,
                            email = action.email ?: currentUser.email
                        )
                    )
                }
            }
            is HomeAction.User.SwitchRole -> {
                val currentUser = _state.value.user as? UserState.LoggedIn ?: return
                _state.update {
                    it.copy(
                        user = currentUser.copy(role = action.role)
                    )
                }
            }
        }
    }

    private fun handleCatalogAction(action: HomeAction.Catalog) {
        when (action) {
            HomeAction.Catalog.LoadProducts -> {
                loadProducts()
            }
            HomeAction.Catalog.RefreshProducts -> {
                _state.update {
                    it.copy(ui = it.ui.copy(isRefreshing = true))
                }
                loadProducts()
            }
            is HomeAction.Catalog.SelectProduct -> {
                _state.update {
                    it.copy(
                        catalog = it.catalog.copy(
                            selectedProduct = action.product,
                            selectedQuantity = 1.0
                        )
                    )
                }
            }
            is HomeAction.Catalog.SetQuantity -> {
                _state.update {
                    it.copy(
                        catalog = it.catalog.copy(selectedQuantity = action.quantity)
                    )
                }
            }
            is HomeAction.Catalog.SelectCategory -> {
                _state.update {
                    it.copy(
                        catalog = it.catalog.copy(selectedCategory = action.category)
                    )
                }
                // Filter products by category
                filterProducts()
            }
            is HomeAction.Catalog.ChangeSortBy -> {
                _state.update {
                    it.copy(
                        catalog = it.catalog.copy(sortBy = action.sortBy)
                    )
                }
                sortProducts()
            }
            is HomeAction.Catalog.ApplyFilters -> {
                // Apply filters to product list
                _state.update {
                    it.copy(
                        ui = it.ui.copy(
                            bottomSheetState = BottomSheetState.FilterOptions(action.filters)
                        )
                    )
                }
                filterProducts()
            }
            HomeAction.Catalog.ClearFilters -> {
                _state.update {
                    it.copy(
                        catalog = it.catalog.copy(
                            selectedCategory = null,
                            sortBy = ProductSortBy.NAME
                        )
                    )
                }
                loadProducts()
            }
        }
    }

    private fun handleBasketAction(action: HomeAction.Basket) {
        when (action) {
            is HomeAction.Basket.AddToBasket -> {
                val currentItems = _state.value.basket.items.toMutableList()
                val existingItem = currentItems.find { it.productId == action.product.id }

                if (existingItem != null) {
                    // Update quantity
                    val index = currentItems.indexOf(existingItem)
                    currentItems[index] = existingItem.copy(
                        amountCount = existingItem.amountCount + action.quantity
                    )
                } else {
                    // Add new item
                    currentItems.add(
                        OrderedProduct(
                            productId = action.product.id,
                            productName = action.product.productName,
                            price = action.product.price,
                            unit = action.product.unit,
                            amountCount = action.quantity,
                            piecesCount = action.quantity.toInt(),
                            amount = (action.product.price * action.quantity).toString()
                        )
                    )
                }

                _state.update {
                    it.copy(
                        basket = it.basket.copy(
                            items = currentItems,
                            totalAmount = currentItems.sumOf { item -> item.price * item.amountCount },
                            itemCount = currentItems.size,
                            lastAddedItem = action.product
                        )
                    )
                }

                showSnackbar("${action.product.productName} added to basket", SnackbarType.SUCCESS)
            }
            is HomeAction.Basket.RemoveFromBasket -> {
                _state.update {
                    val updatedItems = it.basket.items.filter { item -> item.productId != action.productId }
                    it.copy(
                        basket = it.basket.copy(
                            items = updatedItems,
                            totalAmount = updatedItems.sumOf { item -> item.price * item.amountCount },
                            itemCount = updatedItems.size
                        )
                    )
                }
            }
            is HomeAction.Basket.UpdateQuantity -> {
                _state.update { state ->
                    val updatedItems = state.basket.items.map { item ->
                        if (item.productId == action.productId) {
                            item.copy(amountCount = action.quantity)
                        } else item
                    }
                    state.copy(
                        basket = state.basket.copy(
                            items = updatedItems,
                            totalAmount = updatedItems.sumOf { it.price * it.amountCount }
                        )
                    )
                }
            }
            HomeAction.Basket.ClearBasket -> {
                _state.update {
                    it.copy(basket = BasketState())
                }
                showSnackbar("Basket cleared", SnackbarType.INFO)
            }
            is HomeAction.Basket.ApplyDiscount -> {
                // Simulate discount validation
                viewModelScope.launch {
                    delay(500)
                    _state.update {
                        it.copy(
                            basket = it.basket.copy(
                                appliedDiscount = DiscountState(
                                    code = action.code,
                                    percentage = 10.0,
                                    amount = it.basket.totalAmount * 0.1
                                )
                            )
                        )
                    }
                    showSnackbar("Discount applied!", SnackbarType.SUCCESS)
                }
            }
            HomeAction.Basket.RemoveDiscount -> {
                _state.update {
                    it.copy(
                        basket = it.basket.copy(appliedDiscount = null)
                    )
                }
            }
            HomeAction.Basket.StartCheckout -> {
                _state.update {
                    it.copy(basket = it.basket.copy(isCheckingOut = true))
                }
            }
            HomeAction.Basket.CompleteCheckout -> {
                viewModelScope.launch {
                    delay(2000) // Simulate checkout process
                    _state.update {
                        it.copy(basket = BasketState())
                    }
                    showSnackbar("Order placed successfully!", SnackbarType.SUCCESS)
                }
            }
        }
    }

    private fun handleSearchAction(action: HomeAction.Search) {
        when (action) {
            HomeAction.Search.OpenSearch -> {
                _state.update {
                    it.copy(search = it.search.copy(isActive = true))
                }
            }
            HomeAction.Search.CloseSearch -> {
                _state.update {
                    it.copy(
                        search = it.search.copy(
                            isActive = false,
                            query = "",
                            searchResults = emptyList()
                        )
                    )
                }
            }
            is HomeAction.Search.UpdateQuery -> {
                _state.update {
                    it.copy(search = it.search.copy(query = action.query))
                }
                // Generate suggestions
                generateSuggestions(action.query)
            }
            HomeAction.Search.ExecuteSearch -> {
                performSearch()
            }
            is HomeAction.Search.SelectSuggestion -> {
                _state.update {
                    it.copy(search = it.search.copy(query = action.suggestion))
                }
                performSearch()
            }
            is HomeAction.Search.SelectRecentSearch -> {
                _state.update {
                    it.copy(search = it.search.copy(query = action.query))
                }
                performSearch()
            }
            HomeAction.Search.ClearRecentSearches -> {
                _state.update {
                    it.copy(search = it.search.copy(recentSearches = emptyList()))
                }
            }
        }
    }

    private fun handleUiAction(action: HomeAction.UI) {
        when (action) {
            is HomeAction.UI.ShowSnackbar -> {
                _state.update {
                    it.copy(
                        ui = it.ui.copy(
                            snackbarMessage = SnackbarState(
                                message = action.message,
                                type = action.type,
                                actionLabel = action.actionLabel
                            )
                        )
                    )
                }
            }
            HomeAction.UI.DismissSnackbar -> {
                _state.update {
                    it.copy(ui = it.ui.copy(snackbarMessage = null))
                }
            }
            HomeAction.UI.SnackbarActionClicked -> {
                // Handle snackbar action
                _state.value.ui.snackbarMessage?.let { snackbar ->
                    // Process based on snackbar type/context
                }
            }
            is HomeAction.UI.ShowDialog -> {
                _state.update {
                    it.copy(ui = it.ui.copy(dialogState = action.dialog))
                }
            }
            HomeAction.UI.DismissDialog -> {
                _state.update {
                    it.copy(ui = it.ui.copy(dialogState = null))
                }
            }
            HomeAction.UI.DialogConfirmed -> {
                // Handle dialog confirmation based on current dialog type
                _state.update {
                    it.copy(ui = it.ui.copy(dialogState = null))
                }
            }
            HomeAction.UI.DialogCancelled -> {
                _state.update {
                    it.copy(ui = it.ui.copy(dialogState = null))
                }
            }
            is HomeAction.UI.ShowBottomSheet -> {
                _state.update {
                    it.copy(ui = it.ui.copy(bottomSheetState = action.sheet))
                }
            }
            HomeAction.UI.DismissBottomSheet -> {
                _state.update {
                    it.copy(ui = it.ui.copy(bottomSheetState = null))
                }
            }
            HomeAction.UI.StartRefresh -> {
                _state.update {
                    it.copy(ui = it.ui.copy(isRefreshing = true))
                }
            }
            HomeAction.UI.EndRefresh -> {
                _state.update {
                    it.copy(ui = it.ui.copy(isRefreshing = false))
                }
            }
        }
    }

    private fun handleConnectionAction(action: HomeAction.Connection) {
        when (action) {
            HomeAction.Connection.CheckConnection -> {
                // Check network connection
                viewModelScope.launch {
                    // Simulate connection check
                    _state.update {
                        it.copy(connection = ConnectionState.Connected)
                    }
                }
            }
            HomeAction.Connection.RetryConnection -> {
                _state.update {
                    it.copy(connection = ConnectionState.Connected)
                }
            }
            HomeAction.Connection.StartSync -> {
                _state.update {
                    it.copy(connection = ConnectionState.Syncing(0f))
                }
            }
            is HomeAction.Connection.UpdateSyncProgress -> {
                _state.update {
                    it.copy(connection = ConnectionState.Syncing(action.progress))
                }
            }
            HomeAction.Connection.CompleteSync -> {
                _state.update {
                    it.copy(connection = ConnectionState.Connected)
                }
                showSnackbar("Sync completed", SnackbarType.SUCCESS)
            }
        }
    }

    // Helper functions

    private fun loadInitialData() {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _state.update {
                it.copy(catalog = it.catalog.copy(isLoading = true))
            }

            delay(500) // Simulate network delay

            _state.update {
                it.copy(
                    catalog = it.catalog.copy(
                        isLoading = false,
                        products = PreviewData.sampleArticles,
                        categories = PreviewData.sampleArticles.map { it.category }.distinct(),
                        error = null,
                        lastRefreshTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                    ),
                    ui = it.ui.copy(isRefreshing = false)
                )
            }
        }
    }

    private fun filterProducts() {
        // Apply category and other filters
        val allProducts = PreviewData.sampleArticles
        val category = _state.value.catalog.selectedCategory

        val filtered = if (category != null) {
            allProducts.filter { it.category == category }
        } else {
            allProducts
        }

        _state.update {
            it.copy(catalog = it.catalog.copy(products = filtered))
        }
    }

    private fun sortProducts() {
        val currentProducts = _state.value.catalog.products
        val sorted = when (_state.value.catalog.sortBy) {
            ProductSortBy.NAME -> currentProducts.sortedBy { it.productName }
            ProductSortBy.PRICE_LOW_TO_HIGH -> currentProducts.sortedBy { it.price }
            ProductSortBy.PRICE_HIGH_TO_LOW -> currentProducts.sortedByDescending { it.price }
            ProductSortBy.CATEGORY -> currentProducts.sortedBy { it.category }
            ProductSortBy.AVAILABILITY -> currentProducts // TODO: Add availability field
        }

        _state.update {
            it.copy(catalog = it.catalog.copy(products = sorted))
        }
    }

    private fun performSearch() {
        viewModelScope.launch {
            _state.update {
                it.copy(search = it.search.copy(isSearching = true))
            }

            delay(300) // Simulate search delay

            val query = _state.value.search.query.lowercase()
            val results = PreviewData.sampleArticles.filter {
                it.productName.lowercase().contains(query) ||
                it.category.lowercase().contains(query)
            }

            _state.update {
                it.copy(
                    search = it.search.copy(
                        isSearching = false,
                        searchResults = results,
                        recentSearches = (listOf(query) + it.search.recentSearches).distinct().take(5)
                    )
                )
            }
        }
    }

    private fun generateSuggestions(query: String) {
        if (query.isEmpty()) {
            _state.update {
                it.copy(search = it.search.copy(suggestions = emptyList()))
            }
            return
        }

        val allProductNames = PreviewData.sampleArticles.map { it.productName }
        val suggestions = allProductNames
            .filter { it.lowercase().startsWith(query.lowercase()) }
            .take(5)

        _state.update {
            it.copy(search = it.search.copy(suggestions = suggestions))
        }
    }

    private fun showSnackbar(
        message: String,
        type: SnackbarType = SnackbarType.INFO,
        actionLabel: String? = null
    ) {
        _state.update {
            it.copy(
                ui = it.ui.copy(
                    snackbarMessage = SnackbarState(
                        message = message,
                        type = type,
                        actionLabel = actionLabel
                    )
                )
            )
        }
    }
}