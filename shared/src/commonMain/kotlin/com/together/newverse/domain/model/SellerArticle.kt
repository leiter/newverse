package com.together.newverse.domain.model

/**
 * Facts about an article that only the seller may see: what it cost, how it is
 * priced and where it came from.
 *
 * Stored apart from the public [Article] (see `SellerArticleRepository`), because a
 * database read permission always covers a whole node — a buyer who can list the
 * articles could otherwise read every purchase price.
 */
data class SellerArticleData(
    val acquirePrice: Double = 0.0,     // Purchase price per unit in EUR (net)
    val markupFactor: Double = 1.0,     // Applied on top of acquirePrice before tax
    val supplier: String = "",          // Supplier code (BTR, SCH, …)
    val origin: String = "",            // Country code or "REG"
    val certification: String = "",     // BNN IK code (DD, DB, EG, …)
    val quality: String = "",           // Handelsklasse
    val barcode: String = "",           // EAN, empty if unknown
    val packageSize: Double = 0.0       // Gebinde size in [Article.unit]
)

/**
 * The seller's view of an article: the public [article] every buyer sees, plus the
 * seller-only [sellerData].
 *
 * The two halves live in separate database nodes and arrive through separate
 * listeners, so [sellerData] is `null` whenever the private half is not known —
 * for an article created before purchase data was stored, or for the moment
 * between the two listeners delivering an update. Code must never treat `null`
 * as "zero cost", and must never write the private half from a state where it
 * was `null`.
 */
data class SellerArticle(
    val article: Article,
    val sellerData: SellerArticleData? = null
) {
    val id: String get() = article.id
}
