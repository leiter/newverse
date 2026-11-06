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

/**
 * The prefix on an order id minted for local-only storage.
 *
 * The first `DEMO_FIREBASE_WRITE_LIMIT` demo orders a buyer places are written under
 * `demo_orders/`; after that the app switches to local mode and mints its own ids. The
 * prefix is the only thing that tells the two apart, and it decides where a record has
 * to be removed from.
 */
const val LOCAL_DEMO_ID_PREFIX = "demo_"

/** Whether this demo order is held only in [com.together.newverse.domain.config.MutableSellerConfig], not under `demo_orders/`. */
val Order.isLocalDemo: Boolean get() = id.startsWith(LOCAL_DEMO_ID_PREFIX)

/**
 * Whether a demo order is removed outright rather than marked `CANCELLED`.
 *
 * It always is, and the reason is that nobody is on the other end. The seller does not
 * subscribe to `demo_orders/` (see `observeSellerOrders`), so a `CANCELLED` record there
 * is litter in a trunk nobody reads — and cancelling is a message to a seller who has
 * your order. A delete is the honest write.
 *
 * It also has to be a delete to work at all. `cancelOrder` refuses an order past its edit
 * deadline, which is the right rule for an order a seller is planning around and a dead
 * end for a leftover demo order: approval can arrive weeks after the day the buyer
 * picked, and they would be left holding a record they can neither confirm nor remove.
 */
fun Order.isRemovedNotCancelled(): Boolean = origin.isDemo
