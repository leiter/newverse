package com.together.newverse.ui.screens.buy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Order
import com.together.newverse.ui.adaptive.AdaptiveDefaults
import com.together.newverse.ui.state.BuyAction
import com.together.newverse.ui.state.BuyProfileAction
import com.together.newverse.ui.state.OrderHistoryScreenState
import com.together.newverse.ui.state.core.AsyncStateContent
import com.together.newverse.ui.state.toAsyncState
import com.together.newverse.util.formatPrice
import com.together.newverse.util.formatString
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

@Composable
fun OrderHistoryScreen(
    orderHistoryState: OrderHistoryScreenState,
    showMergeDialog: Boolean,
    tappedOrder: Order?,
    onAction: (BuyAction) -> Unit,
    onNavigateToBasket: () -> Unit,
    onRetry: () -> Unit
) {
    // Load order history when screen opens
    androidx.compose.runtime.LaunchedEffect(Unit) {
        println("📋 OrderHistoryScreen: Triggering loadOrderHistory")
        onAction(BuyProfileAction.LoadOrderHistory)
    }

    if (showMergeDialog && tappedOrder != null) {
        OrderHistoryMergeDialog(
            onMerge = { onAction(BuyProfileAction.MergeHistoryOrder) },
            onDiscard = { onAction(BuyProfileAction.DiscardAndLoadHistoryOrder) },
            onCancel = { onAction(BuyProfileAction.HideHistoryMergeDialog) }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Use AsyncStateContent for clean loading/error/success handling
        AsyncStateContent(
            state = orderHistoryState.toAsyncState(),
            onRetry = onRetry,
            loadingContent = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(Res.string.order_history_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            errorContent = { message, retryable ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (retryable) {
                            Button(onClick = onRetry) {
                                Text(stringResource(Res.string.button_retry))
                            }
                        }
                    }
                }
            }
        ) { orders ->
            val sortedOrders = orders.sortedByDescending { it.pickUpDate }

            if (sortedOrders.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                            )
                        }
                        Text(
                            text = stringResource(Res.string.order_history_empty_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(Res.string.order_history_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Order list
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = AdaptiveDefaults.ListItemMinWidth),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = if (sortedOrders.size == 1) stringResource(Res.string.order_history_count_single) else formatString(stringResource(Res.string.order_history_count_plural), sortedOrders.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(sortedOrders) { order ->
                        OrderHistoryCard(
                            order = order,
                            onClick = { onAction(BuyProfileAction.HistoryOrderTapped(order)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderHistoryCard(
    order: Order,
    onClick: () -> Unit
) {
    val pickupDate = formatDate(order.pickUpDate)
    val createdDate = formatDate(order.createdDate)
    val totalPrice = order.articles.sumOf { it.price * it.amountCount }
    val itemCount = order.articles.size
    val daysUntilPickup = getDaysUntilPickup(order.pickUpDate)
    val isUpcoming = daysUntilPickup >= 0
    val canEdit = daysUntilPickup > 3

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUpcoming) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Order ID and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formatString(stringResource(Res.string.format_order_id), order.id.takeLast(8)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatString(stringResource(Res.string.order_history_created_date), createdDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        daysUntilPickup < 0 -> MaterialTheme.colorScheme.surfaceVariant
                        daysUntilPickup == 0L -> MaterialTheme.colorScheme.tertiaryContainer
                        daysUntilPickup <= 3 -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = when {
                            daysUntilPickup < 0 -> stringResource(Res.string.order_status_picked_up)
                            daysUntilPickup == 0L -> stringResource(Res.string.basket_today)
                            daysUntilPickup == 1L -> stringResource(Res.string.basket_tomorrow)
                            daysUntilPickup <= 3 -> stringResource(Res.string.order_status_soon)
                            else -> if (canEdit) stringResource(Res.string.order_status_editable) else stringResource(Res.string.order_status_scheduled)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            daysUntilPickup < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                            daysUntilPickup == 0L -> MaterialTheme.colorScheme.onTertiaryContainer
                            daysUntilPickup <= 3 -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pickup date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = formatString(stringResource(Res.string.order_history_pickup_date), pickupDate),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (daysUntilPickup > 0) {
                    Text(
                        text = if (daysUntilPickup == 1L) stringResource(Res.string.order_history_in_day) else formatString(stringResource(Res.string.order_history_in_days), daysUntilPickup),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Order details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formatString(stringResource(Res.string.format_item_count), itemCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(Res.string.label_total_plain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${totalPrice.formatPrice()} €",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderHistoryMergeDialog(
    onMerge: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.order_history_merge_title)) },
        text = { Text(stringResource(Res.string.order_history_merge_message)) },
        confirmButton = {
            Button(onClick = onMerge) {
                Text(stringResource(Res.string.basket_merge_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(Res.string.basket_draft_discard_continue))
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.button_cancel))
            }
        }
    )
}

/**
 * Helper function to format date
 */
private fun formatDate(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = dateTime.day.toString().padStart(2, '0')
    val month = dateTime.month.number.toString().padStart(2, '0')
    val year = dateTime.year
    return "$day.$month.$year"
}

/**
 * Helper function to format date key for order lookup
 * Format: yyyyMMdd (matches Firebase storage format)
 */
private fun formatDateKey(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = dateTime.day.toString().padStart(2, '0')
    val month = dateTime.month.number.toString().padStart(2, '0')
    val year = dateTime.year
    return "$year$month$day"
}

/**
 * Helper function to get days until pickup (calendar days, not hours)
 */
private fun getDaysUntilPickup(pickupDate: Long): Long {
    val now = Clock.System.now()
    val pickupInstant = Instant.fromEpochMilliseconds(pickupDate)

    val todayDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val pickupLocalDate = pickupInstant.toLocalDateTime(TimeZone.currentSystemDefault()).date

    return (pickupLocalDate.toEpochDays() - todayDate.toEpochDays()).toLong()
}
