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
import androidx.compose.material3.Button
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
import com.together.newverse.domain.model.ActionAudience
import com.together.newverse.domain.model.ActionLocation
import com.together.newverse.domain.model.SellerUiAction
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.button_save
import newverse.shared.generated.resources.seller_actions_add
import newverse.shared.generated.resources.seller_actions_audience
import newverse.shared.generated.resources.seller_actions_audience_all
import newverse.shared.generated.resources.seller_actions_audience_demo
import newverse.shared.generated.resources.seller_actions_audience_approved
import newverse.shared.generated.resources.seller_actions_explain
import newverse.shared.generated.resources.seller_actions_full
import newverse.shared.generated.resources.seller_actions_incomplete
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
 * Edits are local until the seller presses save, like [SellerContactsCard]. The first
 * version wrote a row the moment it had both a label and a target, which looked
 * reasonable and was unusable: typing the label one character at a time made the row
 * complete on the first keystroke, so it was handed off and removed from the editor
 * mid-word while the live flow round-tripped. The first button ever published on dev
 * ended up labelled "g". Nothing is written here until save.
 *
 * The address is entered and stored without a scheme, which is why the field says so.
 * See `doc/guides/seller-ui-actions.md`.
 */
@Composable
fun UiActionListEditor(
    actions: List<SellerUiAction>,
    onSave: (SellerUiAction) -> Unit,
    onRemove: (String) -> Unit,
    isSaving: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Re-seeded whenever the stored list changes, so a save elsewhere is picked up
    // rather than silently overwritten by a stale draft — and so a newly minted id
    // replaces the blank one a fresh row carried.
    var rows by remember(actions) { mutableStateOf(actions) }

    val edited = rows.filter { it.isUsable } != actions.filter { it.isUsable }
    val incomplete = rows.any { !it.isUsable && (it.label.isNotBlank() || it.value.isNotBlank()) }

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

        rows.forEachIndexed { index, row ->
            UiActionRow(
                action = row,
                onChange = { updated ->
                    rows = rows.toMutableList().also { it[index] = updated }
                },
                onRemove = {
                    // A row that was never stored just goes; a stored one has to be
                    // deleted, or the live flow would bring it straight back.
                    if (row.id.isNotEmpty() && actions.any { it.id == row.id }) {
                        onRemove(row.id)
                    }
                    rows = rows.filterIndexed { i, _ -> i != index }
                }
            )
            if (index != rows.lastIndex) HorizontalDivider()
        }

        // Capped per slot, matching what the repository refuses, so the two agree.
        val slotsWithRoom = ActionLocation.entries.filter { location ->
            rows.count { it.location == location } < SellerUiAction.MAX_PER_LOCATION
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (slotsWithRoom.isNotEmpty()) {
                TextButton(
                    onClick = { rows = rows + SellerUiAction(location = slotsWithRoom.first()) }
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(stringResource(Res.string.seller_actions_add))
                }
            } else {
                Text(
                    text = stringResource(Res.string.seller_actions_full),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { rows.filter { it.isUsable }.forEach(onSave) },
                enabled = !isSaving && edited && !incomplete
            ) {
                Text(stringResource(Res.string.button_save))
            }
        }

        // Said rather than left to a disabled button: a save that will not fire needs
        // a reason beside it.
        if (incomplete) {
            Text(
                text = stringResource(Res.string.seller_actions_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
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

        // Who sees it. Beside the slot rather than below the address, because the two
        // together are the one question the seller is answering: where, and for whom.
        var audienceMenuOpen by remember { mutableStateOf(false) }
        Column {
            OutlinedTextField(
                value = action.audience.displayName(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.seller_actions_audience)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { audienceMenuOpen = true },
                singleLine = true,
                enabled = false
            )
            DropdownMenu(
                expanded = audienceMenuOpen,
                onDismissRequest = { audienceMenuOpen = false }
            ) {
                ActionAudience.entries.forEach { audience ->
                    DropdownMenuItem(
                        text = { Text(audience.displayName()) },
                        onClick = {
                            onChange(action.copy(audience = audience))
                            audienceMenuOpen = false
                        }
                    )
                }
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

/** What the seller sees in the audience picker. */
@Composable
private fun ActionAudience.displayName(): String = when (this) {
    ActionAudience.EVERYONE -> stringResource(Res.string.seller_actions_audience_all)
    ActionAudience.DEMO_ONLY -> stringResource(Res.string.seller_actions_audience_demo)
    ActionAudience.APPROVED_ONLY -> stringResource(Res.string.seller_actions_audience_approved)
}

/** What the seller sees in the slot picker. */
@Composable
private fun ActionLocation.displayName(): String = when (this) {
    ActionLocation.TOPBAR_HOME -> stringResource(Res.string.seller_actions_location_home)
    ActionLocation.TOPBAR_PROFILE -> stringResource(Res.string.seller_actions_location_profile)
}
