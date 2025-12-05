package com.together.newverse.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.together.newverse.ui.MainScreenModern
import com.together.newverse.ui.screens.common.*
import com.together.newverse.ui.state.UnifiedAppAction
import com.together.newverse.ui.state.UnifiedAppState

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
            onAction = onAction
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
            onAction = onAction
        )
    }

    composable(NavRoutes.Register.route) {
        RegisterScreen(
            authState = appState.screens.auth,
            onAction = onAction
        )
    }
}
