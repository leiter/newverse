package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Order
import com.together.newverse.util.formatPrice
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.graphics.Color

/**
 * Order detail screen for sellers to view and manage individual orders
 * Note: Does not have its own Scaffold - relies on AppScaffold for top bar
 */
@Composable
fun OrderDetailScreen(
    orderId: String,
    onNavigateBack: () -> Unit,
    viewModel: OrdersViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    // Find the order from the current state
    val order = when (val state = uiState) {
        is OrdersUiState.Success -> state.orders.find { it.id == orderId }
        else -> null
    }

    Scaffold(
        floatingActionButton = {
            if (order != null) {
                FloatingActionButton(
                    onClick = { showDeleteDialog = true },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.delete_order)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (order == null) {
            // Order not found
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.order_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onNavigateBack) {
                        Text(stringResource(Res.string.back))
                    }
                }
            }
        } else {
            // Show order details
            OrderDetailContent(order = order)
        }

        // Delete confirmation dialog
        if (showDeleteDialog && order != null) {
            DeleteOrderConfirmationDialog(
                order = order,
                isDeleting = isDeleting,
                onConfirm = {
                    isDeleting = true
                    viewModel.hideOrder(
                        order = order,
                        onSuccess = {
                            isDeleting = false
                            onNavigateBack()
                        },
                        onError = { error ->
                            isDeleting = false
                            // TODO: Show error message
                            println("❌ Failed to hide order: $error")
                        }
                    )
                },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }
        }
    }
}

@Composable
private fun OrderDetailContent(order: Order) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Customer information section
        item {
            OrderDetailSection(title = stringResource(Res.string.customer_information)) {
                OrderDetailRow(
                    label = stringResource(Res.string.label_name),
                    value = order.buyerProfile.displayName
                )
                if (order.buyerProfile.emailAddress.isNotEmpty()) {
                    OrderDetailRow(
                        label = stringResource(Res.string.label_email),
                        value = order.buyerProfile.emailAddress
                    )
                }
                if (order.buyerProfile.telephoneNumber.isNotEmpty()) {
                    OrderDetailRow(
                        label = stringResource(Res.string.label_phone),
                        value = order.buyerProfile.telephoneNumber
                    )
                }
            }
        }

        // Order information section
        item {
            OrderDetailSection(title = stringResource(Res.string.order_information)) {
                OrderDetailRow(
                    label = stringResource(Res.string.order_id),
                    value = order.id.take(8)
                )
                OrderDetailRow(
                    label = stringResource(Res.string.pickup_date),
                    value = order.getFormattedPickupDateAndTime()
                )
                OrderDetailRow(
                    label = stringResource(Res.string.order_status),
                    value = order.status.name
                )
                if (order.message.isNotEmpty()) {
                    OrderDetailRow(
                        label = stringResource(Res.string.customer_message),
                        value = order.message
                    )
                }
            }
        }

        // Ordered products section
        item {
            Text(
                text = stringResource(Res.string.ordered_products),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(order.articles) { product ->
            OrderedProductDetailCard(product = product)
        }

        // Total section
        item {
            OrderTotalCard(order = order)
        }
    }
}

@Composable
private fun OrderDetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
private fun OrderDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Card displaying individual ordered product with prices
 */
@Composable
private fun OrderedProductDetailCard(product: com.together.newverse.domain.model.OrderedProduct) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = product.getFormattedAmount(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                // Total price for this product
                Text(
                    text = "${product.getFormattedTotalPrice()}€",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Price per unit
                Text(
                    text = "${product.getFormattedPricePerUnit()}€/${product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Card displaying the total order amount
 */
@Composable
private fun OrderTotalCard(order: Order) {
    val totalAmount = order.articles.sumOf { it.getTotalPrice() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.total_amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${totalAmount.formatPrice()}€",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DeleteOrderConfirmationDialog(
    order: Order,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = {
            Text(stringResource(Res.string.delete_order_title))
        },
        text = {
            Text(
                stringResource(
                    Res.string.delete_order_confirmation,
                    order.buyerProfile.displayName
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text(stringResource(Res.string.button_cancel))
            }
        }
    )
}
