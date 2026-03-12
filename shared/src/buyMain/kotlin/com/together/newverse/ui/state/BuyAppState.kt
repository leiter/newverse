package com.together.newverse.ui.state

import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.Invitation

/**
 * Flattened state for Buy/Customer flavor.
 *
 * Contains only buyer-relevant fields - no dashboard, product creation,
 * or other seller-specific state.
 */
data class BuyAppState(
    // User state
    val user: UserState = UserState.Guest,
    val requiresLogin: Boolean = false,

    // Seller connection
    val connectedSellerId: String = "",
    val connectedSellerDisplayName: String = "",
    val accessStatus: AccessStatus = AccessStatus.NONE,
    val isAccessStatusLoaded: Boolean = false,

    // Invitation state
    val pendingInvitations: List<Invitation> = emptyList(),
    val showConnectionConfirmDialog: ConnectionConfirmation? = null,

    // Basket
    val basket: BasketState = BasketState(),

    // Navigation
    val navigation: NavigationState = NavigationState(),

    // UI
    val ui: GlobalUiState = GlobalUiState(),

    // Platform sign-in triggers
    val triggerGoogleSignIn: Boolean = false,
    val triggerTwitterSignIn: Boolean = false,
    val triggerAppleSignIn: Boolean = false,
    val triggerGoogleSignOut: Boolean = false,

    // Screen states
    val auth: AuthScreenState = AuthScreenState(),
    val products: ProductsScreenState = ProductsScreenState(),
    val mainScreen: MainScreenState = MainScreenState(),
    val basketScreen: BasketScreenState = BasketScreenState(),
    val customerProfile: CustomerProfileScreenState = CustomerProfileScreenState(),
    val orderHistory: OrderHistoryScreenState = OrderHistoryScreenState(),

    // Messaging
    val messaging: MessagingScreenState = MessagingScreenState(),
    val unreadMessageCount: Int = 0,

    // App metadata
    val meta: AppMetaState = AppMetaState()
) {
    /** Only show demo banner once the real status has been loaded from Firebase. */
    val isDemoMode: Boolean get() = isAccessStatusLoaded && accessStatus != AccessStatus.APPROVED
}

/**
 * Data for the connection confirmation dialog.
 */
data class ConnectionConfirmation(
    val invitation: Invitation,
    val sellerDisplayName: String
)
