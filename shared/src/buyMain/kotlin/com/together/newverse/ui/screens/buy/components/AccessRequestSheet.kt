package com.together.newverse.ui.screens.buy.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.state.AccessRequestDraft
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.access_request_cancel
import newverse.shared.generated.resources.access_request_name_label
import newverse.shared.generated.resources.access_request_name_required
import newverse.shared.generated.resources.access_request_note_hint
import newverse.shared.generated.resources.access_request_note_label
import newverse.shared.generated.resources.access_request_send
import newverse.shared.generated.resources.access_request_sheet_intro
import newverse.shared.generated.resources.access_request_sheet_title
import org.jetbrains.compose.resources.stringResource

/**
 * The buyer composing their request for access.
 *
 * This is the identity step, and it is the whole of it. What came before sent a buyer
 * who tapped *Zugang anfragen* to a profile form to satisfy a gate — name, pickup
 * time, street, house number — which is a data-collection sequence wearing the costume
 * of a permission check. Most of those fields were for the *seller's* later use at the
 * stall or the van, not for the decision the buyer was trying to get made.
 *
 * So it asks the two things that decision actually needs:
 *
 *  - **A name**, pre-filled from the profile, and the only requirement. A request from
 *    nobody cannot be answered.
 *  - **A line in the buyer's own words**, optional, and the part that makes the request
 *    answerable at all. The seller's list used to show a display name and a raw UUID;
 *    *"war Donnerstag am Stand"* is what turns that into a person they can place.
 *
 * The reason is stated above the fields, because a stranger being asked for their name
 * is owed the reason before they answer rather than after.
 *
 * A dialog, not a bottom sheet, despite the name: it matches every other decision
 * point in this app, and its content column scrolls so the keyboard cannot bury the
 * note field.
 */
@Composable
internal fun AccessRequestSheet(
    draft: AccessRequestDraft,
    isSubmitting: Boolean,
    onNameChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(Res.string.access_request_sheet_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.access_request_sheet_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(Res.string.access_request_name_label)) },
                    singleLine = true,
                    isError = draft.nameError,
                    enabled = !isSubmitting,
                    supportingText = if (draft.nameError) {
                        { Text(stringResource(Res.string.access_request_name_required)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = draft.note,
                    onValueChange = {
                        // The rules cap this at 280 characters and reject the whole
                        // write past it, so the field refuses the 281st keystroke
                        // rather than letting the request fail on send.
                        if (it.length <= NOTE_MAX_LENGTH) onNoteChange(it)
                    },
                    label = { Text(stringResource(Res.string.access_request_note_label)) },
                    placeholder = { Text(stringResource(Res.string.access_request_note_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                // Enabled even with an empty name: the submit is what raises the
                // "please give a name" message, and a dead button with no reason
                // beside it reads as the app being broken.
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text(stringResource(Res.string.access_request_send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(Res.string.access_request_cancel))
            }
        }
    )
}

/** Matches the bound in `database.rules.json`. */
private const val NOTE_MAX_LENGTH = 280
