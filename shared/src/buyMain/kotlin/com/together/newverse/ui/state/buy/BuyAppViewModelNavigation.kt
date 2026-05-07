package com.together.newverse.ui.state.buy

import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.state.BuyAppViewModel
import kotlinx.coroutines.flow.update

internal fun BuyAppViewModel.navigateTo(route: NavRoutes) {
    _state.update {
        it.copy(
            navigation = it.navigation.copy(
                pendingRoute = route
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
