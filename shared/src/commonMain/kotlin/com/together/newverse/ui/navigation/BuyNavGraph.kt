package com.together.newverse.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.together.newverse.ui.screens.buy.*
import com.together.newverse.ui.state.UnifiedAppAction
import com.together.newverse.ui.state.UnifiedAppState

/**
 * Buy (Customer) Navigation Routes Module
 *
 * Contains routes specific to the Buy/Customer flavor:
 * - Basket
 * - Customer Profile
 * - Order History
 */
fun NavGraphBuilder.buyNavGraph(
    navController: NavHostController,
    appState: UnifiedAppState,
    onAction: (UnifiedAppAction) -> Unit
) {
    // Basket screen - uses base route with optional query parameters
    composable(
        route = NavRoutes.Buy.Basket.route + "?orderId={orderId}&orderDate={orderDate}",
        arguments = listOf(
            androidx.navigation.navArgument("orderId") {
                type = androidx.navigation.NavType.StringType
                nullable = true
                defaultValue = null
            },
            androidx.navigation.navArgument("orderDate") {
                type = androidx.navigation.NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        val orderIdArg = backStackEntry.arguments?.getString("orderId")
        val orderDateArg = backStackEntry.arguments?.getString("orderDate")

        BasketScreen(
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
