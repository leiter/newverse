package com.together.newverse.ui.screens.buy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Order
import com.together.newverse.util.OrderDateUtils
import com.together.newverse.util.formatPrice
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.basket_checkout_proceed
import newverse.shared.generated.resources.basket_checkout_processing
import newverse.shared.generated.resources.basket_empty_description
import newverse.shared.generated.resources.basket_empty_title
import newverse.shared.generated.resources.button_remove
import newverse.shared.generated.resources.label_total
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Basket Screen - Stateful composable with ViewModel
 */
@Composable
fun BasketScreen(
    viewModel: BasketViewModel = koinViewModel(),
    orderId: String? = null,
    orderDate: String? = null,
    currentArticles: List<Article> = emptyList()
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
        currentArticles = currentArticles,
        onAction = viewModel::onAction
    )
}

/**
 * Basket Content - Stateless composable
 *
 * @param state The screen state
 * @param currentArticles The list of currently loaded articles with current prices
 * @param onAction Callback for user actions
 */
@Composable
fun BasketContent(
    state: BasketScreenState,
    currentArticles: List<Article>,
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

        // Show cancel success message
        if (state.cancelSuccess) {
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
                        text = "✓ Bestellung erfolgreich storniert!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show reorder success message
        if (state.reorderSuccess) {
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
                        text = "✓ Bestellung kopiert mit aktualisierten Preisen!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bitte überprüfen und bestätigen Sie die Bestellung.",
                        style = MaterialTheme.typography.bodySmall,
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

        // Show reorder date picker dialog
        if (state.showReorderDatePicker) {
            ReorderDatePickerDialog(
                availableDates = state.availablePickupDates,
                isReordering = state.isReordering,
                onDateSelected = { date ->
                    onAction(BasketAction.ReorderWithNewDate(date, currentArticles))
                },
                onDismiss = {
                    onAction(BasketAction.HideReorderDatePicker)
                }
            )
        }

        // Show merge dialog
        if (state.showMergeDialog && state.existingOrderForMerge != null) {
            OrderMergeDialog(
                existingOrder = state.existingOrderForMerge,
                conflicts = state.mergeConflicts,
                isMerging = state.isMerging,
                onResolveConflict = { productId, resolution ->
                    onAction(BasketAction.ResolveMergeConflict(productId, resolution))
                },
                onConfirm = { onAction(BasketAction.ConfirmMerge) },
                onDismiss = { onAction(BasketAction.HideMergeDialog) }
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
//                        onQuantityChange = { newQty ->
//                            onAction(BasketAction.UpdateQuantity(item.productId, newQty))
//                        }
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
                    enabled = state.hasChanges && state.items.isNotEmpty() && !state.isCheckingOut && !state.isCancelling
                ) {
                    if (state.isCheckingOut) {
                        Text(stringResource(Res.string.basket_checkout_processing))
                    } else if (state.hasChanges) {
                        Text("Bestellung aktualisieren")
                    } else {
                        Text("Keine Änderungen")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Show cancel button
                OutlinedButton(
                    onClick = { onAction(BasketAction.CancelOrder) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCheckingOut && !state.isCancelling && !state.isReordering,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (state.isCancelling) {
                        Text("Storniere...")
                    } else {
                        Text("Bestellung stornieren")
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

                // Show reorder button only when pickup date is in the past
                val currentTime = Clock.System.now().toEpochMilliseconds()
                val isPickupDateInPast = state.pickupDate?.let { it < currentTime } == true
                if (isPickupDateInPast) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { onAction(BasketAction.ShowReorderDatePicker) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isReordering && state.items.isNotEmpty()
                    ) {
                        if (state.isReordering) {
                            Text("Erstelle neue Bestellung...")
                        } else {
                            Text("Neu bestellen mit anderem Datum")
                        }
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
//    onQuantityChange: (Double) -> Unit = {}
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

/**
 * Reorder Date Picker Dialog
 * Shows available pickup dates for creating a new order from an existing one
 */
@Composable
fun ReorderDatePickerDialog(
    availableDates: List<Long>,
    isReordering: Boolean,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isReordering) onDismiss() },
        title = {
            Text(
                text = "Neues Abholdatum wählen",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Wählen Sie ein neues Datum für Ihre Bestellung. Die Preise werden automatisch aktualisiert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isReordering) {
                    // Show loading state
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Preise werden aktualisiert...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (availableDates.isEmpty()) {
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
                            ReorderDateOption(
                                date = date,
                                onSelected = { onDateSelected(date) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Empty - selection happens via clicking date cards
        },
        dismissButton = {
            if (!isReordering) {
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        }
    )
}

/**
 * Individual date option card for reorder picker
 */
@Composable
private fun ReorderDateOption(
    date: Long,
    onSelected: () -> Unit
) {
    val instant = Instant.fromEpochMilliseconds(date)
    val formatted = OrderDateUtils.formatDisplayDate(instant)
    val deadline = OrderDateUtils.calculateEditDeadline(instant)
    val deadlineFormatted = OrderDateUtils.formatDisplayDateTime(deadline)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = onSelected
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Donnerstag, $formatted",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Bestellbar bis: $deadlineFormatted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

// ===== Merge Dialog Components =====

/**
 * Dialog for merging basket items with an existing order
 */
@Composable
fun OrderMergeDialog(
    existingOrder: Order,
    conflicts: List<MergeConflict>,
    isMerging: Boolean,
    onResolveConflict: (productId: String, resolution: MergeResolution) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isMerging) onDismiss() },
        title = {
            Text(
                text = "Bestellung zusammenführen",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Existing order summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Für dieses Datum existiert bereits eine Bestellung:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${existingOrder.articles.size} Artikel, ${existingOrder.articles.sumOf { it.price * it.amountCount }.formatPrice()} €",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (isMerging) {
                    // Show loading state
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bestellungen werden zusammengeführt...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (conflicts.isEmpty()) {
                    // No conflicts - auto-merge message
                    Text(
                        text = "Keine Konflikte gefunden. Neue Artikel werden zur bestehenden Bestellung hinzugefügt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    // Show conflicts
                    Text(
                        text = "Folgende Artikel haben unterschiedliche Mengen:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(conflicts) { conflict ->
                            MergeConflictItem(
                                conflict = conflict,
                                onResolve = { resolution ->
                                    onResolveConflict(conflict.productId, resolution)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isMerging && conflicts.all { it.resolution != MergeResolution.UNDECIDED }
            ) {
                Text("Zusammenführen")
            }
        },
        dismissButton = {
            if (!isMerging) {
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        }
    )
}

/**
 * Individual conflict item with resolution options
 */
@Composable
private fun MergeConflictItem(
    conflict: MergeConflict,
    onResolve: (MergeResolution) -> Unit
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Product name
            Text(
                text = conflict.productName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Resolution options
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Keep existing option
                ResolutionOption(
                    label = "Behalten: ${conflict.existingQuantity.formatPrice()} ${conflict.unit}",
                    selected = conflict.resolution == MergeResolution.KEEP_EXISTING,
                    onClick = { onResolve(MergeResolution.KEEP_EXISTING) }
                )

                // Use new option
                ResolutionOption(
                    label = "Neu: ${conflict.newQuantity.formatPrice()} ${conflict.unit}",
                    selected = conflict.resolution == MergeResolution.USE_NEW,
                    onClick = { onResolve(MergeResolution.USE_NEW) }
                )

                // Add option
                ResolutionOption(
                    label = "Addieren: ${(conflict.existingQuantity + conflict.newQuantity).formatPrice()} ${conflict.unit}",
                    selected = conflict.resolution == MergeResolution.ADD,
                    onClick = { onResolve(MergeResolution.ADD) }
                )
            }
        }
    }
}

/**
 * Single resolution option button
 */
@Composable
private fun ResolutionOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Ausgewählt",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
