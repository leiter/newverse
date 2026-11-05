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
    /**
     * What the buyer wrote to the seller when asking, in their own words.
     *
     * Optional, and the single most useful thing on this record. The seller's decision
     * is "do I want this person ordering from me?", and a display name plus a UUID
     * barely helps them answer it — "Michael" and `k3Jx…` is not a person they can
     * recognise. *"war Donnerstag am Stand, hab nach Kartoffeln gefragt"* is.
     *
     * Deliberately free text and deliberately not required: it is the buyer's chance
     * to identify themselves however actually works, not another field to fill in.
     * Empty for a buyer who had nothing to add and for every request made before this
     * existed.
     *
     * Capped at 280 characters by `database.rules.json` — this is buyer-written text
     * in a seller-readable node, so the bound is enforced where it cannot be bypassed
     * rather than only in the UI.
     */
    val note: String = "",
    val requestedAt: Long
)
