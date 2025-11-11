package com.together.newverse.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.together.newverse.domain.repository.BasketRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.together.newverse.ui.screens.SplashScreen
import com.together.newverse.ui.state.UnifiedAppViewModel
import kotlinx.coroutines.launch
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    onGoogleSignInRequested: () -> Unit = {},
    onTwitterSignInRequested: () -> Unit = {}
) {
    // Get the unified ViewModel
    val viewModel = koinViewModel<UnifiedAppViewModel>()
    val appState by viewModel.state.collectAsState()

    // Observe Google Sign-In trigger
    LaunchedEffect(appState.common.triggerGoogleSignIn) {
        println("🔍 AppScaffold: LaunchedEffect triggered, triggerGoogleSignIn=${appState.common.triggerGoogleSignIn}")
        if (appState.common.triggerGoogleSignIn) {
            println("🔐 AppScaffold: Calling onGoogleSignInRequested")
            onGoogleSignInRequested()
            viewModel.resetGoogleSignInTrigger()
        }
    }

    // Observe Twitter Sign-In trigger
    LaunchedEffect(appState.common.triggerTwitterSignIn) {
        if (appState.common.triggerTwitterSignIn) {
            println("🔐 AppScaffold: Calling onTwitterSignInRequested")
            onTwitterSignInRequested()
            viewModel.resetTwitterSignInTrigger()
        }
    }

    // Get BasketRepository to observe cart count
    val basketRepository = koinInject<BasketRepository>()
    val basketItems by basketRepository.observeBasket().collectAsState()

    // Animation state for cart shake
    val shakeOffset = remember { Animatable(0f) }
    var previousBasketSize by remember { mutableStateOf(basketItems.size) }

    // Trigger shake animation when basket size increases
    LaunchedEffect(basketItems.size) {
        if (basketItems.size > previousBasketSize) {
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
        previousBasketSize = basketItems.size
    }

    // Check if app is still initializing
    if (appState.meta.isInitializing) {
        // Show splash screen during initialization
        SplashScreen(
            initializationStep = appState.meta.initializationStep
        )
        return // Exit early, don't show main UI yet
    }

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Observe navigation state changes from ViewModel
    LaunchedEffect(appState.common.navigation.currentRoute) {
        val targetRoute = appState.common.navigation.currentRoute
        val currentDestination = navController.currentBackStackEntry?.destination?.route

        // Only navigate if we're not already at the target route
        if (targetRoute.route != currentDestination && targetRoute.route != NavRoutes.Home.route) {
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
            .find { it.route == currentRoute }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route.route) {
                        // Pop up to the start destination to avoid building up a large stack
                        popUpTo(NavRoutes.Home.route) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                },
                onClose = {
                    scope.launch {
                        drawerState.close()
                    }
                }
            )
        }
    ) {
        Scaffold(
            modifier = if (scrollBehavior != null) {
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            } else {
                Modifier
            },
            topBar = {
                TopAppBar(
                    title = { Text(displayTitle) },
                    navigationIcon = {
                        // Show back arrow for Basket and other detail screens, hamburger menu for main screens
                        if (currentRoute == NavRoutes.Buy.Basket.route ||
                            currentRoute == NavRoutes.Buy.Profile.route ||
                            currentRoute == NavRoutes.About.route ||
                            currentRoute == NavRoutes.Register.route) {
                            IconButton(
                                onClick = {
                                    navController.navigateUp()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        drawerState.open()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu"
                                )
                            }
                        }
                    },
                    actions = {
                        // Show search and cart icons only on Home screen
                        if (currentRoute == NavRoutes.Home.route) {
                            // Search Icon
                            IconButton(onClick = { /* TODO: Search action */ }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Shopping Cart with Badge
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
                                // Cart item count badge
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
                    onAction = { action -> viewModel.dispatch(action) }
                )
            }
        }
    }
}
