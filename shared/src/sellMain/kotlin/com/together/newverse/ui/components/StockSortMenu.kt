package com.together.newverse.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.together.newverse.ui.screens.sell.StockSort
import com.together.newverse.util.formatString
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_stock_sort
import newverse.shared.generated.resources.stock_sort_last_counted
import newverse.shared.generated.resources.stock_sort_name
import newverse.shared.generated.resources.stock_sort_on_hand
import newverse.shared.generated.resources.stock_sort_shortfall
import org.jetbrains.compose.resources.stringResource

/**
 * What order the standing is in, as a menu behind one icon.
 *
 * A menu and not a second row of chips: the filter below is flicked between while
 * working and has to show its choices, while the order is picked once for a task --
 * walking the shelves with a scale, or writing a buy list -- and then left. Spending
 * another strip of the sticky header on four more chips would crowd the thing the
 * header is for.
 *
 * The chosen entry is ticked rather than only highlighted, because the icon gives no
 * clue which order is running and the seller may come back to this screen long after
 * choosing.
 *
 * Accessibility: the button announces what the list is currently sorted by, not merely
 * "sort" -- the state is otherwise invisible until the menu is opened.
 */
@Composable
fun StockSortMenu(
    selected: StockSort,
    onSelect: (StockSort) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val labels = mapOf(
        StockSort.NAME to stringResource(Res.string.stock_sort_name),
        StockSort.ON_HAND to stringResource(Res.string.stock_sort_on_hand),
        StockSort.SHORTFALL to stringResource(Res.string.stock_sort_shortfall),
        StockSort.LAST_COUNTED to stringResource(Res.string.stock_sort_last_counted)
    )
    val buttonDescription =
        formatString(stringResource(Res.string.a11y_stock_sort), labels.getValue(selected))

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = buttonDescription }
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StockSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(labels.getValue(sort)) },
                    leadingIcon = {
                        if (sort == selected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    }
                )
            }
        }
    }
}
