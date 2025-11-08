package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.ui.navigation.NavRoutes

/**
 * All possible actions that can be performed on the Home screen
 *
 * This demonstrates a comprehensive action system that could eventually
 * be part of a larger AppAction sealed hierarchy for single-ViewModel architecture
 */
sealed interface HomeAction {
    // Navigation Actions
    sealed interface Navigation : HomeAction {
        data class NavigateTo(val route: NavRoutes) : Navigation
        data object OpenDrawer : Navigation
        data object CloseDrawer : Navigation
        data object NavigateBack : Navigation
        data class HandleDeepLink(val url: String) : Navigation
    }

    // User Actions
    sealed interface User : HomeAction {
        data class Login(val email: String, val password: String) : User
        data object Logout : User
        data object RefreshProfile : User
        data class UpdateProfile(val name: String? = null, val email: String? = null) : User
        data class SwitchRole(val role: UserRole) : User
    }

    // Product Catalog Actions
    sealed interface Catalog : HomeAction {
        data object LoadProducts : Catalog
        data object RefreshProducts : Catalog
        data class SelectProduct(val product: Article) : Catalog
        data class SetQuantity(val quantity: Double) : Catalog
        data class SelectCategory(val category: String?) : Catalog
        data class ChangeSortBy(val sortBy: ProductSortBy) : Catalog
        data class ApplyFilters(val filters: FilterState) : Catalog
        data object ClearFilters : Catalog
    }

    // Basket Actions
    sealed interface Basket : HomeAction {
        data class AddToBasket(val product: Article, val quantity: Double) : Basket
        data class RemoveFromBasket(val productId: String) : Basket
        data class UpdateQuantity(val productId: String, val quantity: Double) : Basket
        data object ClearBasket : Basket
        data class ApplyDiscount(val code: String) : Basket
        data object RemoveDiscount : Basket
        data object StartCheckout : Basket
        data object CompleteCheckout : Basket
    }

    // Search Actions
    sealed interface Search : HomeAction {
        data object OpenSearch : Search
        data object CloseSearch : Search
        data class UpdateQuery(val query: String) : Search
        data object ExecuteSearch : Search
        data class SelectSuggestion(val suggestion: String) : Search
        data class SelectRecentSearch(val query: String) : Search
        data object ClearRecentSearches : Search
    }

    // UI Actions
    sealed interface UI : HomeAction {
        data object DismissSnackbar : UI
        data class ShowSnackbar(
            val message: String,
            val type: SnackbarType = SnackbarType.INFO,
            val actionLabel: String? = null
        ) : UI
        data object SnackbarActionClicked : UI

        data class ShowDialog(val dialog: DialogState) : UI
        data object DismissDialog : UI
        data object DialogConfirmed : UI
        data object DialogCancelled : UI

        data class ShowBottomSheet(val sheet: BottomSheetState) : UI
        data object DismissBottomSheet : UI

        data object StartRefresh : UI
        data object EndRefresh : UI
    }

    // Connection Actions
    sealed interface Connection : HomeAction {
        data object CheckConnection : Connection
        data object RetryConnection : Connection
        data object StartSync : Connection
        data class UpdateSyncProgress(val progress: Float) : Connection
        data object CompleteSync : Connection
    }
}

/**
 * Convenience type aliases for cleaner code
 */
typealias NavAction = HomeAction.Navigation
typealias UserAction = HomeAction.User
typealias CatalogAction = HomeAction.Catalog
typealias BasketAction = HomeAction.Basket
typealias SearchAction = HomeAction.Search
typealias UiAction = HomeAction.UI
typealias ConnectionAction = HomeAction.Connection