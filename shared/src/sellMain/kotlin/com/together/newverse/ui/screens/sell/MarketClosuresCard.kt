package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.MarketClosure
import org.jetbrains.compose.resources.stringResource
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.button_save
import newverse.shared.generated.resources.closures_add
import newverse.shared.generated.resources.closures_all_markets
import newverse.shared.generated.resources.closures_date_hint
import newverse.shared.generated.resources.closures_empty
import newverse.shared.generated.resources.closures_invalid_date
import newverse.shared.generated.resources.closures_market
import newverse.shared.generated.resources.closures_reason_hint
import newverse.shared.generated.resources.closures_remove
import newverse.shared.generated.resources.closures_title

/**
 * The days on which a market does not happen.
 *
 * Entered as `TT.MM.JJJJ` and stored as `yyyyMMdd`, so the stored form carries no
 * timezone and sorts. A closure with no market selected shuts every market — the
 * seller being away — which is the common case and therefore the default.
 */
@Composable
fun MarketClosuresCard(
    closures: List<MarketClosure>,
    markets: List<Market>,
    isSaving: Boolean,
    onSave: (List<MarketClosure>) -> Unit,
) {
    var draft by remember(closures) { mutableStateOf(closures) }
    var dateInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    var marketInput by remember { mutableStateOf("") }
    var marketMenuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val invalidDate = stringResource(Res.string.closures_invalid_date)
    val allMarkets = stringResource(Res.string.closures_all_markets)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Res.string.closures_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )

            if (draft.isEmpty()) {
                Text(
                    text = stringResource(Res.string.closures_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            draft.sortedBy { it.date }.forEach { closure ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatClosureDate(closure.date),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val scope = if (closure.isSellerWide) allMarkets
                        else markets.firstOrNull { it.id == closure.marketId }?.name.orEmpty()
                        val detail = listOf(scope, closure.reason).filter { it.isNotBlank() }
                        if (detail.isNotEmpty()) {
                            Text(
                                text = detail.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { draft = draft - closure }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.closures_remove),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = dateInput,
                onValueChange = { dateInput = it; error = null },
                label = { Text(stringResource(Res.string.closures_date_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = error != null
            )

            OutlinedTextField(
                value = reasonInput,
                onValueChange = { reasonInput = it },
                label = { Text(stringResource(Res.string.closures_reason_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (markets.isNotEmpty()) {
                Column {
                    OutlinedTextField(
                        value = markets.firstOrNull { it.id == marketInput }?.name ?: allMarkets,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(stringResource(Res.string.closures_market)) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { marketMenuOpen = true }
                    )
                    DropdownMenu(
                        expanded = marketMenuOpen,
                        onDismissRequest = { marketMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(allMarkets) },
                            onClick = { marketInput = ""; marketMenuOpen = false }
                        )
                        markets.forEach { market ->
                            DropdownMenuItem(
                                text = { Text(market.name) },
                                onClick = { marketInput = market.id; marketMenuOpen = false }
                            )
                        }
                    }
                }
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TextButton(onClick = {
                val key = parseClosureDate(dateInput)
                if (key == null) {
                    error = invalidDate
                } else {
                    // Replacing rather than appending keeps one entry per day and
                    // scope, so entering the same day twice is a correction.
                    draft = draft.filterNot { it.date == key && it.marketId == marketInput } +
                        MarketClosure(date = key, marketId = marketInput, reason = reasonInput.trim())
                    dateInput = ""
                    reasonInput = ""
                    error = null
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.closures_add))
            }

            Button(
                onClick = { onSave(draft) },
                enabled = !isSaving && draft.toSet() != closures.toSet(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(Res.string.button_save))
            }
        }
    }
}

/**
 * `TT.MM.JJJJ` to the stored `yyyyMMdd`, or null when it is not a real date.
 *
 * Deliberately strict: a half-typed date silently stored as a closure would take a
 * market day away with no way for the seller to see why.
 */
internal fun parseClosureDate(input: String): String? {
    val parts = input.trim().split(".", "/", "-")
    if (parts.size != 3) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31 || year < 2000 || year > 2999) return null

    val daysInMonth = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    }
    if (day > daysInMonth) return null

    return "$year" + month.toString().padStart(2, '0') + day.toString().padStart(2, '0')
}

/** `yyyyMMdd` back to `TT.MM.JJJJ` for display. */
internal fun formatClosureDate(key: String): String =
    if (key.length != 8) key
    else "${key.substring(6, 8)}.${key.substring(4, 6)}.${key.substring(0, 4)}"
