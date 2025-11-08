package com.together.newverse.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Navigation Drawer for the app
 *
 * Groups navigation items by category (Common, Customer, Seller)
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
                text = "Newverse",
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
                // Group routes by category
                val groupedRoutes = NavRoutes.getAllRoutes()
                    .groupBy { NavRoutes.getCategory(it) }

                groupedRoutes.forEach { (category, routes) ->
                    item {
                        Text(
                            text = category,
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
            Text(NavRoutes.getDisplayName(route))
        },
        selected = isSelected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
