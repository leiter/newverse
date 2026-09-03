package com.together.newverse.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.util.formatString
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
            val badgeDescription = sellerBadgeDescription(item.route, badgeCount)
            NavigationBarItem(
                icon = {
                    SellerNavItemIcon(
                        item = item,
                        badgeCount = badgeCount,
                        badgeDescription = badgeDescription
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
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Spacer(Modifier.weight(1f))
        SellerBottomNavItems.forEach { item ->
            val label = stringResource(item.labelRes)
            val badgeCount = when (item.route) {
                NavRoutes.Sell.Orders.route -> pendingOrdersCount
                NavRoutes.Sell.Profile.route -> pendingAccessRequestCount
                else -> 0
            }
            val badgeDescription = sellerBadgeDescription(item.route, badgeCount)
            NavigationRailItem(
                modifier = Modifier.padding(horizontal = 12.dp),
                icon = {
                    SellerNavItemIcon(
                        item = item,
                        badgeCount = badgeCount,
                        badgeDescription = badgeDescription
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
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Screen-reader phrase for a nav-item count badge, or null when there is nothing
 * to announce. A bare "3" next to "Nachfrage" tells a screen-reader user nothing;
 * "3 offene Bestellungen" / "3 Zugangsanfragen" does.
 */
@Composable
private fun sellerBadgeDescription(route: String, badgeCount: Int): String? {
    if (badgeCount <= 0) return null
    return when (route) {
        NavRoutes.Sell.Orders.route ->
            formatString(stringResource(Res.string.a11y_pending_orders_badge), badgeCount)
        NavRoutes.Sell.Profile.route ->
            formatString(stringResource(Res.string.a11y_access_requests_badge), badgeCount)
        else -> null
    }
}

@Composable
private fun SellerNavItemIcon(
    item: BottomNavItem,
    badgeCount: Int,
    badgeDescription: String?
) {
    // The icon is decorative: the nav item's always-visible text label carries the
    // name and the item itself supplies the Tab role and selected state.
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    modifier = Modifier.clearAndSetSemantics {
                        if (badgeDescription != null) contentDescription = badgeDescription
                    }
                ) { Text(badgeCount.toString()) }
            }
        ) {
            Icon(imageVector = item.icon, contentDescription = null)
        }
    } else {
        Icon(imageVector = item.icon, contentDescription = null)
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
