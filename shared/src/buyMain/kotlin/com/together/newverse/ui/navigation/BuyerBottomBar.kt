package com.together.newverse.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.together.newverse.util.formatString
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_basket_item_count
import newverse.shared.generated.resources.nav_customer_profile
import newverse.shared.generated.resources.nav_home
import newverse.shared.generated.resources.nav_shopping_basket
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

data class BuyerBottomNavItem(
    val route: String,
    val icon: ImageVector,
    val labelRes: StringResource
)

val BuyerBottomNavItems = listOf(
    BuyerBottomNavItem(
        route = NavRoutes.Home.route,
        icon = Icons.Default.Home,
        labelRes = Res.string.nav_home
    ),
    BuyerBottomNavItem(
        route = NavRoutes.Buy.Basket.route,
        icon = Icons.Default.ShoppingCart,
        labelRes = Res.string.nav_shopping_basket
    ),
    BuyerBottomNavItem(
        route = NavRoutes.Buy.Profile.route,
        icon = Icons.Default.Person,
        labelRes = Res.string.nav_customer_profile
    )
)

@Composable
fun BuyerBottomNavigationBar(
    currentRoute: String,
    basketItemCount: Int,
    onNavigate: (String) -> Unit
) {
    // Shake animation for the cart icon
    val cartShake = remember { Animatable(0f) }
    var prevBasketCount by remember { mutableStateOf(basketItemCount) }

    LaunchedEffect(basketItemCount) {
        if (basketItemCount != prevBasketCount && !(prevBasketCount == 0 && basketItemCount == 0)) {
            cartShake.animateTo(15f, animationSpec = tween(50))
            cartShake.animateTo(-15f, animationSpec = tween(100))
            cartShake.animateTo(10f, animationSpec = tween(100))
            cartShake.animateTo(-10f, animationSpec = tween(100))
            cartShake.animateTo(0f, animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh
            ))
        }
        prevBasketCount = basketItemCount
    }

    NavigationBar {
        BuyerBottomNavItems.forEach { item ->
            val label = stringResource(item.labelRes)
            val isSelected = currentRoute == item.route ||
                currentRoute.startsWith(item.route)

            val badgeCount = when (item.route) {
                NavRoutes.Buy.Basket.route -> basketItemCount
                else -> 0
            }

            val isCartItem = item.route == NavRoutes.Buy.Basket.route

            NavigationBarItem(
                icon = {
                    val iconModifier = if (isCartItem) {
                        Modifier.graphicsLayer { rotationZ = cartShake.value }
                    } else {
                        Modifier
                    }
                    BuyerNavItemIcon(
                        item = item,
                        badgeCount = badgeCount,
                        modifier = iconModifier
                    )
                },
                label = { Text(label) },
                selected = isSelected,
                onClick = {
                    if (!isSelected) onNavigate(item.route)
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
fun BuyerNavigationRail(
    currentRoute: String,
    basketItemCount: Int,
    onNavigate: (String) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Spacer(Modifier.weight(1f))
        BuyerBottomNavItems.forEach { item ->
            val label = stringResource(item.labelRes)
            val isSelected = currentRoute == item.route ||
                currentRoute.startsWith(item.route)

            val badgeCount = when (item.route) {
                NavRoutes.Buy.Basket.route -> basketItemCount
                else -> 0
            }

            NavigationRailItem(
                modifier = Modifier.padding(horizontal = 12.dp),
                icon = { BuyerNavItemIcon(item = item, badgeCount = badgeCount) },
                label = { Text(label) },
                selected = isSelected,
                onClick = {
                    if (!isSelected) onNavigate(item.route)
                }
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun BuyerNavItemIcon(
    item: BuyerBottomNavItem,
    badgeCount: Int,
    modifier: Modifier = Modifier
) {
    // The icon is decorative: NavigationBarItem's always-visible text label carries
    // the name, and the item itself supplies the Tab role and selected state.
    if (badgeCount > 0) {
        val badgeDescription = formatString(
            stringResource(Res.string.a11y_basket_item_count),
            badgeCount
        )
        BadgedBox(
            badge = {
                Badge(
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = badgeDescription
                    }
                ) { Text(badgeCount.toString()) }
            }
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = modifier
            )
        }
    } else {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = modifier
        )
    }
}
