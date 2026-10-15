package com.together.newverse.domain.model

/**
 * Where an order came from — its provenance, independent of [OrderStatus].
 *
 * Provenance used to be encoded twice: as `Order.isDemoOrder` and as the status value
 * `OrderStatus.DEMO_ORDER`. Squatting in the status slot meant a demo order could never
 * also be PLACED, LOCKED or COMPLETED, so an order that moved to production kept
 * reporting DEMO_ORDER and ended up half-real. Origin and lifecycle are now separate.
 *
 * The *path* remains the access and traffic boundary — [DEMO] orders live under
 * `/demo_orders/{sellerId}/{yyyyMMdd}/{orderId}`, the other two under
 * `/orders/{sellerId}/{yyyyMMdd}/{orderId}`. Origin only records how a record got there.
 */
enum class OrderOrigin {

    /**
     * Placed by a buyer who is not yet approved by the seller. Lives under `demo_orders/`
     * or in local storage only, is never fulfilled and never enters the seller's books.
     */
    DEMO,

    /**
     * Was a demo order and became real when the seller approved the buyer. Lives under
     * `orders/` and is fulfilled and booked exactly like [PRODUCTION]; the value is kept
     * so the seller can tell that an order predates the approval.
     */
    TRANSITIONED,

    /** Placed by an approved buyer. The ordinary case. */
    PRODUCTION;

    /** True for orders that are demonstrations only — never fulfilled, never booked. */
    val isDemo: Boolean get() = this == DEMO

    /** True for orders the seller is expected to fulfil and book. */
    val isReal: Boolean get() = !isDemo
}
