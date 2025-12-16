package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.navigation.NavRoutes
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Buyer App State - Simplified architecture with single source of truth
 *
 * Key principles:
 * - `data` holds domain data (single source of truth)
 * - `ui` holds screen-specific transient state (dialogs, selections, loading flags)
 * - Screens read from `data`, dispatch actions for changes
 */
data class BuyerAppState(
    val data: BuyerDataState = BuyerDataState(),
    val ui: BuyerUiStates = BuyerUiStates(),
    val auth: BuyerAuthState = BuyerAuthState(),
    val meta: BuyerMetaState = BuyerMetaState()
)

// ===== DATA STATE (Single source of truth for domain data) =====

/**
 * All domain data lives here. Screens READ from this state.
 * No screen-specific data - this is shared truth.
 */
data class BuyerDataState(
    // Products
    val articles: List<Article> = emptyList(),
    val isLoadingArticles: Boolean = false,

    // User profile
    val buyerProfile: BuyerProfile? = null,
    val isLoadingProfile: Boolean = false,

    // Current order (the one being edited)
    val currentOrder: Order? = null,
    val isLoadingOrder: Boolean = false,

    // Order history
    val orderHistory: List<Order> = emptyList(),
    val isLoadingOrderHistory: Boolean = false,

    // Basket (items being added, not yet saved to order)
    val basketItems: List<OrderedProduct> = emptyList(),

    // General loading/error state
    val error: String? = null
) {
    // === Computed properties ===

    val basketTotal: Double
        get() = basketItems.sumOf { it.price * it.amountCount }

    val basketItemCount: Int
        get() = basketItems.size

    val canEditCurrentOrder: Boolean
        get() = currentOrder?.canEdit() ?: true

    val availableArticles: List<Article>
        get() = articles.filter { it.available }

    val favourites: Set<String>
        get() = buyerProfile?.favouriteArticles?.toSet() ?: emptySet()

    val currentOrderId: String?
        get() = currentOrder?.id

    val currentOrderDate: String?
        get() = currentOrder?.pickUpDate?.let { timestamp ->
            // Convert to date string format YYYYMMDD
            val dt = Instant.fromEpochMilliseconds(timestamp)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            "${dt.year}${dt.monthNumber.toString().padStart(2, '0')}${dt.dayOfMonth.toString().padStart(2, '0')}"
        }

    val hasCurrentOrder: Boolean
        get() = currentOrder != null

    val isLoading: Boolean
        get() = isLoadingArticles || isLoadingProfile || isLoadingOrder || isLoadingOrderHistory
}

// ===== UI STATE (Screen-specific, transient) =====

/**
 * Container for all screen-specific UI states.
 * These are transient states that don't affect domain data.
 */
data class BuyerUiStates(
    val mainScreen: MainScreenUiState = MainScreenUiState(),
    val basketScreen: BasketScreenUiState = BasketScreenUiState(),
    val profileScreen: ProfileScreenUiState = ProfileScreenUiState(),
    val global: GlobalBuyerUiState = GlobalBuyerUiState()
)

/**
 * Main screen UI state - article selection, filtering, quantity input
 */
data class MainScreenUiState(
    val selectedArticle: Article? = null,
    val selectedQuantity: Double = 0.0,
    val activeFilter: ProductFilter = ProductFilter.ALL,
    val showNewOrderSnackbar: Boolean = false
)

/**
 * Basket screen UI state - date pickers, dialogs, submission state
 */
data class BasketScreenUiState(
    // Pickup date selection
    val showDatePicker: Boolean = false,
    val selectedPickupDate: Long? = null,
    val availablePickupDates: List<Long> = emptyList(),

    // Order submission
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitError: String? = null,

    // Cancel order
    val isCancelling: Boolean = false,
    val cancelSuccess: Boolean = false,

    // Reorder with new date
    val showReorderDatePicker: Boolean = false,
    val isReordering: Boolean = false,
    val reorderSuccess: Boolean = false,

    // Merge dialog (when order exists for selected date)
    val showMergeDialog: Boolean = false,
    val existingOrderForMerge: Order? = null,
    val mergeConflicts: List<BuyerMergeConflict> = emptyList(),
    val isMerging: Boolean = false
)

/**
 * Profile screen UI state - editing mode, form fields
 */
data class ProfileScreenUiState(
    val isEditing: Boolean = false,
    val editedName: String = "",
    val editedEmail: String = "",
    val editedPhone: String = "",
    val isSaving: Boolean = false,
    val saveError: String? = null
)

/**
 * Global UI state - snackbars, dialogs, navigation
 */
data class GlobalBuyerUiState(
    val isRefreshing: Boolean = false,
    val snackbar: BuyerSnackbar? = null,
    val currentRoute: NavRoutes = NavRoutes.Home,
    val isDrawerOpen: Boolean = false
)

// ===== AUTH STATE =====

/**
 * Authentication state for the buyer app
 */
data class BuyerAuthState(
    val user: BuyerUserState = BuyerUserState.Guest,
    val isLoading: Boolean = false,
    val error: String? = null,
    // Triggers for platform-specific auth
    val triggerGoogleSignIn: Boolean = false,
    val triggerGoogleSignOut: Boolean = false
)

sealed interface BuyerUserState {
    data object Guest : BuyerUserState
    data object Loading : BuyerUserState
    data class LoggedIn(
        val id: String,
        val name: String,
        val email: String,
        val photoUrl: String? = null,
        val isAnonymous: Boolean = false
    ) : BuyerUserState
}

// ===== META STATE =====

/**
 * App metadata and initialization state
 */
data class BuyerMetaState(
    val isInitialized: Boolean = false,
    val initializationStep: BuyerInitStep = BuyerInitStep.NotStarted,
    val devOrderDateOffsetDays: Int = 9
)

sealed class BuyerInitStep {
    data object NotStarted : BuyerInitStep()
    data object CheckingAuth : BuyerInitStep()
    data object LoadingProfile : BuyerInitStep()
    data object LoadingOrder : BuyerInitStep()
    data object LoadingArticles : BuyerInitStep()
    data object Complete : BuyerInitStep()
    data class Failed(val step: String, val message: String) : BuyerInitStep()

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

// ===== SUPPORTING TYPES =====

// Note: ProductFilter is defined in UnifiedAppState.kt and reused here

/**
 * Merge conflict when combining orders
 */
data class BuyerMergeConflict(
    val productId: String,
    val productName: String,
    val unit: String,
    val existingQuantity: Double,
    val newQuantity: Double,
    val existingPrice: Double,
    val newPrice: Double,
    val resolution: BuyerMergeResolution = BuyerMergeResolution.UNDECIDED
)

enum class BuyerMergeResolution {
    UNDECIDED,
    ADD,
    KEEP_EXISTING,
    USE_NEW
}

data class BuyerSnackbar(
    val message: String,
    val type: BuyerSnackbarType = BuyerSnackbarType.INFO,
    val actionLabel: String? = null
)

enum class BuyerSnackbarType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

// ===== EXTENSION FUNCTIONS =====

val BuyerAppState.isUserLoggedIn: Boolean
    get() = auth.user is BuyerUserState.LoggedIn

val BuyerAppState.currentUserId: String?
    get() = (auth.user as? BuyerUserState.LoggedIn)?.id

val BuyerAppState.hasItemsInBasket: Boolean
    get() = data.basketItems.isNotEmpty()

/**
 * Get filtered articles based on current UI filter
 */
fun BuyerDataState.getFilteredArticles(filter: ProductFilter): List<Article> {
    val available = availableArticles
    return when (filter) {
        ProductFilter.ALL -> available
        ProductFilter.FAVOURITES -> available.filter { favourites.contains(it.id) }
        ProductFilter.OBST -> available.filter { it.searchTerms.contains("obst", ignoreCase = true) }
        ProductFilter.GEMUESE -> available.filter { it.searchTerms.contains("gemüse", ignoreCase = true) }
    }
}
