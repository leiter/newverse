package com.together.newverse.ui.mainscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.state.ProductFilter
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CategoryChips(
    activeFilter: ProductFilter,
    onFilterSelected: (ProductFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filterOptions = listOf(
        ProductFilter.ALL to stringResource(Res.string.filter_all),
        ProductFilter.FAVOURITES to stringResource(Res.string.filter_favourites),
        ProductFilter.OBST to stringResource(Res.string.filter_obst),
        ProductFilter.GEMUESE to stringResource(Res.string.filter_gemuese)
    )

    // FilterChip already exposes the selected flag; give the spoken state a
    // localized wording ("Ausgewählt" / "Nicht ausgewählt") instead of the
    // default "checkbox, not checked", and group the row so a screen reader
    // announces "1 of 4".
    val selectedState = stringResource(Res.string.a11y_state_selected)
    val unselectedState = stringResource(Res.string.a11y_state_unselected)

    LazyRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(filterOptions) { (filter, label) ->
            val selected = activeFilter == filter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                label = { Text(label) },
                modifier = Modifier.semantics {
                    stateDescription = if (selected) selectedState else unselectedState
                },
                leadingIcon = if (selected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}
