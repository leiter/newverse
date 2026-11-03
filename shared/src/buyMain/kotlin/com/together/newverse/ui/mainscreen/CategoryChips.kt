package com.together.newverse.ui.mainscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.ui.state.ProductFilterState
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The home-screen filter chips.
 *
 * Five ordinary two-state chips over two independent axes (see [ProductFilterState]),
 * not one row of mutually exclusive options:
 *
 *  - **Alle** bypasses everything. It is not a filter of its own, so it lights up
 *    whenever nothing is narrowed — including when the buyer switched the last chip
 *    off by hand.
 *  - **Favoriten** and **Ohne Favoriten** are the two ends of one tri-state axis,
 *    split across two chips on purpose. A single chip cycling through three states
 *    has no affordance for the third: the inverted state looks exactly like the
 *    positive one, so one tap too many hides the buyer's favourites while the chip
 *    still reads "Favoriten". Two chips keep every state one tap in and one tap out,
 *    and the label always says what is happening.
 *  - **Obst** and **Gemüse** are the category axis, exclusive with each other
 *    because an article cannot be both, and independent of the favourite axis:
 *    Favoriten + Obst is the favourite fruit.
 *
 * Tapping the chip that is already on turns it off, so the row can never trap the
 * buyer in a filter whose only way out is a chip scrolled off the end.
 */
@Composable
internal fun CategoryChips(
    filterState: ProductFilterState,
    onToggleFavourite: (Boolean) -> Unit,
    onToggleCategory: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The label is localized; the value matched against the catalogue is the German
    // category name the articles are actually stored with, so an English UI still
    // filters on "Obst".
    val chips = listOf(
        FilterChipSpec(
            key = "all",
            label = stringResource(Res.string.filter_all),
            selected = filterState.isUnfiltered,
            onClick = onReset
        ),
        FilterChipSpec(
            key = "fav",
            label = stringResource(Res.string.filter_favourites),
            selected = filterState.includeFavourite == true,
            onClick = { onToggleFavourite(true) }
        ),
        FilterChipSpec(
            key = "nonfav",
            label = stringResource(Res.string.filter_without_favourites),
            selected = filterState.includeFavourite == false,
            onClick = { onToggleFavourite(false) }
        ),
        FilterChipSpec(
            key = "obst",
            label = stringResource(Res.string.filter_obst),
            selected = filterState.category == ProductCategory.OBST.displayName,
            onClick = { onToggleCategory(ProductCategory.OBST.displayName) }
        ),
        FilterChipSpec(
            key = "gemuese",
            label = stringResource(Res.string.filter_gemuese),
            selected = filterState.category == ProductCategory.GEMUESE.displayName,
            onClick = { onToggleCategory(ProductCategory.GEMUESE.displayName) }
        )
    )

    // FilterChip already exposes the selected flag; give the spoken state a
    // localized wording ("Ausgewählt" / "Nicht ausgewählt") instead of the
    // default "checkbox, not checked".
    //
    // Deliberately no selectableGroup() here, unlike a single-choice row: these are
    // two independent axes and more than one chip can be on at a time, so announcing
    // the row as "1 of 5" would be a lie.
    val selectedState = stringResource(Res.string.a11y_state_selected)
    val unselectedState = stringResource(Res.string.a11y_state_unselected)

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(chips, key = { it.key }) { chip ->
            FilterChip(
                selected = chip.selected,
                onClick = chip.onClick,
                label = { Text(chip.label) },
                modifier = Modifier.semantics {
                    stateDescription = if (chip.selected) selectedState else unselectedState
                },
                leadingIcon = if (chip.selected) {
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

/** One chip, so the row itself stays a single loop over a list. */
private data class FilterChipSpec(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)
