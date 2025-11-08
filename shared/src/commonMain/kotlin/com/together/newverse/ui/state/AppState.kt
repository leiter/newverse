package com.together.newverse.ui.state

import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.screens.buy.BasketScreenState
import com.together.newverse.ui.screens.buy.ProductsScreenState

/**
 * Unified App State - The single source of truth for the entire application
 *
 * This demonstrates how to evolve from screen-specific ViewModels to a
 * single AppViewModel managing all application state, similar to Redux
 * or MVI architecture patterns.
 *
 * Benefits:
 * - Single source of truth
 * - Easy state persistence and restoration
 * - Simple debugging (all state in one place)
 * - Time-travel debugging capability
 * - Consistent state management across the app
 * - Easy to add middleware (logging, analytics, etc.)
 */
data class AppState(
    // Core app state
    val app: AppMetaState = AppMetaState(),

    // Screen states
    val screens: ScreenStates = ScreenStates(),

    // Global features
    val global: GlobalState = GlobalState(),

    // Feature flags
    val features: FeatureFlags = FeatureFlags()
)

/**
 * App-level metadata and configuration
 */
data class AppMetaState(
    val version: String = "1.0.0",
    val environment: Environment = Environment.PRODUCTION,
    val isFirstLaunch: Boolean = false,
    val lastSyncTime: Long? = null,
    val deviceId: String = "",
    val sessionId: String = "",
    val locale: String = "en"
)

enum class Environment {
    DEVELOPMENT,
    STAGING,
    PRODUCTION
}

/**
 * All screen states in the app
 */
data class ScreenStates(
    // Main screens
    val home: HomeScreenState = HomeScreenState(),
    val products: ProductsScreenState = ProductsScreenState(),
    val basket: BasketScreenState = BasketScreenState(),

    // Seller screens
    val sellerDashboard: SellerDashboardState = SellerDashboardState(),
    val orders: OrdersScreenState = OrdersScreenState(),
    val createProduct: CreateProductScreenState = CreateProductScreenState(),

    // Common screens
    val profile: ProfileScreenState = ProfileScreenState(),
    val settings: SettingsScreenState = SettingsScreenState(),
    val auth: AuthScreenState = AuthScreenState()
)

/**
 * Global state that affects multiple screens
 */
data class GlobalState(
    // User session
    val session: SessionState = SessionState(),

    // Notifications
    val notifications: NotificationState = NotificationState(),

    // Cache
    val cache: CacheState = CacheState(),

    // Analytics
    val analytics: AnalyticsState = AnalyticsState()
)

/**
 * Feature flags for A/B testing and gradual rollouts
 */
data class FeatureFlags(
    val newCheckoutFlow: Boolean = false,
    val darkModeEnabled: Boolean = true,
    val searchSuggestions: Boolean = true,
    val pushNotifications: Boolean = false,
    val offlineMode: Boolean = true,
    val advancedFilters: Boolean = false
)

// Additional state definitions

data class SessionState(
    val isAuthenticated: Boolean = false,
    val authToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val user: UserState = UserState.Guest
)

data class NotificationState(
    val unreadCount: Int = 0,
    val notifications: List<Notification> = emptyList(),
    val pushToken: String? = null,
    val permissions: NotificationPermissions = NotificationPermissions()
)

data class Notification(
    val id: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val type: NotificationType,
    val isRead: Boolean = false,
    val actionUrl: String? = null
)

enum class NotificationType {
    ORDER_PLACED,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    PRODUCT_BACK_IN_STOCK,
    PRICE_DROP,
    PROMOTION,
    SYSTEM
}

data class NotificationPermissions(
    val push: Boolean = false,
    val email: Boolean = true,
    val sms: Boolean = false
)

data class CacheState(
    val sizeInBytes: Long = 0,
    val lastClearedTime: Long? = null,
    val cachedScreens: Set<String> = emptySet()
)

data class AnalyticsState(
    val sessionStartTime: Long = 0,
    val screenViews: Int = 0,
    val eventsTracked: Int = 0,
    val lastEvent: AnalyticsEvent? = null
)

data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, Any>,
    val timestamp: Long
)

// Placeholder states for screens not yet refactored

data class SellerDashboardState(
    val isLoading: Boolean = false,
    val totalRevenue: Double = 0.0,
    val totalOrders: Int = 0,
    val totalProducts: Int = 0,
    val recentOrders: List<Any> = emptyList() // Replace with actual Order type
)

data class OrdersScreenState(
    val isLoading: Boolean = false,
    val orders: List<Any> = emptyList(), // Replace with actual Order type
    val filter: OrderFilter = OrderFilter.ALL,
    val sortBy: OrderSortBy = OrderSortBy.DATE_DESC
)

enum class OrderFilter {
    ALL,
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}

enum class OrderSortBy {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC
}

data class CreateProductScreenState(
    val productName: String = "",
    val price: String = "",
    val unit: String = "",
    val category: String = "",
    val description: String = "",
    val images: List<String> = emptyList(),
    val isCreating: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap()
)

data class ProfileScreenState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val isEditing: Boolean = false,
    val editedProfile: UserProfile? = null,
    val isSaving: Boolean = false
)

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val address: String,
    val imageUrl: String?
)

data class SettingsScreenState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "en",
    val notificationsEnabled: Boolean = true,
    val analyticsEnabled: Boolean = true,
    val cacheEnabled: Boolean = true,
    val offlineModeEnabled: Boolean = false
)

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

data class AuthScreenState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val name: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val validationErrors: Map<String, String> = emptyMap()
)

enum class AuthMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD,
    RESET_PASSWORD
}

/**
 * Extension functions for working with AppState
 */

// Check if user can access specific features
val AppState.canAccessSellerFeatures: Boolean
    get() = screens.home.canAccessSellerFeatures

val AppState.canAccessCustomerFeatures: Boolean
    get() = screens.home.canAccessCustomerFeatures

// Get total items in basket across the app
val AppState.totalBasketItems: Int
    get() = screens.basket.items.size

// Check if any screen is loading
val AppState.isAnyScreenLoading: Boolean
    get() = screens.home.catalog.isLoading ||
            screens.products.isLoading ||
            screens.sellerDashboard.isLoading ||
            screens.orders.isLoading ||
            screens.profile.isLoading ||
            screens.auth.isLoading

// Get current user across the app
val AppState.currentUser: UserState
    get() = global.session.user

// Check if offline mode should be activated
val AppState.shouldUseOfflineMode: Boolean
    get() = features.offlineMode && screens.home.connection is ConnectionState.Disconnected

/**
 * State selectors for efficient recomposition
 *
 * These help components subscribe to only the parts of state they need,
 * preventing unnecessary recompositions
 */
object AppStateSelectors {
    fun selectUser(state: AppState): UserState = state.global.session.user
    fun selectBasketCount(state: AppState): Int = state.screens.basket.items.size
    fun selectCurrentRoute(state: AppState): NavRoutes = state.screens.home.navigation.currentRoute
    fun selectTheme(state: AppState): ThemeMode = state.screens.settings.theme
    fun selectNotificationCount(state: AppState): Int = state.global.notifications.unreadCount
    fun selectIsAuthenticated(state: AppState): Boolean = state.global.session.isAuthenticated
}