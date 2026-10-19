package com.together.newverse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.PriceCause
import com.together.newverse.domain.model.ResolvedPrice
import org.jetbrains.compose.resources.stringResource
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.price_breakdown_base
import newverse.shared.generated.resources.price_cause_handling
import newverse.shared.generated.resources.price_cause_neighborhood_rate
import newverse.shared.generated.resources.price_cause_stall_fee
import newverse.shared.generated.resources.price_cause_transport
import newverse.shared.generated.resources.price_cause_volume_commitment

/** The localized name of a price cause, used when the seller gave no note. */
@Composable
fun PriceCause.displayName(): String = stringResource(
    when (this) {
        PriceCause.STALL_FEE -> Res.string.price_cause_stall_fee
        PriceCause.TRANSPORT -> Res.string.price_cause_transport
        PriceCause.HANDLING -> Res.string.price_cause_handling
        PriceCause.NEIGHBORHOOD_RATE -> Res.string.price_cause_neighborhood_rate
        PriceCause.VOLUME_COMMITMENT -> Res.string.price_cause_volume_commitment
    }
)

/** `2,80 €`, and `+0,25 €` / `−0,15 €` for a signed component. */
fun formatEuro(amount: Double, signed: Boolean = false): String {
    val cents = kotlin.math.round(kotlin.math.abs(amount) * 100).toLong()
    val body = "${cents / 100},${(cents % 100).toString().padStart(2, '0')} €"
    return when {
        !signed -> body
        amount < 0 -> "−$body"
        else -> "+$body"
    }
}

/**
 * Why a price is what it is.
 *
 * The lines are the same data that produced the figure, so the explanation cannot
 * drift away from the total — and they are arranged to sum visibly, because a
 * breakdown whose numbers disagree with the price is worse than none at all. Draws
 * nothing when the market changed nothing.
 */
@Composable
fun PriceBreakdown(
    price: ResolvedPrice,
    modifier: Modifier = Modifier,
) {
    if (!price.isAdjusted) return

    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        BreakdownLine(
            label = stringResource(Res.string.price_breakdown_base),
            value = formatEuro(price.base)
        )
        price.components.forEach { component ->
            BreakdownLine(
                // The seller's own words when they wrote any, the cause's name otherwise.
                label = component.note.ifBlank { component.cause.displayName() },
                value = formatEuro(component.amount, signed = true)
            )
        }
        BreakdownLine(
            label = "",
            value = formatEuro(price.gross),
            emphasised = true
        )
    }
}

@Composable
private fun BreakdownLine(label: String, value: String, emphasised: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasised) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
