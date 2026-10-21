package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.ActionLocation
import com.together.newverse.domain.model.SellerUiAction
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.seller_actions_add
import newverse.shared.generated.resources.seller_actions_explain
import newverse.shared.generated.resources.seller_actions_full
import newverse.shared.generated.resources.seller_actions_label
import newverse.shared.generated.resources.seller_actions_location
import newverse.shared.generated.resources.seller_actions_location_home
import newverse.shared.generated.resources.seller_actions_location_profile
import newverse.shared.generated.resources.seller_actions_remove
import newverse.shared.generated.resources.seller_actions_title
import newverse.shared.generated.resources.seller_actions_value
import org.jetbrains.compose.resources.stringResource

/**
 * Add, edit and remove the buttons this seller publishes into the buy app.
 *
 * Shaped after [ContactListEditor], which solves the same problem — a list of small
 * seller-authored records with a typed kind — but writes through a repository per row
 * rather than replacing a list on the profile, because each action is its own node
 * key and removal is a delete.
 *
 * A row is saved when it has both a label and a target; until then it is a draft held
 * in composition and nothing is written. That keeps a half-typed address out of the
 * database, where the rules would reject it anyway.
 *
 * The address is entered and stored without a scheme, which is why the field says so.
 * See `doc/guides/seller-ui-actions.md`.
 */
@Composable
fun UiActionListEditor(
    actions: List<SellerUiAction>,
    onSave: (SellerUiAction) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Rows the seller has started but not finished. Kept beside the saved ones rather
    // than written early: an action with no label is not a button, and the rules
    // reject it.
    var drafts by remember { mutableStateOf(listOf<SellerUiAction>()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.seller_actions_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(Res.string.seller_actions_explain),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        actions.forEachIndexed { index, action ->
            UiActionRow(
                action = action,
                // Saved rows write on every edit: the row already exists, so there is
                // no half-written state to protect, and the seller gets no save button
                // to forget.
                onChange = { updated -> if (updated.isUsable) onSave(updated) },
                onRemove = { onRemove(action.id) }
            )
            if (index != actions.lastIndex || drafts.isNotEmpty()) HorizontalDivider()
        }

        drafts.forEachIndexed { index, draft ->
            UiActionRow(
                action = draft,
                onChange = { updated ->
                    if (updated.isUsable) {
                        // Complete: hand it over and drop the draft. The live flow
                        // brings it back as a saved row.
                        drafts = drafts.filterIndexed { i, _ -> i != index }
                        onSave(updated)
                    } else {
                        drafts = drafts.toMutableList().also { it[index] = updated }
                    }
                },
                onRemove = { drafts = drafts.filterIndexed { i, _ -> i != index } }
            )
            if (index != drafts.lastIndex) HorizontalDivider()
        }

        // Capped per slot, so the button to add another goes away only when every slot
        // is full — which is also what the repository refuses, so the two agree.
        val slotsWithRoom = ActionLocation.entries.filter { location ->
            actions.count { it.location == location } < SellerUiAction.MAX_PER_LOCATION
        }
        if (slotsWithRoom.isNotEmpty()) {
            TextButton(
                onClick = {
                    drafts = drafts + SellerUiAction(location = slotsWithRoom.first())
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.seller_actions_add))
            }
        } else {
            Text(
                text = stringResource(Res.string.seller_actions_full),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UiActionRow(
    action: SellerUiAction,
    onChange: (SellerUiAction) -> Unit,
    onRemove: () -> Unit,
) {
    var locationMenuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = action.location.displayName(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.seller_actions_location)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { locationMenuOpen = true },
                    singleLine = true,
                    enabled = false
                )
                DropdownMenu(
                    expanded = locationMenuOpen,
                    onDismissRequest = { locationMenuOpen = false }
                ) {
                    ActionLocation.entries.forEach { location ->
                        DropdownMenuItem(
                            text = { Text(location.displayName()) },
                            onClick = {
                                onChange(action.copy(location = location))
                                locationMenuOpen = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.seller_actions_remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        OutlinedTextField(
            value = action.label,
            onValueChange = { onChange(action.copy(label = it.take(SellerUiAction.MAX_LABEL_LENGTH))) },
            label = { Text(stringResource(Res.string.seller_actions_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = action.value,
            onValueChange = { onChange(action.copy(value = it)) },
            label = { Text(stringResource(Res.string.seller_actions_value)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

/** What the seller sees in the slot picker. */
@Composable
private fun ActionLocation.displayName(): String = when (this) {
    ActionLocation.TOPBAR_HOME -> stringResource(Res.string.seller_actions_location_home)
    ActionLocation.TOPBAR_PROFILE -> stringResource(Res.string.seller_actions_location_profile)
}
