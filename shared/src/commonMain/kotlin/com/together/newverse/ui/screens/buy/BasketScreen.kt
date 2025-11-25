package com.together.newverse.ui.screens.buy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.together.newverse.util.OrderDateUtils
import com.together.newverse.util.formatPrice
import org.koin.compose.viewmodel.koinViewModel
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Basket Screen - Stateful composable with ViewModel
 */
@Composable
fun BasketScreen(
    viewModel: BasketViewModel = koinViewModel(),
    orderId: String? = null,
    orderDate: String? = null
) {
    val state by viewModel.state.collectAsState()

    // Load order if provided and not already loaded
    androidx.compose.runtime.LaunchedEffect(orderId, orderDate) {
        if (orderId != null && orderDate != null && state.orderId != orderId) {
            println("🛒 BasketScreen: Loading order - orderId=$orderId, date=$orderDate")
            viewModel.onAction(BasketAction.LoadOrder(orderId, orderDate))
        }
    }

    BasketContent(
        state = state,
        onAction = viewModel::onAction
    )
}

/**
 * Basket Content - Stateless composable
 *
 * @param state The screen state
 * @param onAction Callback for user actions
 */
@Composable
fun BasketContent(
    state: BasketScreenState,
    onAction: (BasketAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
//        Text(
//            text = stringResource(Res.string.basket_title),
//            style = MaterialTheme.typography.headlineMedium,
//            color = MaterialTheme.colorScheme.primary
//        )
//        Spacer(modifier = Modifier.height(16.dp))

        // Show order information if viewing an order
        if (state.orderId != null && state.pickupDate != null) {
            OrderInfoCard(
                orderId = state.orderId,
                pickupDate = state.pickupDate,
                createdDate = state.createdDate ?: 0L,
                canEdit = state.canEdit,
                hasChanges = state.hasChanges
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show success message
        if (state.orderSuccess) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (state.orderId != null) "✓ Bestellung erfolgreich aktualisiert!" else "✓ Bestellung erfolgreich aufgegeben!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show error message
        state.orderError?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✗ $error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show pickup date selector for draft orders (no orderId = new order)
        if (state.orderId == null && state.items.isNotEmpty()) {
            PickupDateSelector(
                selectedDate = state.selectedPickupDate,
                onShowPicker = { onAction(BasketAction.ShowDatePicker) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show date picker dialog
        if (state.showDatePicker) {
            DatePickerDialog(
                availableDates = state.availablePickupDates,
                selectedDate = state.selectedPickupDate,
                onDateSelected = { date ->
                    onAction(BasketAction.SelectPickupDate(date))
                },
                onDismiss = { onAction(BasketAction.HideDatePicker) }
            )
        }

        if (state.items.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.basket_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.basket_empty_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items) { item ->
                    BasketItemCard(
                        productName = item.productName,
                        price = item.price,
                        unit = item.unit,
                        quantity = item.amountCount,
                        canEdit = state.canEdit,
                        onRemove = { onAction(BasketAction.RemoveItem(item.productId)) },
                        onQuantityChange = { newQty ->
                            onAction(BasketAction.UpdateQuantity(item.productId, newQty))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(16.dp))

        // Total price section with improved formatting
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
                    text = stringResource(Res.string.label_total),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${state.total.formatPrice()} €",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show appropriate button based on mode
        if (state.orderId != null) {
            // Viewing an existing order
            if (state.canEdit) {
                // Show update button - disabled if no changes
                Button(
                    onClick = { onAction(BasketAction.UpdateOrder) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.hasChanges && state.items.isNotEmpty() && !state.isCheckingOut
                ) {
                    if (state.isCheckingOut) {
                        Text(stringResource(Res.string.basket_checkout_processing))
                    } else if (state.hasChanges) {
                        Text("Bestellung aktualisieren")
                    } else {
                        Text("Keine Änderungen")
                    }
                }
            } else {
                // Cannot edit anymore
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Bearbeitung nicht mehr möglich",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "(weniger als 3 Tage bis Abholung)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        } else {
            // New order - show checkout button
            Button(
                onClick = { onAction(BasketAction.Checkout) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.items.isNotEmpty() && !state.isCheckingOut
            ) {
                Text(if (state.isCheckingOut) stringResource(Res.string.basket_checkout_processing) else stringResource(Res.string.basket_checkout_proceed))
            }
        }
    }
}

@Composable
private fun BasketItemCard(
    productName: String,
    price: Double,
    unit: String,
    quantity: Double,
    canEdit: Boolean = true,
    onRemove: () -> Unit = {},
    onQuantityChange: (Double) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = productName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${price.formatPrice()} €/$unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "× ${quantity.formatPrice()}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${(price * quantity).formatPrice()} €",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Only show remove button if order is editable
            if (canEdit) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(Res.string.button_remove))
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderInfoCard(
    orderId: String,
    pickupDate: Long,
    createdDate: Long,
    canEdit: Boolean,
    hasChanges: Boolean
) {
    // Format dates
    val pickupDateFormatted = formatDate(pickupDate)
    val createdDateFormatted = formatDate(createdDate)
    val daysUntilPickup = getDaysUntilPickup(pickupDate)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasChanges) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hasChanges) "Bestellung (geändert)" else "Bestelldetails",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (hasChanges) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            // Pickup Date - Most prominent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Abholung:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = pickupDateFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when {
                            daysUntilPickup == 0L -> "Heute"
                            daysUntilPickup == 1L -> "Morgen"
                            daysUntilPickup > 0 -> "in $daysUntilPickup Tagen"
                            else -> "Vergangen"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Order ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Bestellnr:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = orderId.takeLast(8),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Created Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Erstellt:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = createdDateFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Edit deadline warning
            if (canEdit) {
                Spacer(modifier = Modifier.height(8.dp))
                val editDeadlineDate = formatDate(pickupDate - (3 * 24 * 60 * 60 * 1000))
                Text(
                    text = "Bearbeitbar bis: $editDeadlineDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Show message when there are unsaved changes
            if (hasChanges) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "⚠ Sie haben ungespeicherte Änderungen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(8.dp)
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
    val day = dateTime.dayOfMonth.toString().padStart(2, '0')
    val month = dateTime.monthNumber.toString().padStart(2, '0')
    val year = dateTime.year
    return "$day.$month.$year"
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

// ===== Date Picker Components =====

/**
 * Pickup Date Selector Card
 * Shows selected date or prompts user to select one
 */
@Composable
fun PickupDateSelector(
    selectedDate: Long?,
    onShowPicker: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selectedDate != null) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        onClick = onShowPicker
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Abholdatum",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedDate != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (selectedDate != null) {
                    val instant = Instant.fromEpochMilliseconds(selectedDate)
                    val formatted = OrderDateUtils.formatDisplayDate(instant)
                    val deadline = OrderDateUtils.calculateEditDeadline(instant)
                    val deadlineFormatted = OrderDateUtils.formatDisplayDateTime(deadline)

                    Text(
                        text = "Donnerstag, $formatted",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Bestellbar bis: $deadlineFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = "Datum auswählen",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tippen Sie hier um ein Abholdatum zu wählen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Icon(
                imageVector = if (selectedDate != null) Icons.Default.Edit else Icons.Default.DateRange,
                contentDescription = "Datum wählen",
                tint = if (selectedDate != null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

/**
 * Date Picker Dialog
 * Shows available pickup dates with deadlines
 */
@Composable
fun DatePickerDialog(
    availableDates: List<Long>,
    selectedDate: Long?,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Abholdatum wählen",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            if (availableDates.isEmpty()) {
                // No dates available
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Derzeit sind keine Abholtermine verfügbar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                // Show available dates
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableDates) { date ->
                        DateOption(
                            date = date,
                            isSelected = date == selectedDate,
                            onSelected = { onDateSelected(date) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            // Empty - selection happens via clicking date cards
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

/**
 * Individual date option card in the picker
 */
@Composable
private fun DateOption(
    date: Long,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    val instant = Instant.fromEpochMilliseconds(date)
    val formatted = OrderDateUtils.formatDisplayDate(instant)
    val deadline = OrderDateUtils.calculateEditDeadline(instant)
    val deadlineFormatted = OrderDateUtils.formatDisplayDateTime(deadline)
    val timeRemaining = OrderDateUtils.formatTimeUntilDeadline(instant)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null,
        onClick = onSelected
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Donnerstag, $formatted",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Ausgewählt",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Bestellbar bis: $deadlineFormatted",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }
            )

            Text(
                text = "Verbleibende Zeit: $timeRemaining",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }
            )
        }
    }
}


