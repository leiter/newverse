package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.together.newverse.domain.model.Money
import com.together.newverse.domain.model.Sale
import com.together.newverse.domain.model.SellerArticle
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Full-screen dialog for a sale at the market stall. Calls [onBooked] once the
 * sale is in the books and [onDismiss] when closed without booking.
 */
@Composable
fun WalkInSaleDialog(
    onBooked: (Sale) -> Unit,
    onDismiss: () -> Unit,
    viewModel: WalkInSaleViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.booked) {
        state.booked?.let { sale ->
            viewModel.bookingShown()
            onBooked(sale)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            WalkInSaleContent(
                state = state,
                onQueryChange = viewModel::setQuery,
                onAdd = viewModel::add,
                onQuantityChange = viewModel::setQuantity,
                onPriceChange = viewModel::setPrice,
                onRemove = viewModel::remove,
                onBook = viewModel::book,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun WalkInSaleContent(
    state: WalkInUiState,
    onQueryChange: (String) -> Unit,
    onAdd: (SellerArticle) -> Unit,
    onQuantityChange: (Int, String) -> Unit,
    onPriceChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onBook: () -> Unit,
    onDismiss: () -> Unit
) {
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.walkin_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.walkin_close))
            }
        }

        // Selected articles with quantity and charged price
        state.lines.forEachIndexed { index, line ->
            Text(text = line.article.article.productName, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.quantityInput,
                    onValueChange = { onQuantityChange(index, it) },
                    label = { Text(stringResource(Res.string.walkin_quantity)) },
                    suffix = { Text(line.article.article.unit) },
                    singleLine = true,
                    keyboardOptions = decimal,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = line.priceInput,
                    onValueChange = { onPriceChange(index, it) },
                    label = { Text(stringResource(Res.string.walkin_price)) },
                    suffix = { Text("€") },
                    singleLine = true,
                    keyboardOptions = decimal,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.walkin_remove, line.article.article.productName)
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.walkin_total, Money.formatCents(state.totalCents)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
            )
            Button(onClick = onBook, enabled = !state.isSaving && state.lines.isNotEmpty()) {
                if (state.isSaving) CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(Res.string.walkin_book))
            }
        }

        state.message?.let { message ->
            Text(
                text = stringResource(
                    when (message) {
                        WalkInMessage.NOTHING_SELECTED -> Res.string.walkin_msg_nothing
                        WalkInMessage.INVALID_INPUT -> Res.string.walkin_msg_invalid
                        WalkInMessage.SAVE_FAILED -> Res.string.walkin_msg_save_failed
                        WalkInMessage.CATALOG_FAILED -> Res.string.walkin_msg_catalog_failed
                    }
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        HorizontalDivider()

        // Catalog search
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(Res.string.walkin_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.lines.isEmpty()) {
            Text(stringResource(Res.string.walkin_hint), style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.results, key = { it.id }) { article ->
                ListItem(
                    headlineContent = { Text(article.article.productName) },
                    supportingContent = {
                        Text("${Money.formatCents(Money.toCents(article.article.price))} € / ${article.article.unit}")
                    },
                    modifier = Modifier.clickable { onAdd(article) }
                )
            }
        }
    }
}
