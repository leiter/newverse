package com.together.newverse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.screens.sell.StockFilter
import com.together.newverse.util.formatString
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_stock_filter
import newverse.shared.generated.resources.stock_filter_all
import newverse.shared.generated.resources.stock_filter_refill
import newverse.shared.generated.resources.stock_filter_uncounted
import newverse.shared.generated.resources.stock_filter_watched
import org.jetbrains.compose.resources.stringResource

/**
 * The band of the standing the Lager is showing, as chips.
 *
 * Chips rather than the Sortiment's dropdown: on the Lager this is a tool the seller
 * flicks between while working — everything, then what is watched, then what needs
 * counting — where the Sortiment's availability filter is set once and left. A row of
 * chips says what the choices are without being opened.
 *
 * A [LazyRow] like [CategoryChipRow], because four labelled counts in German do not
 * fit a phone width and squeezing or clipping them is worse than scrolling.
 *
 * Each chip carries its count, taken after the search query and before this filter, so
 * "Nachfüllen 7" means seven in the list as currently searched rather than seven in the
 * catalogue. A chip that would show nothing is still offered rather than hidden: it
 * disappearing as the seller types is a worse surprise than it reading 0, and the 0 is
 * itself an answer — nothing here needs counting.
 *
 * Accessibility: the spoken label names the filter and its count, because "Überwacht"
 * and a bare number do not say what tapping it does. [FilterChip] already conveys the
 * selected state.
 */
@Composable
fun StockFilterChipRow(
    selected: StockFilter,
    counts: Map<StockFilter, Int>,
    onSelect: (StockFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val labelTemplate = stringResource(Res.string.a11y_stock_filter)
    val labels = mapOf(
        StockFilter.ALL to stringResource(Res.string.stock_filter_all),
        StockFilter.WATCHED to stringResource(Res.string.stock_filter_watched),
        StockFilter.NEEDS_REFILL to stringResource(Res.string.stock_filter_refill),
        StockFilter.UNCOUNTED to stringResource(Res.string.stock_filter_uncounted)
    )

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(StockFilter.entries, key = { it.name }) { filter ->
            val label = labels.getValue(filter)
            val count = counts[filter] ?: 0
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text("$label $count") },
                modifier = Modifier.semantics {
                    contentDescription = formatString(labelTemplate, label, count)
                }
            )
        }
    }
}
