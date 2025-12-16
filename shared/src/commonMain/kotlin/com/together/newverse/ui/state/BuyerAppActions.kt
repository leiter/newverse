package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct

/**
 * Base interface for all buyer app actions
 */
sealed interface BuyerAction

// ===== DATA ACTIONS (affect BuyerDataState) =====

/**
 * Actions that modify domain data (single source of truth)
 */
sealed interface DataAction : BuyerAction {
    // Articles
    data class SetArticles(val articles: List<Article>) : DataAction
    data class SetLoadingArticles(val loading: Boolean) : DataAction

    // Profile
    data class SetBuyerProfile(val profile: BuyerProfile?) : DataAction
    data class SetLoadingProfile(val loading: Boolean) : DataAction
    data class ToggleFavourite(val articleId: String) : DataAction

    // Current order
    data class SetCurrentOrder(val order: Order?) : DataAction
    data class SetLoadingOrder(val loading: Boolean) : DataAction

    // Order history
    data class SetOrderHistory(val orders: List<Order>) : DataAction
    data class SetLoadingOrderHistory(val loading: Boolean) : DataAction

    // Basket
    data class SetBasketItems(val items: List<OrderedProduct>) : DataAction
    data class AddToBasket(val item: OrderedProduct) : DataAction
    data class RemoveFromBasket(val productId: String) : DataAction
    data class UpdateBasketQuantity(val productId: String, val quantity: Double) : DataAction
    data object ClearBasket : DataAction

    // Error
    data class SetError(val error: String?) : DataAction
}

// ===== MAIN SCREEN UI ACTIONS =====

/**
 * Actions that affect main screen UI state only
 */
sealed interface MainScreenUiAction : BuyerAction {
    data class SelectArticle(val article: Article?) : MainScreenUiAction
    data class SetQuantity(val quantity: Double) : MainScreenUiAction
    data class SetFilter(val filter: ProductFilter) : MainScreenUiAction
    data class ShowNewOrderSnackbar(val show: Boolean) : MainScreenUiAction

    // Trigger adding selected article to basket
    data object AddSelectedToBasket : MainScreenUiAction
}

// ===== BASKET SCREEN UI ACTIONS =====

/**
 * Actions that affect basket screen UI state only
 */
sealed interface BasketScreenUiAction : BuyerAction {
    // Date picker
    data class ShowDatePicker(val show: Boolean) : BasketScreenUiAction
    data class SetSelectedPickupDate(val date: Long?) : BasketScreenUiAction
    data class SetAvailablePickupDates(val dates: List<Long>) : BasketScreenUiAction

    // Order submission
    data class SetSubmitting(val submitting: Boolean) : BasketScreenUiAction
    data class SetSubmitSuccess(val success: Boolean) : BasketScreenUiAction
    data class SetSubmitError(val error: String?) : BasketScreenUiAction

    // Cancel order
    data class SetCancelling(val cancelling: Boolean) : BasketScreenUiAction
    data class SetCancelSuccess(val success: Boolean) : BasketScreenUiAction

    // Reorder
    data class ShowReorderDatePicker(val show: Boolean) : BasketScreenUiAction
    data class SetReordering(val reordering: Boolean) : BasketScreenUiAction
    data class SetReorderSuccess(val success: Boolean) : BasketScreenUiAction

    // Merge dialog
    data class ShowMergeDialog(val show: Boolean) : BasketScreenUiAction
    data class SetExistingOrderForMerge(val order: Order?) : BasketScreenUiAction
    data class SetMergeConflicts(val conflicts: List<BuyerMergeConflict>) : BasketScreenUiAction
    data class UpdateMergeConflictResolution(
        val productId: String,
        val resolution: BuyerMergeResolution
    ) : BasketScreenUiAction
    data class SetMerging(val merging: Boolean) : BasketScreenUiAction

    // Complex operations (trigger side effects in ViewModel)
    data object SubmitOrder : BasketScreenUiAction
    data object CancelOrder : BasketScreenUiAction
    data class ReorderWithNewDate(val newDate: Long) : BasketScreenUiAction
    data object ConfirmMerge : BasketScreenUiAction
}

// ===== PROFILE SCREEN UI ACTIONS =====

/**
 * Actions that affect profile screen UI state only
 */
sealed interface ProfileScreenUiAction : BuyerAction {
    data class SetEditing(val editing: Boolean) : ProfileScreenUiAction
    data class SetEditedName(val name: String) : ProfileScreenUiAction
    data class SetEditedEmail(val email: String) : ProfileScreenUiAction
    data class SetEditedPhone(val phone: String) : ProfileScreenUiAction
    data class SetSaving(val saving: Boolean) : ProfileScreenUiAction
    data class SetSaveError(val error: String?) : ProfileScreenUiAction

    // Start editing with current profile values
    data object StartEditing : ProfileScreenUiAction
    data object SaveProfile : ProfileScreenUiAction
    data object CancelEditing : ProfileScreenUiAction
}

// ===== GLOBAL UI ACTIONS =====

/**
 * Actions that affect global UI state
 */
sealed interface GlobalUiAction : BuyerAction {
    data class SetRefreshing(val refreshing: Boolean) : GlobalUiAction
    data class ShowSnackbar(val snackbar: BuyerSnackbar?) : GlobalUiAction
    data class SetDrawerOpen(val open: Boolean) : GlobalUiAction
}

// ===== AUTH ACTIONS =====

/**
 * Actions related to authentication
 */
sealed interface AuthAction : BuyerAction {
    data object SignInAnonymously : AuthAction
    data class SignInWithEmail(val email: String, val password: String) : AuthAction
    data object SignInWithGoogle : AuthAction
    data object SignOut : AuthAction

    // Internal state updates
    data class SetUser(val user: BuyerUserState) : AuthAction
    data class SetLoading(val loading: Boolean) : AuthAction
    data class SetError(val error: String?) : AuthAction
    data class SetTriggerGoogleSignIn(val trigger: Boolean) : AuthAction
    data class SetTriggerGoogleSignOut(val trigger: Boolean) : AuthAction
}

// ===== META ACTIONS =====

/**
 * Actions that affect app metadata
 */
sealed interface MetaAction : BuyerAction {
    data class SetInitialized(val initialized: Boolean) : MetaAction
    data class SetInitializationStep(val step: BuyerInitStep) : MetaAction
    data class SetDevOrderDateOffset(val days: Int) : MetaAction
}

// ===== LIFECYCLE ACTIONS =====

/**
 * Actions triggered by app lifecycle events
 */
sealed interface LifecycleAction : BuyerAction {
    data object Initialize : LifecycleAction
    data object Refresh : LifecycleAction
    data object OnResume : LifecycleAction
    data object OnPause : LifecycleAction
}

// ===== NAVIGATION ACTIONS =====

/**
 * Actions for navigation events (used to trigger data loading)
 */
sealed interface NavigationAction : BuyerAction {
    data object NavigateToBasket : NavigationAction
    data object NavigateToProfile : NavigationAction
    data object NavigateToOrderHistory : NavigationAction
    data class NavigateToOrderDetail(val orderId: String, val orderDate: String) : NavigationAction
    data object NavigateBack : NavigationAction
}
