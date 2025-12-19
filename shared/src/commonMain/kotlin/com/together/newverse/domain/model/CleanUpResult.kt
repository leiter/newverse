package com.together.newverse.domain.model

/**
 * Tracks the result of account cleanup when a user deletes their account.
 *
 * Business rules:
 * - Future orders (pickup date > now) are CANCELLED
 * - Past orders (pickup date <= now) are kept for seller records
 * - Buyer profile is deleted
 */
data class CleanUpResult(
    val started: Boolean = false,
    /** Orders with future pickup dates that were targeted for cancellation */
    val futureOrderIds: List<String> = emptyList(),
    /** Orders that were successfully cancelled */
    val cancelledOrders: List<String> = emptyList(),
    /** Past orders that were kept for seller history */
    val skippedOrders: List<String> = emptyList(),
    /** Whether the buyer profile was successfully deleted */
    val profileDeleted: Boolean = false,
    /** Whether the Firebase Auth account was deleted */
    val authDeleted: Boolean = false,
    /** Any errors that occurred during cleanup */
    val errors: List<String> = emptyList()
) {
    /** Returns true if all targeted orders were successfully cancelled */
    val allOrdersCancelled: Boolean
        get() = futureOrderIds.size == cancelledOrders.size

    /** Returns true if cleanup completed without errors */
    val isSuccessful: Boolean
        get() = errors.isEmpty() && profileDeleted
}
