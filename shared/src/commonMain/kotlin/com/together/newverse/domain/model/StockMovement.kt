package com.together.newverse.domain.model

/**
 * Why an article's stock changed.
 *
 * The kind decides how a movement is read, not how it is applied: every movement
 * carries a signed [StockMovement.quantity], and the level is their sum. A kind
 * exists so the seller can see where goods went, and so loss can be told apart
 * from sales in the books.
 */
enum class StockMovementKind {
    /** Goods arrived — a delivery, a harvest, anything added to storage. */
    INTAKE,

    /**
     * A count that replaces the running level rather than adjusting it: the seller
     * weighed or counted what is actually there. Stored as the difference, so the
     * sum still gives the level, with [StockMovement.countedTo] holding what was
     * found.
     */
    STOCKTAKE,

    /** Sold and handed over. Booked alongside the [Sale], never instead of it. */
    SALE,

    /** Gone without being sold — spoiled, damaged, given away, miscounted earlier. */
    LOSS
}

/**
 * One change to one article's stock, as it goes into the ledger.
 *
 * Movements are only ever added. A level is never written directly; it is the sum of
 * the movements before it, which is what lets the seller see how a number came about
 * and keeps two devices booking at once from overwriting each other. A mistake is
 * corrected by a further movement, usually a [StockMovementKind.STOCKTAKE].
 *
 * [source] records whether the amount was weighed, typed or merely ordered, so a
 * level built mostly from typed values can be told from one built on the scale.
 */
data class StockMovement(
    val id: String = "",
    val articleId: String,
    /** BNN article number where there is one; empty for articles created by hand. */
    val productId: String = "",
    /** Signed amount in [unit]: positive adds to storage, negative takes away. */
    val quantity: Double,
    /** The article's unit at the time of the movement, as [Article.unit] spells it. */
    val unit: String,
    val kind: StockMovementKind,
    val source: QuantitySource,
    /** Epoch milliseconds the movement was recorded. */
    val recordedAt: Long,
    /**
     * For a [StockMovementKind.STOCKTAKE], the level that was actually found; null
     * for every other kind. Kept because [quantity] only holds the correction, and
     * the found value is what the seller will want to see again.
     */
    val countedTo: Double? = null,
    /** What the seller typed about it; empty when nothing was said. */
    val note: String = "",
    /** The sale this movement belongs to, for a [StockMovementKind.SALE]. */
    val saleId: String? = null
) {
    init {
        require(unit.isNotEmpty()) { "A movement without a unit cannot be read back" }
        require(kind != StockMovementKind.STOCKTAKE || countedTo != null) {
            "A stocktake records what was found"
        }
    }

    companion object {

        /** Goods arriving in storage. */
        fun intake(
            articleId: String,
            amount: MeasuredQuantity,
            recordedAt: Long,
            productId: String = "",
            note: String = ""
        ): StockMovement = StockMovement(
            articleId = articleId,
            productId = productId,
            quantity = amount.value,
            unit = amount.unit,
            kind = StockMovementKind.INTAKE,
            source = amount.source,
            recordedAt = amount.measuredAt ?: recordedAt,
            note = note
        )

        /**
         * A count that sets the level to what was found.
         *
         * @param previousOnHand the level the ledger showed before the count; the
         *   movement stores the difference, so the sum of movements becomes [found].
         */
        fun stocktake(
            articleId: String,
            found: MeasuredQuantity,
            previousOnHand: Double,
            recordedAt: Long,
            productId: String = "",
            note: String = ""
        ): StockMovement = StockMovement(
            articleId = articleId,
            productId = productId,
            quantity = ProductPricing.roundQuantity(found.value - previousOnHand),
            unit = found.unit,
            kind = StockMovementKind.STOCKTAKE,
            source = found.source,
            recordedAt = found.measuredAt ?: recordedAt,
            countedTo = found.value,
            note = note
        )

        /** Goods leaving storage without a sale. */
        fun loss(
            articleId: String,
            amount: MeasuredQuantity,
            recordedAt: Long,
            productId: String = "",
            note: String = ""
        ): StockMovement = StockMovement(
            articleId = articleId,
            productId = productId,
            quantity = -amount.value,
            unit = amount.unit,
            kind = StockMovementKind.LOSS,
            source = amount.source,
            recordedAt = amount.measuredAt ?: recordedAt,
            note = note
        )

        /**
         * The movements a booked sale takes out of storage, one per line.
         *
         * A cancellation has negative line quantities, so its movements add back —
         * no separate case is needed. Lines for articles the catalog no longer knows
         * are kept: the goods still left storage.
         */
        fun forSale(sale: Sale): List<StockMovement> = sale.lines.map { line ->
            StockMovement(
                articleId = line.articleId,
                productId = line.productId,
                quantity = -line.quantity,
                unit = line.unit,
                kind = StockMovementKind.SALE,
                source = QuantitySource.ORDERED,
                recordedAt = sale.confirmedAt,
                saleId = sale.id.takeIf { it.isNotEmpty() }
            )
        }
    }
}

/**
 * What is in storage for one article right now.
 *
 * Derived from the movements, never written on its own. [source] and [lastCountedAt]
 * are what make it judgeable: a level last confirmed by a weighing three days ago is
 * worth more than one that has only been added up.
 */
data class StockLevel(
    val articleId: String,
    val onHand: Double,
    val unit: String,
    /** Epoch milliseconds of the newest movement, or null when there is none. */
    val lastMovementAt: Long? = null,
    /** Epoch milliseconds of the newest [StockMovementKind.STOCKTAKE]. */
    val lastCountedAt: Long? = null,
    /** How the newest movement arrived, for judging how firm the number is. */
    val lastSource: QuantitySource? = null
) {
    val isEmpty: Boolean get() = onHand <= 0.0

    /** Whether a count has ever confirmed this level. */
    val wasEverCounted: Boolean get() = lastCountedAt != null
}

/**
 * The level each article's movements add up to.
 *
 * Movements for one article must share a unit; if the seller changed an article's
 * unit, the newest movement's unit wins and the older amounts are summed as they
 * stand, which is wrong but visible. Changing a unit with stock on hand should be
 * prevented in the UI rather than guessed at here.
 */
fun List<StockMovement>.toLevels(): Map<String, StockLevel> =
    groupBy { it.articleId }.mapValues { (articleId, movements) ->
        val newest = movements.maxByOrNull { it.recordedAt }
        StockLevel(
            articleId = articleId,
            onHand = ProductPricing.roundQuantity(movements.sumOf { it.quantity }),
            unit = newest?.unit.orEmpty(),
            lastMovementAt = newest?.recordedAt,
            lastCountedAt = movements
                .filter { it.kind == StockMovementKind.STOCKTAKE }
                .maxOfOrNull { it.recordedAt },
            lastSource = newest?.source
        )
    }

/** The level one article's movements add up to, or null when it has none. */
fun List<StockMovement>.levelOf(articleId: String): StockLevel? =
    toLevels()[articleId]
