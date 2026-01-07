package com.together.newverse.ui.navigation

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
import androidx.compose.ui.graphics.vector.ImageVector
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.nav_customer_profile
import newverse.shared.generated.resources.nav_home
import newverse.shared.generated.resources.nav_shopping_basket
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom Navigation Bar for the Buyer App
 *
 * Provides 3 main tabs:
 * - Home: Products/main screen
 * - Basket: Shopping basket with badge showing item count
 * - Profile: Customer profile with access to About, Login, Register
 */

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
    NavigationBar {
        BuyerBottomNavItems.forEach { item ->
            val label = stringResource(item.labelRes)
            val isSelected = currentRoute == item.route ||
                currentRoute.startsWith(item.route)

            NavigationBarItem(
                icon = {
                    if (item.route == NavRoutes.Buy.Basket.route && basketItemCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(basketItemCount.toString())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label
                            )
                        }
                    } else {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = label
                        )
                    }
                },
                label = { Text(label) },
                selected = isSelected,
                onClick = {
                    println("🔍 BottomBar: onClick ${item.route}, currentRoute=$currentRoute, isSelected=$isSelected")
                    if (!isSelected) {
                        println("🔍 BottomBar: Calling onNavigate(${item.route})")
                        onNavigate(item.route)
                    } else {
                        println("🔍 BottomBar: Skipped - already selected")
                    }
                }
            )
        }
    }
}
