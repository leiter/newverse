package com.together.newverse.domain.model

import kotlin.random.Random

/**
 * A sale as it goes into the books: what was actually handed over at pickup, at the
 * price, VAT rate and purchase price valid at that moment.
 *
 * A sale is created once, when the seller confirms a pickup, and never changed. It
 * copies everything it needs instead of pointing at the order or the catalog, which
 * the buyer or the seller can still edit afterwards. A mistake is corrected by a
 * cancellation — a second sale with negated quantities, see [reversal] — followed
 * by a new confirmation.
 */
data class Sale(
    val id: String = "",
    val orderId: String,
    /** When the seller confirmed the pickup; the booking date. Epoch milliseconds. */
    val confirmedAt: Long,
    /** The order's pickup date, for reference. Epoch milliseconds. */
    val pickUpDate: Long,
    val lines: List<SaleLine>,
    /** Id of the sale this one cancels; null for an ordinary sale. */
    val reverses: String? = null
) {
    val isReversal: Boolean get() = reverses != null

    /** Sold at the market stall without an app order; [orderId] is then generated. */
    val isWalkIn: Boolean get() = orderId.startsWith(WALK_IN_PREFIX)

    val grossCents: Long get() = lines.sumOf { it.grossCents }
    val netCents: Long get() = lines.sumOf { it.netCents }
    val vatCents: Long get() = lines.sumOf { it.vatCents }

    /**
     * The cancellation of this sale: same lines with negated quantities, so every
     * amount is exactly the negative of the original.
     */
    fun reversal(confirmedAt: Long): Sale {
        require(!isReversal) { "A cancellation cannot itself be cancelled" }
        require(id.isNotEmpty()) { "Only a stored sale can be cancelled" }
        return Sale(
            orderId = orderId,
            confirmedAt = confirmedAt,
            pickUpDate = pickUpDate,
            lines = lines.map { it.copy(quantity = -it.quantity) },
            reverses = id
        )
    }

    companion object {
        const val WALK_IN_PREFIX = "walkin_"

        /**
         * A reference for a walk-in sale, standing in for the order number. It groups
         * the sale and its cancellation in the sale index like an order does.
         */
        fun newWalkInOrderId(now: Long, random: Random = Random.Default): String =
            "$WALK_IN_PREFIX${now}_${random.nextInt(0, 1_000_000).toString().padStart(6, '0')}"
    }
}

/**
 * One article of a [Sale].
 *
 * Amounts are derived per line and rounded to the cent here, once: the export shows
 * these line amounts, and every total is their sum.
 */
data class SaleLine(
    val articleId: String,
    /** BNN article number; empty for articles created by hand. */
    val productId: String,
    val productName: String,
    val unit: String,
    /** The amount actually handed over, in [unit]; negative in a cancellation. */
    val quantity: Double,
    /** Gross price per unit, as the buyer ordered it. */
    val unitPriceCents: Long,
    val taxRate: Double,
    /** Net purchase price per unit; null when the seller never recorded one. */
    val acquirePriceCents: Long? = null
) {
    val grossCents: Long get() = Money.roundHalfAwayFromZero(quantity * unitPriceCents)

    /** Net is split off the gross amount; VAT is the rest, so the two always add up. */
    val netCents: Long get() = Money.roundHalfAwayFromZero(grossCents / (1.0 + taxRate))

    val vatCents: Long get() = grossCents - netCents

    /** Purchase cost of this line, or null when the purchase price is unknown. */
    val acquireCostCents: Long?
        get() = acquirePriceCents?.let { Money.roundHalfAwayFromZero(quantity * it) }
}

/** Net, VAT and gross of all lines at one VAT rate. */
data class VatTotal(
    val taxRate: Double,
    val netCents: Long,
    val vatCents: Long,
    val grossCents: Long
)

/** Totals per VAT rate over any number of sales, cancellations included. */
fun List<Sale>.vatTotals(): List<VatTotal> =
    flatMap { it.lines }
        .groupBy { it.taxRate }
        .map { (rate, lines) ->
            VatTotal(
                taxRate = rate,
                netCents = lines.sumOf { it.netCents },
                vatCents = lines.sumOf { it.vatCents },
                grossCents = lines.sumOf { it.grossCents }
            )
        }
        .sortedBy { it.taxRate }

/**
 * The sale for this order's pickup.
 *
 * @param actualQuantities What was handed over, one entry per line of [Order.articles]
 *   in the same order. A weighed amount replaces the ordered one; 0 means the item was
 *   missing, and the line is left out.
 * @param catalog The seller's articles by id, for the VAT rate, purchase price and
 *   article number. An article no longer in the catalog is booked at the default
 *   rate with an unknown purchase price and no article number.
 */
fun Order.toSale(
    actualQuantities: List<Double>,
    catalog: Map<String, SellerArticle>,
    confirmedAt: Long
): Sale {
    require(actualQuantities.size == articles.size) {
        "One actual quantity per order line: ${articles.size} lines, ${actualQuantities.size} quantities"
    }
    require(actualQuantities.none { it < 0.0 }) { "Quantities handed over cannot be negative" }

    val lines = articles.zip(actualQuantities)
        .filter { (_, quantity) -> quantity > 0.0 }
        .map { (item, quantity) ->
            val sellerArticle = catalog.articleFor(item)
            SaleLine(
                articleId = sellerArticle?.id ?: item.articleKey(),
                // The article number of the catalog article; the order line only
                // knows the database id, which means nothing to a tax advisor.
                productId = sellerArticle?.article?.productId.orEmpty(),
                productName = item.productName,
                unit = item.unit,
                quantity = quantity,
                unitPriceCents = Money.toCents(item.price),
                taxRate = sellerArticle?.article?.taxRate ?: TaxRate.default.rate,
                acquirePriceCents = sellerArticle?.sellerData
                    ?.takeIf { it.hasAcquirePrice }
                    ?.let { Money.toCents(it.acquirePrice) }
            )
        }

    return Sale(
        orderId = id,
        confirmedAt = confirmedAt,
        pickUpDate = pickUpDate,
        lines = lines
    )
}

/**
 * The catalog article an order line refers to, from a map keyed by article id.
 *
 * The buyer app puts the article's database id into [OrderedProduct.productId] and
 * leaves [OrderedProduct.id] empty; [OrderedProduct.id] is only a fallback for
 * lines written some other way.
 */
fun Map<String, SellerArticle>.articleFor(item: OrderedProduct): SellerArticle? =
    this[item.productId] ?: item.id.takeIf { it.isNotEmpty() }?.let { this[it] }

/** The id an order line refers to its article by. */
private fun OrderedProduct.articleKey(): String = productId.takeIf { it.isNotEmpty() && it != "-1" } ?: id

/**
 * The sale currently standing among the sales of one order or walk-in reference:
 * the latest one not cancelled, or null if there is none.
 */
fun List<Sale>.activeSale(): Sale? {
    val cancelled = mapNotNull { it.reverses }.toSet()
    return filter { !it.isReversal && it.id !in cancelled }.maxByOrNull { it.confirmedAt }
}

/** One article sold at the market stall: what, how much, and at which gross price. */
data class WalkInItem(
    val article: SellerArticle,
    val quantity: Double,
    val unitPrice: Double
)

/**
 * A walk-in sale of [items]. VAT rate, purchase price and article number come from
 * the catalog article, as for confirmed orders; the price is the one charged.
 */
fun walkInSale(items: List<WalkInItem>, confirmedAt: Long, orderId: String): Sale {
    require(items.isNotEmpty()) { "A sale without lines books nothing" }
    require(items.all { it.quantity > 0.0 }) { "Quantities must be positive" }
    require(items.all { it.unitPrice > 0.0 }) { "Prices must be positive" }
    require(orderId.startsWith(Sale.WALK_IN_PREFIX)) { "Walk-in sales need a walk-in reference" }
    return Sale(
        orderId = orderId,
        confirmedAt = confirmedAt,
        pickUpDate = confirmedAt,
        lines = items.map { item ->
            val article = item.article.article
            SaleLine(
                articleId = item.article.id,
                productId = article.productId,
                productName = article.productName,
                unit = article.unit,
                quantity = item.quantity,
                unitPriceCents = Money.toCents(item.unitPrice),
                taxRate = article.taxRate,
                acquirePriceCents = item.article.sellerData
                    ?.takeIf { it.hasAcquirePrice }
                    ?.let { Money.toCents(it.acquirePrice) }
            )
        }
    )
}
