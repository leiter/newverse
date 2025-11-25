package com.together.newverse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.together.newverse.shared.BuildKonfig
import com.together.newverse.ui.state.UnifiedAppAction
import com.together.newverse.ui.state.UnifiedAppState

/**
 * Navigation Graph for Newverse App
 *
 * Composes modular navigation graphs based on build flavor:
 * - Buy flavor (IS_BUY_APP=true): Common + Buy routes
 * - Sell flavor (IS_SELL_APP=true): Common + Sell routes
 * - Combined (both flags false): Common + Buy + Sell routes
 *
 * This design allows for:
 * - Clean separation of flavor-specific navigation
 * - Support for buy-only, sell-only, or combined builds
 * - Easy addition of new routes to specific flavors
 *
 * Note: ImagePicker for seller features is provided via LocalImagePicker CompositionLocal
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    appState: UnifiedAppState,
    onAction: (UnifiedAppAction) -> Unit,
    startDestination: String = NavRoutes.Home.route,
    isSelectionMode: Boolean = false,
    onSelectionModeChange: (Boolean) -> Unit = {},
    isAvailabilityMode: Boolean = false,
    onAvailabilityModeChange: (Boolean) -> Unit = {},
    notificationSettingsContent: @Composable () -> Unit = {},
    productsContent: @Composable (() -> Unit) -> Unit = { onCreateProduct -> }
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Always include common routes (Home, Login, Register, About)
        commonNavGraph(navController, appState, onAction)

        // Include Buy routes based on flavor configuration
        // Include if: IS_BUY_APP is true OR it's a combined build (both flags false)
        if (BuildKonfig.IS_BUY_APP || isCombinedBuild()) {
            buyNavGraph(navController, appState, onAction)
        }

        // Include Sell routes based on flavor configuration
        // Include if: IS_SELL_APP is true OR it's a combined build (both flags false)
        if (BuildKonfig.IS_SELL_APP || isCombinedBuild()) {
            sellNavGraph(
                navController = navController,
                isSelectionMode = isSelectionMode,
                onSelectionModeChange = onSelectionModeChange,
                isAvailabilityMode = isAvailabilityMode,
                onAvailabilityModeChange = onAvailabilityModeChange,
                notificationSettingsContent = notificationSettingsContent,
                productsContent = productsContent
            )
        }
    }
}

/**
 * Check if this is a combined build (both Buy and Sell features enabled)
 * This happens when neither IS_BUY_APP nor IS_SELL_APP is set to true
 */
private fun isCombinedBuild(): Boolean {
    return !BuildKonfig.IS_BUY_APP && !BuildKonfig.IS_SELL_APP
}
