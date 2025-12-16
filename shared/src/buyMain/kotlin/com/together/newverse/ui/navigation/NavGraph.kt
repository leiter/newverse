package com.together.newverse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.together.newverse.ui.screens.buy.BasketScreen
import com.together.newverse.ui.screens.buy.CustomerProfileScreenModern
import com.together.newverse.ui.screens.buy.OrderHistoryScreen
import com.together.newverse.ui.state.UnifiedAppAction
import com.together.newverse.ui.state.UnifiedAppState

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
            val orderIdArg = backStackEntry.arguments?.getString("orderId")
            val orderDateArg = backStackEntry.arguments?.getString("orderDate")

            BasketScreen(
                state = appState.screens.basketScreen,
                currentArticles = appState.screens.mainScreen.articles,
                onAction = { action -> onAction(action) },
                orderId = orderIdArg ?: appState.common.basket.currentOrderId,
                orderDate = orderDateArg ?: appState.common.basket.currentOrderDate
            )
        }

        composable(NavRoutes.Buy.Profile.route) {
            CustomerProfileScreenModern(
                state = appState.screens.customerProfile,
                onAction = onAction
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
    }
}
