package com.together.newverse.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.together.newverse.ui.screens.sell.*

/**
 * Sell (Merchant) Navigation Routes Module
 *
 * Contains routes specific to the Sell/Merchant flavor:
 * - Overview
 * - Orders
 * - Create Product
 * - Seller Profile
 * - Pick Delivery Day
 *
 * Note: CreateProductScreen retrieves ImagePicker from LocalImagePicker CompositionLocal
 */
fun NavGraphBuilder.sellNavGraph(
    navController: NavHostController
) {
    composable(NavRoutes.Sell.Overview.route) {
        OverviewScreen()
    }

    composable(NavRoutes.Sell.Orders.route) {
        OrdersScreen()
    }

    composable(NavRoutes.Sell.Create.route) {
        CreateProductScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(NavRoutes.Sell.Profile.route) {
        SellerProfileScreen()
    }

    composable(NavRoutes.Sell.PickDay.route) {
        PickDayScreen()
    }
}
