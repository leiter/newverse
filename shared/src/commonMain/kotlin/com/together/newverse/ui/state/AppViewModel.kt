package com.together.newverse.ui.state

import kotlinx.coroutines.flow.StateFlow

/**
 * Common interface for flavor-specific ViewModels
 *
 * This interface defines the contract that both BuyAppViewModel and SellAppViewModel
 * must implement, allowing the UI layer to work with either flavor transparently.
 */
interface AppViewModel {
    /**
     * The unified app state
     */
    val state: StateFlow<UnifiedAppState>

    /**
     * Dispatch an action to the ViewModel
     */
    fun dispatch(action: UnifiedAppAction)

    /**
     * Reset Google Sign-In trigger after it's been handled
     */
    fun resetGoogleSignInTrigger()

    /**
     * Reset Twitter Sign-In trigger after it's been handled
     */
    fun resetTwitterSignInTrigger()

    /**
     * Reset Apple Sign-In trigger after it's been handled
     */
    fun resetAppleSignInTrigger()

    /**
     * Reset Google Sign-Out trigger after it's been handled
     */
    fun resetGoogleSignOutTrigger()
}
