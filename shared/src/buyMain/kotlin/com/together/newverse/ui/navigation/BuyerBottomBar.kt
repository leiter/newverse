package com.together.newverse.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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
import newverse.shared.generated.resources.nav_messages
import newverse.shared.generated.resources.nav_shopping_basket
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom Navigation Bar for the Buyer App
 *
 * Provides 4 main tabs:
 * - Home: Products/main screen
 * - Basket: Shopping basket with badge showing item count
 * - Messages: Conversations with badge showing unread count
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
        route = NavRoutes.Buy.Messages.route,
        icon = Icons.Default.Email,
        labelRes = Res.string.nav_messages
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
    unreadMessageCount: Int = 0,
    onNavigate: (String) -> Unit
) {
    println("🔍 BottomBar: Composing, currentRoute=$currentRoute")
    NavigationBar {
        BuyerBottomNavItems.forEach { item ->
            val label = stringResource(item.labelRes)
            val isSelected = currentRoute == item.route ||
                currentRoute.startsWith(item.route)

            // Determine badge count for this item
            val badgeCount = when (item.route) {
                NavRoutes.Buy.Basket.route -> basketItemCount
                NavRoutes.Buy.Messages.route -> unreadMessageCount
                else -> 0
            }

            NavigationBarItem(
                icon = {
                    if (badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(badgeCount.toString())
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
