package com.together.newverse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.util.formatString
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_category_filter
import newverse.shared.generated.resources.overview_category_all
import org.jetbrains.compose.resources.stringResource

/**
 * The category filter chips, shown with the product search.
 *
 * A [LazyRow] rather than a Row because the chip count follows the catalog: eight
 * categories is normal and will not fit a phone width, so the row has to scroll
 * rather than squeeze or clip.
 *
 * [selected] of null is the "Alle" chip, which is always offered — without it there
 * is no way back to the unfiltered list once a chip is tapped.
 *
 * Accessibility: each chip carries its category in a spoken label, because "Obst" on
 * its own does not say what tapping it does. The selected state is already conveyed
 * by [FilterChip].
 */
@Composable
fun CategoryChipRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) return

    val labelTemplate = stringResource(Res.string.a11y_category_filter)
    val allLabel = stringResource(Res.string.overview_category_all)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "__all__") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(allLabel) },
                modifier = Modifier.semantics {
                    contentDescription = formatString(labelTemplate, allLabel)
                },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == null
                )
            )
        }
        items(categories, key = { it }) { category ->
            FilterChip(
                selected = selected == category,
                // Tapping the selected chip clears it, so the row never traps you in
                // a category with the only way out being the Alle chip further back
                // up a scrolled row.
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(category) },
                modifier = Modifier.semantics {
                    contentDescription = formatString(labelTemplate, category)
                },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == category
                )
            )
        }
    }
}
