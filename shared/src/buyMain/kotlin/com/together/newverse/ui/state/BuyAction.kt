package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.navigation.NavRoutes

/**
 * Base interface for all buyer app actions.
 * Contains only action groups relevant to the buyer flavor.
 */
sealed interface BuyAction

// ===== Navigation Actions =====
sealed interface BuyNavigationAction : BuyAction {
    data class NavigateTo(val route: NavRoutes) : BuyNavigationAction
    data object NavigateBack : BuyNavigationAction
    data object OpenDrawer : BuyNavigationAction
    data object CloseDrawer : BuyNavigationAction
}

// ===== User/Auth Actions =====
sealed interface BuyUserAction : BuyAction {
    data class Login(val email: String, val password: String) : BuyUserAction
    data object LoginWithGoogle : BuyUserAction
    data object LoginWithTwitter : BuyUserAction
    data object LoginWithApple : BuyUserAction
    data object Logout : BuyUserAction
    data object ContinueAsGuest : BuyUserAction
    data class Register(val email: String, val password: String, val name: String) : BuyUserAction
    data class UpdateProfile(val profile: UserProfile) : BuyUserAction
    data class RequestPasswordReset(val email: String) : BuyUserAction
}

// ===== Account Management Actions =====
sealed interface BuyAccountAction : BuyAction {
    data object ShowLogoutWarning : BuyAccountAction
    data object DismissLogoutWarning : BuyAccountAction
    data object ShowLinkAccountDialog : BuyAccountAction
    data object DismissLinkAccountDialog : BuyAccountAction
    data object ShowDeleteAccountDialog : BuyAccountAction
    data object DismissDeleteAccountDialog : BuyAccountAction
    data object ConfirmGuestLogout : BuyAccountAction
    data object LinkWithGoogle : BuyAccountAction
    data class LinkWithEmail(val email: String, val password: String) : BuyAccountAction
    data object ShowEmailLinkingDialog : BuyAccountAction
    data object DismissEmailLinkingDialog : BuyAccountAction
    data class UpdateEmailLinkingEmail(val email: String) : BuyAccountAction
    data class UpdateEmailLinkingPassword(val password: String) : BuyAccountAction
    data class UpdateEmailLinkingConfirmPassword(val confirmPassword: String) : BuyAccountAction
    data object ConfirmDeleteAccount : BuyAccountAction
}

// ===== Product Actions =====
sealed interface BuyProductAction : BuyAction {
    data object LoadProducts : BuyProductAction
    data object RefreshProducts : BuyProductAction
    data class SelectProduct(val product: Article) : BuyProductAction
    data class ViewProductDetail(val productId: String) : BuyProductAction
}

// ===== Basket Actions =====
sealed interface BuyBasketAction : BuyAction {
    data class AddToBasket(val product: Article, val quantity: Double = 1.0) : BuyBasketAction
    data class RemoveFromBasket(val productId: String) : BuyBasketAction
    data class UpdateQuantity(val productId: String, val quantity: Double) : BuyBasketAction
    data object ClearBasket : BuyBasketAction
    data class ApplyPromoCode(val code: String) : BuyBasketAction
    data object StartCheckout : BuyBasketAction
}

// ===== Order Actions =====
sealed interface BuyOrderAction : BuyAction {
    data object LoadOrders : BuyOrderAction
    data class ViewOrderDetail(val orderId: String) : BuyOrderAction
    data class PlaceOrder(val checkoutData: CheckoutData) : BuyOrderAction
    data class CancelOrder(val orderId: String) : BuyOrderAction
}

// ===== UI Actions =====
sealed interface BuyUiAction : BuyAction {
    data class ShowSnackbar(val message: String, val type: SnackbarType = SnackbarType.INFO) : BuyUiAction
    data object HideSnackbar : BuyUiAction
    data class ShowDialog(val dialog: DialogState) : BuyUiAction
    data object HideDialog : BuyUiAction
    data class ShowBottomSheet(val sheet: BottomSheetState) : BuyUiAction
    data object HideBottomSheet : BuyUiAction
    data class SetRefreshing(val isRefreshing: Boolean) : BuyUiAction
    data object ShowPasswordResetDialog : BuyUiAction
    data object HidePasswordResetDialog : BuyUiAction
    data class SetAuthMode(val mode: AuthMode) : BuyUiAction
}

// ===== Profile Actions =====
sealed interface BuyProfileAction : BuyAction {
    data object LoadProfile : BuyProfileAction
    data class UpdateProfileField(val field: String, val value: String) : BuyProfileAction
    data object SaveProfile : BuyProfileAction
    data object CancelProfileEdit : BuyProfileAction
    data object LoadCustomerProfile : BuyProfileAction
    data object LoadOrderHistory : BuyProfileAction
    data object RefreshCustomerProfile : BuyProfileAction
    data class SaveBuyerProfile(
        val displayName: String,
        val email: String,
        val phone: String
    ) : BuyProfileAction
}

// ===== Main Screen Actions =====
sealed interface BuyMainScreenAction : BuyAction {
    data class SelectArticle(val article: Article) : BuyMainScreenAction
    data class ViewProductDetail(val articleId: String) : BuyMainScreenAction
    data class UpdateQuantity(val quantity: Double) : BuyMainScreenAction
    data class UpdateQuantityText(val text: String) : BuyMainScreenAction
    data object AddToCart : BuyMainScreenAction
    data object RemoveFromBasket : BuyMainScreenAction
    data class ToggleFavourite(val articleId: String) : BuyMainScreenAction
    data class SetFilter(val filter: ProductFilter) : BuyMainScreenAction
    data class UpdateSearchQuery(val query: String) : BuyMainScreenAction
    data object ClearSearchQuery : BuyMainScreenAction
    data object Refresh : BuyMainScreenAction
    data object DismissNewOrderSnackbar : BuyMainScreenAction
    data object StartNewOrder : BuyMainScreenAction
}

// ===== Basket Screen Actions =====
sealed interface BuyBasketScreenAction : BuyAction {
    data class AddItem(val item: OrderedProduct) : BuyBasketScreenAction
    data class RemoveItem(val productId: String) : BuyBasketScreenAction
    data class UpdateItemQuantity(val productId: String, val newQuantity: Double) : BuyBasketScreenAction
    data object ClearBasket : BuyBasketScreenAction
    data object Checkout : BuyBasketScreenAction
    data class LoadOrder(val orderId: String, val date: String) : BuyBasketScreenAction
    data object UpdateOrder : BuyBasketScreenAction
    data object EnableEditing : BuyBasketScreenAction
    data object ResetOrderState : BuyBasketScreenAction
    data object ShowDatePicker : BuyBasketScreenAction
    data object HideDatePicker : BuyBasketScreenAction
    data class SelectPickupDate(val date: Long) : BuyBasketScreenAction
    data object LoadAvailableDates : BuyBasketScreenAction
    data object CancelOrder : BuyBasketScreenAction
    data object ShowReorderDatePicker : BuyBasketScreenAction
    data object HideReorderDatePicker : BuyBasketScreenAction
    data class ReorderWithNewDate(val newPickupDate: Long, val currentArticles: List<Article>) : BuyBasketScreenAction
    data object HideMergeDialog : BuyBasketScreenAction
    data class ResolveMergeConflict(val productId: String, val resolution: MergeResolution) : BuyBasketScreenAction
    data object ConfirmMerge : BuyBasketScreenAction
    data object HideDraftWarningDialog : BuyBasketScreenAction
    data object SaveDraftAndLoadOrder : BuyBasketScreenAction
    data object DiscardDraftAndLoadOrder : BuyBasketScreenAction
}
