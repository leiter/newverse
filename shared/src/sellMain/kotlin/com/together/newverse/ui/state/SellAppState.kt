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
    val products: ProductsScreenState = ProductsScreenState(),

    // App metadata
    val meta: AppMetaState = AppMetaState()
)
