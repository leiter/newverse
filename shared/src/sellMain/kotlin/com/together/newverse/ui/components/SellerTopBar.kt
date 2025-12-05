package com.together.newverse.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.together.newverse.ui.navigation.NavRoutes
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.topbar_change_availability
import newverse.shared.generated.resources.topbar_import_products
import newverse.shared.generated.resources.topbar_new_product
import newverse.shared.generated.resources.topbar_notifications
import newverse.shared.generated.resources.topbar_orders
import newverse.shared.generated.resources.topbar_products
import newverse.shared.generated.resources.topbar_profile
import newverse.shared.generated.resources.topbar_select_delete
import newverse.shared.generated.resources.topbar_seller
import newverse.shared.generated.resources.topbar_sortiment
import org.jetbrains.compose.resources.stringResource

/**
 * Top bar for Seller app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerTopBar(
    currentRoute: String,
    pendingOrdersCount: Int,
    isSelectionMode: Boolean = false,
    isAvailabilityMode: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onRefresh: () -> Unit = {},
    onToggleSelectionMode: () -> Unit = {},
    onToggleAvailabilityMode: () -> Unit = {},
    onImportProducts: () -> Unit = {}
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    val isInSelectionMode = isSelectionMode || isAvailabilityMode

    // Get localized titles
    val sortimentTitle = stringResource(Res.string.topbar_sortiment)
    val ordersTitle = stringResource(Res.string.topbar_orders)
    val productsTitle = stringResource(Res.string.topbar_products)
    val newProductTitle = stringResource(Res.string.topbar_new_product)
    val profileTitle = stringResource(Res.string.topbar_profile)
    val notificationsTitle = stringResource(Res.string.topbar_notifications)
    val sellerTitle = stringResource(Res.string.topbar_seller)
    val selectDeleteTitle = stringResource(Res.string.topbar_select_delete)
    val changeAvailabilityTitle = stringResource(Res.string.topbar_change_availability)
    val importProductsTitle = stringResource(Res.string.topbar_import_products)

    TopAppBar(
        title = {
            Text(
                if (isInSelectionMode) {
                    if (isSelectionMode) selectDeleteTitle else changeAvailabilityTitle
                } else {
                    getRouteTitle(
                        currentRoute,
                        sortimentTitle,
                        ordersTitle,
                        productsTitle,
                        newProductTitle,
                        profileTitle,
                        notificationsTitle,
                        sellerTitle
                    )
                }
            )
        },
        navigationIcon = {
            if (isInSelectionMode) {
                // Show close button to cancel selection mode
                IconButton(
                    onClick = {
                        if (isSelectionMode) onToggleSelectionMode()
                        if (isAvailabilityMode) onToggleAvailabilityMode()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Auswahl abbrechen"
                    )
                }
            } else if (shouldShowBackButton(currentRoute)) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück"
                    )
                }
            }
        },
        actions = {
            // Hide normal actions when in selection mode for cleaner UI
            if (!isInSelectionMode) {
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

                // Show refresh and overflow menu on Overview screen
                if (currentRoute == NavRoutes.Sell.Overview.route) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Aktualisieren"
                        )
                    }

                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Mehr Optionen"
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(selectDeleteTitle) },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleSelectionMode()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(changeAvailabilityTitle) },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleAvailabilityMode()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(importProductsTitle) },
                                onClick = {
                                    showOverflowMenu = false
                                    onImportProducts()
                                }
                            )
                        }
                    }
                }

//                IconButton(onClick = onNavigateToNotifications) {
//                    Icon(
//                        imageVector = Icons.Default.Notifications,
//                        contentDescription = "Benachrichtigungen"
//                    )
//                }

//                IconButton(onClick = onNavigateToProfile) {
//                    Icon(
//                        imageVector = Icons.Default.Person,
//                        contentDescription = "Profil"
//                    )
//                }
            }
        }
    )
}

private fun getRouteTitle(
    route: String,
    sortimentTitle: String,
    ordersTitle: String,
    productsTitle: String,
    newProductTitle: String,
    profileTitle: String,
    notificationsTitle: String,
    sellerTitle: String
): String {
    return when (route) {
        NavRoutes.Sell.Overview.route -> sortimentTitle
        NavRoutes.Sell.Orders.route -> ordersTitle
        NavRoutes.Sell.Products.route -> productsTitle
        NavRoutes.Sell.Create.route -> newProductTitle
        NavRoutes.Sell.Profile.route -> profileTitle
        NavRoutes.Sell.NotificationSettings.route -> notificationsTitle
        else -> sellerTitle
    }
}

private fun shouldShowBackButton(route: String): Boolean {
    return route != NavRoutes.Sell.Overview.route
}
