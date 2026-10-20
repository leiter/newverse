package com.together.newverse.domain.model

/**
 * How urgently one article needs refilling.
 *
 * Ordered by urgency, so the enum's own order sorts a refill list.
 */
enum class RefillState {
    /**
     * Buyers have ordered more than is in storage. The most urgent state there is:
     * someone is already waiting for goods that are not there.
     *
     * Unlike [LOW] this does not depend on a reorder level, so an article the seller
     * never asked to watch still says so when it has been oversold.
     */
    SHORT,

    /** Nothing left. */
    EMPTY,

    /** At or below the seller's reorder level, but not gone. */
    LOW,

    /**
     * Watched, but the ledger has never seen this article: it has to be counted
     * before anything can be said about it. Not the same as empty — an uncounted
     * shelf is usually full.
     */
    UNCOUNTED,

    /** Above the reorder level. */
    OK,

    /** No reorder level set, so the seller asked not to be warned. */
    UNWATCHED;

    /** Whether this state belongs on a refill list. */
    val needsAttention: Boolean
        get() = this == SHORT || this == EMPTY || this == LOW || this == UNCOUNTED
}

/**
 * One article's standing in storage: what is there, what buyers have already bought
 * out of it, what the seller wants there, and how firm the numbers are.
 *
 * [lastCountedAt] is carried because a level built only from arithmetic is weaker
 * evidence than one a weighing confirmed. A seller deciding what to load for
 * tomorrow wants to know which figures were actually checked.
 */
data class RefillNeed(
    val articleId: String,
    val productName: String,
    val unit: String,
    val onHand: Double,
    val reorderLevel: Double,
    val state: RefillState,
    /** Epoch milliseconds the level was last confirmed by a count; null if never. */
    val lastCountedAt: Long? = null,
    /**
     * Whether the ledger has any movement for this article.
     *
     * Needed because [onHand] is 0.0 both for an article known to be empty and for
     * one nothing is known about, and showing "0 kg" for the second is a lie the
     * seller would act on. [RefillState.UNCOUNTED] only says this for watched
     * articles; an unwatched one needs it too.
     */
    val hasLevel: Boolean = false,
    /**
     * How much buyers have ordered and not yet collected. 0.0 when nothing is
     * outstanding — and also when [unitMismatch] made the sum unusable.
     */
    val committed: Double = 0.0,
    /** How many orders [committed] comes from, for naming who is affected. */
    val committedOrderCount: Int = 0,
    /**
     * The orders and the article disagree about the unit, so no arithmetic between
     * them is meaningful and [committed] was dropped.
     *
     * This is shown as a question rather than hidden: a wrong number here becomes a
     * wrong purchase. It happens when an article's unit is changed while orders
     * placed in the old unit are still outstanding.
     */
    val unitMismatch: Boolean = false
) {
    /** What is left to sell once outstanding orders are filled. */
    val remaining: Double get() = ProductPricing.roundQuantity(onHand - committed)

    /**
     * How much to bring to cover outstanding orders and reach the reorder level
     * again; 0 when nothing is needed.
     *
     * With nothing committed this is the old purely threshold-based shortfall, which
     * is why a watched article behaves exactly as before until buyers order.
     */
    val shortfall: Double
        get() = if (state.needsAttention && (reorderLevel > 0.0 || committed > 0.0)) {
            ProductPricing.roundQuantity((committed + reorderLevel - onHand).coerceAtLeast(0.0))
        } else 0.0

    /** Whether a count has ever confirmed this level. */
    val wasEverCounted: Boolean get() = lastCountedAt != null

    /**
     * Whether this article belongs on the list the seller acts on.
     *
     * Wider than [RefillState.needsAttention]: a unit mismatch leaves the state
     * saying nothing is wrong, while the seller still has to resolve it before any
     * number about this article can be believed.
     */
    val needsAttention: Boolean get() = state.needsAttention || unitMismatch
}

/**
 * The refill standing of one article, given what the ledger says about it and what
 * buyers have ordered.
 *
 * @param level the derived level, or null when the article has no movements yet.
 * @param demand outstanding orders for this article, or null when there are none.
 */
fun SellerArticle.refillNeed(level: StockLevel?, demand: ArticleDemand? = null): RefillNeed {
    val reorderLevel = sellerData?.reorderLevel ?: 0.0
    val onHand = level?.onHand ?: 0.0

    // The article's current unit is the one that counts: the level's unit is what it
    // was when last moved, and an order line's is what it was when ordered. Where
    // they disagree the amounts cannot be added, so the demand is dropped rather
    // than converted — kg and Stück have no exchange rate.
    val orderedUnit = demand?.unit
    val mismatch = demand != null &&
        (orderedUnit == null || !orderedUnit.equals(article.unit, ignoreCase = true))
    val committed = if (mismatch) 0.0 else demand?.quantity ?: 0.0

    val state = when {
        // Nothing watched and nothing ordered: the seller asked not to hear about it.
        reorderLevel <= 0.0 && committed <= 0.0 -> RefillState.UNWATCHED
        // Without a movement, 0.0 is not a level, so neither SHORT nor EMPTY can be
        // claimed. Only a watched article says so out loud: demand alone must not
        // turn every never-counted article into a question, which on a fresh ledger
        // is the whole catalogue.
        level == null -> if (reorderLevel > 0.0) RefillState.UNCOUNTED else RefillState.UNWATCHED
        // Oversold outranks empty: an empty shelf with no orders behind it can wait,
        // an oversold one has a buyer waiting for it. Something must actually be
        // ordered — a ledger can stand below zero on its own, when a sale was booked
        // before the intake it came out of, and that is empty, not oversold.
        committed > 0.0 && committed > onHand -> RefillState.SHORT
        onHand <= 0.0 -> RefillState.EMPTY
        reorderLevel > 0.0 && onHand - committed <= reorderLevel -> RefillState.LOW
        else -> RefillState.OK
    }

    return RefillNeed(
        articleId = id,
        productName = article.productName,
        unit = article.unit,
        onHand = onHand,
        reorderLevel = reorderLevel,
        state = state,
        lastCountedAt = level?.lastCountedAt,
        hasLevel = level != null,
        committed = committed,
        committedOrderCount = if (mismatch) 0 else demand?.orderCount ?: 0,
        unitMismatch = mismatch
    )
}

/**
 * What needs refilling, most urgent first: oversold before empty, empty before low,
 * then articles never counted, and within a state the biggest shortfall first.
 *
 * Articles the seller does not watch and those with enough on hand are left out
 * entirely — this is the list the seller acts on, not a full inventory. Use
 * [refillStanding] for every article.
 */
fun List<SellerArticle>.refillNeeds(
    levels: Map<String, StockLevel>,
    demand: Map<String, ArticleDemand> = emptyMap()
): List<RefillNeed> =
    refillStanding(levels, demand)
        .filter { it.needsAttention }
        .sortedWith(compareBy({ it.state.ordinal }, { -it.shortfall }, { it.productName.lowercase() }))

/** The refill standing of every article, whether or not it needs attention. */
fun List<SellerArticle>.refillStanding(
    levels: Map<String, StockLevel>,
    demand: Map<String, ArticleDemand> = emptyMap()
): List<RefillNeed> =
    map { it.refillNeed(levels[it.id], demand[it.id]) }

/**
 * Whether anything needs refilling — for a badge on the stock screen, so the seller
 * does not have to go looking.
 */
fun List<SellerArticle>.hasRefillNeeds(
    levels: Map<String, StockLevel>,
    demand: Map<String, ArticleDemand> = emptyMap()
): Boolean = any { it.refillNeed(levels[it.id], demand[it.id]).needsAttention }
