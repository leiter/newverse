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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.ActionAudience
import com.together.newverse.domain.model.ActionKind
import com.together.newverse.domain.model.ActionLocation
import com.together.newverse.domain.model.ActionPersistence
import com.together.newverse.domain.model.SellerUiAction
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.button_save
import newverse.shared.generated.resources.seller_actions_add
import newverse.shared.generated.resources.seller_actions_audience
import newverse.shared.generated.resources.seller_actions_audience_all
import newverse.shared.generated.resources.seller_actions_audience_demo
import newverse.shared.generated.resources.seller_actions_announcement
import newverse.shared.generated.resources.seller_actions_announcement_explain
import newverse.shared.generated.resources.seller_actions_audience_approved
import newverse.shared.generated.resources.seller_actions_explain
import newverse.shared.generated.resources.seller_actions_full
import newverse.shared.generated.resources.seller_actions_incomplete
import newverse.shared.generated.resources.seller_actions_label
import newverse.shared.generated.resources.seller_actions_location
import newverse.shared.generated.resources.seller_actions_location_home
import newverse.shared.generated.resources.seller_actions_location_home_title
import newverse.shared.generated.resources.seller_actions_location_profile
import newverse.shared.generated.resources.seller_actions_location_home_input
import newverse.shared.generated.resources.seller_actions_location_profile_input
import newverse.shared.generated.resources.seller_actions_prompt
import newverse.shared.generated.resources.seller_actions_prompt_explain
import newverse.shared.generated.resources.seller_actions_remove
import newverse.shared.generated.resources.seller_actions_active
import newverse.shared.generated.resources.seller_actions_persistence
import newverse.shared.generated.resources.seller_actions_persistence_hide_on_action
import newverse.shared.generated.resources.seller_actions_persistence_until_cancelled
import newverse.shared.generated.resources.seller_actions_save_failed
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
    /** Why the last publish was refused, if it was. See `SellerProfileViewModel.uiActionError`. */
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    // Re-seeded whenever the stored list changes, so a save elsewhere is picked up
    // rather than silently overwritten by a stale draft — and so a newly minted id
    // replaces the blank one a fresh row carried.
    var rows by remember(actions) { mutableStateOf(actions) }

    // Compared and saved on *completeness*, not usability: a row the seller has just
    // switched off is complete, is an edit, and must be saved — which is the whole
    // point of there being a switch.
    val edited = rows.filter { it.isComplete } != actions.filter { it.isComplete }
    val incomplete =
        rows.any { !it.isComplete && (it.label.isNotBlank() || it.value.isNotBlank()) }

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
        // Per slot and not one number: the home title holds exactly one, because a
        // title cannot collapse into an overflow menu the way buttons can.
        val slotsWithRoom = ActionLocation.entries.filter { location ->
            rows.count { it.location == location } < location.maxActions
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (slotsWithRoom.isNotEmpty()) {
                TextButton(
                    onClick = {
                        val slot = slotsWithRoom.first()
                        rows = rows + SellerUiAction(location = slot, kind = slot.kind)
                    }
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
                // Shaped the way it will be stored before being handed over, so the
                // announcement rows carry the derived label the rules require.
                onClick = { rows.filter { it.isComplete }.forEach { onSave(it.readyToStore()) } },
                enabled = !isSaving && edited && !incomplete
            ) {
                Text(stringResource(Res.string.button_save))
            }
        }

        // A refusal from the database, said out loud. The common one is a ruleset
        // that does not yet know a field this app writes, which rejects the whole
        // row — and used to look exactly like success.
        errorMessage?.let { message ->
            Text(
                text = stringResource(Res.string.seller_actions_save_failed, message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
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
    val activeDescription = stringResource(Res.string.seller_actions_active)

    val isAnnouncement = action.kind == ActionKind.ANNOUNCE
    val isPrompt = action.kind == ActionKind.QUICK_MESSAGE
    // Both of these are the seller's own prose rather than an address, so they get the
    // room and the wording a sentence needs — and neither asks for a label.
    val isProse = isAnnouncement || isPrompt
    val valueLabel = when {
        isAnnouncement -> Res.string.seller_actions_announcement
        isPrompt -> Res.string.seller_actions_prompt
        else -> Res.string.seller_actions_value
    }

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
                                // The slot decides the kind, so switching slot
                                // re-kinds the row. Otherwise an announcement moved
                                // to a button slot would keep ANNOUNCE and be
                                // refused by the rules on save, with nothing on
                                // screen to say why.
                                onChange(
                                    action.copy(location = location, kind = location.kind)
                                )
                                locationMenuOpen = false
                            }
                        )
                    }
                }
            }

            // Off, not gone. A seller parking the wishbox over winter should not have
            // to retype the question in spring, and a buyer with the app open sees it
            // go either way — the buy app watches this node.
            Switch(
                checked = action.isActive,
                onCheckedChange = { onChange(action.copy(isActive = it)) },
                modifier = Modifier.semantics {
                    contentDescription = activeDescription
                }
            )

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

        // What makes it go away for one buyer. Not offered for an announcement, which
        // has no action to hide on — see ActionKind.hasAction — so the only honest
        // answer there is the default, and asking would invite the wrong one.
        if (action.kind.hasAction) {
            var persistenceMenuOpen by remember { mutableStateOf(false) }
            Column {
                OutlinedTextField(
                    value = action.persistence.displayName(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.seller_actions_persistence)) },
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { persistenceMenuOpen = true },
                    singleLine = true,
                    enabled = false
                )
                DropdownMenu(
                    expanded = persistenceMenuOpen,
                    onDismissRequest = { persistenceMenuOpen = false }
                ) {
                    ActionPersistence.entries.forEach { persistence ->
                        DropdownMenuItem(
                            text = { Text(persistence.displayName()) },
                            onClick = {
                                onChange(action.copy(persistence = persistence))
                                persistenceMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        // An announcement and a message box have no button to label, so the seller is
        // not asked for one.
        // The rules still require a non-empty `label`, so it is derived from the
        // opening words of the text: enough to recognise the row by in a list, and
        // one field rather than two for a market trader to fill in.
        if (!isProse) {
            OutlinedTextField(
                value = action.label,
                onValueChange = {
                    onChange(action.copy(label = it.take(SellerUiAction.MAX_LABEL_LENGTH)))
                },
                label = { Text(stringResource(Res.string.seller_actions_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        OutlinedTextField(
            value = action.value,
            onValueChange = { onChange(action.copy(value = it)) },
            label = { Text(stringResource(valueLabel)) },
            modifier = Modifier.fillMaxWidth(),
            // An announcement is a sentence, so it gets room to be read while being
            // typed. An address is one line by nature.
            singleLine = !isProse,
            minLines = if (isProse) 2 else 1
        )

        if (isProse) {
            Text(
                text = stringResource(
                    if (isPrompt) Res.string.seller_actions_prompt_explain
                    else Res.string.seller_actions_announcement_explain
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** What the seller sees in the picker for what makes a row go away. */
@Composable
private fun ActionPersistence.displayName(): String = when (this) {
    ActionPersistence.UNTIL_CANCELLED ->
        stringResource(Res.string.seller_actions_persistence_until_cancelled)
    ActionPersistence.HIDE_ON_ACTION ->
        stringResource(Res.string.seller_actions_persistence_hide_on_action)
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
    ActionLocation.TOPBAR_HOME_TITLE ->
        stringResource(Res.string.seller_actions_location_home_title)
    ActionLocation.TOPBAR_HOME_INPUT ->
        stringResource(Res.string.seller_actions_location_home_input)
    ActionLocation.TOPBAR_PROFILE_INPUT ->
        stringResource(Res.string.seller_actions_location_profile_input)
}
