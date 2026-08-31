package com.together.newverse.ui.mainscreen

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.together.newverse.util.rememberKeyboardManager
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.cd_product_search
import newverse.shared.generated.resources.products_search_clear
import newverse.shared.generated.resources.products_search_placeholder
import org.jetbrains.compose.resources.stringResource

/**
 * Search input for the product list.
 *
 * Accessibility: the field carries an explicit [contentDescription] label from a
 * string resource rather than leaving a screen reader to infer one from the
 * placeholder — the placeholder disappears once text is entered, so it is not a
 * reliable label. The leading magnifier is decorative; the trailing clear button
 * is labelled and only present while there is a query.
 */
@Composable
fun ProductSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.cd_product_search)
    val keyboardManager = rememberKeyboardManager()

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.semantics { contentDescription = label },
        placeholder = { Text(stringResource(Res.string.products_search_placeholder)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(Res.string.products_search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardManager.hide() }),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}
