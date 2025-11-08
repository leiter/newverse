package com.together.newverse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.together.newverse.ui.MainScreenModern
import com.together.newverse.ui.screens.buy.*
import com.together.newverse.ui.screens.common.*
import com.together.newverse.ui.screens.sell.*

/**
 * Navigation Graph for Newverse App
 *
 * Defines all navigation routes and their corresponding screens
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = NavRoutes.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Home/Main Screen
        composable(NavRoutes.Home.route) {
            MainScreenModern()
        }

        // Common Screens
        composable(NavRoutes.About.route) {
            AboutScreenModern(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Login.route) {
            LoginScreen()
        }

        // Buy (Customer) Screens
        composable(NavRoutes.Buy.Products.route) {
            ProductsScreen()
        }

        composable(NavRoutes.Buy.Basket.route) {
            BasketScreen()
        }

        composable(NavRoutes.Buy.Profile.route) {
            CustomerProfileScreenModern(
                onBackClick = { navController.popBackStack() }
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
