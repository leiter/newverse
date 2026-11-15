package com.together.newverse.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.together.newverse.ui.navigation.NavRoutes
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Top bar for Seller app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerTopBar(
    currentRoute: String,
    /**
     * The Create route is also the edit route -- `sell/create?articleId=…` -- and
     * [currentRoute] is the pattern, so it cannot tell the two apart on its own. The
     * bar said "Neues Produkt" over an article the seller was editing.
     */
    isEditingArticle: Boolean = false,
    isSelectionMode: Boolean = false,
    isAvailabilityMode: Boolean = false,
    isSearchExpanded: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onToggleSelectionMode: () -> Unit = {},
    onToggleAvailabilityMode: () -> Unit = {},
    onImportProducts: () -> Unit = {}
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    val isInSelectionMode = isSelectionMode || isAvailabilityMode
    // Selection and availability mode both take over the whole bar, so search gives
    // way to them rather than competing: three independent bar states would each
    // need to know about the other two.
    //
    // Two screens have a list worth searching: the Sortiment and the Lager. Each keeps
    // its own query (SearchScope), and the route is checked for drawing the field as
    // well as for offering it, so the bar cannot end up holding a field for a screen
    // that has nothing to filter.
    val searchRoute = currentRoute == NavRoutes.Sell.Overview.route ||
        currentRoute == NavRoutes.Sell.Stock.route
    val showSearchField = isSearchExpanded && !isInSelectionMode && searchRoute
    val searchAvailable = searchRoute && !isInSelectionMode

    // Get localized titles
    val sortimentTitle = stringResource(Res.string.topbar_sortiment)
    val ordersTitle = stringResource(Res.string.topbar_orders)
    val newProductTitle = stringResource(Res.string.topbar_new_product)
    val editProductTitle = stringResource(Res.string.topbar_edit_product)
    val profileTitle = stringResource(Res.string.topbar_profile)
    val notificationsTitle = stringResource(Res.string.topbar_notifications)
    val abrechnungTitle = stringResource(Res.string.topbar_abrechnung)
    val sellerTitle = stringResource(Res.string.topbar_seller)
    val customerDetailTitle = stringResource(Res.string.customer_detail_title)
    val selectDeleteTitle = stringResource(Res.string.topbar_select_delete)
    val changeAvailabilityTitle = stringResource(Res.string.topbar_change_availability)
    val importProductsTitle = stringResource(Res.string.topbar_import_products)
    val stockTitle = stringResource(Res.string.topbar_stock)

    TopAppBar(
        title = {
            if (showSearchField) {
                SellerSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    // The bar's own X clears it; see the actions slot below.
                    showClearButton = false
                )
                return@TopAppBar
            }
            Text(
                text = if (isInSelectionMode) {
                    if (isSelectionMode) selectDeleteTitle else changeAvailabilityTitle
                } else {
                    getRouteTitle(
                        currentRoute,
                        sortimentTitle,
                        ordersTitle,
                        if (isEditingArticle) editProductTitle else newProductTitle,
                        profileTitle,
                        notificationsTitle,
                        abrechnungTitle,
                        sellerTitle,
                        stockTitle,
                        customerDetailTitle
                    )
                },
                // Let TalkBack's next-heading gesture land on the screen title
                modifier = Modifier.semantics { heading() }
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
                        contentDescription = stringResource(Res.string.cd_cancel_selection)
                    )
                }
            } else if (shouldShowBackButton(currentRoute)) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back)
                    )
                }
            }
        },
        actions = {
            // While searching, the field needs the whole bar: search, refresh and
            // overflow beside a full-width text field leave it a few characters wide
            // on a phone. Clear-then-close is the only action that still makes sense.
            if (showSearchField) {
                // One X, two steps: it empties the query, and once there is no query
                // it closes the search. The field used to carry a clear button of its
                // own, which put two X's in the bar a touch target apart -- one
                // emptying the field, one closing the whole thing -- with nothing to
                // say which did what. Stepping through them in that order is also the
                // order the seller wants them in: a query is corrected far more often
                // than the search is abandoned.
                val hasQuery = searchQuery.isNotEmpty()
                IconButton(onClick = { if (hasQuery) onSearchQueryChange("") else onToggleSearch() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(
                            if (hasQuery) Res.string.products_search_clear
                            else Res.string.cd_close_search
                        )
                    )
                }
                return@TopAppBar
            }
            // Hide normal actions when in selection mode for cleaner UI
            if (!isInSelectionMode) {
                // No orders shortcut here. It was a badged cart on every route, and
                // it was the second one: the "Nachfrage" item in SellerBottomBar /
                // SellerNavigationRail carries the same pendingOrdersCount badge, from
                // the same value in AppScaffold, and is the way the seller actually
                // navigates. One count in two places can only ever agree or look
                // broken, and the bar's width is better spent on the actions that
                // belong to the screen the seller is on.
                if (searchAvailable) {
                    IconButton(onClick = onToggleSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(Res.string.cd_open_search)
                        )
                    }
                }

                // Show refresh and overflow menu on Overview screen
                if (currentRoute == NavRoutes.Sell.Overview.route) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.button_update)
                        )
                    }

                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(Res.string.cd_more_options)
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
    newProductTitle: String,
    profileTitle: String,
    notificationsTitle: String,
    abrechnungTitle: String,
    sellerTitle: String,
    stockTitle: String,
    customerDetailTitle: String
): String {
    return when (route) {
        NavRoutes.Sell.Overview.route -> sortimentTitle
        NavRoutes.Sell.Orders.route -> ordersTitle
        NavRoutes.Sell.Create.route -> newProductTitle
        NavRoutes.Sell.Profile.route -> profileTitle
        NavRoutes.Sell.NotificationSettings.route -> notificationsTitle
        NavRoutes.Sell.Abrechnung.route -> abrechnungTitle
        NavRoutes.Sell.Stock.route -> stockTitle
        NavRoutes.Sell.CustomerDetail.route -> customerDetailTitle
        else -> sellerTitle
    }
}

private val topLevelRoutes = setOf(
    NavRoutes.Sell.Overview.route,
    NavRoutes.Sell.Orders.route,
    NavRoutes.Sell.Abrechnung.route,
    NavRoutes.Sell.Profile.route,
)

private fun shouldShowBackButton(route: String): Boolean {
    return route !in topLevelRoutes
}
