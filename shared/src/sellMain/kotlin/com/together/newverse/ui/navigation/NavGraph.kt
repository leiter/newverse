package com.together.newverse.ui.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.together.newverse.ui.screens.sell.CreateProductScreen
import com.together.newverse.ui.screens.sell.ImportPreviewScreen
import com.together.newverse.ui.screens.sell.ImportState
import com.together.newverse.ui.screens.sell.NotificationsScreen
import com.together.newverse.ui.screens.sell.OrderDetailScreen
import com.together.newverse.ui.screens.sell.OrdersScreen
import com.together.newverse.ui.screens.sell.OverviewScreen
import com.together.newverse.ui.screens.sell.OverviewViewModel
import com.together.newverse.ui.screens.sell.PickDayScreen
import com.together.newverse.ui.screens.sell.ProductsScreen
import com.together.newverse.ui.screens.sell.SellerProfileScreen
import com.together.newverse.ui.screens.sell.SellerProfileViewModel
import org.koin.compose.viewmodel.koinViewModel
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
    navController: NavController,
    appState: UnifiedAppState,
    onAction: (UnifiedAppAction) -> Unit,
    sellAppViewModel: com.together.newverse.ui.state.SellAppViewModel,
    onNavigateToOrderDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToCreateProduct: () -> Unit = {},
    onNavigateToNotificationSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    notificationSettings: NotificationSettings = NotificationSettings(),
    onNotificationAction: (com.together.newverse.ui.state.NotificationAction) -> Unit = {},
    notificationPlatformContent: @androidx.compose.runtime.Composable (() -> Unit)? = null,
    getSelectionMode: () -> Boolean = { false },
    onSelectionModeChange: (Boolean) -> Unit = {},
    getAvailabilityMode: () -> Boolean = { false },
    onAvailabilityModeChange: (Boolean) -> Unit = {},
    onNavigateToImportPreview: () -> Unit = {},
    onNavigateBackFromImport: () -> Unit = {}
) {
    composable(NavRoutes.Sell.Overview.route) {
        val overviewViewModel: OverviewViewModel = koinViewModel()
        OverviewScreen(
            viewModel = overviewViewModel,
            sellAppViewModel = sellAppViewModel,
            isSelectionMode = getSelectionMode(),
            onSelectionModeChange = onSelectionModeChange,
            isAvailabilityMode = getAvailabilityMode(),
            onAvailabilityModeChange = onAvailabilityModeChange,
            onNavigateToImportPreview = onNavigateToImportPreview
        )
    }

    composable(NavRoutes.Sell.ImportPreview.route) {
        // Get the ViewModel from the Overview screen's backStackEntry to share state
        val parentEntry = remember(it) {
            navController.getBackStackEntry(NavRoutes.Sell.Overview.route)
        }
        val overviewViewModel: OverviewViewModel = koinViewModel(viewModelStoreOwner = parentEntry)
        val importState = overviewViewModel.importState.collectAsState()
        val products = (importState.value as? ImportState.Preview)?.products ?: emptyList()
        val isImporting = importState.value is ImportState.Importing

        // Navigate back on success
        androidx.compose.runtime.LaunchedEffect(importState.value) {
            if (importState.value is ImportState.Success) {
                onNavigateBackFromImport()
            }
        }

        ImportPreviewScreen(
            products = products,
            isImporting = isImporting,
            onImportSelected = { selectedProducts ->
                overviewViewModel.importSelectedProducts(selectedProducts)
            },
            onCancel = {
                overviewViewModel.resetImportState()
                onNavigateBackFromImport()
            }
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
        val profileViewModel: SellerProfileViewModel = koinViewModel()
        val uiState = profileViewModel.uiState.collectAsState()

        SellerProfileScreen(
            uiState = uiState.value,
            onNotificationSettingsClick = onNavigateToNotificationSettings,
            onLogout = onLogout,
            onShowPaymentInfo = { profileViewModel.showPaymentInfo() },
            onHidePaymentInfo = { profileViewModel.hidePaymentInfo() },
            onShowMarketDialog = { market -> profileViewModel.showMarketDialog(market) },
            onHideMarketDialog = { profileViewModel.hideMarketDialog() },
            onSaveMarket = { market ->
                if (uiState.value.editingMarket != null) {
                    profileViewModel.updateMarket(market)
                } else {
                    profileViewModel.addMarket(market)
                }
            },
            onDeleteMarket = { marketId -> profileViewModel.removeMarket(marketId) }
        )
    }

    composable(NavRoutes.Sell.PickDay.route) {
        PickDayScreen()
    }

    composable(NavRoutes.Sell.NotificationSettings.route) {
        NotificationsScreen(
            notificationSettings = notificationSettings,
            onAction = onNotificationAction,
            platformContent = notificationPlatformContent
        )
    }
}
