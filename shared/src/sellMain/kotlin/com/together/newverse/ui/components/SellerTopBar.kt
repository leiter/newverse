package com.together.newverse.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.together.newverse.ui.navigation.NavRoutes

/**
 * Top bar for Seller app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerTopBar(
    currentRoute: String,
    pendingOrdersCount: Int,
    onNavigateBack: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToNotifications: () -> Unit
) {
    TopAppBar(
        title = {
            Text(getRouteTitle(currentRoute))
        },
        navigationIcon = {
            if (shouldShowBackButton(currentRoute)) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück"
                    )
                }
            }
        },
        actions = {
            if (pendingOrdersCount > 0) {
                BadgedBox(
                    badge = {
                        Badge {
                            Text(pendingOrdersCount.toString())
                        }
                    }
                ) {
                    IconButton(onClick = onNavigateToOrders) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Bestellungen"
                        )
                    }
                }
            }

            IconButton(onClick = onNavigateToNotifications) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Benachrichtigungen"
                )
            }

            IconButton(onClick = onNavigateToProfile) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profil"
                )
            }
        }
    )
}

private fun getRouteTitle(route: String): String {
    return when (route) {
        NavRoutes.Sell.Overview.route -> "Dashboard"
        NavRoutes.Sell.Orders.route -> "Bestellungen"
        NavRoutes.Sell.Products.route -> "Produkte"
        NavRoutes.Sell.Create.route -> "Neues Produkt"
        NavRoutes.Sell.Profile.route -> "Profil"
        NavRoutes.Sell.NotificationSettings.route -> "Benachrichtigungen"
        else -> "Verkäufer"
    }
}

private fun shouldShowBackButton(route: String): Boolean {
    return route != NavRoutes.Sell.Overview.route
}
