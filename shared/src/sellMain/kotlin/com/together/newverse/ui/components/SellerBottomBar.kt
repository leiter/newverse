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
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
            val label = stringResource(item.labelRes)
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = label
                    )
                },
                label = { Text(label) },
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
    val labelRes: StringResource,
    val icon: ImageVector
)

private val SellerBottomNavItems = listOf(
    BottomNavItem(
        route = NavRoutes.Sell.Overview.route,
        labelRes = Res.string.bottomnav_dashboard,
        icon = Icons.Default.Home
    ),
    BottomNavItem(
        route = NavRoutes.Sell.Orders.route,
        labelRes = Res.string.bottomnav_demand,
        icon = Icons.Default.ShoppingCart
    ),
    BottomNavItem(
        route = NavRoutes.Sell.Products.route,
        labelRes = Res.string.bottomnav_products,
        icon = Icons.AutoMirrored.Filled.List
    ),
    BottomNavItem(
        route = NavRoutes.Sell.Create.route,
        labelRes = Res.string.bottomnav_new,
        icon = Icons.Default.Add
    ),
    BottomNavItem(
        route = NavRoutes.Sell.Profile.route,
        labelRes = Res.string.bottomnav_profile,
        icon = Icons.Default.Person
    )
)
