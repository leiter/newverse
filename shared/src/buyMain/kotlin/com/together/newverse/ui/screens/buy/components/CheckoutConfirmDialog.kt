package com.together.newverse.ui.screens.buy.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import com.together.newverse.domain.model.DeliveryAddress
import com.together.newverse.domain.model.Fulfilment
import newverse.shared.generated.resources.fulfilment_address_required
import newverse.shared.generated.resources.fulfilment_choose
import newverse.shared.generated.resources.fulfilment_city
import newverse.shared.generated.resources.fulfilment_delivery
import newverse.shared.generated.resources.fulfilment_house_number
import newverse.shared.generated.resources.fulfilment_note_hint
import newverse.shared.generated.resources.fulfilment_pickup
import newverse.shared.generated.resources.fulfilment_save_to_profile
import newverse.shared.generated.resources.fulfilment_street
import newverse.shared.generated.resources.fulfilment_use_profile_address
import newverse.shared.generated.resources.fulfilment_zip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.Market
import com.together.newverse.ui.components.displayName
import com.together.newverse.util.DeadlineWarningLevel
import com.together.newverse.util.OrderDateUtils
import com.together.newverse.util.formatPrice
import kotlin.time.Clock
import kotlin.time.Instant
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.button_cancel
import newverse.shared.generated.resources.checkout_confirm_change
import newverse.shared.generated.resources.checkout_confirm_deadline
import newverse.shared.generated.resources.checkout_confirm_demo_note
import newverse.shared.generated.resources.checkout_confirm_market
import newverse.shared.generated.resources.checkout_confirm_place
import newverse.shared.generated.resources.checkout_confirm_time_left
import newverse.shared.generated.resources.checkout_confirm_title
import newverse.shared.generated.resources.checkout_confirm_total
import newverse.shared.generated.resources.checkout_confirm_when
import newverse.shared.generated.resources.market_choice_profile_label
import org.jetbrains.compose.resources.stringResource

/**
 * The last look before an order is written: where, when, and until when it can be
 * changed.
 *
 * Confirmation rather than a second picker — the buyer has already chosen a slot, and
 * this is where they see what they chose spelled out. With one market it says the
 * date, the deadline and the time left; with several it names the market too, since
 * that is what decides the prices. The *Ändern* button reopens the slot picker, which
 * is the only place date and market move together, so changing either here goes
 * through the same price-change warning as changing it earlier would.
 *
 * `doc/guides/market-pricing-and-access-design.md` §4 specified this step and noted
 * that `formatTimeUntilDeadline` and `getDeadlineWarningLevel` existed and went unused
 * at exactly this moment. This is where they are used.
 */
@Composable
fun CheckoutConfirmDialog(
    pickupDate: Long,
    market: Market?,
    marketName: String,
    total: Double,
    isDemoMode: Boolean,
    fulfilment: Fulfilment,
    deliveryAddress: DeliveryAddress,
    /** Street and number as the buyer's profile holds them; empty if it holds none. */
    profileAddress: DeliveryAddress,
    orderError: String?,
    onFulfilmentChange: (Fulfilment) -> Unit,
    onDeliveryAddressChange: (DeliveryAddress) -> Unit,
    onSaveAddressToProfile: () -> Unit,
    onConfirm: () -> Unit,
    onChangeSlot: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDelivery = fulfilment == Fulfilment.DELIVERY

    // Delivery with nowhere to deliver to. The seller cannot read `buyer_profile`, so
    // such an order has no destination and no way to recover one — hence the confirm
    // button goes off rather than the refusal arriving after the tap. The ViewModel
    // refuses it at the write too; this is the half that explains itself.
    val addressMissing = isDelivery && !deliveryAddress.isComplete

    val pickup = Instant.fromEpochMilliseconds(pickupDate)

    // The market's own schedule. This currently produces the same instant as the
    // app-wide one for every market, and deliberately so rather than by luck:
    // `calculateEditDeadline` takes only the *lead distance* from the config and
    // subtracts it from the actual pickup date, and `Market.scheduleConfig` preserves
    // that distance by construction. Threaded through anyway, because the equivalence
    // holds only while a market cannot set its own lead time or cut-off hour — the day
    // either becomes configurable, this is already correct.
    val config = OrderDateUtils.scheduleConfigFor(market)

    val deadline = OrderDateUtils.calculateEditDeadline(pickup, config = config)
    val timeLeft = OrderDateUtils.formatTimeUntilDeadline(pickup, config = config)
    val warning = OrderDateUtils.getDeadlineWarningLevel(pickup, config = config)

    val day = market?.pickupDay?.displayName().orEmpty()
    val whenText = listOf(day, OrderDateUtils.formatDisplayDate(pickup))
        .filter { it.isNotBlank() }
        .joinToString(", ")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.checkout_confirm_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Pickup or delivery, for this order. First, because it decides
                // whether the market and date below are even the relevant facts.
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

                if (isDelivery) {
                    DeliveryAddressFields(
                        address = deliveryAddress,
                        onChange = onDeliveryAddressChange
                    )

                    // Two ways out of an empty form, whichever the buyer's profile
                    // makes possible: take what the profile already holds, or keep
                    // what they just typed for next time. Never both at once — once
                    // the fields match the profile there is nothing to copy either way.
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
                                // Street and number only: the profile holds no postcode
                                // or town, and the note belongs to this order.
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

                    // Said before the tap, not after it: a disabled button with no
                    // reason beside it reads as the app being broken.
                    if (addressMissing && orderError == null) {
                        Text(
                            text = stringResource(Res.string.fulfilment_address_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Named only when there is more than one market to be at: with a
                // single stall the buyer knows where they are collecting and the row
                // is noise. Skipped entirely for a delivery — the market a delivery
                // order is dispatched from is the seller's business, not the buyer's.
                if (marketName.isNotEmpty() && !isDelivery) {
                    ConfirmRow(
                        label = stringResource(Res.string.market_choice_profile_label),
                        value = marketName
                    )
                }

                ConfirmRow(
                    label = stringResource(Res.string.checkout_confirm_when),
                    value = whenText
                )

                ConfirmRow(
                    label = stringResource(Res.string.checkout_confirm_total),
                    value = total.formatPrice() + " €"
                )

                Text(
                    text = stringResource(
                        Res.string.checkout_confirm_deadline,
                        OrderDateUtils.formatDisplayDateTime(deadline)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Coloured by how close it is: the same figure reads very differently
                // two days out and forty minutes out.
                Text(
                    text = stringResource(Res.string.checkout_confirm_time_left, timeLeft),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (warning) {
                        DeadlineWarningLevel.URGENT,
                        DeadlineWarningLevel.CRITICAL,
                        DeadlineWarningLevel.EXPIRED -> MaterialTheme.colorScheme.error
                        DeadlineWarningLevel.WARNING -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (isDemoMode) {
                    Text(
                        text = stringResource(Res.string.checkout_confirm_demo_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                orderError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                TextButton(
                    onClick = onChangeSlot,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(Res.string.checkout_confirm_change))
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !addressMissing) {
                Text(stringResource(Res.string.checkout_confirm_place))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.button_cancel))
            }
        }
    )
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
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
