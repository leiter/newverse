package com.together.newverse.domain.model

/** Why a basket item can no longer be ordered. */
enum class UnorderableReason {
    /** The seller marked the article unavailable after it went into the basket. */
    WITHDRAWN,

    /** The article is no longer in the seller's catalogue at all. */
    GONE
}

/** One basket item the seller can no longer accept, and why. */
data class UnorderableItem(
    val articleId: String,
    val productName: String,
    val reason: UnorderableReason
)

/**
 * The items in this basket that the seller can no longer accept.
 *
 * `Article.available` has until now only filtered what the catalogue *shows*. A
 * basket outlives that: it is persisted as a draft, an order can be reordered weeks
 * later, and the seller can withdraw an article in between. Checking at the point of
 * ordering is what makes the flag mean anything — see
 * `doc/design/stock-driven-availability-design.md` §4c.
 *
 * An empty [catalog] yields nothing, deliberately. The catalogue arrives over a
 * listener, so "no articles" is indistinguishable from "not loaded yet", and
 * refusing a whole basket because the network is slow would be the worse failure.
 */
fun List<OrderedProduct>.unorderableAgainst(catalog: List<Article>): List<UnorderableItem> {
    if (catalog.isEmpty()) return emptyList()
    val byId = catalog.associateBy { it.id }

    return mapNotNull { item ->
        val key = item.articleKey()
        // A line naming no article cannot be checked against one, and must not be
        // refused on that basis: these are old orders, not withdrawn goods.
        if (key.isEmpty()) return@mapNotNull null

        val article = byId[key]
        when {
            article == null -> UnorderableItem(key, item.productName, UnorderableReason.GONE)
            !article.available -> UnorderableItem(
                key,
                // The catalogue's name, not the basket's: the seller may have
                // renamed it since, and the buyer is about to go looking for it.
                article.productName.ifEmpty { item.productName },
                UnorderableReason.WITHDRAWN
            )
            else -> null
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
 * `doc/design/stock-driven-availability-design.md` §4f, open question 8.
 */
fun List<OrderedProduct>.nearlyGoneAgainst(catalog: List<Article>): Set<String> {
    if (catalog.isEmpty()) return emptySet()
    val byId = catalog.associateBy { it.id }

    return mapNotNull { item ->
        val key = item.articleKey().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        key.takeIf { byId[key]?.scarcity == ArticleScarcity.LAST_FEW }
    }.toSet()
}
