package com.together.newverse.ui.navigation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.together.newverse.ui.components.AppDialog
import com.together.newverse.ui.components.SellerBottomNavigationBar
import com.together.newverse.ui.components.SellerTopBar
import com.together.newverse.ui.screens.SplashScreen
import com.together.newverse.ui.state.NotificationAction
import com.together.newverse.ui.state.NotificationSettings
import com.together.newverse.ui.state.SellAppViewModel
import com.together.newverse.ui.state.SnackbarDuration
import com.together.newverse.ui.state.UnifiedUiAction
import com.together.newverse.ui.state.UnifiedUserAction
import com.together.newverse.util.DocumentPickerResult
import com.together.newverse.util.LocalDocumentPicker
import com.together.newverse.util.rememberKeyboardManager
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * App Scaffold for Sell/Merchant flavor
 * Uses SellAppViewModel and UnifiedAppState
 *
 * This file is in sellMain source set, so it's ONLY compiled for Sell flavor.
 */

/**
 * Platform-specific actions that need to be handled by the platform layer
 */
sealed interface PlatformAction {
    data object GoogleSignIn : PlatformAction
    data object TwitterSignIn : PlatformAction
    data object GoogleSignOut : PlatformAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    onPlatformAction: (PlatformAction) -> Unit = {},
    notificationPlatformContent: @Composable (() -> Unit)? = null
) {
    // Get the Sell-specific ViewModel
    val viewModel = koinViewModel<SellAppViewModel>()
    val state by viewModel.state.collectAsState()

    // Snackbar host state
    val snackbarHostState = remember { SnackbarHostState() }

    // Notification settings state (local for now, can be moved to ViewModel later)
    var notificationSettings by remember { mutableStateOf(NotificationSettings()) }

    // Handle notification actions
    val onNotificationAction: (NotificationAction) -> Unit = { action ->
        notificationSettings = when (action) {
            is NotificationAction.ToggleNewOrders -> notificationSettings.copy(newOrderNotifications = action.enabled)
            is NotificationAction.ToggleOrderUpdates -> notificationSettings.copy(orderUpdateNotifications = action.enabled)
            is NotificationAction.ToggleLowStock -> notificationSettings.copy(lowStockNotifications = action.enabled)
            is NotificationAction.ToggleMarketing -> notificationSettings.copy(marketingNotifications = action.enabled)
            is NotificationAction.ToggleEmail -> notificationSettings.copy(emailNotifications = action.enabled)
            is NotificationAction.TogglePush -> notificationSettings.copy(pushNotifications = action.enabled)
            else -> notificationSettings
        }
    }

    // Observe Google Sign-In trigger
    LaunchedEffect(state.common.triggerGoogleSignIn) {
        if (state.common.triggerGoogleSignIn) {
            onPlatformAction(PlatformAction.GoogleSignIn)
            viewModel.resetGoogleSignInTrigger()
        }
    }

    // Observe Google Sign-Out trigger
    LaunchedEffect(state.common.triggerGoogleSignOut) {
        if (state.common.triggerGoogleSignOut) {
            onPlatformAction(PlatformAction.GoogleSignOut)
            viewModel.resetGoogleSignOutTrigger()
        }
    }

    // Observe Twitter Sign-In trigger
    LaunchedEffect(state.common.triggerTwitterSignIn) {
        if (state.common.triggerTwitterSignIn) {
            onPlatformAction(PlatformAction.TwitterSignIn)
            viewModel.resetTwitterSignInTrigger()
        }
    }

    // Observe snackbar state changes from ViewModel
    LaunchedEffect(state.common.ui.snackbar) {
        state.common.ui.snackbar?.let { snackbar ->
            snackbarHostState.showSnackbar(
                message = snackbar.message,
                actionLabel = snackbar.actionLabel,
                duration = when (snackbar.duration) {
                    SnackbarDuration.SHORT -> androidx.compose.material3.SnackbarDuration.Short
                    SnackbarDuration.LONG -> androidx.compose.material3.SnackbarDuration.Long
                    SnackbarDuration.INDEFINITE -> androidx.compose.material3.SnackbarDuration.Indefinite
                }
            )
            viewModel.dispatch(UnifiedUiAction.HideSnackbar)
        }
    }

    // Check if app is still initializing
    if (state.meta.isInitializing) {
        SplashScreen(
            initializationStep = state.meta.initializationStep
        )
        return
    }

    // Check if login is required (seller flavor without authentication)
    if (state.common.requiresLogin) {
        com.together.newverse.ui.screens.common.ForcedLoginScreen(
            authState = state.screens.auth,
            onAction = { action -> viewModel.dispatch(action) },
            onShowPasswordResetDialog = { viewModel.dispatch(UnifiedUiAction.ShowPasswordResetDialog) },
            onHidePasswordResetDialog = { viewModel.dispatch(UnifiedUiAction.HidePasswordResetDialog) }
        )
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.Sell.Overview.route
    val focusManager = LocalFocusManager.current
    val keyboardManager = rememberKeyboardManager()

    // Selection mode states for product management
    var isSelectionMode by remember { mutableStateOf(false) }
    var isAvailabilityMode by remember { mutableStateOf(false) }

    // Document picker for import
    val documentPicker = LocalDocumentPicker.current
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardManager.hide()
                })
            },
        topBar = {
            SellerTopBar(
                currentRoute = currentRoute,
                pendingOrdersCount = 0, // TODO: Get from state when implemented
                isSelectionMode = isSelectionMode,
                isAvailabilityMode = isAvailabilityMode,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToOrders = {
                    navController.navigate(NavRoutes.Sell.Orders.route)
                },
                onRefresh = {
                    // Recreate the screen to trigger refresh
                    navController.navigate(NavRoutes.Sell.Overview.route) {
                        popUpTo(NavRoutes.Sell.Overview.route) { inclusive = true }
                    }
                },
                onToggleSelectionMode = {
                    isAvailabilityMode = false
                    isSelectionMode = !isSelectionMode
                },
                onToggleAvailabilityMode = {
                    isSelectionMode = false
                    isAvailabilityMode = !isAvailabilityMode
                },
                onImportProducts = {
                    documentPicker?.let { picker ->
                        coroutineScope.launch {
                            when (val result = picker.pickDocument()) {
                                is DocumentPickerResult.Success -> {
                                    viewModel.setPendingImportContent(result.content)
                                }
                                is DocumentPickerResult.Error -> {
                                    // Could show snackbar here if needed
                                }
                                is DocumentPickerResult.Cancelled -> {
                                    // User cancelled - no action needed
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            SellerBottomNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        // Pop up to the start destination to avoid building up a large stack
                        popUpTo(NavRoutes.Sell.Overview.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        // Navigation content
        NavHost(
            navController = navController,
            startDestination = NavRoutes.Sell.Overview.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            navGraph(
                navController = navController,
                appState = state,
                onAction = viewModel::dispatch,
                sellAppViewModel = viewModel,
                onNavigateToOrderDetail = { orderId ->
                    navController.navigate(NavRoutes.Sell.OrderDetail.createRoute(orderId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCreateProduct = {
                    navController.navigate(NavRoutes.Sell.Create.route)
                },
                onNavigateToNotificationSettings = {
                    navController.navigate(NavRoutes.Sell.NotificationSettings.route)
                },
                onLogout = {
                    viewModel.dispatch(UnifiedUserAction.Logout)
                },
                notificationSettings = notificationSettings,
                onNotificationAction = onNotificationAction,
                notificationPlatformContent = notificationPlatformContent,
                getSelectionMode = { isSelectionMode },
                onSelectionModeChange = { isSelectionMode = it },
                getAvailabilityMode = { isAvailabilityMode },
                onAvailabilityModeChange = { isAvailabilityMode = it },
                onNavigateToImportPreview = {
                    navController.navigate(NavRoutes.Sell.ImportPreview.route)
                },
                onNavigateBackFromImport = {
                    navController.popBackStack()
                }
            )
        }

        // Show dialog if present
        state.common.ui.dialog?.let { dialog ->
            AppDialog(
                dialog = dialog,
                onDismiss = {
                    viewModel.dispatch(UnifiedUiAction.HideDialog)
                }
            )
        }
    }
}
