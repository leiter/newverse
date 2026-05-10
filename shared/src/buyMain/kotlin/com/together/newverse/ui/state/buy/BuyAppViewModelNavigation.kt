package com.together.newverse.ui.state.buy

import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.state.BuyAppViewModel
import kotlinx.coroutines.flow.update

internal fun BuyAppViewModel.navigateTo(route: NavRoutes) {
    // This is for navigating to non-top-level screens, implementation might be needed later.
    _state.update {
        it.copy(
            navigation = it.navigation.copy(
                pendingRoute = route
            )
        )
    }
}

/**
 * Performs a top-level navigation, like tapping a bottom navigation bar item.
 * It sets the new route as the current one and clears the back stack.
 */
internal fun BuyAppViewModel.navigateToTopLevel(route: NavRoutes) {
    _state.update {
        it.copy(
            navigation = it.navigation.copy(
                currentRoute = route,
                backStack = listOf(route) // Reset back stack to the new root
            )
        )
    }
}

internal fun BuyAppViewModel.clearPendingNavigation() {
    _state.update {
        it.copy(
            navigation = it.navigation.copy(
                pendingRoute = null
            )
        )
    }
}

internal fun BuyAppViewModel.navigateBack() {
    // To be implemented
}

internal fun BuyAppViewModel.openDrawer() {
    _state.update {
        it.copy(
            navigation = it.navigation.copy(
                isDrawerOpen = true
            )
        )
    }
}

internal fun BuyAppViewModel.closeDrawer() {
    _state.update {
        it.copy(
            navigation = it.navigation.copy(
                isDrawerOpen = false
            )
        )
    }
}
