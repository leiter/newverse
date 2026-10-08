package com.together.newverse.ui.state

import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.Invitation
import com.together.newverse.domain.model.MissingProfileData
import com.together.newverse.domain.model.missingProfileData
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderReminderSettings

/**
 * Flattened state for Buy/Customer flavor.
 *
 * Contains only buyer-relevant fields - no dashboard, product creation,
 * or other seller-specific state.
 */
data class BuyAppState(
    // User state
    val user: UserState = UserState.Loading,
    /** Resolved from the auth session, not from the email address. */
    val authProvider: AuthProvider = AuthProvider.ANONYMOUS,
    /** Every provider linked to the account, for the profile badge. */
    val linkedProviders: List<AuthProvider> = emptyList(),
    /**
     * Whether the Firebase user is anonymous, read from the auth session.
     *
     * Defaults to false on purpose: until it resolves, a real account holder
     * must not be shown the guest branch and its account-wiping button.
     */
    val isAnonymousUser: Boolean = false,
    val requiresLogin: Boolean = false,

    // Seller connection
    val connectedSellerId: String = "",
    val connectedSellerDisplayName: String = "",
    val accessStatus: AccessStatus = AccessStatus.NONE,
    val isAccessStatusLoaded: Boolean = false,
    val isRequestingAccess: Boolean = false,

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
    /**
     * The buyer's order-reminder settings. Device-local, not part of the Firebase
     * profile, so it lives beside [customerProfile] rather than inside it.
     */
    val orderReminder: OrderReminderSettings = OrderReminderSettings(),
    /**
     * True when the reminder is switched on but the system will not show notifications
     * for this app, so the UI can say so instead of silently never reminding anyone.
     */
    val notificationsBlocked: Boolean = false,
    val orderHistory: OrderHistoryScreenState = OrderHistoryScreenState(),

    // Messaging
    val messaging: MessagingScreenState = MessagingScreenState(),
        val unreadMessageCount: Int = 0,

    // Merge dialog for past orders
    val showHistoryMergeDialog: Boolean = false,
    val tappedHistoryOrder: Order? = null,

    // Navigation trigger for navigating to the basket as a top-level destination
    val navigateToBasketAsTopLevel: Boolean = false,

    // Scroll trigger for access card on profile screen
    val triggerScrollToAccessInProfile: Boolean = false,

    // Profile completeness
    val showProfileIncompleteDialog: Boolean = false,
    val pendingConnectToken: Pair<String, String>? = null, // (sellerId, token) awaiting profile completion

    // App metadata
    val meta: AppMetaState = AppMetaState()
) {
    /** Only treat the buyer as a demo buyer once the real status has been loaded from Firebase. */
    val isDemoMode: Boolean get() = isAccessStatusLoaded && accessStatus != AccessStatus.APPROVED

    /**
     * The one completeness rule, shared by the access gate and every prompt
     * about it. See [MissingProfileData].
     */
    val missingProfileData: MissingProfileData get() = customerProfile.profile.missingProfileData()

    val isProfileIncomplete: Boolean get() = !missingProfileData.isComplete

    /**
     * Whether the profile tab carries an attention badge.
     *
     * A badge promises a task the buyer can finish, so it appears only once the
     * missing data actually stands in their way: in production, where real
     * orders carry it to the seller, or after a seller token has been parked
     * waiting for it. A demo buyer who is only browsing is not nagged.
     */
    val showProfileAttentionBadge: Boolean
        get() = isProfileIncomplete &&
            (accessStatus == AccessStatus.APPROVED || pendingConnectToken != null)
}

/**
 * Everything belonging to the signed-in buyer, cleared.
 *
 * Sign-out, the guest wipe and account deletion all need the same set. Keeping
 * it in one place is deliberate: these three paths each used to clear a
 * slightly different subset, and every difference between them showed the next
 * user something of the previous one's - profile, favourites, order history,
 * seller connection.
 *
 * Left alone on purpose: [user], [requiresLogin] and the sign-out triggers,
 * which each caller sets to suit its flow, and the seller's catalogue in
 * [mainScreen] and [products], which is not the buyer's data.
 */
fun BuyAppState.clearedForSignOut(): BuyAppState = copy(
    authProvider = AuthProvider.ANONYMOUS,
    linkedProviders = emptyList(),
    isAnonymousUser = false,
    connectedSellerId = "",
    connectedSellerDisplayName = "",
    accessStatus = AccessStatus.NONE,
    isAccessStatusLoaded = false,
    isRequestingAccess = false,
    pendingInvitations = emptyList(),
    showConnectionConfirmDialog = null,
    basket = BasketState(),
    auth = AuthScreenState(),
    basketScreen = BasketScreenState(),
    customerProfile = CustomerProfileScreenState(),
    orderHistory = OrderHistoryScreenState(),
    messaging = MessagingScreenState(),
    unreadMessageCount = 0,
    mainScreen = mainScreen.copy(favouriteArticles = emptyList()),
    showHistoryMergeDialog = false,
    tappedHistoryOrder = null,
    navigateToBasketAsTopLevel = false,
    triggerScrollToAccessInProfile = false,
    showProfileIncompleteDialog = false,
    pendingConnectToken = null
)

/**
 * Data for the connection confirmation dialog.
 */
data class ConnectionConfirmation(
    val invitation: Invitation,
    val sellerDisplayName: String
)
