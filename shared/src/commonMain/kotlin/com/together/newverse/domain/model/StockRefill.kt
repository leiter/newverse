package com.together.newverse.domain.model

/**
 * How urgently one article needs refilling.
 *
 * Ordered by urgency, so the enum's own order sorts a refill list.
 */
enum class RefillState {
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
    val needsAttention: Boolean get() = this == EMPTY || this == LOW || this == UNCOUNTED
}

/**
 * One article's standing in storage: what is there, what the seller wants there, and
 * how firm the number is.
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
    val hasLevel: Boolean = false
) {
    /** How much to bring to reach the reorder level again; 0 when nothing is needed. */
    val shortfall: Double
        get() = if (state.needsAttention && reorderLevel > 0.0) {
            ProductPricing.roundQuantity((reorderLevel - onHand).coerceAtLeast(0.0))
        } else 0.0

    /** Whether a count has ever confirmed this level. */
    val wasEverCounted: Boolean get() = lastCountedAt != null
}

/**
 * The refill standing of one article, given what the ledger says about it.
 *
 * @param level the derived level, or null when the article has no movements yet.
 */
fun SellerArticle.refillNeed(level: StockLevel?): RefillNeed {
    val reorderLevel = sellerData?.reorderLevel ?: 0.0
    val onHand = level?.onHand ?: 0.0
    val state = when {
        reorderLevel <= 0.0 -> RefillState.UNWATCHED
        level == null -> RefillState.UNCOUNTED
        onHand <= 0.0 -> RefillState.EMPTY
        onHand <= reorderLevel -> RefillState.LOW
        else -> RefillState.OK
    }
    return RefillNeed(
        articleId = id,
        productName = article.productName,
        // The article's current unit: the level's unit is what it was when last moved.
        unit = article.unit,
        onHand = onHand,
        reorderLevel = reorderLevel,
        state = state,
        lastCountedAt = level?.lastCountedAt,
        hasLevel = level != null
    )
}

/**
 * What needs refilling, most urgent first: empty before low, then articles never
 * counted, and within a state the biggest shortfall first.
 *
 * Articles the seller does not watch and those with enough on hand are left out
 * entirely — this is the list the seller acts on, not a full inventory. Use
 * [refillStanding] for every article.
 */
fun List<SellerArticle>.refillNeeds(levels: Map<String, StockLevel>): List<RefillNeed> =
    refillStanding(levels)
        .filter { it.state.needsAttention }
        .sortedWith(compareBy({ it.state.ordinal }, { -it.shortfall }, { it.productName.lowercase() }))

/** The refill standing of every article, whether or not it needs attention. */
fun List<SellerArticle>.refillStanding(levels: Map<String, StockLevel>): List<RefillNeed> =
    map { it.refillNeed(levels[it.id]) }

/**
 * Whether anything needs refilling — for a badge on the stock screen, so the seller
 * does not have to go looking.
 */
fun List<SellerArticle>.hasRefillNeeds(levels: Map<String, StockLevel>): Boolean =
    any { it.refillNeed(levels[it.id]).state.needsAttention }
