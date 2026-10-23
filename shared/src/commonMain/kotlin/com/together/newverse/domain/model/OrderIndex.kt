package com.together.newverse.domain.model

/**
 * [BuyerProfile.placedOrderIds] with one order filed under [date], and nowhere else.
 *
 * The index is how a buyer finds their own orders: the rules grant them read on an order
 * carrying their `buyerId`, but not on the seller's order tree, so nothing can be
 * discovered by listing — an order missing from here is invisible to the buyer who placed
 * it, however intact the record is.
 *
 * Every write of an order record has to go through this, not just the first one. Two ways
 * the old "add it when placing, assume it afterwards" rule broke:
 *
 *  - delete an order and save it again. Deleting prunes the entry, but the basket still
 *    holds the order id, so the next save is an *edit* — which rewrote the record and left
 *    it unindexed.
 *  - edit an order's pickup day. The record moves to the new date's path while the entry
 *    still names the old one, so the lookup finds nothing.
 *
 * Hence filing by value as well as by key: any entry pointing at this order is dropped
 * before the current one is written, which keeps the map one-entry-per-order and makes
 * repeated calls idempotent.
 */
fun orderIndexWith(
    index: Map<String, String>,
    date: String,
    orderId: String
): Map<String, String> = index.filterValues { it != orderId } + (date to orderId)
