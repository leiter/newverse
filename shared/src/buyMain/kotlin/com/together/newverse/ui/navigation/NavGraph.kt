package com.together.newverse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.together.newverse.ui.screens.buy.BasketScreen
import com.together.newverse.ui.screens.buy.CustomerProfileScreenModern
import com.together.newverse.ui.screens.buy.FavoritesScreen
import com.together.newverse.ui.screens.buy.OrderHistoryScreen
import com.together.newverse.ui.state.AuthProvider
import com.together.newverse.ui.state.UnifiedAppAction
import com.together.newverse.ui.state.UnifiedAppState
import com.together.newverse.ui.state.UserState

/**
 * Navigation Graph for Buy/Customer App
 *
 * Contains:
 * - Common routes (Home, Login, Register, About)
 * - Buy-specific routes (Basket, Customer Profile, Order History)
 *
 * This file is in buyMain source set, so it's ONLY compiled for Buy flavor.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    appState: UnifiedAppState,
    onAction: (UnifiedAppAction) -> Unit,
    startDestination: String = NavRoutes.Home.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Common routes (Home, Login, Register, About)
        commonNavGraph(navController, appState, onAction)

        // Buy-specific routes

        // Basket screen - uses base route with optional query parameters
        composable(
            route = NavRoutes.Buy.Basket.route + "?orderId={orderId}&orderDate={orderDate}",
            arguments = listOf(
                navArgument("orderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("orderDate") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val orderIdArg = backStackEntry.arguments?.read { getStringOrNull("orderId") }
            val orderDateArg = backStackEntry.arguments?.read { getStringOrNull("orderDate") }

            BasketScreen(
                state = appState.screens.basketScreen,
                currentArticles = appState.screens.mainScreen.articles,
                onAction = { action -> onAction(action) },
                onNavigateToOrders = { navController.navigate(NavRoutes.Buy.OrderHistory.route) },
                orderId = orderIdArg ?: appState.common.basket.currentOrderId,
                orderDate = orderDateArg ?: appState.common.basket.currentOrderDate
            )
        }

        composable(NavRoutes.Buy.Profile.route) {
            // Determine auth status from user state and profile
            val userState = appState.common.user
            val profile = appState.screens.customerProfile.profile

            // Determine authProvider first
            val authProvider = when {
                profile?.anonymous == true -> AuthProvider.ANONYMOUS
                userState is UserState.LoggedIn -> {
                    // Determine provider from email pattern (simplified heuristic)
                    when {
                        userState.email.isEmpty() -> AuthProvider.ANONYMOUS
                        userState.email.contains("@gmail.com") -> AuthProvider.GOOGLE
                        else -> AuthProvider.EMAIL
                    }
                }
                else -> AuthProvider.ANONYMOUS
            }

            // User is anonymous if authProvider is ANONYMOUS or userState is Guest
            val isAnonymous = authProvider == AuthProvider.ANONYMOUS || userState is UserState.Guest

            val userEmail = when (userState) {
                is UserState.LoggedIn -> userState.email.ifEmpty { null }
                else -> null
            }

            CustomerProfileScreenModern(
                state = appState.screens.customerProfile,
                onAction = onAction,
                onNavigateToAbout = { navController.navigate(NavRoutes.About.route) },
                onNavigateToOrders = { navController.navigate(NavRoutes.Buy.OrderHistory.route) },
                onNavigateToFavorites = { navController.navigate(NavRoutes.Buy.Favorites.route) },
                isAnonymous = isAnonymous,
                authProvider = authProvider,
                userEmail = userEmail
            )
        }

        composable(NavRoutes.Buy.OrderHistory.route) {
            OrderHistoryScreen(
                appState = appState,
                onAction = onAction,
                onBackClick = { navController.popBackStack() },
                onOrderClick = { orderId, orderDate ->
                    // Navigate to basket screen with order details
                    navController.navigate(NavRoutes.Buy.Basket.createRoute(orderId, orderDate))
                }
            )
        }

        composable(NavRoutes.Buy.Favorites.route) {
            FavoritesScreen(
                state = appState.screens.mainScreen,
                onAction = onAction,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
