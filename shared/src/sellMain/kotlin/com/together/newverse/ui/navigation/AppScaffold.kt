package com.together.newverse.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.together.newverse.ui.components.AppDialog
import com.together.newverse.ui.components.SellerBottomNavigationBar
import com.together.newverse.ui.components.SellerTopBar
import com.together.newverse.ui.state.SellAppViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * App Scaffold for Sell/Merchant flavor
 * Uses SellAppViewModel and UnifiedAppState (standalone, no base class)
 *
 * This file is in sellMain source set, so it's ONLY compiled for Sell flavor.
 */
/**
 * Platform-specific actions that need to be handled by the platform layer
 */
sealed interface PlatformAction {
    data object GoogleSignIn : PlatformAction
    data object TwitterSignIn : PlatformAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    onPlatformAction: (PlatformAction) -> Unit = {},
    notificationSettingsContent: @Composable () -> Unit = {},
    productsContent: @Composable (() -> Unit) -> Unit = { onCreateProduct -> }
) {
    // Get the Sell-specific ViewModel (standalone, no inheritance)
    val viewModel = koinViewModel<SellAppViewModel>()
    val state by viewModel.state.collectAsState()

    // Check if app is still initializing
    if (state.meta.isInitializing) {
        // Show splash screen during initialization
        com.together.newverse.ui.screens.SplashScreen(
            initializationStep = state.meta.initializationStep
        )
        return // Exit early, don't show main UI yet
    }

    val navController = rememberNavController()

    Scaffold(
        topBar = {
            SellerTopBar(
                currentRoute = state.common.navigation.currentRoute?.route ?: "",
                pendingOrdersCount = 0, // TODO: Implement pending orders count
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToOrders = {
                    navController.navigate(NavRoutes.Sell.Orders.route)
                },
                onNavigateToProfile = {
                    navController.navigate(NavRoutes.Sell.Profile.route)
                },
                onNavigateToNotifications = {
                    navController.navigate(NavRoutes.Sell.Notifications.route)
                }
            )
        },
        bottomBar = {
            SellerBottomNavigationBar(
                currentRoute = state.common.navigation.currentRoute?.route ?: "",
                onNavigate = { route ->
                    navController.navigate(route)
                }
            )
        },
        snackbarHost = {
            // TODO: Implement proper SnackbarHost with SnackbarHostState
            // For now, just provide empty SnackbarHost
            SnackbarHost(hostState = remember { SnackbarHostState() })
        }
    ) { innerPadding ->
        // Navigation content
        NavHost(
            navController = navController,
            startDestination = NavRoutes.Sell.Overview.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            navGraph(
                appState = state,
                onAction = viewModel::dispatch
            )
        }

        // Show dialog if present
        state.common.ui.dialog?.let { dialog ->
            AppDialog(
                dialog = dialog,
                onDismiss = {
                    viewModel.dispatch(com.together.newverse.ui.state.UnifiedUiAction.HideDialog)
                }
            )
        }

        // Check if seller profile is complete
        // TODO: Implement seller profile completeness check
        // if (!state.isSellerProfileComplete && state.common.user is UserState.LoggedIn) {
        //     LaunchedEffect(Unit) {
        //         viewModel.dispatch(
        //             UnifiedUiAction.ShowSnackbar(
        //                 message = "Please complete your seller profile",
        //                 type = SnackbarType.WARNING
        //             )
        //         )
        //         navController.navigate(NavRoutes.Sell.Profile.route)
        //     }
        // }
    }
}