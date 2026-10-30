package com.together.newverse.ui.state

import com.together.newverse.domain.model.ActionLocation
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.DeliveryAddress
import com.together.newverse.domain.model.Fulfilment
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.SellerUiAction
import com.together.newverse.ui.navigation.NavRoutes
import kotlinx.datetime.DayOfWeek

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
    data object ClearPendingNavigation : BuyNavigationAction
    data object NavigationToBasketHandled : BuyNavigationAction
    data object TriggerScrollToAccessInProfile : BuyNavigationAction
    data object ScrollToAccessInProfileHandled : BuyNavigationAction
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
    data object ShowChangePasswordDialog : BuyAccountAction
    data object DismissChangePasswordDialog : BuyAccountAction
    data class UpdateChangePasswordCurrent(val password: String) : BuyAccountAction
    data class UpdateChangePasswordNew(val password: String) : BuyAccountAction
    data class UpdateChangePasswordConfirm(val password: String) : BuyAccountAction
    data object SubmitChangePassword : BuyAccountAction
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
    /**
     * The buyer closed the seller's home-title announcement.
     *
     * Carries no key: the only announcement that can be dismissed is the one being
     * shown, and the state already knows which that is. Passing a key from the UI
     * would let a stale recomposition dismiss an announcement the seller replaced
     * while the frame was in flight.
     */
    data object DismissHomeAnnouncement : BuyUiAction

    /**
     * The buyer closed the seller's message box on [location].
     *
     * Carries the slot rather than the key, for the reason [DismissHomeAnnouncement]
     * carries nothing: the state resolves which row is in that slot, so a stale
     * recomposition cannot suppress a question the seller published while the frame
     * was in flight. The slot *is* needed — there are two input slots, and closing
     * the one on the profile screen must not take the one on home away.
     */
    data class DismissPrompt(val location: ActionLocation) : BuyUiAction

    /**
     * The buyer pressed send in the message box on [location].
     *
     * The text comes from the UI because it is the buyer's own typing and lives in
     * the field until it is sent; everything else about the message — who asked, what
     * was asked, which kind of box — is resolved from state, so a caller cannot
     * misattribute an answer.
     */
    data class SendPrompt(val location: ActionLocation, val text: String) : BuyUiAction

    /**
     * A failed send has been shown to the buyer, so stop holding on to it.
     *
     * The failure is kept in state as a `StringResource` rather than a resolved
     * message, because a ViewModel has no resource environment to resolve one in —
     * the lesson the change-password dialog's error slots learned. Resolving it and
     * putting it in front of the buyer is the top bar's job; this is how the top bar
     * says it has done so, and why a refusal is announced once rather than on every
     * recomposition.
     */
    data object ClearPromptError : BuyUiAction

    /**
     * The buyer tapped a published button, so a row the seller marked
     * [com.together.newverse.domain.model.ActionPersistence.HIDE_ON_ACTION] should now
     * go.
     *
     * Carries the row rather than its id, because hiding it needs the text as well:
     * the key is the id *and* a hash of the wording, so a reworded row comes back.
     * Dispatched for *every* tap and filtered in the handler — the top bar should not
     * have to know what the flag means, and the one place that does is the one place
     * that decides.
     */
    data class NotePublishedActionUsed(val action: SellerUiAction) : BuyUiAction

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
    data class SetAuthError(val error: String) : BuyUiAction
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
    data class HistoryOrderTapped(val order: Order) : BuyProfileAction

    /**
     * Take up a demo order the buyer still holds now that the seller has approved them.
     * Its items go into the basket; its pickup slot does not, because that is the thing
     * being confirmed.
     */
    data class ConfirmDemoOrder(val order: Order) : BuyProfileAction
    data object MergeHistoryOrder : BuyProfileAction
    data object DiscardAndLoadHistoryOrder : BuyProfileAction
    data object HideHistoryMergeDialog : BuyProfileAction

    /**
     * The buyer saved a new pickup/delivery preference.
     *
     * Carries the value rather than having the app re-read the profile, which would
     * race the save. A placed order is not re-aimed by it — the buyer is told.
     */
    data class FulfilmentPreferenceSaved(val isSelfPickup: Boolean) : BuyProfileAction
}

// ===== Order Reminder Actions =====
sealed interface BuyReminderAction : BuyAction {
    /** Read the stored settings and arm the next reminder. Safe to repeat. */
    data object LoadReminderSettings : BuyReminderAction
    data class SetReminderEnabled(val enabled: Boolean) : BuyReminderAction
    /** Add or remove one weekday. */
    data class ToggleReminderDay(val day: DayOfWeek) : BuyReminderAction
    data class SetReminderTime(val hour: Int, val minute: Int) : BuyReminderAction
    data class SetReminderOnlyWhenNotOrdered(val onlyWhenNotOrdered: Boolean) : BuyReminderAction
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

// ===== Seller Connection Actions =====
sealed interface BuySellerAction : BuyAction {
    data class ConnectWithToken(val sellerId: String, val buyerToken: String) : BuySellerAction
    data class ConnectWithInvitation(
        val sellerId: String,
        val invitationId: String,
        val expiresAt: Long
    ) : BuySellerAction
    data class AcceptPendingInvitation(val invitationId: String) : BuySellerAction
    data class RejectPendingInvitation(val invitationId: String) : BuySellerAction
    data object ConfirmConnection : BuySellerAction
    data object DismissConnectionDialog : BuySellerAction
    data object RequestAccess : BuySellerAction
    data object DismissProfileIncompleteDialog : BuySellerAction
    data object RetryPendingConnection : BuySellerAction

    /** Open the market picker deliberately, from the profile rather than onboarding. */
    data object ShowMarketPicker : BuySellerAction

    /** The buyer said where they collect. Writes `BuyerProfile.defaultMarket`. */
    data class ChooseDefaultMarket(val marketId: String) : BuySellerAction

    /** Closed without answering. Asked again next launch — see `marketChoiceDeferred`. */
    data object DismissMarketPicker : BuySellerAction
}

// ===== Messaging Actions =====
sealed interface BuyMessagingAction : BuyAction {
    data object LoadConversations : BuyMessagingAction
    data class OpenConversation(val conversationId: String) : BuyMessagingAction
    data class AddBuyerContact(val buyerId: String, val name: String) : BuyMessagingAction
}

// ===== Basket Screen Actions =====
sealed interface BuyBasketScreenAction : BuyAction {
    data class AddItem(val item: OrderedProduct) : BuyBasketScreenAction
    data class RemoveItem(val productId: String) : BuyBasketScreenAction
    data class UpdateItemQuantity(val productId: String, val newQuantity: Double) : BuyBasketScreenAction
    data object ClearBasket : BuyBasketScreenAction
    /** Pressed Bestellen: validates, then opens the confirmation. */
    data object Checkout : BuyBasketScreenAction

    /** Confirmed in the dialog: the order is written. */
    data object ConfirmCheckout : BuyBasketScreenAction

    /** Backed out of the confirmation. The basket is untouched. */
    data object DismissCheckout : BuyBasketScreenAction

    /** Pickup or delivery, for this order only. */
    data class SetFulfilment(val fulfilment: Fulfilment) : BuyBasketScreenAction

    /** Edit the address this order is delivered to. */
    data class SetDeliveryAddress(val address: DeliveryAddress) : BuyBasketScreenAction

    /**
     * Keep the address typed at checkout: write street and number to the profile.
     *
     * Opt-in, and separate from [SetDeliveryAddress], because the order snapshots its
     * own address — a one-off delivery somewhere else must not move the buyer's home.
     * The note is not copied; it belongs to this order, not to the address.
     */
    data object SaveDeliveryAddressToProfile : BuyBasketScreenAction
        data class LoadOrder(val orderId: String, val date: String, val forceLoad: Boolean = false) : BuyBasketScreenAction
    data object UpdateOrder : BuyBasketScreenAction
    data object EnableEditing : BuyBasketScreenAction
    data object DisableEditing : BuyBasketScreenAction
    data object ResetOrderState : BuyBasketScreenAction
    data object ShowDatePicker : BuyBasketScreenAction
    data object HideDatePicker : BuyBasketScreenAction

    /** The buyer accepted the prices at the market they just chose. */
    data object ConfirmSlotPriceChange : BuyBasketScreenAction

    /** The buyer declined; the slot they already had is kept. */
    data object DismissSlotPriceChange : BuyBasketScreenAction
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

    // Cancel confirmation dialog
    data object ShowCancelConfirmDialog : BuyBasketScreenAction
    data object HideCancelConfirmDialog : BuyBasketScreenAction
}
