package com.together.newverse.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    onNavigate: (String) -> Unit,
    pendingOrdersCount: Int = 0,
    pendingAccessRequestCount: Int = 0
) {
    NavigationBar {
        SellerBottomNavItems.forEach { item ->
            val label = stringResource(item.labelRes)
            val badgeCount = when (item.route) {
                NavRoutes.Sell.Orders.route -> pendingOrdersCount
                NavRoutes.Sell.Profile.route -> pendingAccessRequestCount
                else -> 0
            }
            NavigationBarItem(
                icon = { SellerNavItemIcon(item = item, label = label, badgeCount = badgeCount) },
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

/**
 * Side navigation rail shown instead of the bottom bar on Expanded windows
 * (tablet landscape). Same items, routes and badges as the bottom bar.
 */
@Composable
fun SellerNavigationRail(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    pendingOrdersCount: Int = 0,
    pendingAccessRequestCount: Int = 0
) {
    NavigationRail {
        Spacer(Modifier.weight(1f))
        SellerBottomNavItems.forEach { item ->
            val label = stringResource(item.labelRes)
            val badgeCount = when (item.route) {
                NavRoutes.Sell.Orders.route -> pendingOrdersCount
                NavRoutes.Sell.Profile.route -> pendingAccessRequestCount
                else -> 0
            }
            NavigationRailItem(
                icon = { SellerNavItemIcon(item = item, label = label, badgeCount = badgeCount) },
                label = { Text(label) },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        onNavigate(item.route)
                    }
                }
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SellerNavItemIcon(
    item: BottomNavItem,
    label: String,
    badgeCount: Int
) {
    if (badgeCount > 0) {
        BadgedBox(badge = { Badge { Text(badgeCount.toString()) } }) {
            Icon(imageVector = item.icon, contentDescription = label)
        }
    } else {
        Icon(imageVector = item.icon, contentDescription = label)
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
        route = NavRoutes.Sell.Abrechnung.route,
        labelRes = Res.string.bottomnav_abrechnung,
        icon = Icons.Default.BarChart
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
