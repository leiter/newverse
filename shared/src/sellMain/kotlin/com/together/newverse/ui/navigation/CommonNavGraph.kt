package com.together.newverse.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.together.newverse.ui.MainScreenModern
import com.together.newverse.ui.screens.common.*
import com.together.newverse.ui.state.AuthMode
import com.together.newverse.ui.state.MainScreenState
import com.together.newverse.ui.state.SellAppState
import com.together.newverse.ui.state.SellAction
import com.together.newverse.ui.state.SellUiAction
import com.together.newverse.ui.state.SellUserAction

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
    appState: SellAppState,
    onAction: (SellAction) -> Unit
) {
    // Home/Main Screen
    composable(NavRoutes.Home.route) {
        // Sell app uses Overview as start destination; Home route is unused
        // but must compile for shared CommonNavGraph structure
        MainScreenModern(
            state = MainScreenState(),
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
            authState = appState.auth,
            onLogin = { email, pw -> onAction(SellUserAction.Login(email, pw)) },
            onLoginWithGoogle = { onAction(SellUserAction.LoginWithGoogle) },
            onLoginWithTwitter = { onAction(SellUserAction.LoginWithTwitter) },
            onLoginWithApple = { onAction(SellUserAction.LoginWithApple) },
            onContinueAsGuest = { onAction(SellUserAction.ContinueAsGuest) },
            onNavigateToRegister = { onAction(SellUiAction.SetAuthMode(AuthMode.REGISTER)) },
            onRequestPasswordReset = { email -> onAction(SellUserAction.RequestPasswordReset(email)) },
            onShowPasswordResetDialog = { onAction(SellUiAction.ShowPasswordResetDialog) },
            onHidePasswordResetDialog = { onAction(SellUiAction.HidePasswordResetDialog) }
        )
    }

    composable(NavRoutes.Register.route) {
        RegisterScreen(
            authState = appState.auth,
            onRegister = { email, pw, name -> onAction(SellUserAction.Register(email, pw, name)) },
            onNavigateToLogin = { onAction(SellUiAction.SetAuthMode(AuthMode.LOGIN)) }
        )
    }
}
