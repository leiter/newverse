package com.together.newverse.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.together.newverse.util.rememberKeyboardManager
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.cd_product_search
import newverse.shared.generated.resources.products_search_clear
import newverse.shared.generated.resources.products_search_placeholder
import org.jetbrains.compose.resources.stringResource

/**
 * The search input that replaces the title in the seller top bar.
 *
 * A [TextField] with transparent containers and no indicator, rather than the buyer's
 * [androidx.compose.material3.OutlinedTextField]: inside a top bar an outlined box
 * reads as a second surface stacked on the bar, and the bar is already the container.
 *
 * It takes focus and raises the keyboard on appearing, since it only exists because
 * the seller just tapped search — making them tap again would be a wasted step.
 *
 * Accessibility: an explicit label from a string resource, because the placeholder
 * disappears as soon as there is a query and cannot be relied on as the name.
 */
@Composable
fun SellerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(Res.string.cd_product_search)
    val keyboardManager = rememberKeyboardManager()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .semantics { contentDescription = label },
        placeholder = { Text(stringResource(Res.string.products_search_placeholder)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(Res.string.products_search_clear)
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardManager.hide() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
