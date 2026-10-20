package com.together.newverse.domain.model

/**
 * Whether the ledger permits this article to be offered.
 *
 * True whenever the article is not gated — the default — so an ungated article is
 * decided by the seller's intent alone and this whole mechanism costs it nothing.
 *
 * Closing takes a *known* empty shelf, not an unknown one: `onHand` reads 0.0 both
 * for an article that is sold out and for one the ledger has never seen, and
 * treating the second as the first would withdraw an article sitting there in full
 * view — on a fresh ledger, the whole catalogue. See [RefillNeed.hasLevel] and
 * `doc/design/stock-driven-availability-design.md` §4b.
 *
 * A unit mismatch leaves the demand out of the sum (see [SellerArticle.refillNeed]),
 * which means a gated article is judged on `onHand` alone until the seller resolves
 * it. That is the lenient direction on purpose: an article stays open while the
 * numbers are in doubt, rather than closing on arithmetic nobody can trust.
 */
fun SellerArticle.stockPermitsOrders(level: StockLevel?, demand: ArticleDemand? = null): Boolean {
    if (!stockGated) return true
    val need = refillNeed(level, demand)
    return !need.hasLevel || need.remaining > 0.0
}

/**
 * Whether buyers should see this article: the seller's intent, narrowed by the shelf.
 *
 * This is what belongs in the public `available` field. The field is an output, not
 * something to hand-edit — the seller edits [SellerArticleData.sellerAvailable] and
 * this follows.
 */
fun SellerArticle.derivedAvailability(
    level: StockLevel?,
    demand: ArticleDemand? = null
): Boolean = sellerAvailable && stockPermitsOrders(level, demand)

/**
 * How much is left, as a buyer may be told it.
 *
 * [ArticleScarcity.UNKNOWN] — say nothing — unless all three hold:
 *  - the seller gated this article on stock, so they asked for it to be governed by
 *    the ledger at all;
 *  - the ledger has seen it, because `onHand` of 0.0 on a never-counted article
 *    means nothing (§4b);
 *  - there is a **reference quantity** to measure "few" against.
 *
 * That last one is why this is not a fixed threshold. Half a kilo is nearly nothing
 * in potatoes and a lot in saffron, so a constant would be meaningless across a
 * catalogue. The reference is the seller's own [SellerArticleData.reorderLevel] —
 * the amount they themselves restock at — falling back to
 * [SellerArticleData.packageSize], the Gebinde the article is bought in, which the
 * price-list import fills for most articles. With neither, nothing is published:
 * "few" would be this code's opinion rather than the seller's.
 */
fun SellerArticle.scarcityOf(level: StockLevel?, demand: ArticleDemand? = null): ArticleScarcity {
    if (!stockGated) return ArticleScarcity.UNKNOWN
    val data = sellerData ?: return ArticleScarcity.UNKNOWN

    val need = refillNeed(level, demand)
    if (!need.hasLevel) return ArticleScarcity.UNKNOWN

    val reference = data.reorderLevel.takeIf { it > 0.0 }
        ?: data.packageSize.takeIf { it > 0.0 }
        ?: return ArticleScarcity.UNKNOWN

    val remaining = need.remaining
    return when {
        // Sold out, or sold beyond the shelf: `available` carries that, and saying
        // "last few" about nothing would contradict it.
        remaining <= 0.0 -> ArticleScarcity.LAST_FEW
        remaining <= reference / 2 -> ArticleScarcity.LAST_FEW
        remaining <= reference -> ArticleScarcity.LIMITED
        else -> ArticleScarcity.PLENTIFUL
    }
}

/** What the public half of an article should say about itself. */
data class ArticlePublication(
    val available: Boolean,
    val scarcity: ArticleScarcity
)

/**
 * The articles whose public fields no longer say what the seller and the ledger say,
 * mapped to what they should hold.
 *
 * Only the differences: a correction pass writes this map and nothing else, so an
 * evaluation that agrees with the database writes nothing at all. That is what keeps
 * the pass idempotent — and what stops it looping, since its own write re-emits the
 * catalogue and the next evaluation then finds nothing to do.
 *
 * Articles whose private half has not arrived yet are skipped rather than guessed
 * at. Without it there is no `stockGated` to read, and defaulting to "not gated"
 * would publish an availability the seller never asked for.
 *
 * A gated article with no recorded intent is skipped too, and this one is not
 * caution but correctness: [SellerArticle.sellerAvailable] falls back to the public
 * flag, so closing such an article would make this pass's own write read back as the
 * seller's intent, and nothing could ever reopen it. The editor records the intent
 * whenever the gate is set, so a gate that was configured through the app always has
 * one.
 */
fun List<SellerArticle>.publicationCorrections(
    levels: Map<String, StockLevel>,
    demand: Map<String, ArticleDemand> = emptyMap()
): Map<String, ArticlePublication> = buildMap {
    for (article in this@publicationCorrections) {
        val data = article.sellerData ?: continue
        if (data.stockGated && data.sellerAvailable == null) continue
        val level = levels[article.id]
        val articleDemand = demand[article.id]
        val derived = ArticlePublication(
            available = article.derivedAvailability(level, articleDemand),
            scarcity = article.scarcityOf(level, articleDemand)
        )
        if (derived.available != article.article.available ||
            derived.scarcity != article.article.scarcity
        ) {
            put(article.id, derived)
        }
    }
}
