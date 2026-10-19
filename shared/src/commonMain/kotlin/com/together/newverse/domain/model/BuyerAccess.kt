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
    /**
     * Which markets this buyer may collect at. **Empty means all of them.**
     *
     * [status] answers *whether* they may order; this answers *where*. The two are
     * kept apart on purpose: BLOCKED is a judgement about a person and would make
     * no sense per stall, while this is logistics the seller can change freely
     * without touching the trust state machine. Empty meaning all is also what
     * makes every record written before this field keep its meaning untouched.
     */
    val allowedMarkets: List<String> = emptyList(),
    val updatedAt: Long = 0L
) {
    /** Whether this buyer may collect at [marketId]. An unscoped buyer may use any. */
    fun mayUse(marketId: String): Boolean =
        allowedMarkets.isEmpty() || marketId in allowedMarkets

    /** The subset of [markets] this buyer may collect at, in the given order. */
    fun eligible(markets: List<Market>): List<Market> =
        if (allowedMarkets.isEmpty()) markets else markets.filter { it.id in allowedMarkets }
}

/**
 * Markets encoded for storage as one string.
 *
 * A comma-joined list rather than a child map: the value has to be compared whole
 * inside a security rule, and RTDB rules can test a string for equality but cannot
 * walk a map to prove two of them match. That comparison is what stops a buyer
 * redeeming a stall-scoped invite and granting themselves every market.
 */
object MarketScope {
    fun encode(markets: List<String>): String =
        markets.filter { it.isNotBlank() }.distinct().sorted().joinToString(",")

    fun decode(raw: String?): List<String> =
        raw.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
