package com.together.newverse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
    navController: NavHostController,
    isSelectionMode: Boolean = false,
    onSelectionModeChange: (Boolean) -> Unit = {},
    isAvailabilityMode: Boolean = false,
    onAvailabilityModeChange: (Boolean) -> Unit = {},
    notificationSettingsContent: @Composable () -> Unit = {},
    productsContent: @Composable (() -> Unit) -> Unit = { onCreateProduct -> }
) {
    composable(NavRoutes.Sell.Overview.route) {
        OverviewScreen(
            isSelectionMode = isSelectionMode,
            onSelectionModeChange = onSelectionModeChange,
            isAvailabilityMode = isAvailabilityMode,
            onAvailabilityModeChange = onAvailabilityModeChange
        )
    }

    composable(NavRoutes.Sell.Orders.route) {
        OrdersScreen(
            onOrderClick = { orderId ->
                navController.navigate(NavRoutes.Sell.OrderDetail.createRoute(orderId))
            }
        )
    }

    composable(
        route = NavRoutes.Sell.OrderDetail.route,
        arguments = listOf(
            navArgument("orderId") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val orderId = backStackEntry.arguments?.getString("orderId") ?: return@composable
        OrderDetailScreen(
            orderId = orderId,
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(NavRoutes.Sell.Create.route) {
        CreateProductScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(NavRoutes.Sell.Profile.route) {
        SellerProfileScreen(
            onNotificationSettingsClick = {
                navController.navigate(NavRoutes.Sell.NotificationSettings.route)
            }
        )
    }

    composable(NavRoutes.Sell.PickDay.route) {
        PickDayScreen()
    }

    composable(NavRoutes.Sell.NotificationSettings.route) {
        notificationSettingsContent()
    }

    composable(NavRoutes.Sell.Products.route) {
        productsContent {
            navController.navigate(NavRoutes.Sell.Create.route)
        }
    }
}
