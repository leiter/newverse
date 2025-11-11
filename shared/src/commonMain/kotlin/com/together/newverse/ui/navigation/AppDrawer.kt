package com.together.newverse.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource

/**
 * Navigation Drawer for the app
 *
 * Groups navigation items by category (Common, Customer, Seller)
 * Items are automatically filtered based on build flavor
 */
@Composable
fun AppDrawer(
    currentRoute: String,
    onNavigate: (NavRoutes) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // App Header
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation Items
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Get routes filtered by current build flavor
                val filteredRoutes = NavRoutes.getRoutesForCurrentFlavor()

                val groupedRoutes = filteredRoutes.groupBy { NavRoutes.getCategoryRes(it) }

                groupedRoutes.forEach { (categoryRes, routes) ->
                    item {
                        Text(
                            text = stringResource(categoryRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                top = 16.dp,
                                bottom = 8.dp
                            )
                        )
                    }

                    items(routes) { route ->
                        DrawerItem(
                            route = route,
                            isSelected = currentRoute == route.route,
                            onClick = {
                                onNavigate(route)
                                onClose()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    route: NavRoutes,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Text(stringResource(NavRoutes.getDisplayNameRes(route)))
        },
        selected = isSelected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
