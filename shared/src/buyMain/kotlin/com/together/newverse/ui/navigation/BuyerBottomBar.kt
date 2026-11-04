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
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.together.newverse.ui.theme.LocalBarColors
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_basket_item_count
import newverse.shared.generated.resources.a11y_label_with_badge
import newverse.shared.generated.resources.a11y_profile_incomplete_badge
import newverse.shared.generated.resources.nav_customer_profile
import newverse.shared.generated.resources.nav_home
import newverse.shared.generated.resources.nav_shopping_basket
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
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

/**
 * Unselected bar items are dimmed for hierarchy, but not so far that they stop
 * being readable: at this alpha the content still clears 4.5:1 on the brown bar
 * in both themes (6.2:1 light, 4.9:1 dark).
 */
private const val UnselectedItemAlpha = 0.74f

@Composable
fun BuyerBottomNavigationBar(
    currentRoute: String,
    basketItemCount: Int,
    showProfileBadge: Boolean = false,
    onNavigate: (String) -> Unit
) {
    val cartShake = rememberCartShake(basketItemCount)

    // Soil brown, the same bar colour as the top app bar, so the app is framed
    // by the brand in both themes. The item colours have to be spelled out:
    // NavigationBarItem's defaults put the selected icon on a secondaryContainer
    // pill, which is this very brown in the dark scheme, and the selected tab
    // would vanish into the bar.
    val barColors = LocalBarColors.current
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = barColors.onSelectionIndicator,
        selectedTextColor = barColors.content,
        indicatorColor = barColors.selectionIndicator,
        unselectedIconColor = barColors.content.copy(alpha = UnselectedItemAlpha),
        unselectedTextColor = barColors.content.copy(alpha = UnselectedItemAlpha),
    )

    NavigationBar(
        containerColor = barColors.container,
        contentColor = barColors.content,
    ) {
        BuyerBottomNavItems.forEach { item ->
            val label = stringResource(item.labelRes)
            val isSelected = currentRoute == item.route ||
                currentRoute.startsWith(item.route)

            val badgeCount = when (item.route) {
                NavRoutes.Buy.Basket.route -> basketItemCount
                else -> 0
            }

            // Unfinished profile data is the buyer's own task, so it is marked on
            // the tab that holds it. Demo mode is not: it needs the seller.
            val showAttentionDot = showProfileBadge &&
                item.route == NavRoutes.Buy.Profile.route

            val badgeText = badgeDescription(badgeCount, showAttentionDot)

            val isCartItem = item.route == NavRoutes.Buy.Basket.route

            NavigationBarItem(
                modifier = itemSemantics(label, badgeText),
                icon = {
                    BuyerNavItemIcon(
                        item = item,
                        badgeCount = badgeCount,
                        showAttentionDot = showAttentionDot,
                        modifier = if (isCartItem) cartShake else Modifier
                    )
                },
                label = { Text(label) },
                selected = isSelected,
                colors = itemColors,
                onClick = {
                    if (!isSelected) onNavigate(item.route)
                }
            )
        }
    }
}

/**
 * Side navigation rail shown instead of the bottom bar on Expanded windows
 * (tablet landscape). Same items, routes, badges and cart shake as the bottom bar.
 */
@Composable
fun BuyerNavigationRail(
    currentRoute: String,
    basketItemCount: Int,
    showProfileBadge: Boolean = false,
    onNavigate: (String) -> Unit
) {
    val cartShake = rememberCartShake(basketItemCount)
    val barColors = LocalBarColors.current
    val itemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = barColors.onSelectionIndicator,
        selectedTextColor = barColors.content,
        indicatorColor = barColors.selectionIndicator,
        unselectedIconColor = barColors.content.copy(alpha = UnselectedItemAlpha),
        unselectedTextColor = barColors.content.copy(alpha = UnselectedItemAlpha),
    )

    NavigationRail(
        containerColor = barColors.container,
        contentColor = barColors.content,
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

            // Unfinished profile data is the buyer's own task, so it is marked on
            // the tab that holds it. Demo mode is not: it needs the seller.
            val showAttentionDot = showProfileBadge &&
                item.route == NavRoutes.Buy.Profile.route

            val badgeText = badgeDescription(badgeCount, showAttentionDot)

            val isCartItem = item.route == NavRoutes.Buy.Basket.route

            NavigationRailItem(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .then(itemSemantics(label, badgeText)),
                icon = {
                    BuyerNavItemIcon(
                        item = item,
                        badgeCount = badgeCount,
                        showAttentionDot = showAttentionDot,
                        modifier = if (isCartItem) cartShake else Modifier
                    )
                },
                label = { Text(label) },
                selected = isSelected,
                colors = itemColors,
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
    showAttentionDot: Boolean = false,
    modifier: Modifier = Modifier
) {
    // The icon is decorative: NavigationBarItem's always-visible text label carries
    // the name, and the item itself supplies the Tab role and selected state.
    // The badge is muted here and described by the item instead, so that the tab
    // name is announced before its count rather than after it.
    when {
        badgeCount > 0 -> {
            BadgedBox(
                badge = {
                    Badge(
                        modifier = Modifier.clearAndSetSemantics { }
                    ) { Text(badgeCount.toString()) }
                }
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = modifier
                )
            }
        }
        // A dot rather than a number: it marks a state, it does not count anything.
        showAttentionDot -> {
            BadgedBox(
                badge = {
                    Badge(
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                }
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = modifier
                )
            }
        }
        else -> {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = modifier
            )
        }
    }
}

/**
 * Screen-reader text for an item's badge, or null when it carries none.
 * Pluralised, so a basket holding one article does not read as "1 items".
 */
@Composable
private fun badgeDescription(badgeCount: Int, showAttentionDot: Boolean): String? = when {
    badgeCount > 0 -> pluralStringResource(
        Res.plurals.a11y_basket_item_count,
        badgeCount,
        badgeCount
    )
    showAttentionDot -> stringResource(Res.string.a11y_profile_incomplete_badge)
    else -> null
}

/**
 * Describes a badged item as "name, badge". A description on the item replaces its
 * label for screen readers, which is what fixes the order; the Tab role and the
 * selected state are separate semantics and survive untouched. Items without a
 * badge keep their plain label.
 *
 * The two parts are joined by a resource rather than in code, so a locale that
 * separates them differently can say so.
 */
@Composable
private fun itemSemantics(label: String, badgeText: String?): Modifier {
    if (badgeText == null) return Modifier
    val description = stringResource(Res.string.a11y_label_with_badge, label, badgeText)
    return Modifier.semantics { contentDescription = description }
}

/**
 * The basket as a top-bar action, for screens that want it within thumb reach of
 * the bar rather than only on the tab row.
 *
 * Shares this file with [BuyerBottomNavigationBar] on purpose rather than living
 * at the call site: the icon, the count, the pluralised badge wording and the
 * "name, badge" reading order are all decisions already made for the basket tab,
 * and two copies would eventually describe the same basket two different ways.
 * The way in is the caller's, so it can be the very same tab navigation.
 */
@Composable
fun BuyerBasketAction(
    basketItemCount: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cartShake = rememberCartShake(basketItemCount)
    val label = stringResource(Res.string.nav_shopping_basket)
    val badgeText = badgeDescription(basketItemCount, showAttentionDot = false)
    // "Warenkorb, 3 Artikel" -- the name before the count, joined by the same
    // resource the bar items use so a locale can order it differently.
    val description = if (badgeText == null) {
        label
    } else {
        stringResource(Res.string.a11y_label_with_badge, label, badgeText)
    }

    IconButton(onClick = onClick, modifier = modifier) {
        if (basketItemCount > 0) {
            BadgedBox(
                // Muted, because the count is already part of the button's own
                // description; read on its own it would arrive after the name.
                badge = {
                    Badge(modifier = Modifier.clearAndSetSemantics { }) {
                        Text(basketItemCount.toString())
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = description,
                    tint = tint,
                    modifier = cartShake
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = description,
                tint = tint,
                modifier = cartShake
            )
        }
    }
}

/**
 * A modifier that rocks the cart icon whenever [basketItemCount] changes.
 *
 * Shared by every cart the buyer can see -- the bottom bar's tab, the rail's tab
 * at Expanded width, and the top-bar action -- so the two that are on screen
 * together rock together instead of one twitching while the other sits still.
 *
 * The rotation is read inside [graphicsLayer]'s block rather than returned as a
 * number: that defers the read to draw time, so eleven frames of animation do not
 * recompose the bar that holds it.
 *
 * It does not fire on first composition -- the previous count starts out as the
 * current one -- so arriving at a screen with a full basket is not an event. Only
 * a change while the buyer is looking at it is.
 */
@Composable
private fun rememberCartShake(basketItemCount: Int): Modifier {
    val rotation = remember { Animatable(0f) }
    var previousCount by remember { mutableStateOf(basketItemCount) }

    LaunchedEffect(basketItemCount) {
        if (basketItemCount != previousCount) {
            rotation.animateTo(15f, animationSpec = tween(50))
            rotation.animateTo(-15f, animationSpec = tween(100))
            rotation.animateTo(10f, animationSpec = tween(100))
            rotation.animateTo(-10f, animationSpec = tween(100))
            rotation.animateTo(
                0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
        }
        previousCount = basketItemCount
    }

    return Modifier.graphicsLayer { rotationZ = rotation.value }
}
