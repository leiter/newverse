package com.together.newverse.ui.state

/**
 * Flattened state for Sell/Merchant flavor.
 *
 * Contains only seller-relevant fields - no basket, customer profile,
 * order history, or other buyer-specific state.
 */
data class SellAppState(
    // User state
    val user: UserState = UserState.Guest,
    val requiresLogin: Boolean = false,

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

    /**
     * Changing the seller's own password. Lives here rather than on
     * `SellerProfileViewModel` so the success snackbar goes through the same
     * host the scaffold already feeds from this state, and so the buy and sell
     * flavors drive the shared dialog the same way.
     */
    val changePassword: ChangePasswordState = ChangePasswordState(),

    /**
     * Whether this account is backed by a password. A Google-only seller has
     * nothing to change, so the entry point stays hidden rather than offering
     * a dialog that could only fail.
     */
    val canChangePassword: Boolean = false,

    // Messaging
    val unreadMessageCount: Int = 0,

    // Access requests
    val pendingAccessRequestCount: Int = 0,

    // App metadata
    val meta: AppMetaState = AppMetaState()
)
