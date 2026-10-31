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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.together.newverse.domain.model.ContactChannel
import com.together.newverse.domain.model.LatLng
import com.together.newverse.domain.model.SellerContact
import com.together.newverse.ui.components.displayName
import org.jetbrains.compose.resources.stringResource
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.contact_add
import newverse.shared.generated.resources.contact_channel
import newverse.shared.generated.resources.contact_label_hint
import newverse.shared.generated.resources.contact_latitude
import newverse.shared.generated.resources.contact_location_hint
import newverse.shared.generated.resources.contact_location_invalid
import newverse.shared.generated.resources.contact_longitude
import newverse.shared.generated.resources.contact_remove
import newverse.shared.generated.resources.contact_value

/**
 * Add, edit and remove the ways to reach someone.
 *
 * Used twice: for the business on the profile form, and for a single stall inside
 * [MarketEditDialog]. The seller sets a stall's number while altering that stall,
 * which is why this is a composable rather than a screen.
 *
 * The value is stored as the seller types it and normalized on save by
 * [ContactChannel.normalize], so what they see is what they entered and what is
 * dialled is what works.
 */
@Composable
fun ContactListEditor(
    title: String,
    contacts: List<SellerContact>,
    onContactsChange: (List<SellerContact>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )

        contacts.forEachIndexed { index, contact ->
            ContactRow(
                contact = contact,
                onChange = { updated ->
                    onContactsChange(contacts.toMutableList().also { it[index] = updated })
                },
                onRemove = {
                    onContactsChange(contacts.toMutableList().also { it.removeAt(index) })
                }
            )
            if (index != contacts.lastIndex) HorizontalDivider()
        }

        TextButton(onClick = { onContactsChange(contacts + SellerContact()) }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.contact_add))
        }
    }
}

@Composable
private fun ContactRow(
    contact: SellerContact,
    onChange: (SellerContact) -> Unit,
    onRemove: () -> Unit,
) {
    var channelMenuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = contact.channel.displayName(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.contact_channel)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { channelMenuOpen = true },
                    singleLine = true,
                    enabled = false
                )
                DropdownMenu(
                    expanded = channelMenuOpen,
                    onDismissRequest = { channelMenuOpen = false }
                ) {
                    ContactChannel.entries.forEach { channel ->
                        DropdownMenuItem(
                            text = { Text(channel.displayName()) },
                            onClick = {
                                onChange(contact.copy(channel = channel))
                                channelMenuOpen = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.contact_remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        if (contact.channel == ContactChannel.LOCATION) {
            LocationFields(
                value = contact.value,
                onValueChange = { onChange(contact.copy(value = it)) }
            )
        } else {
            OutlinedTextField(
                value = contact.value,
                onValueChange = { onChange(contact.copy(value = it)) },
                label = { Text(stringResource(Res.string.contact_value)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        OutlinedTextField(
            value = contact.label,
            onValueChange = { onChange(contact.copy(label = it)) },
            label = { Text(stringResource(Res.string.contact_label_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}


/**
 * The two halves of a [ContactChannel.LOCATION] value, as two fields.
 *
 * One field holding "52.5200,13.4050" would be a format to be taught; two labelled
 * ones are a pair of numbers to be copied, which is what the seller is actually doing
 * -- reading them off a map on another screen. They are joined back into the single
 * stored string on every keystroke, so the rest of the app keeps seeing one contact
 * row like any other.
 *
 * Both fields are text, not numbers: a half-typed "52." or a lone "-" has to survive
 * long enough to be finished. The error is shown only once both sides have something
 * in them, so it appears when the pair is wrong rather than while it is incomplete,
 * and the row is simply withheld from buyers until it parses ([SellerContact.isUsable]).
 */
@Composable
private fun LocationFields(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val parts = value.split(',')
    val latitude = parts.getOrNull(0).orEmpty()
    val longitude = parts.getOrNull(1).orEmpty()
    val bothEntered = latitude.isNotBlank() && longitude.isNotBlank()
    val invalid = bothEntered && LatLng.parse(value) == null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = latitude,
            onValueChange = { onValueChange("$it,$longitude") },
            label = { Text(stringResource(Res.string.contact_latitude)) },
            isError = invalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = longitude,
            onValueChange = { onValueChange("$latitude,$it") },
            label = { Text(stringResource(Res.string.contact_longitude)) },
            isError = invalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }

    Text(
        text = stringResource(
            if (invalid) Res.string.contact_location_invalid else Res.string.contact_location_hint
        ),
        style = MaterialTheme.typography.bodySmall,
        color = if (invalid) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Drop the blank rows an editor leaves behind and store each value in the shape its
 * scheme needs. Called once, when the seller saves.
 */
fun List<SellerContact>.normalizedForSave(): List<SellerContact> =
    map { it.copy(value = it.channel.normalize(it.value), label = it.label.trim()) }
        // Filtered *after* normalizing, not before: a row whose value survives the
        // editor but not its own scheme -- a phone of "n/a", half a coordinate --
        // would otherwise be stored as an empty string that no screen can show and
        // the seller cannot see to delete.
        .filter { it.isUsable }
