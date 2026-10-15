package com.together.newverse.domain.model

/**
 * Represents the lifecycle status of an order.
 *
 * Lifecycle only — whether an order is a demo, a transitioned or a production order is
 * [OrderOrigin], carried separately on [Order]. The former `DEMO_ORDER` value mixed the
 * two and is gone; records that still hold it are read as [PLACED] by `storedOrderStatus`,
 * with the demo-ness recovered into the origin.
 */
enum class OrderStatus {
    /**
     * Order is being created but not yet saved to Firebase
     * Exists only in local BasketRepository
     */
    DRAFT,

    /**
     * Order has been saved to Firebase and is editable
     * User can modify until Tuesday 23:59 before pickup
     */
    PLACED,

    /**
     * Edit deadline (Tuesday 23:59) has passed
     * Order is locked and cannot be modified
     * Waiting for pickup on Thursday
     */
    LOCKED,

    /**
     * Pickup date has passed
     * Order is completed
     */
    COMPLETED,

    /**
     * Order was cancelled by user or system
     */
    CANCELLED,

    /**
     * The seller marked that nobody collected the order. Nothing was sold, so nothing
     * is booked. App versions that predate this status read it as DRAFT.
     */
    NOT_PICKED_UP
}

/**
 * Extension functions for OrderStatus
 */

/**
 * Check if order can be modified
 */
fun OrderStatus.isEditable(): Boolean {
    return this == OrderStatus.DRAFT || this == OrderStatus.PLACED
}

/**
 * Check if order is finalized (cannot be changed)
 */
fun OrderStatus.isFinalized(): Boolean {
    return this == OrderStatus.LOCKED || this == OrderStatus.COMPLETED ||
        this == OrderStatus.CANCELLED || this == OrderStatus.NOT_PICKED_UP
}

/**
 * Check if order status is active (not completed or cancelled)
 * Note: This only checks the status enum. Use Order.isActiveOrder() to also check pickup date.
 */
fun OrderStatus.isActive(): Boolean {
    return this != OrderStatus.COMPLETED && this != OrderStatus.CANCELLED &&
        this != OrderStatus.NOT_PICKED_UP
}
