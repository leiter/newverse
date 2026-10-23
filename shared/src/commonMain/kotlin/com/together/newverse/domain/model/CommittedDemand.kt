package com.together.newverse.domain.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * How much of one article buyers have ordered and not yet collected.
 *
 * [units] holds every unit the contributing order lines used, because an order line
 * carries the unit as it was when the item was ordered and the article's unit can
 * have changed since. More than one entry means the sum cannot be trusted — see
 * `doc/guides/stock-driven-availability-design.md` § "Two things the arithmetic refuses".
 */
data class ArticleDemand(
    val articleId: String,
    /** Sum of [OrderedProduct.amountCount] over the contributing lines. */
    val quantity: Double,
    /** The units those lines were ordered in; normally exactly one. */
    val units: Set<String>,
    /** How many orders contributed, for telling the seller who is affected. */
    val orderCount: Int
) {
    /** The single unit everything was ordered in, or null when they disagree. */
    val unit: String? get() = units.singleOrNull()
}

/**
 * Whether this order's items are goods the seller still owes a buyer.
 *
 * - [OrderOrigin.isReal] only: a [OrderOrigin.DEMO] order is phantom demand, placed
 *   by a buyer the seller has not approved and never fulfilled.
 *   [OrderOrigin.TRANSITIONED] does count — it is a real order that merely started
 *   life under `demo_orders/`.
 * - [OrderStatus.PLACED] and [OrderStatus.LOCKED] only. A [OrderStatus.DRAFT] has not
 *   been ordered; [OrderStatus.CANCELLED] and [OrderStatus.NOT_PICKED_UP] never will
 *   be.
 * - [OrderStatus.COMPLETED] is excluded deliberately, and not because it is in the
 *   past: handing an order over books a [StockMovementKind.SALE] movement, which has
 *   already taken the goods out of `onHand`. Counting a fulfilled order as demand as
 *   well would subtract it twice.
 * - Its pickup day must not have passed, whatever the status says. Status alone is not
 *   enough, because nothing moves an order to COMPLETED on its own: only the *buy* app
 *   calls [Order.transitionStatusIfNeeded], and only for an order the buyer opens, so
 *   an order nobody reopens stays PLACED for ever. Without this, a years-old order goes
 *   on being counted as goods the seller still owes — which is exactly what production
 *   did, where 48 orders with no stored status read as PLACED and inflated the refill
 *   list by 21,435 units.
 */
fun Order.countsAsCommitted(
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Boolean =
    origin.isReal &&
        (status == OrderStatus.PLACED || status == OrderStatus.LOCKED) &&
        !pickupDayHasPassed(now, timeZone)

/**
 * Whether this order's pickup day is behind us.
 *
 * Compared as whole local days, not as instants: [Order.pickUpDate] is the pickup day at
 * 00:00 local, so an instant comparison would call today's order past from 00:01 onwards
 * and drop the market the seller is packing for this morning.
 *
 * An order with no pickup date at all reads as passed. It cannot be packed for any
 * market, and the status filter has already excluded the drafts, where an unset date
 * belongs.
 */
private fun Order.pickupDayHasPassed(now: Instant, timeZone: TimeZone): Boolean =
    Instant.fromEpochMilliseconds(pickUpDate).toLocalDateTime(timeZone).date <
        now.toLocalDateTime(timeZone).date

/**
 * What buyers have ordered per article, keyed by the article id the stock ledger uses.
 *
 * @param pickupDates when given, only orders collected on one of these dates count —
 *   `Order.pickUpDate` values, as [PickupSlot.date] holds them. Null counts every
 *   order that still has to be fulfilled, which is what a buying decision wants; a
 *   date set is what packing one market's goods wants.
 * @param now and @param timeZone decide whether an order's pickup day has passed; see
 *   [countsAsCommitted].
 */
fun List<Order>.committedDemand(
    pickupDates: Set<Long>? = null,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Map<String, ArticleDemand> {
    val quantities = mutableMapOf<String, Double>()
    val units = mutableMapOf<String, MutableSet<String>>()
    val orders = mutableMapOf<String, MutableSet<String>>()

    for (order in this) {
        if (!order.countsAsCommitted(now, timeZone)) continue
        if (pickupDates != null && order.pickUpDate !in pickupDates) continue
        for (item in order.articles) {
            val key = item.articleKey()
            // A line naming no article cannot be attributed to one. It would
            // otherwise all pile up under the empty key as one phantom article.
            if (key.isEmpty()) continue
            quantities[key] = (quantities[key] ?: 0.0) + item.amountCount
            item.unit.takeIf { it.isNotBlank() }?.let { units.getOrPut(key) { mutableSetOf() }.add(it) }
            orders.getOrPut(key) { mutableSetOf() }.add(order.id)
        }
    }

    return quantities.mapValues { (key, quantity) ->
        ArticleDemand(
            articleId = key,
            quantity = ProductPricing.roundQuantity(quantity),
            units = units[key].orEmpty(),
            orderCount = orders[key]?.size ?: 0
        )
    }
}
