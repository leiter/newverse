package com.together.newverse.domain.model

/**
 * What became of a buyer's demo orders when the seller approved them.
 *
 * Three outcomes, and they are deliberately not collapsed into a count:
 *
 * - **Transitioned.** Moved into `orders/` as [OrderOrigin.TRANSITIONED], keyed
 *   date → orderId, which is the shape `BuyerProfile.placedOrderIds` takes.
 * - **Expired.** Pickup had already passed, so the order describes a collection that
 *   never happened; moving it would plant a sale in the seller's books that was never
 *   made. Dropped with the rest of the demo data.
 * - **Not eligible.** The order names a market the seller did not grant this buyer.
 *   These are *kept* rather than dropped, and the buyer is told — see
 *   [notEligibleMarketIds].
 *
 * `doc/guides/market-pricing-and-access-design.md` §2a has the reasoning.
 */
data class DemoTransitionResult(
    /** date (`yyyyMMdd`) → orderId, for the orders now living under `orders/`. */
    val transitioned: Map<String, String> = emptyMap(),
    /**
     * date → orderId for demo records this run deleted without moving, because their
     * pickup day had already passed.
     *
     * These are the only index entries a transition may remove. [transitioned] says
     * nothing about the orders a buyer already had, so it can never stand in for the
     * whole index — see [applyTo].
     */
    val dropped: Map<String, String> = emptyMap(),
    /** How many were dropped because their pickup day had already passed. */
    val expired: Int = 0,
    /**
     * The markets named by orders left behind because this buyer may not collect
     * there, de-duplicated and in encounter order.
     *
     * Left in `demo_orders/` on purpose. An order carries the price it was placed at,
     * and that price came from a market the buyer turns out not to have. Moving it
     * would charge a market's prices for a stall the buyer cannot reach; repricing it
     * silently would change a total they already confirmed. Neither is ours to do, so
     * the order stays where it is and the buyer is asked.
     */
    val notEligibleMarketIds: List<String> = emptyList(),
) {
    val hasRefusals: Boolean get() = notEligibleMarketIds.isNotEmpty()

    /**
     * This buyer's order index after the transition: what they had, minus the records
     * that are genuinely gone, plus the ones that moved.
     *
     * A merge, never a replacement. [transitioned] holds only what this run moved, so
     * assigning it to `placedOrderIds` wholesale threw away every order the buyer
     * already had — their entire history when nothing moved, which is what a buyer
     * approved for one market only would hit: their single demo order is refused,
     * `transitioned` comes back empty, and the index is emptied with it. The orders
     * themselves survived in `orders/`; only the index that finds them was lost.
     *
     * An order left behind on purpose — refused for a market the buyer may not use —
     * keeps its entry, because `demo_orders/` still holds it and the buyer is meant to
     * be asked about it.
     */
    fun applyTo(index: Map<String, String>): Map<String, String> =
        (index - dropped.keys) + transitioned
}
