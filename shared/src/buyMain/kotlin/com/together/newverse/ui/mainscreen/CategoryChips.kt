package com.together.newverse.ui.mainscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Four chips over two independent axes (see [ProductFilterState]), not one row of
 * mutually exclusive options:
 *
 *  - **Alle** bypasses everything. It is not a filter of its own, so it lights up
 *    whenever nothing is narrowed — including when the buyer switched the last chip
 *    off by hand.
 *  - **Favoriten** is one chip for the whole tri-state favourite axis, cycling
 *    bypassed -> favourites -> everything but favourites -> bypassed. It can be one
 *    chip only because it shows which of the three it is in: the positive state is
 *    filled and led by a check, and the inverted state is filled and led by a cancel
 *    icon *and* relabelled "Ohne Favoriten". That last part is the whole safety
 *    argument. A tri-state chip that signalled the inverted state by fill alone
 *    would look exactly like the positive one, so one tap too many would hide the
 *    buyer's favourites while the chip still read "Favoriten" — which is why this
 *    row used to carry two separate chips instead. The icon and the label together
 *    say what is happening, so the third state is now legible rather than hidden,
 *    and the axis costs one chip instead of two.
 *  - **Obst** and **Gemüse** are the category axis, exclusive with each other
 *    because an article cannot be both, and independent of the favourite axis:
 *    Favoriten + Obst is the favourite fruit.
 *
 * No state is more than two taps from being off, and the category chips turn
 * themselves off when tapped while on, so the row can never trap the buyer in a
 * filter whose only way out is a chip scrolled off the end.
 */
@Composable
internal fun CategoryChips(
    filterState: ProductFilterState,
    onCycleFavourite: () -> Unit,
    onToggleCategory: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The label is localized; the value matched against the catalogue is the German
    // category name the articles are actually stored with, so an English UI still
    // filters on "Obst".
    val favouriteExcluded = filterState.includeFavourite == false
    val chips = listOf(
        FilterChipSpec(
            key = "all",
            label = stringResource(Res.string.filter_all),
            selected = filterState.isUnfiltered,
            onClick = onReset
        ),
        FilterChipSpec(
            key = "fav",
            // The label is part of how the inverted state is told apart from the
            // positive one, not decoration: both are filled.
            label = stringResource(
                if (favouriteExcluded) Res.string.filter_without_favourites
                else Res.string.filter_favourites
            ),
            selected = filterState.includeFavourite != null,
            // Cancel, not a check: this chip is on, but it is subtracting.
            icon = if (favouriteExcluded) Icons.Default.Close else Icons.Default.Check,
            onClick = onCycleFavourite
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
    // default "checkbox, not checked". The favourite chip needs nothing extra for
    // its third state: it is selected either way and its own label says which end.
    //
    // Deliberately no selectableGroup() here, unlike a single-choice row: these are
    // two independent axes and more than one chip can be on at a time, so announcing
    // the row as "1 of 4" would be a lie.
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
                    { Icon(chip.icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
    val onClick: () -> Unit,
    /** Shown only while [selected]. A check unless the chip is subtracting. */
    val icon: ImageVector = Icons.Default.Check
)
