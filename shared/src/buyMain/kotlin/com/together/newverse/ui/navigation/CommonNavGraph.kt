package com.together.newverse.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.together.newverse.ui.mainscreen.MainScreenModern
import com.together.newverse.ui.screens.common.*
import com.together.newverse.ui.state.UnifiedAppAction
import com.together.newverse.ui.state.UnifiedAppState
import com.together.newverse.ui.state.UnifiedUiAction

/**
 * Common Navigation Routes Module
 *
 * Contains routes that are shared across all flavors:
 * - Home (MainScreenModern)
 * - Login
 * - Register
 * - About
 */
fun NavGraphBuilder.commonNavGraph(
    navController: NavHostController,
    appState: UnifiedAppState,
    onAction: (UnifiedAppAction) -> Unit
) {
    // Home/Main Screen
    composable(NavRoutes.Home.route) {
        MainScreenModern(
            state = appState.screens.mainScreen,
            onAction = onAction,
            onNavigateToProductDetail = { articleId ->
                navController.navigate(NavRoutes.Buy.ProductDetail.createRoute(articleId))
            }
        )
    }

    // Common Screens
    composable(NavRoutes.About.route) {
        AboutScreenModern(
            onBackClick = { navController.popBackStack() }
        )
    }

    composable(NavRoutes.Login.route) {
        LoginScreen(
            authState = appState.screens.auth,
            onAction = onAction,
            onShowPasswordResetDialog = { onAction(UnifiedUiAction.ShowPasswordResetDialog) },
            onHidePasswordResetDialog = { onAction(UnifiedUiAction.HidePasswordResetDialog) }
        )
    }

    composable(NavRoutes.Register.route) {
        RegisterScreen(
            authState = appState.screens.auth,
            onAction = onAction
        )
    }
}
