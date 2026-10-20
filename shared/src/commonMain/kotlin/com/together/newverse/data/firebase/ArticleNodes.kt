package com.together.newverse.data.firebase

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.ArticlePublication
import com.together.newverse.domain.model.ArticleScarcity
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.TaxRate

/**
 * How articles are laid out in the Realtime Database.
 *
 * ```
 * /articles/{sellerId}/{articleId}         public half, readable by every signed-in user
 * /seller_articles/{sellerId}/{articleId}  private half, readable by the seller only
 * ```
 *
 * Writes are expressed as multi-path update maps (path → value) against the database
 * root, so both halves change in one atomic operation. Every field gets its own path:
 * an update then only touches the fields this app version knows, and fields written
 * by a newer version survive.
 *
 * Pure functions only — no Firebase types — so all of it is unit-testable.
 */
internal object ArticleNodes {

    const val PUBLIC_ROOT = "articles"
    const val PRIVATE_ROOT = "seller_articles"

    fun publicPath(sellerId: String, articleId: String) = "$PUBLIC_ROOT/$sellerId/$articleId"
    fun privatePath(sellerId: String, articleId: String) = "$PRIVATE_ROOT/$sellerId/$articleId"

    /** The public fields of an article. */
    fun publicFields(article: Article): Map<String, Any?> = mapOf(
        "productId" to article.productId,
        "productName" to article.productName,
        "available" to article.available,
        "unit" to article.unit,
        "price" to article.price,
        "weightPerPiece" to article.weightPerPiece,
        "imageUrl" to article.imageUrl,
        "category" to article.category,
        "searchTerms" to article.searchTerms,
        "detailInfo" to article.detailInfo,
        "taxRate" to article.taxRate,
        "scarcity" to article.scarcity.name
    )

    fun privateFields(data: SellerArticleData): Map<String, Any?> = mapOf(
        "acquirePrice" to data.acquirePrice,
        "markupFactor" to data.markupFactor,
        "supplier" to data.supplier,
        "origin" to data.origin,
        "certification" to data.certification,
        "quality" to data.quality,
        "barcode" to data.barcode,
        "packageSize" to data.packageSize,
        "reorderLevel" to data.reorderLevel,
        "stockGated" to data.stockGated
    ) + buildMap {
        // Only written once the seller has an intent to record. Writing null would
        // delete a stored intent, and writing the fallback would freeze today's
        // derived flag in as if the seller had asked for it.
        data.sellerAvailable?.let { put("sellerAvailable", it) }
    }

    /**
     * The update map that saves [article] under [articleId].
     *
     * Without [SellerArticle.sellerData] only the public half is written, so a save
     * made while the private half was unknown cannot overwrite it.
     */
    fun saveUpdate(sellerId: String, articleId: String, article: SellerArticle): Map<String, Any?> {
        require(articleId.isNotEmpty()) { "articleId must be assigned before building the update" }
        val public = publicPath(sellerId, articleId)
        val update = publicFields(article.article).mapKeys { (field, _) -> "$public/$field" }
        val data = article.sellerData ?: return update
        val private = privatePath(sellerId, articleId)
        return update + privateFields(data).mapKeys { (field, _) -> "$private/$field" }
    }

    /**
     * The update map that publishes what the ledger says about several articles:
     * whether they can be ordered, and how much is left.
     *
     * The public half only, and only those two fields: this is written by the
     * correction pass that derives both from the ledger
     * (`StockAvailability.kt`), which has no business touching a price or a name.
     */
    fun publicationUpdate(
        sellerId: String,
        publication: Map<String, ArticlePublication>
    ): Map<String, Any?> = buildMap {
        for ((articleId, state) in publication) {
            require(articleId.isNotEmpty()) { "articleId must not be empty" }
            put("${publicPath(sellerId, articleId)}/available", state.available)
            put("${publicPath(sellerId, articleId)}/scarcity", state.scarcity.name)
        }
    }

    /** The update map that removes both halves of an article. */
    fun deleteUpdate(sellerId: String, articleId: String): Map<String, Any?> {
        require(articleId.isNotEmpty()) { "articleId must not be empty" }
        return mapOf(
            publicPath(sellerId, articleId) to null,
            privatePath(sellerId, articleId) to null
        )
    }

    fun articleFromMap(articleId: String, value: Map<*, *>): Article = Article(
        id = articleId,
        productId = value.string("productId"),
        productName = value.string("productName"),
        available = value["available"] as? Boolean == true,
        unit = value.string("unit"),
        price = value.double("price") ?: 0.0,
        weightPerPiece = value.double("weightPerPiece") ?: 0.0,
        imageUrl = value.string("imageUrl"),
        category = value.string("category"),
        searchTerms = value.string("searchTerms"),
        detailInfo = value.string("detailInfo"),
        // Articles saved before taxRate was stored fall back to the default rate.
        taxRate = value.double("taxRate") ?: TaxRate.default.rate,
        // Absent, or a band written by a newer version than this one: say nothing
        // rather than guess. UNKNOWN is what an ungated article publishes anyway.
        scarcity = (value["scarcity"] as? String)
            ?.let { stored -> ArticleScarcity.entries.firstOrNull { it.name == stored } }
            ?: ArticleScarcity.UNKNOWN
    )

    fun sellerDataFromMap(value: Map<*, *>): SellerArticleData = SellerArticleData(
        acquirePrice = value.double("acquirePrice") ?: 0.0,
        markupFactor = value.double("markupFactor") ?: 1.0,
        supplier = value.string("supplier"),
        origin = value.string("origin"),
        certification = value.string("certification"),
        quality = value.string("quality"),
        barcode = value.string("barcode"),
        packageSize = value.double("packageSize") ?: 0.0,
        // Articles saved before refill watching existed are simply not watched.
        reorderLevel = value.double("reorderLevel") ?: 0.0,
        // Absent means no intent was ever recorded; SellerArticle.sellerAvailable
        // then falls back to the public flag rather than reading it as "off".
        sellerAvailable = value["sellerAvailable"] as? Boolean,
        stockGated = value["stockGated"] as? Boolean == true
    )

    /**
     * Pairs every public article with its private half, keeping the public order.
     *
     * Articles without a private half get `sellerData = null`. Private halves without
     * a public article (left behind if only the public node was deleted) are dropped:
     * the catalog is defined by what buyers can see.
     */
    fun join(
        public: Map<String, Article>,
        private: Map<String, SellerArticleData>
    ): List<SellerArticle> = public.map { (id, article) ->
        SellerArticle(article = article, sellerData = private[id])
    }

    private fun Map<*, *>.string(key: String): String = this[key] as? String ?: ""

    // The database returns whole numbers as Long, so read every number as Number.
    private fun Map<*, *>.double(key: String): Double? = (this[key] as? Number)?.toDouble()
}
