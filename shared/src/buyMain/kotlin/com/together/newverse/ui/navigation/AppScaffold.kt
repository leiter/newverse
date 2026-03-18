package com.together.newverse.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.ui.screens.SplashScreen
import com.together.newverse.util.rememberKeyboardManager
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BuySellerAction
import com.together.newverse.ui.state.DeepLinkRouter
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main App Scaffold with Navigation Drawer
 *
 * Provides the overall app structure with:
 * - Navigation drawer
 * - Top app bar with menu button
 * - Navigation graph
 */
/**
 * Platform-specific actions that need to be handled by the platform layer
 */
sealed interface PlatformAction {
    data object GoogleSignIn : PlatformAction
    data object TwitterSignIn : PlatformAction
    data object AppleSignIn : PlatformAction
    data object GoogleSignOut : PlatformAction
    data object ScanQrCode : PlatformAction
    data class ShareText(val text: String) : PlatformAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    onPlatformAction: (PlatformAction) -> Unit = {}
) {
    // Get the app ViewModel (BuyAppViewModel in buy flavor, SellAppViewModel in sell flavor)
    val viewModel = koinViewModel<BuyAppViewModel>()
    val appState by viewModel.state.collectAsState()

    // Observe Google Sign-In trigger
    LaunchedEffect(appState.triggerGoogleSignIn) {
        println("🔍 AppScaffold: LaunchedEffect triggered, triggerGoogleSignIn=${appState.triggerGoogleSignIn}")
        if (appState.triggerGoogleSignIn) {
            println("🔐 AppScaffold: Calling onPlatformAction(GoogleSignIn)")
            onPlatformAction(PlatformAction.GoogleSignIn)
            viewModel.resetGoogleSignInTrigger()
        }
    }

    // Observe Twitter Sign-In trigger
    LaunchedEffect(appState.triggerTwitterSignIn) {
        if (appState.triggerTwitterSignIn) {
            println("🔐 AppScaffold: Calling onPlatformAction(TwitterSignIn)")
            onPlatformAction(PlatformAction.TwitterSignIn)
            viewModel.resetTwitterSignInTrigger()
        }
    }

    // Observe Apple Sign-In trigger
    LaunchedEffect(appState.triggerAppleSignIn) {
        if (appState.triggerAppleSignIn) {
            println("🔐 AppScaffold: Calling onPlatformAction(AppleSignIn)")
            onPlatformAction(PlatformAction.AppleSignIn)
            viewModel.resetAppleSignInTrigger()
        }
    }

    // Observe incoming deep link URLs (iOS: forwarded from Swift .onOpenURL via DeepLinkRouter)
    val pendingDeepLink by DeepLinkRouter.pendingUrl.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        val url = pendingDeepLink ?: return@LaunchedEffect
        DeepLinkRouter.consume()
        println("AppScaffold: handling deep link: $url")

        try {
            // Parse query string using Kotlin stdlib only (KMP-compatible)
            val queryStart = url.indexOf('?')
            val queryMap: Map<String, String> = if (queryStart >= 0) {
                url.substring(queryStart + 1).split("&").mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx < 0) null else part.substring(0, idx) to part.substring(idx + 1)
                }.toMap()
            } else emptyMap()

            val seller   = queryMap["seller"]   ?: queryMap["sellerId"]
            val token    = queryMap["token"]
            val inviteId = queryMap["inviteId"]
            val expires  = queryMap["expires"]?.toLongOrNull()

            when {
                seller != null && token != null ->
                    viewModel.dispatch(BuySellerAction.ConnectWithToken(seller, token))
                seller != null && inviteId != null && expires != null ->
                    viewModel.dispatch(BuySellerAction.ConnectWithInvitation(seller, inviteId, expires))
                seller != null ->
                    println("AppScaffold: deep link with bare seller ID (no token/invite) ignored: $seller")
                else ->
                    println("AppScaffold: deep link has no recognised seller param: $url")
            }
        } catch (e: Exception) {
            println("AppScaffold: failed to parse deep link '$url': ${e.message}")
        }
    }

    // Observe Google Sign-Out trigger
    LaunchedEffect(appState.triggerGoogleSignOut) {
        if (appState.triggerGoogleSignOut) {
            println("🔐 AppScaffold: Calling onPlatformAction(GoogleSignOut)")
            onPlatformAction(PlatformAction.GoogleSignOut)
            viewModel.resetGoogleSignOutTrigger()
        }
    }

    // Get BasketRepository to observe cart count (actual basket items)
    val basketRepository = koinInject<BasketRepository>()
    val basketItems by basketRepository.observeBasket().collectAsState()

    // Check if app is still initializing
    if (appState.meta.isInitializing) {
        // Show splash screen during initialization
        SplashScreen(
            initializationStep = appState.meta.initializationStep
        )
        return // Exit early, don't show main UI yet
    }

    // Check if user needs to authenticate (no session, show login/register screen)
    if (appState.user is com.together.newverse.ui.state.UserState.NotAuthenticated) {
        when (appState.auth.mode) {
            com.together.newverse.ui.state.AuthMode.REGISTER -> {
                // Show register screen
                com.together.newverse.ui.screens.common.RegisterScreen(
                    authState = appState.auth,
                    onRegister = { email, pw, name -> viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.Register(email, pw, name)) },
                    onNavigateToLogin = { viewModel.dispatch(com.together.newverse.ui.state.BuyUiAction.SetAuthMode(com.together.newverse.ui.state.AuthMode.LOGIN)) }
                )
            }
            else -> {
                // Default to login screen (LOGIN, FORGOT_PASSWORD, etc.)
                com.together.newverse.ui.screens.common.LoginScreen(
                    authState = appState.auth,
                    onLogin = { email, pw -> viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.Login(email, pw)) },
                    onLoginWithGoogle = { viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.LoginWithGoogle) },
                    onLoginWithTwitter = { viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.LoginWithTwitter) },
                    onLoginWithApple = { viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.LoginWithApple) },
                    onContinueAsGuest = { viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.ContinueAsGuest) },
                    onNavigateToRegister = { viewModel.dispatch(com.together.newverse.ui.state.BuyUiAction.SetAuthMode(com.together.newverse.ui.state.AuthMode.REGISTER)) },
                    onRequestPasswordReset = { email -> viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.RequestPasswordReset(email)) },
                    onShowPasswordResetDialog = { viewModel.dispatch(com.together.newverse.ui.state.BuyUiAction.ShowPasswordResetDialog) },
                    onHidePasswordResetDialog = { viewModel.dispatch(com.together.newverse.ui.state.BuyUiAction.HidePasswordResetDialog) }
                )
            }
        }
        return // Exit early, don't show main UI yet
    }

    // Check if login is required (seller flavor without authentication)
    if (appState.requiresLogin) {
        // Show forced login screen
        com.together.newverse.ui.screens.common.ForcedLoginScreen(
            authState = appState.auth,
            onLogin = { email, pw -> viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.Login(email, pw)) },
            onLoginWithGoogle = { viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.LoginWithGoogle) },
            onRequestPasswordReset = { email -> viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.RequestPasswordReset(email)) }
        )
        return // Exit early, don't show main UI yet
    }

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardManager = rememberKeyboardManager()

    // Observe snackbar state changes from ViewModel
    LaunchedEffect(appState.ui.snackbar) {
        appState.ui.snackbar?.let { snackbar ->
            snackbarHostState.showSnackbar(
                message = snackbar.message,
                actionLabel = snackbar.actionLabel,
                duration = when (snackbar.duration) {
                    com.together.newverse.ui.state.SnackbarDuration.SHORT -> androidx.compose.material3.SnackbarDuration.Short
                    com.together.newverse.ui.state.SnackbarDuration.LONG -> androidx.compose.material3.SnackbarDuration.Long
                    com.together.newverse.ui.state.SnackbarDuration.INDEFINITE -> androidx.compose.material3.SnackbarDuration.Indefinite
                }
            )
            // Auto-hide snackbar after showing
            viewModel.dispatch(com.together.newverse.ui.state.BuyUiAction.HideSnackbar)
        }
    }

    // Observe navigation state changes from ViewModel
    // DISABLED: This was causing navigation conflicts on iOS when using bottom bar
    // The ViewModel state wasn't syncing with direct navController navigation,
    // causing this LaunchedEffect to re-navigate back to the previous screen
    /*
    LaunchedEffect(appState.navigation.currentRoute) {
        val targetRoute = appState.navigation.currentRoute
        val currentDestination = navController.currentBackStackEntry?.destination?.route

        // Only navigate if we're not already at the target route
        if (targetRoute.route != currentDestination) {
            navController.navigate(targetRoute.route) {
                // Pop up to the start destination to avoid building up a large stack
                popUpTo(NavRoutes.Home.route) {
                    saveState = true
                }
                // Avoid multiple copies of the same destination
                launchSingleTop = true
                // Restore state when reselecting a previously selected item
                restoreState = true
            }
        }
    }
    */

    // Get current route for highlighting in drawer
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.Home.route

    // Get screen title based on current route
    val defaultAppName = stringResource(Res.string.app_name)
    val screenTitle = remember(currentRoute) {
        NavRoutes.getAllRoutes()
            .find { route ->
                // Match routes with or without query parameters
                currentRoute == route.route || currentRoute.startsWith(route.route + "?")
            }
    }
    val displayTitle = screenTitle?.let {
        stringResource(NavRoutes.getDisplayNameRes(it))
    } ?: defaultAppName

    // Scroll behavior for collapsing toolbar (only for Home screen)
    // Using exitUntilCollapsedScrollBehavior for snappy hide/show behavior
    val scrollBehavior = if (currentRoute == NavRoutes.Home.route) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    } else {
        null
    }

    // Determine if bottom bar should be shown (main screens only)
    val showBottomBar = currentRoute == NavRoutes.Home.route ||
        currentRoute.startsWith(NavRoutes.Buy.Basket.route) ||
        currentRoute == NavRoutes.Buy.Profile.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BuyerBottomNavigationBar(
                    currentRoute = currentRoute,
                    basketItemCount = basketItems.size,
                    onNavigate = { route ->
                        println("🔍 AppScaffold: onNavigate called, route=$route")
                        if (route == NavRoutes.Home.route) {
                            // For home (start destination), don't use saveState/restoreState
                            // This avoids iOS state restoration issues with the start destination
                            println("🔍 AppScaffold: Navigating to HOME with inclusive popUpTo")
                            navController.navigate(route) {
                                popUpTo(NavRoutes.Home.route) { inclusive = true }
                                launchSingleTop = true
                            }
                            println("🔍 AppScaffold: Navigation to HOME completed")
                        } else {
                            println("🔍 AppScaffold: Navigating to $route with saveState/restoreState")
                            navController.navigate(route) {
                                popUpTo(NavRoutes.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            println("🔍 AppScaffold: Navigation to $route completed")
                        }
                    }
                )
            }
        },
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardManager.hide()
                })
            }
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                }
            ),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = { Text(displayTitle) },
                navigationIcon = {
                    // Show back arrow for detail screens that aren't main bottom nav tabs
                    val isDetailScreen = currentRoute == NavRoutes.Buy.OrderHistory.route ||
                        currentRoute == NavRoutes.About.route ||
                        currentRoute == NavRoutes.Register.route ||
                        currentRoute == NavRoutes.Login.route ||
                        currentRoute.startsWith(NavRoutes.Buy.Messages.route) ||
                        currentRoute.startsWith(NavRoutes.Buy.ProductDetail.route) ||
                        currentRoute == NavRoutes.Buy.BuyerContacts.route ||
                        currentRoute == NavRoutes.Buy.AddBuyerContact.route ||
                        currentRoute.startsWith(NavRoutes.Buy.ConversationDetail.route.substringBefore("{"))

                    if (isDetailScreen) {
                        IconButton(
                            onClick = {
                                viewModel.dispatch(com.together.newverse.ui.state.BuyNavigationAction.NavigateBack)
                                navController.navigateUp()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    // Show contacts icon on Messages screen
                    if (currentRoute == NavRoutes.Buy.Messages.route) {
                        IconButton(onClick = {
                            navController.navigate(NavRoutes.Buy.BuyerContacts.route) {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = "Contacts",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    // Show email icon on Profile screen
                    if (currentRoute == NavRoutes.Buy.Profile.route) {
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            IconButton(onClick = {
                                navController.navigate(NavRoutes.Buy.Messages.route) {
                                    launchSingleTop = true
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Messages",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            if (appState.unreadMessageCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = appState.unreadMessageCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                    // Show messages icon on Home screen
                    if (currentRoute == NavRoutes.Home.route) {
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            IconButton(onClick = {
                                navController.navigate(NavRoutes.Buy.Messages.route) {
                                    launchSingleTop = true
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Messages",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            if (appState.unreadMessageCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = appState.unreadMessageCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            NavGraph(
                navController = navController,
                appState = appState,
                onAction = { action -> viewModel.dispatch(action) },
                onPlatformAction = onPlatformAction,
            )
        }
    }
}

/**
 * Format order date key (yyyyMMdd) into a readable format for subtitle
 * e.g., "20251127" -> "Do. 27.11."
 * Uses kotlinx.datetime which works on all Android API levels.
 */
private fun formatOrderDateForSubtitle(dateKey: String): String {
    return try {
        if (dateKey.length != 8) return dateKey

        val year = dateKey.substring(0, 4).toInt()
        val month = dateKey.substring(4, 6).toInt()
        val day = dateKey.substring(6, 8).toInt()

        // Calculate day of week manually (no external library needed)
        // Using Zeller's congruence algorithm
        val adjustedMonth = if (month < 3) month + 12 else month
        val adjustedYear = if (month < 3) year - 1 else year
        val q = day
        val k = adjustedYear % 100
        val j = adjustedYear / 100
        val h = (q + (13 * (adjustedMonth + 1)) / 5 + k + k / 4 + j / 4 - 2 * j) % 7
        val dayOfWeekIndex = ((h + 5) % 7) // 0 = Monday, 6 = Sunday

        val dayOfWeek = when (dayOfWeekIndex) {
            0 -> "Mo"
            1 -> "Di"
            2 -> "Mi"
            3 -> "Do"
            4 -> "Fr"
            5 -> "Sa"
            6 -> "So"
            else -> ""
        }

        "$dayOfWeek. $day.$month."
    } catch (e: Exception) {
        dateKey
    }
}
