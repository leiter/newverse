package com.together.newverse.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.together.newverse.ui.navigation.NavRoutes

/**
 * Bottom navigation bar for Seller app
 */
@Composable
fun SellerBottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar {
        SellerBottomNavItems.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        onNavigate(item.route)
                    }
                }
            )
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val SellerBottomNavItems = listOf(
    BottomNavItem(
        route = NavRoutes.Sell.Overview.route,
        label = "Dashboard",
        icon = Icons.Default.Home
    ),
    BottomNavItem(
        route = NavRoutes.Sell.Orders.route,
        label = "Bestellungen",
        icon = Icons.Default.ShoppingCart
    ),
    BottomNavItem(
        route = NavRoutes.Sell.Products.route,
        label = "Produkte",
        icon = Icons.AutoMirrored.Filled.List
    ),
    BottomNavItem(
        route = NavRoutes.Sell.Create.route,
        label = "Neu",
        icon = Icons.Default.Add
    ),
    BottomNavItem(
        route = NavRoutes.Sell.Profile.route,
        label = "Profil",
        icon = Icons.Default.Person
    )
)
