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
    val acquirePrice: Double = 0.0,     // Purchase price per unit in EUR (net); 0 = unknown
    val markupFactor: Double = 1.0,     // Applied on top of acquirePrice before tax
    val supplier: String = "",          // Supplier code (BTR, SCH, …)
    val origin: String = "",            // Country code or "REG"
    val certification: String = "",     // BNN IK code (DD, DB, EG, …)
    val quality: String = "",           // Handelsklasse
    val barcode: String = "",           // EAN, empty if unknown
    val packageSize: Double = 0.0,      // Gebinde size in [Article.unit]
    /**
     * Refill below this amount, in [Article.unit]. 0 = not watched, the default:
     * stock is still counted, the seller is simply never warned about it.
     *
     * Seller-only because it is a business decision, not something buyers see.
     */
    val reorderLevel: Double = 0.0,
    /**
     * Whether the seller wants this article offered at all — their own intent,
     * apart from whatever the stock ledger says about it.
     *
     * `null` means no intent was ever recorded, which is every article saved before
     * this field existed. Read it through [SellerArticle.sellerAvailable], which
     * falls back to the public flag: that *is* the seller's standing intent for an
     * article nobody has touched since.
     *
     * The public [Article.available] is derived from this and the ledger; one
     * boolean cannot hold both the seller's intent and the shelf's state, because
     * a restock would then undo "off for the season". See
     * `doc/guides/stock-driven-availability-design.md` § "`available` is derived, not edited".
     */
    val sellerAvailable: Boolean? = null,
    /**
     * Whether running out closes this article to buyers. Opt-in, per article.
     *
     * Off by default on purpose: weighed goods where the seller brings what they
     * bring should not be gated at all, and a gate that defaulted on would empty
     * the shop the moment it shipped — `onHand` is 0.0 for every article the ledger
     * has never seen.
     */
    val stockGated: Boolean = false
) {
    /** Whether the seller asked to be warned when this article runs low. */
    val isWatched: Boolean get() = reorderLevel > 0.0

    /** Whether a purchase price was recorded. Zero means unknown, never free. */
    val hasAcquirePrice: Boolean get() = acquirePrice > 0.0
}

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

    /**
     * Whether the seller wants this article offered, regardless of stock.
     *
     * Falls back to the public flag where no intent was recorded: an article saved
     * before intent was stored separately carries it in the only field there was.
     * Never read `sellerData?.sellerAvailable == true` directly — that reads a
     * missing intent as "off" and would withdraw the whole catalogue.
     */
    val sellerAvailable: Boolean get() = sellerData?.sellerAvailable ?: article.available

    /** Whether running out closes this article to buyers. */
    val stockGated: Boolean get() = sellerData?.stockGated == true
}
