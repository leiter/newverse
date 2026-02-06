package com.together.newverse.ui.state.buy

import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.state.BuyAppViewModel
import kotlinx.coroutines.flow.update

/**
 * Navigation extension functions for BuyAppViewModel
 *
 * Handles navigation between routes, back navigation, and drawer state.
 *
 * Extracted functions:
 * - navigateTo
 * - navigateBack
 * - openDrawer
 * - closeDrawer
 */

internal fun BuyAppViewModel.navigateTo(route: NavRoutes) {
    _state.update { current ->
        current.copy(
            navigation = current.navigation.copy(
                previousRoute = current.navigation.currentRoute,
                currentRoute = route,
                backStack = current.navigation.backStack + route
            )
        )
    }
}

internal fun BuyAppViewModel.navigateBack() {
    _state.update { current ->
        val backStack = current.navigation.backStack
        if (backStack.size > 1) {
            val newBackStack = backStack.dropLast(1)
            current.copy(
                navigation = current.navigation.copy(
                    currentRoute = newBackStack.last(),
                    previousRoute = current.navigation.currentRoute,
                    backStack = newBackStack
                )
            )
        } else current
    }
}

internal fun BuyAppViewModel.openDrawer() {
    _state.update { current ->
        current.copy(
            navigation = current.navigation.copy(isDrawerOpen = true)
        )
    }
}

internal fun BuyAppViewModel.closeDrawer() {
    _state.update { current ->
        current.copy(
            navigation = current.navigation.copy(isDrawerOpen = false)
        )
    }
}
