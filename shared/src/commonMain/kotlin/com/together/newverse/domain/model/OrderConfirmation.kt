package com.together.newverse.domain.model

/**
 * Whether this order is still the buyer's to confirm before it counts for anything.
 *
 * A demo order is a basket the app told the buyer was not binding, placed against a slot
 * chosen in a session that committed to nothing. Approval does not adopt it — see
 * `BuyAppViewModelAccess.promptToConfirmDemoOrders` — so once the buyer is approved it
 * sits in `demo_orders/` waiting for them to say they still want it, at a pickup day
 * that is open now rather than whenever they happened to place it.
 *
 * Keyed on the path's own answer ([OrderOrigin.isDemo]) and on approval being *known*
 * and granted. While the buyer is still unapproved there is nothing to confirm: they
 * cannot place a real order at all, so the demo order is simply their current order.
 */
fun Order.awaitsConfirmation(isApproved: Boolean): Boolean = isApproved && origin.isDemo
