package com.together.newverse.domain.model

import com.together.newverse.util.OrderDateUtils
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** One article in one order that the seller cannot fully cover. */
data class ShortfallLine(
    val articleId: String,
    val productName: String,
    val unit: String,
    /** What this order asked for. */
    val ordered: Double,
    /** How much is missing across *all* outstanding orders, not this one's share. */
    val missingInTotal: Double
)

/**
 * An order holding at least one article that is short, and whose buyer the seller
 * may want to talk to.
 *
 * Nothing here is allocated between orders. With 6 kg on the shelf and three buyers
 * wanting 5 kg each, every one of them is listed and the shortfall is reported in
 * total — which buyer goes without, or gets a substitute, or a part-fill, is a
 * decision about customers and not a division sum. See
 * `doc/guides/stock-driven-availability-design.md` § "Telling a buyer their order is short".
 */
data class OrderShortfall(
    val orderId: String,
    /** `yyyyMMdd` of the pickup, as the order's database path uses it. */
    val cycle: String,
    val buyerId: String,
    val buyerName: String,
    val pickUpDate: Long,
    val lines: List<ShortfallLine>
)

/**
 * The outstanding orders affected by [needs], most urgent first.
 *
 * Only [RefillState.SHORT] articles count. `LOW` means the buffer is thin, which is
 * the seller's problem and not worth writing to a customer about; `UNCOUNTED` means
 * nothing is known yet.
 */
fun List<Order>.shortfallsAgainst(
    needs: List<RefillNeed>,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    now: Instant = Clock.System.now()
): List<OrderShortfall> {
    val short = needs
        .filter { it.state == RefillState.SHORT && !it.unitMismatch }
        .associateBy { it.articleId }
    if (short.isEmpty()) return emptyList()

    return filter { it.countsAsCommitted(now, timeZone) }
        .mapNotNull { order ->
            val lines = order.articles.mapNotNull { item ->
                val need = short[item.articleKey()] ?: return@mapNotNull null
                ShortfallLine(
                    articleId = need.articleId,
                    productName = need.productName.ifEmpty { item.productName },
                    unit = need.unit,
                    ordered = item.amountCount,
                    missingInTotal = need.shortfall
                )
            }
            if (lines.isEmpty()) return@mapNotNull null
            OrderShortfall(
                orderId = order.id,
                cycle = OrderDateUtils.formatDateKey(
                    Instant.fromEpochMilliseconds(order.pickUpDate),
                    timeZone
                ),
                buyerId = order.buyerProfile.id,
                buyerName = order.buyerProfile.displayName,
                pickUpDate = order.pickUpDate,
                lines = lines
            )
        }
        // Soonest pickup first: that is the one the seller has least time to sort out.
        .sortedWith(compareBy({ it.pickUpDate }, { it.buyerName.lowercase() }))
}

/**
 * A message to the buyer of [shortfall], for the seller to edit before sending.
 *
 * A draft, never sent by itself. A shortfall is usually still fixable — more stock
 * at the wholesaler at 5am, a substitute, a part-fill — and an automatic *"we cannot
 * deliver your order"* is a message the seller then has to walk back, which costs
 * more trust than the silence would have. §5 and decision D13.
 *
 * German, like the rest of what a buyer reads here, and assembled rather than taken
 * from a string resource because the seller rewrites it anyway: this is a starting
 * point in the shop's own voice, not UI text.
 */
fun draftShortfallMessage(
    shortfall: OrderShortfall,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    val date = Instant.fromEpochMilliseconds(shortfall.pickUpDate).toLocalDateTime(timeZone).date
    val day = "${date.day.toString().padStart(2, '0')}.${date.month.number.toString().padStart(2, '0')}."
    val greeting = shortfall.buyerName.takeIf { it.isNotBlank() }
        ?.let { "Hallo $it," }
        ?: "Hallo,"

    val items = shortfall.lines.joinToString("\n") { line ->
        "• ${line.productName}: ${ProductPricing.formatQuantity(line.ordered)} ${line.unit}"
    }

    return buildString {
        appendLine(greeting)
        appendLine()
        appendLine("leider kann ich Ihre Bestellung für den $day nicht vollständig liefern:")
        appendLine()
        appendLine(items)
        appendLine()
        appendLine("Ich melde mich, sobald ich weiß, was ich Ihnen anbieten kann.")
        appendLine()
        append("Viele Grüße")
    }
}
