package com.together.newverse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.together.newverse.ui.screens.sell.*
import com.together.newverse.ui.state.ListingState
import com.together.newverse.ui.state.NotificationSettings
import com.together.newverse.ui.state.UnifiedAppAction
import com.together.newverse.ui.state.UnifiedAppState

/**
 * Sell (Merchant) Navigation Routes Module
 *
 * Contains routes specific to the Sell/Merchant flavor:
 * - Overview
 * - Orders
 * - Products
 * - Create Product
 * - Seller Profile
 * - Pick Delivery Day
 * - Notification Settings
 *
 * This file is in sellMain source set, so it's ONLY compiled for Sell flavor.
 */
fun NavGraphBuilder.navGraph(
    appState: UnifiedAppState,
    onAction: (UnifiedAppAction) -> Unit,
    onNavigateToOrderDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToCreateProduct: () -> Unit = {},
    notificationSettings: NotificationSettings = NotificationSettings(),
    onNotificationAction: (com.together.newverse.ui.state.NotificationAction) -> Unit = {},
    getSelectionMode: () -> Boolean = { false },
    onSelectionModeChange: (Boolean) -> Unit = {},
    getAvailabilityMode: () -> Boolean = { false },
    onAvailabilityModeChange: (Boolean) -> Unit = {}
) {
    composable(NavRoutes.Sell.Overview.route) {
        OverviewScreen(
            isSelectionMode = getSelectionMode(),
            onSelectionModeChange = onSelectionModeChange,
            isAvailabilityMode = getAvailabilityMode(),
            onAvailabilityModeChange = onAvailabilityModeChange
        )
    }

    composable(NavRoutes.Sell.Orders.route) {
        OrdersScreen(
            onOrderClick = { orderId ->
                onNavigateToOrderDetail(orderId)
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
            onNavigateBack = onNavigateBack
        )
    }

    composable(NavRoutes.Sell.Products.route) {
        ProductsScreen(
            productsState = appState.screens.products,
            onCreateProduct = onNavigateToCreateProduct,
            onProductClick = { /* TODO: Navigate to product detail */ }
        )
    }

    composable(NavRoutes.Sell.Create.route) {
        CreateProductScreen(
            onNavigateBack = onNavigateBack,
            onAction = onAction
        )
    }

    composable(NavRoutes.Sell.Profile.route) {
        SellerProfileScreen(
            onNotificationSettingsClick = {
                // Navigation handled by navController in AppScaffold
            }
        )
    }

    composable(NavRoutes.Sell.PickDay.route) {
        PickDayScreen()
    }

    composable(NavRoutes.Sell.NotificationSettings.route) {
        NotificationsScreen(
            notificationSettings = notificationSettings,
            onAction = onNotificationAction
        )
    }
}
