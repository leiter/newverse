package com.together.newverse.ui.state.core

import com.together.newverse.ui.state.AuthScreenState
import com.together.newverse.ui.state.BasketScreenState
import com.together.newverse.ui.state.CustomerProfileScreenState
import com.together.newverse.ui.state.MainScreenState

/**
 * Predefined screen IDs for type-safe state management.
 * Each screen in the app should have a corresponding ScreenId object here.
 *
 * Usage:
 * ```
 * val mainState = registry.getState(ScreenIds.Main)
 * registry.updateState(ScreenIds.Basket) { current -> current?.copy(isLoading = true) }
 * ```
 */
object ScreenIds {
    // Buy flavor screens
    object Main : ScreenId<MainScreenState> {
        override val key = "main"
    }

    object Basket : ScreenId<BasketScreenState> {
        override val key = "basket"
    }

    object CustomerProfile : ScreenId<CustomerProfileScreenState> {
        override val key = "customer_profile"
    }

    object Auth : ScreenId<AuthScreenState> {
        override val key = "auth"
    }

    // Generic screen IDs that can be used with custom state types
    // These are useful for dynamically created screens or when the state type
    // is defined elsewhere

    /**
     * Creates a custom ScreenId for a specific state type.
     * Use this when you need a screen ID not predefined in ScreenIds.
     */
    fun <S : Any> custom(screenKey: String): ScreenId<S> = object : ScreenId<S> {
        override val key = screenKey
    }
}
