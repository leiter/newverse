package com.together.newverse.ui.state

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

    // App metadata
    val meta: AppMetaState = AppMetaState()
)
