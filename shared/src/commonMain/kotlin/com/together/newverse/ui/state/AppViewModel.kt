package com.together.newverse.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.screens.buy.BasketAction
import com.together.newverse.ui.screens.buy.BasketScreenState
import com.together.newverse.ui.screens.buy.ProductsAction
import com.together.newverse.ui.screens.buy.ProductsScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single ViewModel for the entire application
 *
 * This demonstrates the ultimate evolution of the unified state pattern:
 * one ViewModel managing all application state through a single StateFlow.
 *
 * Benefits:
 * - Single source of truth for entire app
 * - Consistent state management
 * - Easy to add middleware (logging, persistence, analytics)
 * - Time-travel debugging possible
 * - Simple testing - just test state transitions
 * - State persistence/restoration is trivial
 *
 * This pattern is inspired by Redux/MVI but adapted for Kotlin Multiplatform
 * and Compose Multiplatform.
 */
class AppViewModel : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    // Middleware pipeline for cross-cutting concerns
    private val middleware = listOf(
        LoggingMiddleware(),
        AnalyticsMiddleware(),
        PersistenceMiddleware(),
        ValidationMiddleware()
    )

    init {
        // Restore persisted state
        restoreState()

        // Initialize app
        initializeApp()
    }

    /**
     * Single entry point for ALL actions in the app
     *
     * This makes it trivial to:
     * - Log all user actions
     * - Track analytics
     * - Replay actions for debugging
     * - Implement undo/redo
     * - Persist and restore state
     */
    fun dispatch(action: AppAction) {
        // Run through middleware pipeline
        middleware.forEach { it.process(action, _state.value) }

        // Process the action
        when (action) {
            is HomeAction -> handleHomeAction(action)
            is ProductsAction -> handleProductsAction(action)
            is BasketAction -> handleBasketAction(action)
            is AppAction.Global -> handleGlobalAction(action)
            is AppAction.Navigation -> handleNavigationAction(action)
            is AppAction.Settings -> handleSettingsAction(action)
            is AppAction.Auth -> handleAuthAction(action)
        }

        // Persist state after each action (could be debounced)
        persistState()
    }

    private fun handleHomeAction(action: HomeAction) {
        _state.update { appState ->
            appState.copy(
                screens = appState.screens.copy(
                    home = reduceHomeState(appState.screens.home, action)
                )
            )
        }
    }

    private fun handleProductsAction(action: ProductsAction) {
        _state.update { appState ->
            appState.copy(
                screens = appState.screens.copy(
                    products = when (action) {
                        is ProductsAction.AddToBasket -> {
                            // Also update basket state
                            handleCrossScreenAction(appState, action)
                            appState.screens.products
                        }
                        else -> reduceProductsState(appState.screens.products, action)
                    }
                )
            )
        }
    }

    private fun handleBasketAction(action: BasketAction) {
        _state.update { appState ->
            appState.copy(
                screens = appState.screens.copy(
                    basket = reduceBasketState(appState.screens.basket, action)
                )
            )
        }
    }

    private fun handleGlobalAction(action: AppAction.Global) {
        when (action) {
            is AppAction.Global.UpdateSession -> {
                _state.update { appState ->
                    appState.copy(
                        global = appState.global.copy(
                            session = action.session
                        )
                    )
                }
            }
            is AppAction.Global.AddNotification -> {
                _state.update { appState ->
                    appState.copy(
                        global = appState.global.copy(
                            notifications = appState.global.notifications.copy(
                                notifications = appState.global.notifications.notifications + action.notification,
                                unreadCount = appState.global.notifications.unreadCount + 1
                            )
                        )
                    )
                }
            }
            is AppAction.Global.MarkNotificationRead -> {
                _state.update { appState ->
                    val notifications = appState.global.notifications.notifications.map {
                        if (it.id == action.notificationId) it.copy(isRead = true) else it
                    }
                    appState.copy(
                        global = appState.global.copy(
                            notifications = appState.global.notifications.copy(
                                notifications = notifications,
                                unreadCount = notifications.count { !it.isRead }
                            )
                        )
                    )
                }
            }
            is AppAction.Global.ClearCache -> {
                _state.update { appState ->
                    appState.copy(
                        global = appState.global.copy(
                            cache = CacheState()
                        )
                    )
                }
            }
            AppAction.Global.CheckAuth -> {
                // Check authentication status
                viewModelScope.launch {
                    // TODO: Implement auth check
                }
            }
            AppAction.Global.InitAnalytics -> {
                // Initialize analytics
                viewModelScope.launch {
                    // TODO: Implement analytics initialization
                }
            }
        }
    }

    private fun handleNavigationAction(action: AppAction.Navigation) {
        when (action) {
            is AppAction.Navigation.NavigateTo -> {
                _state.update { appState ->
                    appState.copy(
                        screens = appState.screens.copy(
                            home = appState.screens.home.copy(
                                navigation = appState.screens.home.navigation.copy(
                                    currentRoute = action.route,
                                    navigationStack = appState.screens.home.navigation.navigationStack + action.route
                                )
                            )
                        )
                    )
                }
            }
            AppAction.Navigation.NavigateBack -> {
                _state.update { appState ->
                    val stack = appState.screens.home.navigation.navigationStack
                    if (stack.size > 1) {
                        appState.copy(
                            screens = appState.screens.copy(
                                home = appState.screens.home.copy(
                                    navigation = appState.screens.home.navigation.copy(
                                        currentRoute = stack[stack.size - 2],
                                        navigationStack = stack.dropLast(1)
                                    )
                                )
                            )
                        )
                    } else appState
                }
            }
        }
    }

    private fun handleSettingsAction(action: AppAction.Settings) {
        when (action) {
            is AppAction.Settings.ChangeTheme -> {
                _state.update { appState ->
                    appState.copy(
                        screens = appState.screens.copy(
                            settings = appState.screens.settings.copy(theme = action.theme)
                        )
                    )
                }
            }
            is AppAction.Settings.ChangeLanguage -> {
                _state.update { appState ->
                    appState.copy(
                        screens = appState.screens.copy(
                            settings = appState.screens.settings.copy(language = action.language)
                        ),
                        app = appState.app.copy(locale = action.language)
                    )
                }
            }
            is AppAction.Settings.ToggleNotifications -> {
                _state.update { appState ->
                    appState.copy(
                        screens = appState.screens.copy(
                            settings = appState.screens.settings.copy(
                                notificationsEnabled = action.enabled
                            )
                        )
                    )
                }
            }
        }
    }

    private fun handleAuthAction(action: AppAction.Auth) {
        when (action) {
            is AppAction.Auth.Login -> {
                viewModelScope.launch {
                    _state.update { appState ->
                        appState.copy(
                            screens = appState.screens.copy(
                                auth = appState.screens.auth.copy(isLoading = true)
                            )
                        )
                    }

                    // Simulate login
                    kotlinx.coroutines.delay(1000)

                    _state.update { appState ->
                        appState.copy(
                            global = appState.global.copy(
                                session = SessionState(
                                    isAuthenticated = true,
                                    authToken = "mock-token",
                                    user = UserState.LoggedIn(
                                        id = "user-001",
                                        name = "John Doe",
                                        email = action.email,
                                        role = UserRole.CUSTOMER
                                    )
                                )
                            ),
                            screens = appState.screens.copy(
                                auth = AuthScreenState(),
                                home = appState.screens.home.copy(
                                    user = UserState.LoggedIn(
                                        id = "user-001",
                                        name = "John Doe",
                                        email = action.email,
                                        role = UserRole.CUSTOMER
                                    )
                                )
                            )
                        )
                    }
                }
            }
            AppAction.Auth.Logout -> {
                _state.update { appState ->
                    appState.copy(
                        global = appState.global.copy(
                            session = SessionState()
                        ),
                        screens = appState.screens.copy(
                            home = appState.screens.home.copy(user = UserState.Guest),
                            basket = BasketScreenState() // Clear basket on logout
                        )
                    )
                }
            }
            is AppAction.Auth.Register -> {
                // Handle registration
            }
        }
    }

    /**
     * Handle actions that affect multiple screens
     */
    private fun handleCrossScreenAction(appState: AppState, action: ProductsAction.AddToBasket): AppState {
        // Update basket when adding from products screen
        val article = action.article
        val newBasketItem = OrderedProduct(
            productId = article.id,
            productName = article.productName,
            price = article.price,
            unit = article.unit,
            amountCount = 1.0,
            piecesCount = 1,
            amount = article.price.toString()
        )

        return appState.copy(
            screens = appState.screens.copy(
                basket = appState.screens.basket.copy(
                    items = appState.screens.basket.items + newBasketItem,
                    total = appState.screens.basket.total + article.price
                ),
                home = appState.screens.home.copy(
                    basket = appState.screens.home.basket.copy(
                        items = appState.screens.home.basket.items + newBasketItem,
                        itemCount = appState.screens.home.basket.itemCount + 1,
                        totalAmount = appState.screens.home.basket.totalAmount + article.price
                    )
                )
            )
        )
    }

    /**
     * State reducers for individual screens
     * These could be extracted to separate files for better organization
     */
    private fun reduceHomeState(state: HomeScreenState, action: HomeAction): HomeScreenState {
        // Delegate to the existing HomeViewModel logic
        // This is just a placeholder
        return state
    }

    private fun reduceProductsState(state: ProductsScreenState, action: ProductsAction): ProductsScreenState {
        return when (action) {
            ProductsAction.Refresh -> state.copy(isLoading = true)
            is ProductsAction.AddToBasket -> state // Handled in cross-screen action
            else -> state
        }
    }

    private fun reduceBasketState(state: BasketScreenState, action: BasketAction): BasketScreenState {
        return when (action) {
            is BasketAction.RemoveItem -> {
                val updatedItems = state.items.filter { it.productId != action.productId }
                state.copy(
                    items = updatedItems,
                    total = updatedItems.sumOf { it.price * it.amountCount }
                )
            }
            BasketAction.Checkout -> state.copy(isCheckingOut = true)
            else -> state
        }
    }

    private fun initializeApp() {
        viewModelScope.launch {
            // Load initial data
            handleHomeAction(HomeAction.Catalog.LoadProducts)

            // Check authentication
            dispatch(AppAction.Global.CheckAuth)

            // Initialize analytics
            dispatch(AppAction.Global.InitAnalytics)
        }
    }

    private fun restoreState() {
        // TODO: Implement state restoration from persistent storage
    }

    private fun persistState() {
        // TODO: Implement state persistence
        // This could save to DataStore, SharedPreferences, or a database
    }
}

/**
 * Additional app-level actions
 */
sealed interface AppAction {
    sealed interface Global : AppAction {
        data class UpdateSession(val session: SessionState) : Global
        data class AddNotification(val notification: Notification) : Global
        data class MarkNotificationRead(val notificationId: String) : Global
        data object ClearCache : Global
        data object CheckAuth : Global
        data object InitAnalytics : Global
    }

    sealed interface Navigation : AppAction {
        data class NavigateTo(val route: NavRoutes) : Navigation
        data object NavigateBack : Navigation
    }

    sealed interface Settings : AppAction {
        data class ChangeTheme(val theme: ThemeMode) : Settings
        data class ChangeLanguage(val language: String) : Settings
        data class ToggleNotifications(val enabled: Boolean) : Settings
    }

    sealed interface Auth : AppAction {
        data class Login(val email: String, val password: String) : Auth
        data object Logout : Auth
        data class Register(val email: String, val password: String, val name: String) : Auth
    }
}

/**
 * Middleware for cross-cutting concerns
 */
interface Middleware {
    fun process(action: Any, state: AppState)
}

class LoggingMiddleware : Middleware {
    override fun process(action: Any, state: AppState) {
        // Log action and state for debugging
        println("Action: $action")
    }
}

class AnalyticsMiddleware : Middleware {
    override fun process(action: Any, state: AppState) {
        // Track analytics events
    }
}

class PersistenceMiddleware : Middleware {
    override fun process(action: Any, state: AppState) {
        // Persist critical state changes
    }
}

class ValidationMiddleware : Middleware {
    override fun process(action: Any, state: AppState) {
        // Validate state transitions
    }
}