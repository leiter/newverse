package com.together.newverse.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.ui.screens.SplashScreen
import com.together.newverse.ui.state.BuyAppViewModel
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
    data object GoogleSignOut : PlatformAction
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
    LaunchedEffect(appState.common.triggerGoogleSignIn) {
        println("🔍 AppScaffold: LaunchedEffect triggered, triggerGoogleSignIn=${appState.common.triggerGoogleSignIn}")
        if (appState.common.triggerGoogleSignIn) {
            println("🔐 AppScaffold: Calling onPlatformAction(GoogleSignIn)")
            onPlatformAction(PlatformAction.GoogleSignIn)
            viewModel.resetGoogleSignInTrigger()
        }
    }

    // Observe Twitter Sign-In trigger
    LaunchedEffect(appState.common.triggerTwitterSignIn) {
        if (appState.common.triggerTwitterSignIn) {
            println("🔐 AppScaffold: Calling onPlatformAction(TwitterSignIn)")
            onPlatformAction(PlatformAction.TwitterSignIn)
            viewModel.resetTwitterSignInTrigger()
        }
    }

    // Observe Google Sign-Out trigger
    LaunchedEffect(appState.common.triggerGoogleSignOut) {
        if (appState.common.triggerGoogleSignOut) {
            println("🔐 AppScaffold: Calling onPlatformAction(GoogleSignOut)")
            onPlatformAction(PlatformAction.GoogleSignOut)
            viewModel.resetGoogleSignOutTrigger()
        }
    }

    // Get BasketRepository to observe cart count (actual basket items)
    val basketRepository = koinInject<BasketRepository>()
    val basketItems by basketRepository.observeBasket().collectAsState()
    basketItems.size

    // Animation state for cart shake
    val shakeOffset = remember { Animatable(0f) }
    var previousBasketItems by remember { mutableStateOf(basketItems) }

    // Trigger shake animation when basket changes (items added, removed, or quantities changed)
    LaunchedEffect(basketItems) {
        val hasChanged = basketItems != previousBasketItems
        val isInitialLoad = previousBasketItems.isEmpty() && basketItems.isEmpty()

        // Trigger animation if basket changed and it's not the initial empty load
        if (hasChanged && !isInitialLoad) {
            // Shake animation: rotate left-right-left-right
            shakeOffset.animateTo(
                targetValue = 15f,
                animationSpec = tween(durationMillis = 50)
            )
            shakeOffset.animateTo(
                targetValue = -15f,
                animationSpec = tween(durationMillis = 100)
            )
            shakeOffset.animateTo(
                targetValue = 10f,
                animationSpec = tween(durationMillis = 100)
            )
            shakeOffset.animateTo(
                targetValue = -10f,
                animationSpec = tween(durationMillis = 100)
            )
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
        }
        previousBasketItems = basketItems
    }

    // Check if app is still initializing
    if (appState.meta.isInitializing) {
        // Show splash screen during initialization
        SplashScreen(
            initializationStep = appState.meta.initializationStep
        )
        return // Exit early, don't show main UI yet
    }

    // Check if login is required (seller flavor without authentication)
    if (appState.common.requiresLogin) {
        // Show forced login screen
        com.together.newverse.ui.screens.common.ForcedLoginScreen(
            onAction = { action -> viewModel.dispatch(action) }
        )
        return // Exit early, don't show main UI yet
    }

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    // Observe snackbar state changes from ViewModel
    LaunchedEffect(appState.common.ui.snackbar) {
        appState.common.ui.snackbar?.let { snackbar ->
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
            viewModel.dispatch(com.together.newverse.ui.state.UnifiedUiAction.HideSnackbar)
        }
    }

    // Observe navigation state changes from ViewModel
    LaunchedEffect(appState.common.navigation.currentRoute) {
        val targetRoute = appState.common.navigation.currentRoute
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
        currentRoute.startsWith(NavRoutes.Buy.Profile.route)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BuyerBottomNavigationBar(
                    currentRoute = currentRoute,
                    basketItemCount = basketItems.size,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(NavRoutes.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        modifier = if (scrollBehavior != null) {
            Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        } else {
            Modifier
        },
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
                        currentRoute == NavRoutes.Login.route

                    if (isDetailScreen) {
                        IconButton(
                            onClick = {
                                viewModel.dispatch(com.together.newverse.ui.state.UnifiedNavigationAction.NavigateBack)
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
                    // Show cart icon on Home screen (for quick access)
                    if (currentRoute == NavRoutes.Home.route) {
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            IconButton(onClick = {
                                navController.navigate(NavRoutes.Buy.Basket.route)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = "Shopping Cart",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            rotationZ = shakeOffset.value
                                        }
                                )
                            }
                            if (basketItems.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = basketItems.size.toString(),
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
            )
        }
    }
}

/**
 * Format order date key (yyyyMMdd) into a readable format for subtitle
 * e.g., "20251127" -> "Do. 27.11."
 */
private fun formatOrderDateForSubtitle(dateKey: String): String {
    return try {
        if (dateKey.length != 8) return dateKey

        val year = dateKey.substring(0, 4).toInt()
        val month = dateKey.substring(4, 6).toInt()
        val day = dateKey.substring(6, 8).toInt()

        // Create LocalDate to get day of week
        val localDate = kotlinx.datetime.LocalDate(year, month, day)
        val dayOfWeek = when (localDate.dayOfWeek) {
            kotlinx.datetime.DayOfWeek.MONDAY -> "Mo"
            kotlinx.datetime.DayOfWeek.TUESDAY -> "Di"
            kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Mi"
            kotlinx.datetime.DayOfWeek.THURSDAY -> "Do"
            kotlinx.datetime.DayOfWeek.FRIDAY -> "Fr"
            kotlinx.datetime.DayOfWeek.SATURDAY -> "Sa"
            kotlinx.datetime.DayOfWeek.SUNDAY -> "So"
            else -> ""
        }

        "$dayOfWeek. $day.$month."
    } catch (e: Exception) {
        dateKey
    }
}
