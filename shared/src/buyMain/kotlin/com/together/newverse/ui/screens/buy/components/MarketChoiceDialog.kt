package com.together.newverse.ui.screens.buy.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Market
import com.together.newverse.ui.components.displayName
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.button_cancel
import newverse.shared.generated.resources.market_choice_confirm
import newverse.shared.generated.resources.market_choice_demo_note
import newverse.shared.generated.resources.market_choice_message
import newverse.shared.generated.resources.market_choice_row_a11y
import newverse.shared.generated.resources.market_choice_title
import org.jetbrains.compose.resources.stringResource

/**
 * Ask the buyer where they collect.
 *
 * One question, asked once during onboarding and reachable afterwards from the
 * profile. It decides which prices the buyer sees, what their access request asks
 * for, and so which market the seller approves them at — see
 * `doc/guides/market-pricing-and-access-design.md` §2a.
 *
 * Single-select rather than the seller's multi-select [MarketScopePicker] shape: a
 * buyer collects somewhere, not at a set of places, and "all" is not an answer they
 * can give. There is deliberately no "decide later" button beyond dismissing — the
 * question returns next launch rather than being permanently waived.
 *
 * Only shown when there is a real choice; a single-market seller never reaches it.
 */
@Composable
fun MarketChoiceDialog(
    markets: List<Market>,
    currentMarketId: String,
    isDemoMode: Boolean,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (markets.isEmpty()) return

    var selectedId by remember(currentMarketId) {
        mutableStateOf(currentMarketId.ifEmpty { markets.first().id })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.market_choice_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(Res.string.market_choice_message),
                    style = MaterialTheme.typography.bodyMedium
                )

                // Said before the choice, not after: a demo buyer's prices are the real
                // ones of the market they pick, and they should know that is what they
                // are picking.
                if (isDemoMode) {
                    Text(
                        text = stringResource(Res.string.market_choice_demo_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    markets.forEach { market ->
                        MarketRow(
                            market = market,
                            selected = market.id == selectedId,
                            onSelect = { selectedId = market.id }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(selectedId) }) {
                Text(stringResource(Res.string.market_choice_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.button_cancel))
            }
        }
    )
}

/**
 * One market: its name, the day it runs and its hours.
 *
 * The day and hours are what make the choice answerable — a buyer picks a market by
 * when they can get there, not by its name. Merged into one semantics node so a
 * screen reader announces the row as a single selectable option rather than three
 * loose texts and a radio button.
 */
@Composable
private fun MarketRow(
    market: Market,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val day = market.pickupDay?.displayName().orEmpty()
    val hours = listOf(market.begin, market.end)
        .filter { it.isNotBlank() }
        .joinToString(" – ")
    val where = listOf(market.street, market.houseNumber)
        .filter { it.isNotBlank() }
        .joinToString(" ")

    val detail = listOf(day, hours, where).filter { it.isNotBlank() }.joinToString(" · ")
    val spoken = stringResource(Res.string.market_choice_row_a11y, market.name, detail)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
            Text(text = market.name, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
