package com.together.newverse.domain.repository

import com.together.newverse.domain.model.SellerEvent
import com.together.newverse.domain.model.SellerEventType
import kotlinx.coroutines.flow.Flow

/**
 * Append-only book keeping log of buyer account lifecycle events, per seller.
 *
 * Buyers write events (account deleted, guest data wiped, account linked);
 * the seller reads them to keep records of who disappeared and when.
 */
interface SellerEventRepository {

    /**
     * Append an event to the seller's log.
     *
     * Never throws: book keeping must not break the operation it records.
     */
    suspend fun logEvent(
        sellerId: String,
        type: SellerEventType,
        buyerId: String,
        firebaseUserId: String,
        buyerUUID: String = "",
        buyerName: String = "",
        buyerEmail: String = "",
        cancelledOrderCount: Int = 0,
        details: String = ""
    ): Result<SellerEvent>

    /** Observe the seller's event log, newest first. */
    fun observeEvents(sellerId: String, limit: Int = 100): Flow<List<SellerEvent>>
}
