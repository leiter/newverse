package com.together.newverse.ui.screens.buy.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Fulfilment
import com.together.newverse.ui.screens.buy.ProfileValidation
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.pickup_contact_delivery_intro
import newverse.shared.generated.resources.pickup_contact_name_hint
import newverse.shared.generated.resources.pickup_contact_name_label
import newverse.shared.generated.resources.pickup_contact_phone_invalid
import newverse.shared.generated.resources.pickup_contact_phone_label
import newverse.shared.generated.resources.pickup_contact_pickup_intro
import newverse.shared.generated.resources.pickup_contact_save_phone
import newverse.shared.generated.resources.pickup_contact_use_profile_phone
import org.jetbrains.compose.resources.stringResource

/**
 * Who is taking receipt of this order, and how to reach them.
 *
 * Both belong to the order rather than to the buyer, which is why they are asked here
 * and not on the profile page:
 *
 *  - **The name** is pre-filled with the buyer's own and routinely wants changing. The
 *    person who places the order is often not the person who turns up — a flatmate, a
 *    partner, somebody sent round on the way home — and until now the only way to tell
 *    the seller was to write it into the order message and hope they read it.
 *  - **The phone number** is optional and the first one that actually reaches the
 *    seller. `BuyerProfile.telephoneNumber` has been collected on the profile page for
 *    as long as there has been one, and no order ever carried it: the seller's detail
 *    screen has a phone row that could not fill. Asked per order it is also
 *    *withdrawable* per order, which a profile field is not.
 *
 * Shown for a delivery as much as a pickup, with only the opening line changing —
 * somebody is at the door either way — unlike [FulfilmentChoice]'s two halves, which
 * are exclusive by construction.
 */
@Composable
internal fun PickupContactFields(
    fulfilment: Fulfilment,
    pickupName: String,
    pickupPhone: String,
    /** The number the buyer's profile holds; empty if it holds none. */
    profilePhone: String,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSavePhoneToProfile: () -> Unit,
) {
    // Characters only, and the length the database rules bound. There is no shape a
    // phone number has to take, and a rule insisting on one refuses real numbers
    // written the way their owners write them.
    val phoneUsable = pickupPhone.isBlank() ||
        (pickupPhone.length <= PHONE_MAX_LENGTH && ProfileValidation.hasValidPhoneChars(pickupPhone))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(
                if (fulfilment == Fulfilment.DELIVERY) {
                    Res.string.pickup_contact_delivery_intro
                } else {
                    Res.string.pickup_contact_pickup_intro
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = pickupName,
            onValueChange = { if (it.length <= NAME_MAX_LENGTH) onNameChange(it) },
            label = { Text(stringResource(Res.string.pickup_contact_name_label)) },
            placeholder = { Text(stringResource(Res.string.pickup_contact_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = pickupPhone,
            onValueChange = { if (it.length <= PHONE_MAX_LENGTH) onPhoneChange(it) },
            label = { Text(stringResource(Res.string.pickup_contact_phone_label)) },
            singleLine = true,
            isError = !phoneUsable,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            supportingText = if (!phoneUsable) {
                { Text(stringResource(Res.string.pickup_contact_phone_invalid)) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )

        // One offer at a time, exactly as the address and the time have: take what the
        // profile holds, or keep what was typed here for next time. Never both — once
        // they match there is nothing to copy either way.
        val canFillFromProfile = profilePhone.isNotBlank() && profilePhone != pickupPhone
        val canSaveToProfile = !canFillFromProfile &&
            pickupPhone.isNotBlank() && phoneUsable && profilePhone != pickupPhone

        if (canFillFromProfile) {
            TextButton(onClick = { onPhoneChange(profilePhone) }) {
                Text(stringResource(Res.string.pickup_contact_use_profile_phone))
            }
        } else if (canSaveToProfile) {
            TextButton(onClick = onSavePhoneToProfile) {
                Text(stringResource(Res.string.pickup_contact_save_phone))
            }
        }
    }
}

/** Both match the bounds in `database.rules.json`. */
private const val NAME_MAX_LENGTH = 99
private const val PHONE_MAX_LENGTH = 30
