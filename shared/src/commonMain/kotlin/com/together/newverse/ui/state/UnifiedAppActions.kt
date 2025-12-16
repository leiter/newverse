package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.navigation.NavRoutes

/**
 * Base interface for all app actions
 * Follows Redux/MVI pattern where all state changes are triggered by actions
 */
sealed interface UnifiedAppAction

// ===== Navigation Actions =====
sealed interface UnifiedNavigationAction : UnifiedAppAction {
    data class NavigateTo(val route: NavRoutes) : UnifiedNavigationAction
    data object NavigateBack : UnifiedNavigationAction
    data object OpenDrawer : UnifiedNavigationAction
    data object CloseDrawer : UnifiedNavigationAction
}

// ===== User/Auth Actions =====
sealed interface UnifiedUserAction : UnifiedAppAction {
    data class Login(val email: String, val password: String) : UnifiedUserAction
    data object LoginWithGoogle : UnifiedUserAction
    data object LoginWithTwitter : UnifiedUserAction
    data object Logout : UnifiedUserAction
    data class Register(val email: String, val password: String, val name: String) : UnifiedUserAction
    data class UpdateProfile(val profile: UserProfile) : UnifiedUserAction
    data class RequestPasswordReset(val email: String) : UnifiedUserAction
}

// ===== Product Actions =====
sealed interface UnifiedProductAction : UnifiedAppAction {
    data object LoadProducts : UnifiedProductAction
    data object RefreshProducts : UnifiedProductAction
    data class SelectProduct(val product: Article) : UnifiedProductAction
    data class ViewProductDetail(val productId: String) : UnifiedProductAction
    data class CreateProduct(val productData: ProductFormData) : UnifiedProductAction
    data class UpdateProduct(val productId: String, val productData: ProductFormData) : UnifiedProductAction
    data class DeleteProduct(val productId: String) : UnifiedProductAction
}

// ===== Basket Actions =====
sealed interface UnifiedBasketAction : UnifiedAppAction {
    data class AddToBasket(val product: Article, val quantity: Double = 1.0) : UnifiedBasketAction
    data class RemoveFromBasket(val productId: String) : UnifiedBasketAction
    data class UpdateQuantity(val productId: String, val quantity: Double) : UnifiedBasketAction
    data object ClearBasket : UnifiedBasketAction
    data class ApplyPromoCode(val code: String) : UnifiedBasketAction
    data object StartCheckout : UnifiedBasketAction
}

// ===== Order Actions =====
sealed interface UnifiedOrderAction : UnifiedAppAction {
    data object LoadOrders : UnifiedOrderAction
    data class ViewOrderDetail(val orderId: String) : UnifiedOrderAction
    data class PlaceOrder(val checkoutData: CheckoutData) : UnifiedOrderAction
    data class CancelOrder(val orderId: String) : UnifiedOrderAction
}

// ===== UI Actions =====
sealed interface UnifiedUiAction : UnifiedAppAction {
    data class ShowSnackbar(val message: String, val type: SnackbarType = SnackbarType.INFO) : UnifiedUiAction
    data object HideSnackbar : UnifiedUiAction
    data class ShowDialog(val dialog: DialogState) : UnifiedUiAction
    data object HideDialog : UnifiedUiAction
    data class ShowBottomSheet(val sheet: BottomSheetState) : UnifiedUiAction
    data object HideBottomSheet : UnifiedUiAction
    data class SetRefreshing(val isRefreshing: Boolean) : UnifiedUiAction
    data object ShowPasswordResetDialog : UnifiedUiAction
    data object HidePasswordResetDialog : UnifiedUiAction
}

// ===== Profile Actions =====
sealed interface UnifiedProfileAction : UnifiedAppAction {
    data object LoadProfile : UnifiedProfileAction
    data class UpdateProfileField(val field: String, val value: String) : UnifiedProfileAction
    data object SaveProfile : UnifiedProfileAction
    data object CancelProfileEdit : UnifiedProfileAction
    data object LoadCustomerProfile : UnifiedProfileAction
    data object LoadOrderHistory : UnifiedProfileAction
    data object RefreshCustomerProfile : UnifiedProfileAction
    data class SaveBuyerProfile(
        val displayName: String,
        val email: String,
        val phone: String
    ) : UnifiedProfileAction
}

// ===== Search Actions =====
sealed interface UnifiedSearchAction : UnifiedAppAction {
    data class Search(val query: String) : UnifiedSearchAction
    data object ClearSearch : UnifiedSearchAction
    data class AddToSearchHistory(val query: String) : UnifiedSearchAction
}

// ===== Filter Actions =====
sealed interface UnifiedFilterAction : UnifiedAppAction {
    data class ApplyFilter(val key: String, val value: FilterValue) : UnifiedFilterAction
    data class RemoveFilter(val key: String) : UnifiedFilterAction
    data object ClearFilters : UnifiedFilterAction
    data class SaveFilter(val name: String) : UnifiedFilterAction
    data class LoadSavedFilter(val filterId: String) : UnifiedFilterAction
}

// ===== Main Screen Actions =====
sealed interface UnifiedMainScreenAction : UnifiedAppAction {
    data class SelectArticle(val article: Article) : UnifiedMainScreenAction
    data class UpdateQuantity(val quantity: Double) : UnifiedMainScreenAction
    data class UpdateQuantityText(val text: String) : UnifiedMainScreenAction
    data object AddToCart : UnifiedMainScreenAction
    data object RemoveFromBasket : UnifiedMainScreenAction
    data class ToggleFavourite(val articleId: String) : UnifiedMainScreenAction
    data class SetFilter(val filter: ProductFilter) : UnifiedMainScreenAction
    data object Refresh : UnifiedMainScreenAction
    data object DismissNewOrderSnackbar : UnifiedMainScreenAction
    data object StartNewOrder : UnifiedMainScreenAction
}

// ===== Basket Screen Actions (full checkout/order workflow) =====
sealed interface UnifiedBasketScreenAction : UnifiedAppAction {
    // Item management
    data class AddItem(val item: OrderedProduct) : UnifiedBasketScreenAction
    data class RemoveItem(val productId: String) : UnifiedBasketScreenAction
    data class UpdateItemQuantity(val productId: String, val newQuantity: Double) : UnifiedBasketScreenAction
    data object ClearBasket : UnifiedBasketScreenAction

    // Checkout flow
    data object Checkout : UnifiedBasketScreenAction
    data class LoadOrder(val orderId: String, val date: String) : UnifiedBasketScreenAction
    data object UpdateOrder : UnifiedBasketScreenAction
    data object EnableEditing : UnifiedBasketScreenAction
    data object ResetOrderState : UnifiedBasketScreenAction

    // Pickup date selection
    data object ShowDatePicker : UnifiedBasketScreenAction
    data object HideDatePicker : UnifiedBasketScreenAction
    data class SelectPickupDate(val date: Long) : UnifiedBasketScreenAction
    data object LoadAvailableDates : UnifiedBasketScreenAction

    // Cancel order
    data object CancelOrder : UnifiedBasketScreenAction

    // Reorder with new date
    data object ShowReorderDatePicker : UnifiedBasketScreenAction
    data object HideReorderDatePicker : UnifiedBasketScreenAction
    data class ReorderWithNewDate(val newPickupDate: Long, val currentArticles: List<Article>) : UnifiedBasketScreenAction

    // Merge dialog actions
    data object HideMergeDialog : UnifiedBasketScreenAction
    data class ResolveMergeConflict(val productId: String, val resolution: MergeResolution) : UnifiedBasketScreenAction
    data object ConfirmMerge : UnifiedBasketScreenAction
}

/**
 * Convenience builders for common actions
 */
object UnifiedActions {
    // Navigation
    fun navigateTo(route: NavRoutes) = UnifiedNavigationAction.NavigateTo(route)
    fun back() = UnifiedNavigationAction.NavigateBack

    // Products
    fun loadProducts() = UnifiedProductAction.LoadProducts
    fun refreshProducts() = UnifiedProductAction.RefreshProducts
    fun selectProduct(product: Article) = UnifiedProductAction.SelectProduct(product)

    // Basket
    fun addToBasket(product: Article, quantity: Double = 1.0) =
        UnifiedBasketAction.AddToBasket(product, quantity)
    fun removeFromBasket(productId: String) = UnifiedBasketAction.RemoveFromBasket(productId)
    fun updateQuantity(productId: String, quantity: Double) =
        UnifiedBasketAction.UpdateQuantity(productId, quantity)
    fun clearBasket() = UnifiedBasketAction.ClearBasket

    // UI
    fun showSnackbar(message: String, type: SnackbarType = SnackbarType.INFO) =
        UnifiedUiAction.ShowSnackbar(message, type)
    fun hideSnackbar() = UnifiedUiAction.HideSnackbar
    fun showError(message: String) =
        UnifiedUiAction.ShowDialog(DialogState.Error(message = message))
    fun showConfirmation(title: String, message: String) =
        UnifiedUiAction.ShowDialog(DialogState.Confirmation(title, message))

    // User
    fun login(email: String, password: String) = UnifiedUserAction.Login(email, password)
    fun logout() = UnifiedUserAction.Logout
    fun register(email: String, password: String, name: String) =
        UnifiedUserAction.Register(email, password, name)

    // Search
    fun search(query: String) = UnifiedSearchAction.Search(query)
    fun clearSearch() = UnifiedSearchAction.ClearSearch
}