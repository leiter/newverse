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
import com.together.newverse.domain.model.ContactChannel
import com.together.newverse.domain.model.SellerContact
import com.together.newverse.ui.components.displayName
import org.jetbrains.compose.resources.stringResource
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.contact_add
import newverse.shared.generated.resources.contact_channel
import newverse.shared.generated.resources.contact_label_hint
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

        OutlinedTextField(
            value = contact.value,
            onValueChange = { onChange(contact.copy(value = it)) },
            label = { Text(stringResource(Res.string.contact_value)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

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
 * Drop the blank rows an editor leaves behind and store each value in the shape its
 * scheme needs. Called once, when the seller saves.
 */
fun List<SellerContact>.normalizedForSave(): List<SellerContact> =
    filter { it.value.isNotBlank() }
        .map { it.copy(value = it.channel.normalize(it.value), label = it.label.trim()) }
