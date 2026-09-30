package com.together.newverse.domain.model

data class AccessRequest(
    val sellerId: String,
    /** The buyer's Firebase auth uid — the key the request is stored under. */
    val buyerId: String,
    val buyerDisplayName: String,
    val requestedAt: Long
)
