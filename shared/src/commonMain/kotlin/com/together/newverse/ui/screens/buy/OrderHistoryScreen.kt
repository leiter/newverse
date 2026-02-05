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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Order
import com.together.newverse.util.formatPrice
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

@Composable
fun OrderHistoryScreen(
    appState: com.together.newverse.ui.state.UnifiedAppState,
    onAction: (com.together.newverse.ui.state.UnifiedAppAction) -> Unit = {},
    onBackClick: () -> Unit = {},
    onOrderClick: (orderId: String, orderDate: String) -> Unit = { _, _ -> }
) {
    val orderHistory = appState.screens.orderHistory.items.sortedByDescending { it.pickUpDate }
    val isLoading = appState.screens.orderHistory.isLoading

    // Load order history when screen opens
    androidx.compose.runtime.LaunchedEffect(Unit) {
        println("📋 OrderHistoryScreen: Triggering loadOrderHistory")
        onAction(com.together.newverse.ui.state.UnifiedProfileAction.LoadOrderHistory)
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

        when {
            isLoading -> {
                // Loading state
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
                            text = "Bestellungen werden geladen...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            orderHistory.isEmpty() -> {
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
                            text = "Keine Bestellungen",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Sie haben noch keine Bestellungen aufgegeben",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                // Order list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "${orderHistory.size} ${if (orderHistory.size == 1) "Bestellung" else "Bestellungen"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(orderHistory) { order ->
                        OrderHistoryCard(
                            order = order,
                            onClick = {
                                val dateKey = formatDateKey(order.pickUpDate)
                                onOrderClick(order.id, dateKey)
                            }
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

    // TODO: Re-enable click to navigate to order details screen
    Card(
        modifier = Modifier
            .fillMaxWidth(),
            // .clickable(onClick = onClick),
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
                        text = "Bestellung #${order.id.takeLast(8)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Erstellt: $createdDate",
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
                            daysUntilPickup < 0 -> "Abgeholt"
                            daysUntilPickup == 0L -> "Heute"
                            daysUntilPickup == 1L -> "Morgen"
                            daysUntilPickup <= 3 -> "Bald"
                            else -> if (canEdit) "Bearbeitbar" else "Geplant"
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
                    text = "Abholung: $pickupDate",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (daysUntilPickup > 0) {
                    Text(
                        text = "(in $daysUntilPickup ${if (daysUntilPickup == 1L) "Tag" else "Tagen"})",
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
                        text = "$itemCount ${if (itemCount == 1) "Artikel" else "Artikel"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Gesamt",
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
