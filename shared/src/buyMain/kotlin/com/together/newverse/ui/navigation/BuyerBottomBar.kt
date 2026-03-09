package com.together.newverse.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import newverse.shared.generated.resources.Res
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
                    if (badgeCount > 0) {
                        BadgedBox(
                            badge = { Badge { Text(badgeCount.toString()) } }
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label,
                                modifier = iconModifier
                            )
                        }
                    } else {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = label,
                            modifier = iconModifier
                        )
                    }
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
