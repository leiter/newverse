package com.together.newverse.domain.model

/** Why a basket item can no longer be ordered. */
enum class UnorderableReason {
    /** The seller marked the article unavailable after it went into the basket. */
    WITHDRAWN,

    /** The article is no longer in the seller's catalogue at all. */
    GONE,

    /**
     * The article is closed, but this order already holds some of it: the buyer is
     * asking for *more* than the shelf that was set aside for them.
     *
     * The remedy is a smaller number, not an empty line, so
     * [UnorderableItem.maxAmount] carries what the order already holds.
     */
    OVER_COMMITTED
}

/** One basket item the seller can no longer accept, and why. */
data class UnorderableItem(
    val articleId: String,
    val productName: String,
    val reason: UnorderableReason,
    /**
     * The largest amount still orderable, for [UnorderableReason.OVER_COMMITTED]:
     * what the placed order already holds. Zero for the other reasons, where no
     * amount is orderable at all.
     */
    val maxAmount: Double = 0.0,
    /** The article's unit, so [maxAmount] can be stated as "1 kg" and not "1". */
    val unit: String = ""
)

/**
 * The items in this basket that the seller can no longer accept.
 *
 * `Article.available` has until now only filtered what the catalogue *shows*. A
 * basket outlives that: it is persisted as a draft, an order can be reordered weeks
 * later, and the seller can withdraw an article in between. Checking at the point of
 * ordering is what makes the flag mean anything — see
 * `doc/guides/stock-driven-availability-design.md` § "The gate at placement".
 *
 * ### Only the increase is judged
 *
 * [committed] is what the order being edited already holds — `originalOrderItems`,
 * empty for a draft and for a reorder. Those amounts are **not** judged against the
 * catalogue, because the shelf behind them has already been counted: a buyer who
 * ordered exactly the seller's whole stock closes the article for everybody else, and
 * `available` going false is that working correctly. Asking them to remove the very
 * line the stock was set aside for would be the app contradicting itself.
 *
 * So what is checked is the part that is *new*: a fresh line, or the amount by which
 * an existing line grew. Exceeding by any amount asks for a smaller number
 * ([UnorderableReason.OVER_COMMITTED]) rather than an empty line, and the number to
 * fall back to is one the buyer already agreed to.
 *
 * A line the catalogue has lost entirely is still refused however much of it the order
 * holds: there is no price, no name and no unit to carry it forward with.
 *
 * An empty [catalog] yields nothing, deliberately. The catalogue arrives over a
 * listener, so "no articles" is indistinguishable from "not loaded yet", and
 * refusing a whole basket because the network is slow would be the worse failure.
 */
fun List<OrderedProduct>.unorderableAgainst(
    catalog: List<Article>,
    committed: List<OrderedProduct> = emptyList()
): List<UnorderableItem> {
    if (catalog.isEmpty()) return emptyList()
    val byId = catalog.associateBy { it.id }
    val holdsByKey = committed.associate { it.articleKey() to it.amountCount }

    return mapNotNull { item ->
        val key = item.articleKey()
        // A line naming no article cannot be checked against one, and must not be
        // refused on that basis: these are old orders, not withdrawn goods.
        if (key.isEmpty()) return@mapNotNull null

        val article = byId[key]
        // The catalogue's name, not the basket's: the seller may have renamed it
        // since, and the buyer is about to go looking for it.
        val name = article?.productName?.ifEmpty { item.productName } ?: item.productName
        val holds = holdsByKey[key] ?: 0.0

        when {
            article == null -> UnorderableItem(key, item.productName, UnorderableReason.GONE)
            article.available -> null
            // Nothing set aside for this line: it is new demand on a closed article.
            holds <= 0.0 -> UnorderableItem(key, name, UnorderableReason.WITHDRAWN)
            // Within what the seller already accepted. The shelf behind it is spoken
            // for, so there is nothing to warn about — and if the seller cannot fill
            // it after all, that is theirs to raise (`OrderShortfalls`), not something
            // to make the buyer police.
            item.amountCount <= holds -> null
            else -> UnorderableItem(
                key, name, UnorderableReason.OVER_COMMITTED,
                maxAmount = holds, unit = article.unit
            )
        }
    }
}

/** Whether every item in this basket can still be ordered. */
fun List<OrderedProduct>.allOrderable(catalog: List<Article>): Boolean =
    unorderableAgainst(catalog).isEmpty()

/**
 * The items of this basket that can still be ordered.
 *
 * Used where a basket is rebuilt from an older order — a reorder — and carrying a
 * withdrawn article forward would only produce an order the seller has to refuse.
 */
fun List<OrderedProduct>.orderableOnly(catalog: List<Article>): List<OrderedProduct> {
    if (catalog.isEmpty()) return this
    val unorderable = unorderableAgainst(catalog).map { it.articleId }.toSet()
    return filter { it.articleKey() !in unorderable }
}

/**
 * The article ids in this basket that the shop is nearly out of.
 *
 * [ArticleScarcity.LAST_FEW] only, deliberately. `LIMITED` is browsing information —
 * useful while choosing, noise beside something already chosen — while "nearly gone"
 * is the one state that changes what the buyer should do about an item in their
 * basket, because nothing reserves it for them.
 *
 * It exists because without it the buyer's first notice that waiting was risky is
 * the checkout refusing the whole order (see [unorderableAgainst]) and naming an
 * article they now have to remove. See
 * `doc/guides/stock-driven-availability-design.md` § "The scarcity band".
 */
fun List<OrderedProduct>.nearlyGoneAgainst(
    catalog: List<Article>,
    committed: List<OrderedProduct> = emptyList()
): Set<String> {
    if (catalog.isEmpty()) return emptySet()
    val byId = catalog.associateBy { it.id }
    val holdsByKey = committed.associate { it.articleKey() to it.amountCount }

    return mapNotNull { item ->
        val key = item.articleKey().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        // Not said about an amount the order already holds, for the reason the band
        // exists at all: it warns that nothing is reserving this for the buyer, and
        // for a placed order something is. See [unorderableAgainst].
        if (item.amountCount <= (holdsByKey[key] ?: 0.0)) return@mapNotNull null
        key.takeIf { byId[key]?.scarcity == ArticleScarcity.LAST_FEW }
    }.toSet()
}
