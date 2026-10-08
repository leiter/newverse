package com.together.newverse.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.together.newverse.ui.mainscreen.MainScreenModern
import com.together.newverse.ui.screens.common.*
import com.together.newverse.ui.state.AuthMode
import com.together.newverse.ui.state.BuyAppState
import com.together.newverse.ui.state.BuyAction
import com.together.newverse.ui.state.BuyUiAction
import com.together.newverse.ui.state.BuyUserAction

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
    appState: BuyAppState,
    onAction: (BuyAction) -> Unit
) {
    // Home/Main Screen
    composable(NavRoutes.Home.route) {
        MainScreenModern(
            state = appState.mainScreen,
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
            authState = appState.auth,
            onLogin = { email, pw -> onAction(BuyUserAction.Login(email, pw)) },
            onLoginWithGoogle = { onAction(BuyUserAction.LoginWithGoogle) },
            onLoginWithTwitter = { onAction(BuyUserAction.LoginWithTwitter) },
            onLoginWithApple = { onAction(BuyUserAction.LoginWithApple) },
            onContinueAsGuest = { onAction(BuyUserAction.ContinueAsGuest) },
            onNavigateToRegister = { onAction(BuyUiAction.SetAuthMode(AuthMode.REGISTER)) },
            onRequestPasswordReset = { email -> onAction(BuyUserAction.RequestPasswordReset(email)) },
            onShowPasswordResetDialog = { onAction(BuyUiAction.ShowPasswordResetDialog) },
            onHidePasswordResetDialog = { onAction(BuyUiAction.HidePasswordResetDialog) }
        )
    }

    composable(NavRoutes.Register.route) {
        RegisterScreen(
            authState = appState.auth,
            onRegister = { email, pw, name -> onAction(BuyUserAction.Register(email, pw, name)) },
            onNavigateToLogin = { onAction(BuyUiAction.SetAuthMode(AuthMode.LOGIN)) }
        )
    }
}
