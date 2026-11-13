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
    data object CollapseHomeAnnouncement : BuyUiAction

    /**
     * The buyer closed the seller's message box on [location], collapsing it to an icon.
     *
     * Carries the slot rather than the key, for the reason [CollapseHomeAnnouncement]
     * carries nothing: the state resolves which row is in that slot, so a stale
     * recomposition cannot suppress a question the seller published while the frame
     * was in flight. The slot *is* needed — there are two input slots, and closing
     * the one on the profile screen must not take the one on home away.
     */
    data class CollapsePrompt(val location: ActionLocation) : BuyUiAction

    /**
     * The buyer tapped the icon a collapsed row left behind, and wants it back.
     *
     * The half that never existed. Closing used to be permanent — the dismissal set
     * only ever grew — so a buyer who caught the × by accident had lost that
     * announcement until the seller reworded it. Carries the slot rather than the
     * key, like its two counterparts, so a stale frame cannot reopen a row the seller
     * has since replaced.
     */
    data class ExpandRow(val location: ActionLocation) : BuyUiAction

    /**
     * The buyer long-pressed a collapsed row's icon: hide it for good.
     *
     * The deliberate way out, as against [CollapseHomeAnnouncement] and
     * [CollapsePrompt], which are the reversible one. Both exist because they answer
     * different things: "not now" and "not ever". Collapsing used to have to serve as
     * both, which made the accidental tap and the considered one indistinguishable.
     *
     * Behind a long-press rather than a second button, because a destructive choice
     * one tap from a non-destructive one is the problem collapsing was introduced to
     * fix. The snackbar that follows carries an undo.
     */
    data class DismissRow(val location: ActionLocation) : BuyUiAction

    /**
     * Undo a [DismissRow] — the buyer took the snackbar's offer.
     *
     * Carries the **key**, not the slot, and is the one action here that does. Every
     * other one takes a slot so a stale frame cannot act on a row the seller has since
     * replaced; an undo wants the opposite guarantee. It must put back exactly what
     * was just hidden, even if the published row has changed underneath in the seconds
     * the snackbar was up.
     */
    data class RestoreRow(val dismissalKey: String) : BuyUiAction

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
     * The buyer pressed send in the choice picker on [location].
     *
     * Carries the picked **labels**, resolved from state in every other respect for
     * [SendPrompt]'s reason. Labels rather than positions because that is what gets
     * stored — see `SellerInboxMessage.selections` — and because a position sent from
     * a frame drawn against an older row would file the answer under a different
     * option than the buyer tapped.
     *
     * The selections are checked against the row the *state* holds before anything is
     * written, so a label that is no longer offered is dropped rather than sent.
     */
    data class SendChoice(
        val location: ActionLocation,
        val selections: List<String>,
    ) : BuyUiAction

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

    /**
     * The buyer does not want this demo order after all: remove the record outright.
     *
     * The other half of [ConfirmDemoOrder], and not a Storno — nobody ever saw this
     * order, so there is no one to tell. See `Order.isRemovedNotCancelled`.
     */
    data class DiscardDemoOrder(val order: Order) : BuyProfileAction
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
    /**
     * The hero card's amount field gained or lost focus. Reported rather than
     * inferred from the keyboard: the search field raises the same keyboard, and a
     * buyer searching has not asked to be shown their basket.
     */
    data class SetQuantityInputFocused(val focused: Boolean) : BuyMainScreenAction
    /**
     * Tap on the favourite chip. Carries no payload on purpose: the chip has three
     * states and the next one follows from the current one, which
     * [ProductFilterState.cycleFavourite] owns — the UI does not work it out itself.
     */
    data object CycleFavouriteFilter : BuyMainScreenAction
    /** Tap on a category chip. Tapping the active one clears it. */
    data class ToggleCategoryFilter(val category: String) : BuyMainScreenAction
    /** The "Alle" chip: every axis bypassed. Leaves the typed search query alone. */
    data object ResetFilters : BuyMainScreenAction
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
    /**
     * Tap on *Zugang anfragen*: open the sheet that composes the request.
     *
     * Opening rather than sending. The request carries a name and, optionally, a line
     * the seller can recognise the buyer by, and asking for those is the request —
     * not a precondition checked before one can be made.
     */
    data object RequestAccess : BuySellerAction
    data class UpdateAccessRequestName(val name: String) : BuySellerAction
    data class UpdateAccessRequestNote(val note: String) : BuySellerAction
    /** Send it, or redeem the invite token the sheet is standing in front of. */
    data object SubmitAccessRequest : BuySellerAction
    data object DismissAccessRequest : BuySellerAction

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

    /**
     * When the buyer means to collect this order, `HH:mm`, or blank for no answer.
     *
     * Per order, like the address beside it. Asked at checkout and again while the
     * order is editable, because it is a fact about one collection — and because it
     * used to be a required profile field that stood in front of the access request
     * and reached no seller screen at all.
     */
    data class SetPickupTime(val time: String) : BuyBasketScreenAction

    /** Keep the time chosen here as the buyer's usual one. Opt-in; blank is ignored. */
    data object SavePickupTimeToProfile : BuyBasketScreenAction

    /**
     * Who is collecting this order — pre-filled with the buyer's own name.
     *
     * Per order because the person who placed it routinely is not the person who
     * turns up: a flatmate, a partner, somebody sent round on the way home. Left as
     * it was pre-filled it means "me", and the seller's screens say nothing extra.
     */
    data class SetPickupName(val name: String) : BuyBasketScreenAction

    /**
     * A number to reach whoever collects. Optional, pre-filled from the profile.
     *
     * The first path by which a phone number actually reaches the seller: the profile
     * has held one for as long as there has been a profile page, and no order ever
     * carried it.
     */
    data class SetPickupPhone(val phone: String) : BuyBasketScreenAction

    /** Keep the number given here in the profile. Opt-in; blank is ignored. */
    data object SavePickupPhoneToProfile : BuyBasketScreenAction
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
