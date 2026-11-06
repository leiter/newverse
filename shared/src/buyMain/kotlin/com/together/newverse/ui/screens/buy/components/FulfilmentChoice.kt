package com.together.newverse.ui.screens.buy.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import com.together.newverse.domain.model.DeliveryAddress
import com.together.newverse.domain.model.Fulfilment
import com.together.newverse.ui.screens.buy.ProfileValidation
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.fulfilment_address_required
import newverse.shared.generated.resources.fulfilment_choose
import newverse.shared.generated.resources.fulfilment_city
import newverse.shared.generated.resources.fulfilment_delivery
import newverse.shared.generated.resources.fulfilment_house_number
import newverse.shared.generated.resources.fulfilment_note_hint
import newverse.shared.generated.resources.fulfilment_pickup
import newverse.shared.generated.resources.fulfilment_pickup_only
import newverse.shared.generated.resources.delivery_time_outside_hours
import newverse.shared.generated.resources.fulfilment_delivery_time_label
import newverse.shared.generated.resources.fulfilment_pickup_time_hint
import newverse.shared.generated.resources.fulfilment_pickup_time_label
import newverse.shared.generated.resources.fulfilment_save_time_to_profile
import newverse.shared.generated.resources.pickup_time_outside_hours
import newverse.shared.generated.resources.fulfilment_use_profile_time
import newverse.shared.generated.resources.fulfilment_save_to_profile
import newverse.shared.generated.resources.fulfilment_street
import newverse.shared.generated.resources.fulfilment_use_profile_address
import newverse.shared.generated.resources.fulfilment_zip
import org.jetbrains.compose.resources.stringResource

/**
 * Pickup or delivery, and — depending on which — where to take it or when to expect
 * the buyer.
 *
 * One composable rather than two copies, because it is asked twice about the same
 * order: at the checkout confirmation before it is written, and again in the basket
 * while editing a placed one. The second is not a convenience — a buyer whose profile
 * read as delivery (which is every untouched profile, see [Fulfilment.fromProfile])
 * had no way at all to turn a placed order into a pickup, short of cancelling it.
 *
 * The two halves are deliberately exclusive. A delivery has an address and no
 * collection time, because it arrives when the seller's round reaches it; a pickup has
 * a time and no address. Asking for both would collect one of them for nobody.
 *
 * Decides nothing itself: [deliveryOffered] is the stall's capability and
 * [showAddressRequired] the caller's reading of its own gate, so the one rule about
 * what a market allows stays in `Fulfilment.forMarket` and the one rule about what may
 * be written stays in the ViewModel.
 */
@Composable
internal fun FulfilmentChoice(
    fulfilment: Fulfilment,
    deliveryAddress: DeliveryAddress,
    /** Street and number as the buyer's profile holds them; empty if it holds none. */
    profileAddress: DeliveryAddress,
    /** Whether this stall delivers at all. False turns the choice into a statement. */
    deliveryOffered: Boolean,
    /** Say the address is missing. The caller owns that judgement, not this row. */
    showAddressRequired: Boolean,
    /**
     * The time the buyer would like, `HH:mm`. Blank is an answer: "whenever".
     *
     * When they mean to collect a pickup, and when they would like a delivery to
     * arrive. One field and one question; only the wording differs.
     */
    pickupTime: String,
    /** The buyer's usual time as the profile holds it; empty if it holds none. */
    profilePickupTime: String,
    onFulfilmentChange: (Fulfilment) -> Unit,
    onDeliveryAddressChange: (DeliveryAddress) -> Unit,
    onSaveAddressToProfile: () -> Unit,
    onPickupTimeChange: (String) -> Unit,
    onSavePickupTimeToProfile: () -> Unit,
) {
    val isDelivery = fulfilment == Fulfilment.DELIVERY

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (deliveryOffered) {
            Text(
                text = stringResource(Res.string.fulfilment_choose),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fulfilment.entries.forEach { option ->
                    FilterChip(
                        selected = option == fulfilment,
                        onClick = { onFulfilmentChange(option) },
                        label = {
                            Text(
                                stringResource(
                                    if (option == Fulfilment.DELIVERY) Res.string.fulfilment_delivery
                                    else Res.string.fulfilment_pickup
                                )
                            )
                        }
                    )
                }
            }
        } else {
            // Said rather than offered-and-refused: a buyer whose profile asks for
            // delivery would otherwise find the choice simply missing.
            Text(
                text = stringResource(Res.string.fulfilment_pickup_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isDelivery) {
            DeliveryAddressFields(
                address = deliveryAddress,
                onChange = onDeliveryAddressChange
            )

            // Two ways out of an empty form, whichever the buyer's profile makes
            // possible: take what the profile already holds, or keep what they just
            // typed for next time. Never both at once — once the fields match the
            // profile there is nothing to copy either way.
            val canFillFromProfile = profileAddress.isComplete &&
                (profileAddress.street != deliveryAddress.street ||
                    profileAddress.houseNumber != deliveryAddress.houseNumber)
            val canSaveToProfile = !canFillFromProfile &&
                deliveryAddress.isComplete &&
                (profileAddress.street != deliveryAddress.street ||
                    profileAddress.houseNumber != deliveryAddress.houseNumber)

            if (canFillFromProfile) {
                TextButton(
                    onClick = {
                        // Street and number only: the profile holds no postcode or
                        // town, and the note belongs to this order.
                        onDeliveryAddressChange(
                            deliveryAddress.copy(
                                street = profileAddress.street,
                                houseNumber = profileAddress.houseNumber
                            )
                        )
                    }
                ) {
                    Text(stringResource(Res.string.fulfilment_use_profile_address))
                }
            } else if (canSaveToProfile) {
                TextButton(onClick = onSaveAddressToProfile) {
                    Text(stringResource(Res.string.fulfilment_save_to_profile))
                }
            }

            // Said before the tap, not after it: a disabled button with no reason
            // beside it reads as the app being broken.
            if (showAddressRequired) {
                Text(
                    text = stringResource(Res.string.fulfilment_address_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Asked of a delivery as well as a pickup, underneath the address rather than
        // instead of it. A delivery does arrive when the seller's round reaches it,
        // which is why this is a wish and not an appointment — but the round is
        // planned by someone who is better off knowing one buyer is out until four.
        PickupTimeField(
            pickupTime = pickupTime,
            profilePickupTime = profilePickupTime,
            isDelivery = isDelivery,
            onChange = onPickupTimeChange,
            onSaveToProfile = onSavePickupTimeToProfile
        )
    }
}

/**
 * The time the buyer would like: when they mean to collect, or when they would like
 * the box brought round.
 *
 * Labelled optional and meant it. This is the field that used to live in the profile
 * as a *requirement*: a buyer could not ask a seller for access until they had named
 * an Abholzeit, and no screen in the seller app ever displayed it. Asked here it is
 * worth asking — the seller reads it on the order, and the buyer is answering about a
 * collection they are actually arranging rather than filling in a form about
 * themselves.
 *
 * Opens the same time picker the profile's own field does, so no format can be typed
 * wrong; the error line below is for a value that arrived some other way, and
 * `refuseUnreadablePickupTime` is the matching gate.
 *
 * One offer at a time, like the address above: take the usual time, or keep this one
 * as the usual time. Never both — once they match there is nothing to copy either way.
 */
@Composable
private fun PickupTimeField(
    pickupTime: String,
    profilePickupTime: String,
    /** Only the wording: the field, the range and the gate are the same either way. */
    isDelivery: Boolean,
    onChange: (String) -> Unit,
    onSaveToProfile: () -> Unit,
) {
    // The same reading the checkout gate and the database rules use: blank, or a
    // time the stall is open. The picker offers nothing else, so this only shows for
    // a value that arrived some other way.
    val usable = pickupTime.isBlank() || ProfileValidation.isTimeInBusinessHours(pickupTime)

    TimePickerField(
        value = pickupTime,
        onValueChange = onChange,
        label = stringResource(
            if (isDelivery) Res.string.fulfilment_delivery_time_label
            else Res.string.fulfilment_pickup_time_label
        ),
        hint = stringResource(Res.string.fulfilment_pickup_time_hint),
        leadingIcon = Icons.Default.DateRange,
        isValid = usable,
        errorMessage = if (usable) null else {
            stringResource(
                if (isDelivery) Res.string.delivery_time_outside_hours
                else Res.string.pickup_time_outside_hours
            )
        },
        // Optional means reversible: a buyer who named a time must be able to take
        // it back, both at checkout and while the order is still editable.
        allowEmpty = true
    )

    val canFillFromProfile = profilePickupTime.isNotBlank() &&
        profilePickupTime != pickupTime
    val canSaveToProfile = !canFillFromProfile &&
        pickupTime.isNotBlank() && usable &&
        profilePickupTime != pickupTime

    if (canFillFromProfile) {
        TextButton(onClick = { onChange(profilePickupTime) }) {
            Text(stringResource(Res.string.fulfilment_use_profile_time))
        }
    } else if (canSaveToProfile) {
        TextButton(onClick = onSaveToProfile) {
            Text(stringResource(Res.string.fulfilment_save_time_to_profile))
        }
    }
}

/**
 * The address this order is delivered to.
 *
 * Seeded from the buyer's profile when they switch to delivery, and editable here
 * because the order snapshots it: a one-off delivery somewhere else must not have to
 * go through rewriting the profile, and rewriting the profile must not re-aim orders
 * already placed.
 *
 * Street and number are what [DeliveryAddress.isComplete] requires; postcode and town
 * are optional, since this is one seller serving one area.
 */
@Composable
private fun DeliveryAddressFields(
    address: DeliveryAddress,
    onChange: (DeliveryAddress) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = address.street,
                onValueChange = { onChange(address.copy(street = it)) },
                label = { Text(stringResource(Res.string.fulfilment_street)) },
                singleLine = true,
                modifier = Modifier.weight(2f)
            )
            OutlinedTextField(
                value = address.houseNumber,
                onValueChange = { onChange(address.copy(houseNumber = it)) },
                label = { Text(stringResource(Res.string.fulfilment_house_number)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = address.zipCode,
                onValueChange = { onChange(address.copy(zipCode = it)) },
                label = { Text(stringResource(Res.string.fulfilment_zip)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = address.city,
                onValueChange = { onChange(address.copy(city = it)) },
                label = { Text(stringResource(Res.string.fulfilment_city)) },
                singleLine = true,
                modifier = Modifier.weight(2f)
            )
        }
        OutlinedTextField(
            value = address.note,
            onValueChange = { onChange(address.copy(note = it)) },
            label = { Text(stringResource(Res.string.fulfilment_note_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
