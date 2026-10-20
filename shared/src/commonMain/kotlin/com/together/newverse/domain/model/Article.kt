package com.together.newverse.domain.model

/**
 * A product as buyers see it — the public half of an article.
 *
 * Purchase price, markup and sourcing are seller-only and live in
 * [SellerArticleData]; the seller works with both halves as a [SellerArticle].
 */
data class Article(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val available: Boolean = false,
    val unit: String = "",
    val price: Double = 0.0,
    val weightPerPiece: Double = 0.0,
    val imageUrl: String = "",
    val category: String = "",
    val searchTerms: String = "",
    val detailInfo: String = "",
    val taxRate: Double = TaxRate.REDUCED.rate,
    /**
     * How much is left, coarsely, for articles whose seller gates them on stock.
     *
     * Derived and published by the seller's app alongside [available] — never typed.
     * A band and not a number: this node is readable by every signed-in user, and a
     * quantity would be both a promise the shop cannot keep once it is a few minutes
     * stale and a turnover feed for anyone who polls it. See
     * `doc/design/stock-driven-availability-design.md` §4f.
     */
    val scarcity: ArticleScarcity = ArticleScarcity.UNKNOWN,
    val mode: Int = MODE_UNDEFINED
) {
    companion object {
        const val MODE_ADDED = 0
        const val MODE_CHANGED = 1
        const val MODE_MOVED = 2
        const val MODE_REMOVED = 3
        const val MODE_UNDEFINED = -1
    }
}
