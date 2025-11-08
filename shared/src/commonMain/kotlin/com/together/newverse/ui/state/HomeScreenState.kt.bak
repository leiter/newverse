package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.navigation.NavRoutes

/**
 * Unified state for the Home screen
 *
 * This demonstrates a comprehensive state object that could eventually
 * be part of a larger AppState for single-ViewModel architecture
 */
data class HomeScreenState(
    // User & Auth State
    val user: UserState = UserState.Guest,

    // Navigation State
    val navigation: NavigationState = NavigationState(),

    // Product Catalog State
    val catalog: CatalogState = CatalogState(),

    // Shopping Basket State
    val basket: BasketState = BasketState(),

    // Search State
    val search: SearchState = SearchState(),

    // UI State
    val ui: UiState = UiState(),

    // Connection State
    val connection: ConnectionState = ConnectionState.Connected
)

/**
 * User authentication and profile state
 */
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

/**
 * Navigation-related state
 */
data class NavigationState(
    val currentRoute: NavRoutes = NavRoutes.Home,
    val isDrawerOpen: Boolean = false,
    val navigationStack: List<NavRoutes> = listOf(NavRoutes.Home),
    val pendingDeepLink: String? = null
)

/**
 * Product catalog state
 */
data class CatalogState(
    val isLoading: Boolean = false,
    val products: List<Article> = emptyList(),
    val selectedProduct: Article? = null,
    val selectedQuantity: Double = 0.0,
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val sortBy: ProductSortBy = ProductSortBy.NAME,
    val error: String? = null,
    val lastRefreshTime: Long? = null
)

enum class ProductSortBy {
    NAME,
    PRICE_LOW_TO_HIGH,
    PRICE_HIGH_TO_LOW,
    CATEGORY,
    AVAILABILITY
}

/**
 * Shopping basket state
 */
data class BasketState(
    val items: List<OrderedProduct> = emptyList(),
    val totalAmount: Double = 0.0,
    val itemCount: Int = 0,
    val appliedDiscount: DiscountState? = null,
    val isCheckingOut: Boolean = false,
    val lastAddedItem: Article? = null
)

data class DiscountState(
    val code: String,
    val percentage: Double,
    val amount: Double
)

/**
 * Search state
 */
data class SearchState(
    val isActive: Boolean = false,
    val query: String = "",
    val searchResults: List<Article> = emptyList(),
    val isSearching: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)

/**
 * General UI state
 */
data class UiState(
    val isRefreshing: Boolean = false,
    val snackbarMessage: SnackbarState? = null,
    val dialogState: DialogState? = null,
    val bottomSheetState: BottomSheetState? = null
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

data class FilterState(
    val categories: List<String> = emptyList(),
    val priceRange: PriceRange? = null,
    val inStockOnly: Boolean = false,
    val onSaleOnly: Boolean = false
)

data class PriceRange(
    val min: Double,
    val max: Double
)

/**
 * Connection and sync state
 */
sealed interface ConnectionState {
    data object Connected : ConnectionState
    data object Disconnected : ConnectionState
    data class Syncing(val progress: Float) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

/**
 * Extension functions for state calculations
 */
val HomeScreenState.isUserLoggedIn: Boolean
    get() = user is UserState.LoggedIn

val HomeScreenState.userRole: UserRole?
    get() = (user as? UserState.LoggedIn)?.role

val HomeScreenState.canAccessSellerFeatures: Boolean
    get() = userRole == UserRole.SELLER || userRole == UserRole.BOTH

val HomeScreenState.canAccessCustomerFeatures: Boolean
    get() = userRole == UserRole.CUSTOMER || userRole == UserRole.BOTH

val HomeScreenState.hasItemsInBasket: Boolean
    get() = basket.items.isNotEmpty()

val HomeScreenState.basketItemCount: Int
    get() = basket.items.sumOf { it.piecesCount }

val HomeScreenState.basketTotal: Double
    get() = basket.items.sumOf { it.price * it.amountCount }

val HomeScreenState.effectiveTotal: Double
    get() = basket.appliedDiscount?.let { discount ->
        basketTotal - discount.amount
    } ?: basketTotal