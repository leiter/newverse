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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Order
import com.together.newverse.ui.state.BasketScreenState
import com.together.newverse.ui.state.MergeConflict
import com.together.newverse.ui.state.MergeResolution
import com.together.newverse.ui.state.UnifiedBasketScreenAction
import com.together.newverse.util.OrderDateUtils
import com.together.newverse.util.formatPrice
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.together.newverse.util.formatString

/**
 * Basket Screen - Now receives state and callbacks from parent
 */
@Composable
fun BasketScreen(
    state: BasketScreenState,
    currentArticles: List<Article>,
    onAction: (UnifiedBasketScreenAction) -> Unit,
    onNavigateToOrders: () -> Unit = {},
    orderId: String? = null,
    orderDate: String? = null
) {
    // Load order if provided and not already loaded
    LaunchedEffect(orderId, orderDate) {
        if (orderId != null && orderDate != null && state.orderId != orderId) {
            println("🛒 BasketScreen: Loading order - orderId=$orderId, date=$orderDate")
            onAction(UnifiedBasketScreenAction.LoadOrder(orderId, orderDate))
        }
    }

    BasketContent(
        state = state,
        currentArticles = currentArticles,
        onAction = onAction,
        onNavigateToOrders = onNavigateToOrders
    )
}

/**
 * Basket Content - Stateless composable
 *
 * @param state The screen state
 * @param currentArticles The list of currently loaded articles with current prices
 * @param onAction Callback for user actions
 * @param onNavigateToOrders Callback to navigate to order history
 */
@Composable
fun BasketContent(
    state: BasketScreenState,
    currentArticles: List<Article>,
    onAction: (UnifiedBasketScreenAction) -> Unit,
    onNavigateToOrders: () -> Unit = {}
) {
    // Show dialogs outside the LazyColumn
    if (state.showDatePicker) {
        DatePickerDialog(
            availableDates = state.availablePickupDates,
            selectedDate = state.selectedPickupDate,
            onDateSelected = { date ->
                onAction(UnifiedBasketScreenAction.SelectPickupDate(date))
            },
            onDismiss = { onAction(UnifiedBasketScreenAction.HideDatePicker) }
        )
    }

    if (state.showReorderDatePicker) {
        ReorderDatePickerDialog(
            availableDates = state.availablePickupDates,
            isReordering = state.isReordering,
            onDateSelected = { date ->
                onAction(UnifiedBasketScreenAction.ReorderWithNewDate(date, currentArticles))
            },
            onDismiss = {
                onAction(UnifiedBasketScreenAction.HideReorderDatePicker)
            }
        )
    }

    if (state.showMergeDialog && state.existingOrderForMerge != null) {
        OrderMergeDialog(
            existingOrder = state.existingOrderForMerge,
            conflicts = state.mergeConflicts,
            isMerging = state.isMerging,
            onResolveConflict = { productId, resolution ->
                onAction(UnifiedBasketScreenAction.ResolveMergeConflict(productId, resolution))
            },
            onConfirm = { onAction(UnifiedBasketScreenAction.ConfirmMerge) },
            onDismiss = { onAction(UnifiedBasketScreenAction.HideMergeDialog) }
        )
    }

    // Single scrollable LazyColumn for all content
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        // Order information card (for existing orders)
        if (state.orderId != null && state.pickupDate != null) {
            item {
                OrderInfoCard(
                    orderId = state.orderId,
                    pickupDate = state.pickupDate,
                    createdDate = state.createdDate ?: 0L,
                    canEdit = state.canEdit,
                    hasChanges = state.hasChanges
                )
            }
        }

        // Success message
        if (state.orderSuccess) {
            item {
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
                            text = if (state.orderId != null) stringResource(Res.string.basket_order_updated) else stringResource(Res.string.basket_order_placed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // Cancel success message
        if (state.cancelSuccess) {
            item {
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
                            text = stringResource(Res.string.basket_order_cancelled),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // Reorder success message
        if (state.reorderSuccess) {
            item {
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
                            text = stringResource(Res.string.basket_order_copied),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.basket_review_order),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // Error message
        state.orderError?.let { error ->
            item {
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
            }
        }

        // Pickup date selector (for new orders)
        if (state.orderId == null && state.items.isNotEmpty()) {
            item {
                PickupDateSelector(
                    selectedDate = state.selectedPickupDate,
                    onShowPicker = { onAction(UnifiedBasketScreenAction.ShowDatePicker) }
                )
            }
        }

        // Empty basket or items list
        if (state.items.isEmpty()) {
            item {
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
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onNavigateToOrders
                        ) {
                            Text(stringResource(Res.string.action_orders))
                        }
                    }
                }
            }
        } else {
            // Basket items
            items(state.items) { item ->
                BasketItemCard(
                    productName = item.productName,
                    price = item.price,
                    unit = item.unit,
                    quantity = item.amountCount,
                    canEdit = state.canEdit,
                    onRemove = { onAction(UnifiedBasketScreenAction.RemoveItem(item.productId)) }
                )
            }

            // Divider
            item {
                HorizontalDivider()
            }

            // Total section
            item {
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
            }

            // Action buttons
            item {
                BasketActionButtons(
                    state = state,
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun BasketActionButtons(
    state: BasketScreenState,
    onAction: (UnifiedBasketScreenAction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.orderId != null) {
            // Viewing an existing order
            if (state.canEdit) {
                // Show update button - disabled if no changes
                Button(
                    onClick = { onAction(UnifiedBasketScreenAction.UpdateOrder) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.hasChanges && state.items.isNotEmpty() && !state.isCheckingOut && !state.isCancelling
                ) {
                    if (state.isCheckingOut) {
                        Text(stringResource(Res.string.basket_checkout_processing))
                    } else if (state.hasChanges) {
                        Text(stringResource(Res.string.basket_update_order))
                    } else {
                        Text(stringResource(Res.string.basket_no_changes))
                    }
                }

                // Show cancel button
                OutlinedButton(
                    onClick = { onAction(UnifiedBasketScreenAction.CancelOrder) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCheckingOut && !state.isCancelling && !state.isReordering,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (state.isCancelling) {
                        Text(stringResource(Res.string.basket_cancelling))
                    } else {
                        Text(stringResource(Res.string.basket_cancel_order))
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
                            text = stringResource(Res.string.basket_edit_disabled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(Res.string.basket_edit_deadline_reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Show reorder button only when pickup date is in the past
                val currentTime = Clock.System.now().toEpochMilliseconds()
                val isPickupDateInPast = state.pickupDate?.let { it < currentTime } == true
                if (isPickupDateInPast) {
                    OutlinedButton(
                        onClick = { onAction(UnifiedBasketScreenAction.ShowReorderDatePicker) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isReordering && state.items.isNotEmpty()
                    ) {
                        if (state.isReordering) {
                            Text(stringResource(Res.string.basket_creating_order))
                        } else {
                            Text(stringResource(Res.string.basket_reorder))
                        }
                    }
                }
            }
        } else {
            // New order - show checkout button
            Button(
                onClick = { onAction(UnifiedBasketScreenAction.Checkout) },
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
                    text = if (hasChanges) stringResource(Res.string.basket_order_modified) else stringResource(Res.string.basket_order_details),
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
                    text = stringResource(Res.string.basket_pickup),
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
                            daysUntilPickup == 0L -> stringResource(Res.string.basket_today)
                            daysUntilPickup == 1L -> stringResource(Res.string.basket_tomorrow)
                            daysUntilPickup > 0 -> formatString(stringResource(Res.string.basket_in_days), daysUntilPickup.toInt())
                            else -> stringResource(Res.string.basket_past)
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
                    text = stringResource(Res.string.basket_order_number),
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
                    text = stringResource(Res.string.basket_created),
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
                val editDeadline = OrderDateUtils.calculateEditDeadline(Instant.fromEpochMilliseconds(pickupDate))
                val editDeadlineDate = formatDate(editDeadline.toEpochMilliseconds())
                Text(
                    text = formatString(stringResource(Res.string.basket_editable_until), editDeadlineDate),
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
                        text = stringResource(Res.string.basket_unsaved_changes),
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
    val day = dateTime.day.toString().padStart(2, '0')
    val month = dateTime.month.number.toString().padStart(2, '0')
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
                    text = stringResource(Res.string.basket_pickup_date),
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
                        text = "${stringResource(Res.string.day_thursday)}, $formatted",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatString(stringResource(Res.string.basket_orderable_until), deadlineFormatted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.basket_select_date),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(Res.string.basket_select_date_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Icon(
                imageVector = if (selectedDate != null) Icons.Default.Edit else Icons.Default.DateRange,
                contentDescription = stringResource(Res.string.basket_choose_date),
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
                text = stringResource(Res.string.basket_choose_pickup_date),
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
                        text = stringResource(Res.string.basket_no_dates_available),
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
                Text(stringResource(Res.string.button_cancel))
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
                    text = "${stringResource(Res.string.day_thursday)}, $formatted",
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
                        contentDescription = stringResource(Res.string.basket_selected),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatString(stringResource(Res.string.basket_orderable_until), deadlineFormatted),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }
            )

            Text(
                text = formatString(stringResource(Res.string.basket_time_remaining), timeRemaining),
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
                text = stringResource(Res.string.basket_choose_new_date),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.basket_reorder_info),
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
                            text = stringResource(Res.string.basket_updating_prices),
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
                            text = stringResource(Res.string.basket_no_dates_available),
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
                    Text(stringResource(Res.string.button_cancel))
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
                text = "${stringResource(Res.string.day_thursday)}, $formatted",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatString(stringResource(Res.string.basket_orderable_until), deadlineFormatted),
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
                text = stringResource(Res.string.basket_merge_title),
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
                            text = stringResource(Res.string.basket_merge_existing),
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
                            text = stringResource(Res.string.basket_merging),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (conflicts.isEmpty()) {
                    // No conflicts - auto-merge message
                    Text(
                        text = stringResource(Res.string.basket_merge_no_conflicts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    // Show conflicts
                    Text(
                        text = stringResource(Res.string.basket_merge_conflicts),
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
                Text(stringResource(Res.string.basket_merge_confirm))
            }
        },
        dismissButton = {
            if (!isMerging) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.button_cancel))
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
                    label = formatString(stringResource(Res.string.basket_conflict_keep), conflict.existingQuantity.formatPrice(), conflict.unit),
                    selected = conflict.resolution == MergeResolution.KEEP_EXISTING,
                    onClick = { onResolve(MergeResolution.KEEP_EXISTING) }
                )

                // Use new option
                ResolutionOption(
                    label = formatString(stringResource(Res.string.basket_conflict_new), conflict.newQuantity.formatPrice(), conflict.unit),
                    selected = conflict.resolution == MergeResolution.USE_NEW,
                    onClick = { onResolve(MergeResolution.USE_NEW) }
                )

                // Add option
                ResolutionOption(
                    label = formatString(stringResource(Res.string.basket_conflict_add), (conflict.existingQuantity + conflict.newQuantity).formatPrice(), conflict.unit),
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
                    contentDescription = stringResource(Res.string.basket_selected),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
