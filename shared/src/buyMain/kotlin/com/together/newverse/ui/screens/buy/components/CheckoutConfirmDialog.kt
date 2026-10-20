package com.together.newverse.ui.screens.buy.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
    onConfirm: () -> Unit,
    onChangeSlot: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                // Named only when there is more than one market to be at: with a
                // single stall the buyer knows where they are collecting and the row
                // is noise.
                if (marketName.isNotEmpty()) {
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

                TextButton(
                    onClick = onChangeSlot,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(Res.string.checkout_confirm_change))
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
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
