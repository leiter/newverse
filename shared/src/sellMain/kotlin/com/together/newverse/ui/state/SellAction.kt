package com.together.newverse.ui.state

import com.together.newverse.ui.navigation.NavRoutes

/**
 * Base interface for all seller app actions.
 * Contains only action groups relevant to the seller flavor.
 */
sealed interface SellAction

// ===== Navigation Actions =====
sealed interface SellNavigationAction : SellAction {
    data class NavigateTo(val route: NavRoutes) : SellNavigationAction
    data object NavigateBack : SellNavigationAction
    data object OpenDrawer : SellNavigationAction
    data object CloseDrawer : SellNavigationAction
}

// ===== User/Auth Actions =====
sealed interface SellUserAction : SellAction {
    data class Login(val email: String, val password: String) : SellUserAction
    data object LoginWithGoogle : SellUserAction
    data object LoginWithTwitter : SellUserAction
    data object LoginWithApple : SellUserAction
    data object Logout : SellUserAction
    data object ContinueAsGuest : SellUserAction
    data class Register(val email: String, val password: String, val name: String) : SellUserAction
    data class UpdateProfile(val profile: UserProfile) : SellUserAction
    data class RequestPasswordReset(val email: String) : SellUserAction
}

// ===== Order Actions =====
sealed interface SellOrderAction : SellAction {
    data object LoadOrders : SellOrderAction
    data class ViewOrderDetail(val orderId: String) : SellOrderAction
    data class PlaceOrder(val checkoutData: CheckoutData) : SellOrderAction
    data class CancelOrder(val orderId: String) : SellOrderAction
}

// ===== UI Actions =====
sealed interface SellUiAction : SellAction {
    data class ShowSnackbar(val message: String, val type: SnackbarType = SnackbarType.INFO) : SellUiAction
    data object HideSnackbar : SellUiAction
    data class ShowDialog(val dialog: DialogState) : SellUiAction
    data object HideDialog : SellUiAction
    data class ShowBottomSheet(val sheet: BottomSheetState) : SellUiAction
    data object HideBottomSheet : SellUiAction
    data class SetRefreshing(val isRefreshing: Boolean) : SellUiAction
    data object ShowPasswordResetDialog : SellUiAction
    data object HidePasswordResetDialog : SellUiAction
    data class SetAuthMode(val mode: AuthMode) : SellUiAction
}

// ===== Profile Actions =====
sealed interface SellProfileAction : SellAction {
    data object LoadProfile : SellProfileAction
    data class UpdateProfileField(val field: String, val value: String) : SellProfileAction
    data object SaveProfile : SellProfileAction
    data object CancelProfileEdit : SellProfileAction
}

// ===== Customer Management Actions =====
sealed interface SellCustomerAction : SellAction {
    data class BlockCustomer(val buyerId: String) : SellCustomerAction
    data class UnblockCustomer(val buyerId: String) : SellCustomerAction
}

// ===== Messaging Actions =====
sealed interface SellMessagingAction : SellAction {
    data class OpenConversation(val conversationId: String) : SellMessagingAction
}

// ===== Invitation Actions =====
sealed interface SellInvitationAction : SellAction {
    data class GenerateInvitation(val expiryMinutes: Int = 1440) : SellInvitationAction
    data class SendInvitationToBuyer(val buyerId: String) : SellInvitationAction
    data class RevokeInvitation(val invitationId: String) : SellInvitationAction
}
