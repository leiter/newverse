package com.together.newverse.domain.model

/**
 * A seller's record of one buyer's access, as stored at
 * `buyer_access_status/{sellerId}/{buyerId}`.
 *
 * This is the single source of truth for whether a buyer may order from a seller.
 * It replaces the knownClientIds / approvedBuyerIds / blockedClientIds maps that
 * used to be denormalised into the seller profile: those could not be written by a
 * buyer, so a buyer who redeemed an invite token stayed invisible to the seller
 * until the seller next opened the app.
 *
 * [buyerId] is the buyer's Firebase auth uid, which is also the id their orders are
 * keyed by, so no correlation step is needed to line the two up.
 */
data class BuyerAccess(
    val buyerId: String = "",
    val displayName: String = "",
    val status: AccessStatus = AccessStatus.NONE,
    val updatedAt: Long = 0L
)
