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
 * The articles whose public flag no longer says what the seller and the ledger say,
 * mapped to the value it should hold.
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
fun List<SellerArticle>.availabilityCorrections(
    levels: Map<String, StockLevel>,
    demand: Map<String, ArticleDemand> = emptyMap()
): Map<String, Boolean> = buildMap {
    for (article in this@availabilityCorrections) {
        val data = article.sellerData ?: continue
        if (data.stockGated && data.sellerAvailable == null) continue
        val derived = article.derivedAvailability(levels[article.id], demand[article.id])
        if (derived != article.article.available) put(article.id, derived)
    }
}
