package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.together.newverse.domain.model.ProductPricing
import com.together.newverse.domain.model.RefillNeed
import com.together.newverse.domain.model.RefillState
import com.together.newverse.domain.model.StockMovementKind
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The storage screen: what needs refilling, the full standing underneath, and the
 * bookings that change either.
 */
@Composable
fun StockScreen(viewModel: StockViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    StockContent(
        state = state,
        onQueryChange = viewModel::setQuery,
        onBook = viewModel::startBooking,
        onQuantityChange = viewModel::setQuantity,
        onNoteChange = viewModel::setNote,
        onFillFromScale = viewModel::fillFromScale,
        onConfirm = viewModel::book,
        onDismiss = viewModel::dismissBooking
    )
}

@Composable
fun StockContent(
    state: StockUiState,
    onQueryChange: (String) -> Unit,
    onBook: (String, StockMovementKind) -> Unit,
    onQuantityChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onFillFromScale: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(Res.string.stock_refill_needed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() }
            )
        }

        when {
            !state.hasWatchedArticles -> item {
                Text(
                    text = stringResource(Res.string.stock_nothing_watched),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.refillNeeds.isEmpty() -> item {
                Text(
                    text = stringResource(Res.string.stock_all_stocked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> items(state.refillNeeds, key = { "need_${it.articleId}" }) { need ->
                RefillCard(need = need, onBook = onBook)
            }
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(Res.string.stock_all_articles),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
        }

        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(Res.string.stock_search)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        items(state.matchingStanding, key = { "all_${it.articleId}" }) { need ->
            StandingRow(need = need, onBook = onBook)
        }
    }

    state.editor?.let { editor ->
        BookingDialog(
            editor = editor,
            state = state,
            onQuantityChange = onQuantityChange,
            onNoteChange = onNoteChange,
            onFillFromScale = onFillFromScale,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun RefillCard(need: RefillNeed, onBook: (String, StockMovementKind) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (need.state) {
                RefillState.EMPTY -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = need.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(need.state.labelRes()),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Text(
                text = if (!need.hasLevel) {
                    stringResource(Res.string.stock_never_counted)
                } else {
                    "${stringResource(Res.string.stock_on_hand)}: " +
                        "${ProductPricing.formatQuantity(need.onHand)} ${need.unit}"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            BookingActions(articleId = need.articleId, onBook = onBook)
        }
    }
}

@Composable
private fun StandingRow(need: RefillNeed, onBook: (String, StockMovementKind) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = need.productName, style = MaterialTheme.typography.bodyLarge)
            Text(
                // 0.0 means "empty" only once something is known; without a
                // movement it means nothing at all.
                text = if (!need.hasLevel) {
                    stringResource(Res.string.stock_never_counted)
                } else {
                    "${ProductPricing.formatQuantity(need.onHand)} ${need.unit}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BookingActions(articleId = need.articleId, onBook = onBook)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingActions(articleId: String, onBook: (String, StockMovementKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { onBook(articleId, StockMovementKind.INTAKE) },
            label = { Text(stringResource(Res.string.stock_intake)) },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
        )
        AssistChip(
            onClick = { onBook(articleId, StockMovementKind.STOCKTAKE) },
            label = { Text(stringResource(Res.string.stock_stocktake)) },
            leadingIcon = { Icon(Icons.Default.Scale, contentDescription = null) }
        )
        AssistChip(
            onClick = { onBook(articleId, StockMovementKind.LOSS) },
            label = { Text(stringResource(Res.string.stock_loss)) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
        )
    }
}

@Composable
private fun BookingDialog(
    editor: StockEditor,
    state: StockUiState,
    onQuantityChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onFillFromScale: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(editor.kind.titleRes())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = editor.productName, style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    value = editor.quantityInput,
                    onValueChange = onQuantityChange,
                    label = { Text(stringResource(Res.string.stock_quantity)) },
                    suffix = { if (editor.unit.isNotEmpty()) Text(editor.unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Only offered where a scale could actually be connected.
                if (state.scaleStatus.isPossibleHere) {
                    TextButton(onClick = onFillFromScale, enabled = state.canWeigh) {
                        Icon(Icons.Default.Scale, contentDescription = null)
                        Text(
                            text = if (state.canWeigh) {
                                stringResource(Res.string.stock_from_scale)
                            } else {
                                stringResource(Res.string.stock_scale_unavailable)
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = editor.note,
                    onValueChange = onNoteChange,
                    label = { Text(stringResource(Res.string.stock_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                state.message?.let { message ->
                    Text(
                        text = stringResource(message.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isSaving) {
                Text(stringResource(Res.string.stock_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.stock_cancel)) }
        }
    )
}

private fun RefillState.labelRes() = when (this) {
    RefillState.EMPTY -> Res.string.stock_state_empty
    RefillState.LOW -> Res.string.stock_state_low
    RefillState.UNCOUNTED -> Res.string.stock_state_uncounted
    RefillState.OK, RefillState.UNWATCHED -> Res.string.stock_on_hand
}

private fun StockMovementKind.titleRes() = when (this) {
    StockMovementKind.INTAKE -> Res.string.stock_record_intake
    StockMovementKind.STOCKTAKE -> Res.string.stock_record_stocktake
    StockMovementKind.LOSS -> Res.string.stock_record_loss
    StockMovementKind.SALE -> Res.string.stock_title
}

private fun StockMessage.labelRes() = when (this) {
    StockMessage.LOAD_FAILED -> Res.string.stock_load_failed
    StockMessage.INVALID_QUANTITY -> Res.string.stock_invalid_quantity
    StockMessage.SAVE_FAILED -> Res.string.stock_save_failed
    StockMessage.SCALE_NOT_READY -> Res.string.stock_scale_unavailable
}
