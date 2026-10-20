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
}
