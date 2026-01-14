package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.OrderStatus
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Orders screen for sellers to manage incoming orders
 * Based on universe project's ShowOrdersFragment
 */
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = koinViewModel(),
    onOrderClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        //Spacer(modifier = Modifier.height(16.dp))

        // Content based on state
        when (val state = uiState) {
            is OrdersUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is OrdersUiState.Success -> {
                if (state.orders.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.no_orders_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Orders list
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.orders) { order ->
                            SellerOrderCard(
                                order = order,
                                onClick = { onOrderClick(order.id) }
                            )
                        }
                    }
                }
            }

            is OrdersUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.refresh() }) {
                            Text(stringResource(Res.string.retry))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Seller order card displaying detailed order information
 * Based on universe project's item_next_order.xml layout
 */
@Composable
private fun SellerOrderCard(
    order: com.together.newverse.domain.model.Order,
    onClick: () -> Unit = {}
) {
    // Check if order is cancelled
    val isCancelled = order.status == OrderStatus.CANCELLED

    // Determine if order is "open" for seller's view
    // Based on universe project: orders are "open" if pickup date hasn't passed yet
    // Simple rule: if pickup date is in the future, the order is still open/active
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val isOpen = order.pickUpDate > now && !isCancelled

    // Use different visual styling based on order state
    val cardColors = when {
        isCancelled -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
        isOpen -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
        else -> CardDefaults.cardColors()
    }

    val borderStroke = when {
        isCancelled -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        isOpen -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = cardColors,
        border = borderStroke
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Customer name and product count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.buyerProfile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${order.articles.size} Produkte",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pickup date and time
            Text(
                text = order.getFormattedPickupDateAndTime(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            // Message (if exists)
            if (order.message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = order.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Ordered products list
            if (order.articles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    order.articles.forEach { product ->
                        OrderedProductItem(product = product)
                    }
                }
            }

            // Status indicator at bottom
            Spacer(modifier = Modifier.height(4.dp))

            // Show "Storniert" label for cancelled orders
            if (isCancelled) {
                Text(
                    text = "Storniert",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Divider color based on order state
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 4.dp,
                color = if (isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Individual ordered product item display
 * Based on universe project's item_ordered_product.xml layout
 */
@Composable
private fun OrderedProductItem(
    product: com.together.newverse.domain.model.OrderedProduct
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Amount
        Text(
            text = product.getFormattedAmount(),
            style = MaterialTheme.typography.bodyLarge
        )

        // Product name
        Text(
            text = product.productName,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
