package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Market
import org.jetbrains.compose.resources.stringResource
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.eligibility_all

/**
 * Choose which markets something applies to — a buyer's eligibility, or what an
 * invitation grants.
 *
 * **Nothing ticked means every market**, which is why "all" is a row of its own
 * rather than an inferred state: a seller who unticks the last market is saying
 * "everywhere", not "nowhere", and an empty list that silently meant "nowhere"
 * would lock a buyer out of ordering entirely.
 */
@Composable
fun MarketScopePicker(
    title: String,
    markets: List<Market>,
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (markets.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )

        ScopeRow(
            label = stringResource(Res.string.eligibility_all),
            checked = selected.isEmpty(),
            onCheckedChange = { everywhere -> if (everywhere) onSelectedChange(emptyList()) }
        )

        markets.forEach { market ->
            ScopeRow(
                label = market.name,
                checked = market.id in selected,
                onCheckedChange = { include ->
                    val next = if (include) selected + market.id else selected - market.id
                    onSelectedChange(next.distinct())
                }
            )
        }
    }
}

@Composable
private fun ScopeRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
