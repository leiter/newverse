package com.together.newverse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.together.newverse.ui.MainScreenModern
import com.together.newverse.ui.screens.buy.*
import com.together.newverse.ui.screens.common.*
import com.together.newverse.ui.screens.sell.*
import com.together.newverse.ui.state.UnifiedAppAction
import com.together.newverse.ui.state.UnifiedAppState

/**
 * Navigation Graph for Newverse App
 *
 * Defines all navigation routes and their corresponding screens
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    appState: UnifiedAppState,
    onAction: (UnifiedAppAction) -> Unit,
    startDestination: String = NavRoutes.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
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

        // Buy (Customer) Screens
        // ProductsScreen removed - MainScreenModern is used instead

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

        // Sell (Merchant) Screens
        composable(NavRoutes.Sell.Overview.route) {
            OverviewScreen()
        }

        composable(NavRoutes.Sell.Orders.route) {
            OrdersScreen()
        }

        composable(NavRoutes.Sell.Create.route) {
            CreateProductScreen()
        }

        composable(NavRoutes.Sell.Profile.route) {
            SellerProfileScreen()
        }

        composable(NavRoutes.Sell.PickDay.route) {
            PickDayScreen()
        }
    }
}
