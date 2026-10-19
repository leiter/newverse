package com.together.newverse.domain.model

data class AccessRequest(
    val sellerId: String,
    /** The buyer's Firebase auth uid — the key the request is stored under. */
    val buyerId: String,
    val buyerDisplayName: String,
    /**
     * The market the buyer is asking to collect at.
     *
     * A request is bound to a market: someone asks to order at *this* stall, which
     * is usually the one they are standing in front of. Approving it grants that
     * market, so the seller is answering the question that was actually asked
     * rather than deciding the scope themselves afterwards.
     *
     * Empty for a request to order anywhere — a single-market seller, or a request
     * made before requests carried a market.
     */
    val marketId: String = "",
    val requestedAt: Long
)
