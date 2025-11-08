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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.together.newverse.ui.screens.SplashScreen
import com.together.newverse.ui.state.UnifiedAppViewModel
import com.together.newverse.ui.theme.Orange
import kotlinx.coroutines.launch
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
fun AppScaffold() {
    // Get the unified ViewModel
    val viewModel = koinViewModel<UnifiedAppViewModel>()
    val appState by viewModel.state.collectAsState()

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

    // Get current route for highlighting in drawer
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.Home.route

    // Get screen title based on current route
    val screenTitle = remember(currentRoute) {
        NavRoutes.getAllRoutes()
            .find { it.route == currentRoute }
            ?.let { NavRoutes.getDisplayName(it) }
            ?: "Newverse"
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
            topBar = {
                TopAppBar(
                    title = { Text(screenTitle) },
                    navigationIcon = {
                        // Show back arrow for Basket and other detail screens, hamburger menu for main screens
                        if (currentRoute == NavRoutes.Buy.Basket.route ||
                            currentRoute == NavRoutes.Buy.Profile.route ||
                            currentRoute == NavRoutes.About.route) {
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
                                    tint = Color.White,
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
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                // Cart item count badge
                                Badge(
                                    containerColor = Orange,
                                    contentColor = Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = "0",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        titleContentColor = MaterialTheme.colorScheme.onSecondary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondary
                    )
                )
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier.padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                NavGraph(navController = navController)
            }
        }
    }
}
