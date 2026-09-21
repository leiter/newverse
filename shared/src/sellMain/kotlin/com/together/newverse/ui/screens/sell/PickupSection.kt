package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Money
import com.together.newverse.util.OrderDateUtils
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * The pickup card in the seller's order detail: confirm what was handed over,
 * mark that nobody came, or cancel a booking.
 */
@Composable
fun PickupSection(
    state: PickupUiState,
    onStartConfirm: () -> Unit,
    onQuantityChange: (Int, String) -> Unit,
    onMissing: (Int) -> Unit,
    onBook: () -> Unit,
    onDismissEditor: () -> Unit,
    onMarkNotPickedUp: () -> Unit,
    onUndoNotPickedUp: () -> Unit,
    onCancelBooking: () -> Unit
) {
    if (state.status == PickupStatus.Unavailable || state.status == PickupStatus.Loading) return

    var confirmCancel by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.pickup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )

            when (val status = state.status) {
                PickupStatus.Open -> {
                    Text(stringResource(Res.string.pickup_open_hint), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStartConfirm, enabled = !state.isSaving) {
                            Text(stringResource(Res.string.pickup_confirm))
                        }
                        OutlinedButton(onClick = onMarkNotPickedUp, enabled = !state.isSaving) {
                            Text(stringResource(Res.string.pickup_mark_not_picked_up))
                        }
                    }
                }

                is PickupStatus.Booked -> {
                    Text(
                        text = stringResource(
                            Res.string.pickup_booked,
                            OrderDateUtils.formatDisplayDateTime(Instant.fromEpochMilliseconds(status.sale.confirmedAt)),
                            Money.formatCents(status.sale.grossCents)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = { confirmCancel = true }, enabled = !state.isSaving) {
                        Text(stringResource(Res.string.pickup_cancel_booking))
                    }
                }

                PickupStatus.NotPickedUp -> {
                    Text(stringResource(Res.string.pickup_not_picked_up_info), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onUndoNotPickedUp) {
                        Text(stringResource(Res.string.pickup_undo))
                    }
                }

                PickupStatus.Unknown -> Text(
                    stringResource(Res.string.pickup_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )

                PickupStatus.Loading, PickupStatus.Unavailable -> Unit
            }

            if (state.isSaving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }

    if (state.isEditing) {
        PickupEditorDialog(
            lines = state.lines,
            isSaving = state.isSaving,
            onQuantityChange = onQuantityChange,
            onMissing = onMissing,
            onBook = onBook,
            onDismiss = onDismissEditor
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(Res.string.pickup_cancel_title)) },
            text = { Text(stringResource(Res.string.pickup_cancel_text)) },
            confirmButton = {
                Button(onClick = { confirmCancel = false; onCancelBooking() }) {
                    Text(stringResource(Res.string.pickup_cancel_booking))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) {
                    Text(stringResource(Res.string.button_cancel))
                }
            }
        )
    }
}

@Composable
private fun PickupEditorDialog(
    lines: List<PickupLine>,
    isSaving: Boolean,
    onQuantityChange: (Int, String) -> Unit,
    onMissing: (Int) -> Unit,
    onBook: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.pickup_editor_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(Res.string.pickup_editor_hint), style = MaterialTheme.typography.bodySmall)
                lines.forEachIndexed { index, line ->
                    Column {
                        Text(line.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = line.input,
                                onValueChange = { onQuantityChange(index, it) },
                                singleLine = true,
                                suffix = { Text(line.unit) },
                                supportingText = {
                                    Text(stringResource(Res.string.pickup_ordered, formatAmount(line.orderedQuantity), line.unit))
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { onMissing(index) }) {
                                Text(stringResource(Res.string.pickup_missing))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onBook, enabled = !isSaving) {
                Text(stringResource(Res.string.pickup_book))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.button_cancel))
            }
        }
    )
}

private fun formatAmount(quantity: Double): String =
    if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString()
    else quantity.toString().replace('.', ',')
