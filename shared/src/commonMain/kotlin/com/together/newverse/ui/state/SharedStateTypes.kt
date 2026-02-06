package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.navigation.NavRoutes

// ===== Base Interfaces =====

/**
 * Base interface for all screen states.
 * Ensures consistent loading and error handling.
 */
interface ScreenState {
    val isLoading: Boolean
    val error: ErrorState?
}

// ===== Generic Screen State Templates =====

/**
 * Generic listing state for screens that display lists.
 * Used for products, orders, etc.
 */
data class ListingState<T>(
    override val isLoading: Boolean = false,
    override val error: ErrorState? = null,
    val items: List<T> = emptyList(),
    val selectedItem: T? = null,
    val selectedItems: Set<T> = emptySet()
) : ScreenState

// Type aliases for specific listing screens
typealias ProductsScreenState = ListingState<Article>
typealias OrdersScreenState = ListingState<Order>
typealias OrderHistoryScreenState = ListingState<Order>

// ===== User State =====

sealed interface UserState {
    data object NotAuthenticated : UserState
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

// ===== Error State =====

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

// ===== Navigation State =====

data class NavigationState(
    val currentRoute: NavRoutes = NavRoutes.Home,
    val previousRoute: NavRoutes? = null,
    val isDrawerOpen: Boolean = false,
    val backStack: List<NavRoutes> = listOf(NavRoutes.Home)
)

// ===== UI State =====

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

    data class DraftBasketWarning(
        val draftItemCount: Int,
        val pendingOrderId: String,
        val pendingOrderDate: String
    ) : DialogState
}

sealed interface BottomSheetState {
    data class ProductDetail(val product: Article) : BottomSheetState
    data class CartSummary(val items: List<OrderedProduct>) : BottomSheetState
}

// ===== Basket State =====

data class BasketState(
    val items: List<OrderedProduct> = emptyList(),
    val totalAmount: Double = 0.0,
    val itemCount: Int = 0,
    val isCheckingOut: Boolean = false,
    val currentOrderId: String? = null,
    val currentOrderDate: String? = null
)

// ===== App Metadata =====

data class AppMetaState(
    val version: String = "1.0.0",
    val buildNumber: Int = 1,
    val lastSyncTime: Long? = null,
    val isInitializing: Boolean = false,
    val isInitialized: Boolean = false,
    val initializationStep: InitializationStep = InitializationStep.NotStarted,
    val devOrderDateOffsetDays: Int = 9
)

sealed class InitializationStep {
    data object NotStarted : InitializationStep()
    data object CheckingAuth : InitializationStep()
    data object LoadingProfile : InitializationStep()
    data object LoadingOrder : InitializationStep()
    data object LoadingArticles : InitializationStep()
    data object Complete : InitializationStep()
    data class Failed(val step: String, val message: String) : InitializationStep()

    fun displayMessage(): String = when (this) {
        is NotStarted -> "Starting app..."
        is CheckingAuth -> "Checking authentication..."
        is LoadingProfile -> "Loading your profile..."
        is LoadingOrder -> "Loading current order..."
        is LoadingArticles -> "Loading products..."
        is Complete -> "Ready!"
        is Failed -> "Error: $message"
    }
}

// ===== Basket Screen State (checkout flow) =====

enum class MergeResolution {
    UNDECIDED,
    ADD,
    KEEP_EXISTING,
    USE_NEW
}

enum class MergeConflictType {
    QUANTITY_CHANGED,
    ITEM_ADDED,
    ITEM_REMOVED
}

data class MergeConflict(
    val productId: String,
    val productName: String,
    val unit: String,
    val conflictType: MergeConflictType,
    val existingQuantity: Double,
    val newQuantity: Double,
    val existingPrice: Double,
    val newPrice: Double,
    val resolution: MergeResolution = MergeResolution.UNDECIDED
)

data class BasketScreenState(
    override val isLoading: Boolean = false,
    override val error: ErrorState? = null,
    val items: List<OrderedProduct> = emptyList(),
    val total: Double = 0.0,
    val isCheckingOut: Boolean = false,
    val orderSuccess: Boolean = false,
    val orderError: String? = null,
    val orderId: String? = null,
    val orderDate: String? = null,
    val pickupDate: Long? = null,
    val createdDate: Long? = null,
    val isEditMode: Boolean = false,
    val canEdit: Boolean = false,
    val isLoadingOrder: Boolean = false,
    val originalOrderItems: List<OrderedProduct> = emptyList(),
    val hasChanges: Boolean = false,
    val selectedPickupDate: Long? = null,
    val availablePickupDates: List<Long> = emptyList(),
    val showDatePicker: Boolean = false,
    val isCancelling: Boolean = false,
    val cancelSuccess: Boolean = false,
    val showReorderDatePicker: Boolean = false,
    val isReordering: Boolean = false,
    val reorderSuccess: Boolean = false,
    val showMergeDialog: Boolean = false,
    val existingOrderForMerge: Order? = null,
    val mergeConflicts: List<MergeConflict> = emptyList(),
    val isMerging: Boolean = false,
    val showDraftWarningDialog: Boolean = false,
    val draftItemCount: Int = 0,
    val pendingOrderIdForLoad: String? = null,
    val pendingOrderDateForLoad: String? = null
) : ScreenState

// ===== Customer Profile Screen State =====

data class CustomerProfileScreenState(
    override val isLoading: Boolean = false,
    override val error: ErrorState? = null,
    val profile: BuyerProfile? = null,
    val photoUrl: String? = null,
    val showLogoutWarningDialog: Boolean = false,
    val showLinkAccountDialog: Boolean = false,
    val showDeleteAccountDialog: Boolean = false,
    val isLinkingAccount: Boolean = false,
    val linkAccountError: String? = null,
    val showEmailLinkingDialog: Boolean = false,
    val emailLinkingEmail: String = "",
    val emailLinkingPassword: String = "",
    val emailLinkingConfirmPassword: String = "",
    val emailLinkingError: String? = null
) : ScreenState

/**
 * Authentication provider types
 */
enum class AuthProvider {
    ANONYMOUS,
    GOOGLE,
    EMAIL,
    TWITTER,
    APPLE;

    val displayName: String
        get() = when (this) {
            ANONYMOUS -> "Gast"
            GOOGLE -> "Google"
            EMAIL -> "E-Mail"
            TWITTER -> "Twitter"
            APPLE -> "Apple"
        }
}

// ===== Main Screen State =====

enum class ProductFilter {
    ALL,
    FAVOURITES,
    OBST,
    GEMUESE
}

data class MainScreenState(
    override val isLoading: Boolean = true,
    override val error: ErrorState? = null,
    val articles: List<Article> = emptyList(),
    val selectedArticle: Article? = null,
    val selectedQuantity: Double = 0.0,
    val cartItemCount: Int = 0,
    val basketItems: List<OrderedProduct> = emptyList(),
    val favouriteArticles: List<String> = emptyList(),
    val activeFilter: ProductFilter = ProductFilter.ALL,
    val canEditOrder: Boolean = true,
    val showNewOrderSnackbar: Boolean = false,
    val searchQuery: String = ""
) : ScreenState {

    val filteredArticles: List<Article>
        get() {
            var filtered = articles.filter { it.available }

            if (searchQuery.isNotBlank()) {
                val query = searchQuery.trim().lowercase()
                filtered = filtered.filter { article ->
                    article.productName.lowercase().contains(query) ||
                    article.searchTerms.lowercase().contains(query) ||
                    article.category.lowercase().contains(query)
                }
            }

            return when (activeFilter) {
                ProductFilter.ALL -> filtered
                ProductFilter.FAVOURITES -> filtered.filter { favouriteArticles.contains(it.id) }
                ProductFilter.OBST -> filtered.filter { it.searchTerms.contains("obst", ignoreCase = true) }
                ProductFilter.GEMUESE -> filtered.filter { it.searchTerms.contains("gemüse", ignoreCase = true) }
            }
        }
}

// ===== Sell-only types =====

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
