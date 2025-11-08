package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.navigation.NavRoutes

/**
 * Unified App State - Modular approach with reduced duplication
 *
 * This is the single source of truth for the entire application.
 * Uses composition and generics to eliminate duplicate state patterns.
 */
data class UnifiedAppState(
    // Common state shared across all screens
    val common: CommonState = CommonState(),

    // Screen-specific states (dynamically managed)
    val screens: ScreenStates = ScreenStates(),

    // Feature-specific states
    val features: FeatureStates = FeatureStates(),

    // App metadata
    val meta: AppMetaState = AppMetaState()
)

/**
 * Common state shared across all screens
 * These are the cross-cutting concerns that multiple screens need
 */
data class CommonState(
    val user: UserState = UserState.Guest,
    val basket: BasketState = BasketState(),
    val navigation: NavigationState = NavigationState(),
    val ui: GlobalUiState = GlobalUiState(),
    val connection: ConnectionState = ConnectionState.Connected,
    val notifications: NotificationState = NotificationState()
)

/**
 * Base interface for all screen states
 * Ensures consistent loading and error handling
 */
interface ScreenState {
    val isLoading: Boolean
    val error: ErrorState?
}

/**
 * Generic listing state for screens that display lists
 * Used for products, orders, customers, etc.
 */
data class ListingState<T>(
    override val isLoading: Boolean = false,
    override val error: ErrorState? = null,
    val items: List<T> = emptyList(),
    val selectedItem: T? = null,
    val selectedItems: Set<T> = emptySet(),
    val filter: FilterState = FilterState(),
    val sort: SortState = SortState(),
    val pagination: PaginationState = PaginationState(),
    val search: SearchState = SearchState()
) : ScreenState

/**
 * Generic form state for screens with user input
 * Used for profile editing, product creation, checkout, etc.
 */
data class FormState<T>(
    override val isLoading: Boolean = false,
    override val error: ErrorState? = null,
    val data: T,
    val originalData: T? = null,
    val validationErrors: Map<String, String> = emptyMap(),
    val isDirty: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitResult: SubmitResult? = null
) : ScreenState

/**
 * Generic detail state for viewing single items
 * Used for product details, order details, user profiles, etc.
 */
data class DetailState<T>(
    override val isLoading: Boolean = false,
    override val error: ErrorState? = null,
    val item: T? = null,
    val relatedItems: List<T> = emptyList(),
    val actions: List<ActionState> = emptyList()
) : ScreenState

/**
 * Container for all screen states in the app
 * Uses type aliases for specific screens
 */
data class ScreenStates(
    // Browse/List screens
    val products: ProductsScreenState = ProductsScreenState(),
    val orders: OrdersScreenState = OrdersScreenState(),

    // Detail screens
    val productDetail: ProductDetailScreenState = ProductDetailScreenState(),
    val orderDetail: OrderDetailScreenState = OrderDetailScreenState(),

    // Form screens
    val profile: ProfileFormState = ProfileFormState(data = UserProfile()),
    val createProduct: CreateProductFormState = CreateProductFormState(data = ProductFormData()),
    val checkout: CheckoutFormState = CheckoutFormState(data = CheckoutData()),

    // Custom screens (don't fit the generic patterns)
    val auth: AuthScreenState = AuthScreenState(),
    val dashboard: DashboardScreenState = DashboardScreenState()
)

// Type aliases for specific screen states using generic states
typealias ProductsScreenState = ListingState<Article>
typealias OrdersScreenState = ListingState<Order>
typealias ProductDetailScreenState = DetailState<Article>
typealias OrderDetailScreenState = DetailState<Order>

// Form states with specific data types
typealias ProfileFormState = FormState<UserProfile>
typealias CreateProductFormState = FormState<ProductFormData>
typealias CheckoutFormState = FormState<CheckoutData>

/**
 * Feature-specific states that can be toggled or configured
 */
data class FeatureStates(
    val search: SearchFeatureState = SearchFeatureState(),
    val filters: FilterFeatureState = FilterFeatureState(),
    val offline: OfflineFeatureState = OfflineFeatureState(),
    val analytics: AnalyticsFeatureState = AnalyticsFeatureState()
)

// ===== Supporting Data Classes =====

data class ErrorState(
    val message: String,
    val code: String? = null,
    val type: ErrorType = ErrorType.GENERAL,
    val retryable: Boolean = true,
    val details: Map<String, Any> = emptyMap()
)

enum class ErrorType {
    NETWORK,
    VALIDATION,
    AUTHENTICATION,
    AUTHORIZATION,
    NOT_FOUND,
    SERVER,
    GENERAL
}

data class FilterState(
    val activeFilters: Map<String, FilterValue> = emptyMap(),
    val availableFilters: List<FilterDefinition> = emptyList()
)

data class FilterValue(
    val value: Any,
    val displayValue: String
)

data class FilterDefinition(
    val key: String,
    val label: String,
    val type: FilterType,
    val options: List<FilterOption> = emptyList()
)

enum class FilterType {
    SINGLE_SELECT,
    MULTI_SELECT,
    RANGE,
    DATE_RANGE,
    BOOLEAN
}

data class FilterOption(
    val value: String,
    val label: String,
    val count: Int? = null
)

data class SortState(
    val sortBy: String = "default",
    val direction: SortDirection = SortDirection.ASC,
    val availableSorts: List<SortOption> = emptyList()
)

enum class SortDirection {
    ASC,
    DESC
}

data class SortOption(
    val key: String,
    val label: String,
    val defaultDirection: SortDirection = SortDirection.ASC
)

data class PaginationState(
    val currentPage: Int = 1,
    val pageSize: Int = 20,
    val totalItems: Int = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val isLoadingMore: Boolean = false
)

data class SearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val results: List<Any> = emptyList()
)

sealed class SubmitResult {
    data class Success(val data: Any? = null) : SubmitResult()
    data class Failure(val error: ErrorState) : SubmitResult()
}

data class ActionState(
    val id: String,
    val label: String,
    val icon: String? = null,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val loading: Boolean = false,
    val type: ActionType = ActionType.DEFAULT
)

enum class ActionType {
    DEFAULT,
    PRIMARY,
    DANGER,
    SUCCESS,
    WARNING
}

// User-related states (kept from original)
sealed interface UserState {
    data object Guest : UserState
    data object Loading : UserState
    data class LoggedIn(
        val id: String,
        val name: String,
        val email: String,
        val role: UserRole,
        val profileImageUrl: String? = null
    ) : UserState
}

enum class UserRole {
    CUSTOMER,
    SELLER,
    BOTH
}

data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val marketLocation: String = "",
    val pickupTime: String = "",
    val notificationsEnabled: Boolean = true,
    val newsletterEnabled: Boolean = false
)

data class ProductFormData(
    val name: String = "",
    val price: Double = 0.0,
    val unit: String = "",
    val category: String = "",
    val description: String = "",
    val inStock: Boolean = true,
    val images: List<String> = emptyList()
)

data class CheckoutData(
    val items: List<OrderedProduct> = emptyList(),
    val deliveryAddress: String = "",
    val pickupTime: String = "",
    val paymentMethod: String = "",
    val notes: String = "",
    val promoCode: String = ""
)

data class BasketState(
    val items: List<OrderedProduct> = emptyList(),
    val totalAmount: Double = 0.0,
    val itemCount: Int = 0,
    val appliedDiscount: DiscountState? = null,
    val isCheckingOut: Boolean = false
)

data class DiscountState(
    val code: String,
    val percentage: Double,
    val amount: Double
)

data class NavigationState(
    val currentRoute: NavRoutes = NavRoutes.Home,
    val previousRoute: NavRoutes? = null,
    val isDrawerOpen: Boolean = false,
    val backStack: List<NavRoutes> = listOf(NavRoutes.Home)
)

data class GlobalUiState(
    val isRefreshing: Boolean = false,
    val snackbar: SnackbarState? = null,
    val dialog: DialogState? = null,
    val bottomSheet: BottomSheetState? = null
)

data class SnackbarState(
    val message: String,
    val type: SnackbarType = SnackbarType.INFO,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.SHORT
)

enum class SnackbarType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

enum class SnackbarDuration {
    SHORT,
    LONG,
    INDEFINITE
}

sealed interface DialogState {
    data class Confirmation(
        val title: String,
        val message: String,
        val confirmLabel: String = "Confirm",
        val cancelLabel: String = "Cancel"
    ) : DialogState

    data class Information(
        val title: String,
        val message: String,
        val dismissLabel: String = "OK"
    ) : DialogState

    data class Error(
        val title: String = "Error",
        val message: String,
        val retryLabel: String = "Retry",
        val dismissLabel: String = "Dismiss"
    ) : DialogState
}

sealed interface BottomSheetState {
    data class ProductDetail(val product: Article) : BottomSheetState
    data class FilterOptions(val currentFilters: FilterState) : BottomSheetState
    data class CartSummary(val items: List<OrderedProduct>) : BottomSheetState
}

sealed interface ConnectionState {
    data object Connected : ConnectionState
    data object Disconnected : ConnectionState
    data class Syncing(val progress: Float) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class NotificationState(
    val unreadCount: Int = 0,
    val notifications: List<Notification> = emptyList(),
    val permissions: NotificationPermissions = NotificationPermissions()
)

data class Notification(
    val id: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val type: NotificationType,
    val isRead: Boolean = false
)

enum class NotificationType {
    ORDER,
    PRODUCT,
    SYSTEM,
    PROMOTION
}

data class NotificationPermissions(
    val push: Boolean = false,
    val email: Boolean = true,
    val sms: Boolean = false
)

data class AppMetaState(
    val version: String = "1.0.0",
    val environment: Environment = Environment.PRODUCTION,
    val buildNumber: Int = 1,
    val lastSyncTime: Long? = null,
    val isInitializing: Boolean = false,
    val isInitialized: Boolean = false,
    val initializationStep: String = ""
)

enum class Environment {
    DEVELOPMENT,
    STAGING,
    PRODUCTION
}

// Custom screen states that don't fit generic patterns
data class AuthScreenState(
    override val isLoading: Boolean = false,
    override val error: ErrorState? = null,
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val name: String = "",
    val validationErrors: Map<String, String> = emptyMap()
) : ScreenState

enum class AuthMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}

data class DashboardScreenState(
    override val isLoading: Boolean = false,
    override val error: ErrorState? = null,
    val stats: DashboardStats = DashboardStats(),
    val recentActivity: List<Activity> = emptyList(),
    val charts: List<ChartData> = emptyList()
) : ScreenState

data class DashboardStats(
    val totalRevenue: Double = 0.0,
    val totalOrders: Int = 0,
    val totalProducts: Int = 0,
    val totalCustomers: Int = 0
)

data class Activity(
    val id: String,
    val type: String,
    val description: String,
    val timestamp: Long
)

data class ChartData(
    val id: String,
    val type: ChartType,
    val title: String,
    val data: List<ChartPoint>
)

enum class ChartType {
    LINE,
    BAR,
    PIE
}

data class ChartPoint(
    val x: Any,
    val y: Any,
    val label: String? = null
)

// Feature states
data class SearchFeatureState(
    val isEnabled: Boolean = true,
    val searchHistory: List<String> = emptyList(),
    val popularSearches: List<String> = emptyList()
)

data class FilterFeatureState(
    val isEnabled: Boolean = true,
    val savedFilters: List<SavedFilter> = emptyList()
)

data class SavedFilter(
    val id: String,
    val name: String,
    val filters: FilterState
)

data class OfflineFeatureState(
    val isEnabled: Boolean = false,
    val cachedData: Map<String, Long> = emptyMap(),
    val pendingActions: List<PendingAction> = emptyList()
)

data class PendingAction(
    val id: String,
    val type: String,
    val data: Any,
    val timestamp: Long
)

data class AnalyticsFeatureState(
    val isEnabled: Boolean = true,
    val sessionId: String = "",
    val events: List<AnalyticsEvent> = emptyList()
)

data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, Any>,
    val timestamp: Long
)

/**
 * Extension functions for convenient state access
 */
val UnifiedAppState.isUserLoggedIn: Boolean
    get() = common.user is UserState.LoggedIn

val UnifiedAppState.currentUser: UserState
    get() = common.user

val UnifiedAppState.basketItemCount: Int
    get() = common.basket.itemCount

val UnifiedAppState.hasItemsInBasket: Boolean
    get() = common.basket.items.isNotEmpty()

val UnifiedAppState.currentRoute: NavRoutes
    get() = common.navigation.currentRoute

val UnifiedAppState.isAnyScreenLoading: Boolean
    get() = screens.products.isLoading ||
            screens.orders.isLoading ||
            screens.profile.isLoading ||
            screens.auth.isLoading ||
            screens.dashboard.isLoading

/**
 * State selectors for efficient recomposition
 */
object StateSelectors {
    fun selectUser(state: UnifiedAppState) = state.common.user
    fun selectBasket(state: UnifiedAppState) = state.common.basket
    fun selectNavigation(state: UnifiedAppState) = state.common.navigation
    fun selectProducts(state: UnifiedAppState) = state.screens.products
    fun selectOrders(state: UnifiedAppState) = state.screens.orders
    fun selectAuth(state: UnifiedAppState) = state.screens.auth
    fun selectNotifications(state: UnifiedAppState) = state.common.notifications
}