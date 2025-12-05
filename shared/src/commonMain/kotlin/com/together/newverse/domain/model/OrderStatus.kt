package com.together.newverse.domain.model

/**
 * Represents the lifecycle status of an order
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
    CANCELLED
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
    return this == OrderStatus.LOCKED || this == OrderStatus.COMPLETED || this == OrderStatus.CANCELLED
}

/**
 * Check if order status is active (not completed or cancelled)
 * Note: This only checks the status enum. Use Order.isActiveOrder() to also check pickup date.
 */
fun OrderStatus.isActive(): Boolean {
    return this != OrderStatus.COMPLETED && this != OrderStatus.CANCELLED
}
