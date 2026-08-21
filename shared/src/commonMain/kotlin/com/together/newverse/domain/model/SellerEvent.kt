package com.together.newverse.domain.model

/**
 * Type of account lifecycle event recorded for a seller's book keeping.
 */
enum class SellerEventType {
    /** A guest (anonymous) buyer logged out; all guest data was deleted. */
    GUEST_DATA_DELETED,

    /** An authenticated buyer deleted their account; profile and future orders are gone. */
    ACCOUNT_DELETED,

    /** A guest upgraded to a permanent account, keeping their data. */
    ACCOUNT_LINKED,

    /**
     * Buyer data was deleted but the Firebase Auth account could not be removed.
     * The auth user is orphaned and needs manual cleanup.
     */
    ACCOUNT_DELETION_INCOMPLETE
}

/**
 * An append-only book keeping record written to the seller's event log.
 *
 * Firebase structure: `seller_events/{sellerId}/{eventId}`
 *
 * Buyers may only append new events; the seller alone can read the log.
 * Records are intentionally denormalised (buyer name and UUID are copied in)
 * because the buyer profile is usually deleted in the same operation.
 */
data class SellerEvent(
    val id: String = "",
    val sellerId: String = "",
    val type: SellerEventType = SellerEventType.ACCOUNT_DELETED,
    val buyerId: String = "",
    /** Firebase Auth uid of the buyer, read straight from the auth session. */
    val firebaseUserId: String = "",
    val buyerUUID: String = "",
    val buyerName: String = "",
    val buyerEmail: String = "",
    val timestamp: Long = 0L,
    /** ISO-8601 rendering of [timestamp], so the log is readable in the console. */
    val timestampIso: String = "",
    /** Number of future orders cancelled as part of this event. */
    val cancelledOrderCount: Int = 0,
    /** Free-form context, e.g. an error message for incomplete deletions. */
    val details: String = ""
)
