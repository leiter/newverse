package com.together.newverse.data.firebase

import com.together.newverse.domain.model.QuantitySource
import com.together.newverse.domain.model.StockLevel
import com.together.newverse.domain.model.StockMovement
import com.together.newverse.domain.model.StockMovementKind
import kotlinx.datetime.TimeZone

/**
 * How the stock ledger is laid out in the Realtime Database. Both nodes are
 * seller-only.
 *
 * ```
 * /stock_movements/{sellerId}/{yyyyMM}/{movementId}   the ledger, by month
 * /stock/{sellerId}/{articleId}                       the level it adds up to
 * ```
 *
 * The ledger is the truth and is append-only; the database rules refuse to change
 * or delete a movement. The level is a cache, so that the stall does not have to
 * read a month of movements to show what is on hand. The two are written in one
 * multi-path update, with `onHand` carried by [LEVEL_ON_HAND] as a server-side
 * increment, so two devices booking at once cannot lose each other's change.
 *
 * A cache can still drift — a movement whose level update never reached the server,
 * say. Nothing here tries to detect that, because the repair is a normal part of the
 * work: a [StockMovementKind.STOCKTAKE] sets the level to what was counted instead
 * of adjusting it, so weighing the shelf fixes the number.
 *
 * Pure functions only — no Firebase types — so all of it is unit-testable. The
 * increment itself is the one thing the repository has to supply.
 */
internal object StockNodes {

    const val MOVEMENTS_ROOT = "stock_movements"
    const val LEVELS_ROOT = "stock"

    /** The level field the repository writes as a server-side increment. */
    const val LEVEL_ON_HAND = "onHand"

    fun monthPath(sellerId: String, month: String) = "$MOVEMENTS_ROOT/$sellerId/$month"

    fun movementPath(sellerId: String, month: String, movementId: String) =
        "${monthPath(sellerId, month)}/$movementId"

    fun levelsPath(sellerId: String) = "$LEVELS_ROOT/$sellerId"

    fun levelPath(sellerId: String, articleId: String) = "${levelsPath(sellerId)}/$articleId"

    fun monthKey(epochMillis: Long, timeZone: TimeZone): String = MonthKeys.of(epochMillis, timeZone)

    fun monthKeys(fromMillis: Long, untilMillis: Long, timeZone: TimeZone): List<String> =
        MonthKeys.range(fromMillis, untilMillis, timeZone)

    // --- writing --------------------------------------------------------------

    /**
     * The update map that appends [movement] under [movementId], without the level.
     *
     * The caller adds the level fields: [levelFields] for everything that does not
     * depend on the previous value, and [LEVEL_ON_HAND] itself, built from
     * [levelChanges]. See [levelOnHandPath].
     */
    fun appendUpdate(
        sellerId: String,
        movementId: String,
        movement: StockMovement,
        timeZone: TimeZone
    ): Map<String, Any?> {
        require(movementId.isNotEmpty()) { "movementId must be assigned before building the update" }
        require(movement.articleId.isNotEmpty()) { "A movement needs the article it belongs to" }
        val month = monthKey(movement.recordedAt, timeZone)
        return mapOf(movementPath(sellerId, month, movementId) to movementToMap(movement))
    }

    /** Where the level's running amount lives, for the increment the repository writes. */
    fun levelOnHandPath(sellerId: String, articleId: String) =
        "${levelPath(sellerId, articleId)}/$LEVEL_ON_HAND"

    /**
     * What a batch of movements does to one article's level.
     *
     * Two shapes, because a stocktake is absolute and everything else is relative.
     */
    sealed interface LevelChange {
        /** Add [delta] to whatever is there — written as a server-side increment. */
        data class Adjust(val delta: Double) : LevelChange

        /** Overwrite with [onHand], because a count said so. */
        data class SetTo(val onHand: Double) : LevelChange
    }

    /**
     * The level change per article for [movements], which may hold several movements
     * for the same article.
     *
     * Aggregated rather than applied one by one, because all of it goes into one
     * multi-path update and a path can only be written once: two movements of the
     * same article would otherwise silently drop one. Within an article the newest
     * stocktake wins and anything recorded after it is added on top, so the order
     * movements are passed in does not matter — only when they happened.
     */
    fun levelChanges(movements: List<StockMovement>): Map<String, LevelChange> =
        movements.groupBy { it.articleId }.mapValues { (_, forArticle) ->
            val ordered = forArticle.sortedBy { it.recordedAt }
            val lastCount = ordered.indexOfLast { it.kind == StockMovementKind.STOCKTAKE }
            if (lastCount < 0) {
                LevelChange.Adjust(ordered.sumOf { it.quantity })
            } else {
                // countedTo is non-null for a stocktake; StockMovement's init enforces it.
                val counted = ordered[lastCount].countedTo ?: 0.0
                val after = ordered.drop(lastCount + 1).sumOf { it.quantity }
                LevelChange.SetTo(counted + after)
            }
        }

    /**
     * The level fields that follow from [movements] alone, keyed by full path, for
     * every article they touch.
     *
     * [LEVEL_ON_HAND] is deliberately absent: see [levelChanges], which the
     * repository turns into either an increment or a plain value. Every other field
     * is written outright, which also means a level node is complete the first time
     * an article moves.
     */
    fun levelFields(sellerId: String, movements: List<StockMovement>): Map<String, Any?> =
        buildMap {
            movements.groupBy { it.articleId }.forEach { (articleId, forArticle) ->
                val path = levelPath(sellerId, articleId)
                val newest = forArticle.maxBy { it.recordedAt }
                put("$path/unit", newest.unit)
                put("$path/lastMovementAt", newest.recordedAt)
                put("$path/lastSource", newest.source.name)
                forArticle
                    .filter { it.kind == StockMovementKind.STOCKTAKE }
                    .maxOfOrNull { it.recordedAt }
                    ?.let { put("$path/lastCountedAt", it) }
            }
        }

    fun movementToMap(movement: StockMovement): Map<String, Any?> = buildMap {
        put("articleId", movement.articleId)
        put("quantity", movement.quantity)
        put("unit", movement.unit)
        put("kind", movement.kind.name)
        put("source", movement.source.name)
        put("recordedAt", movement.recordedAt)
        // Optional fields are left out rather than written empty, so the stored
        // record says what was actually known.
        if (movement.productId.isNotEmpty()) put("productId", movement.productId)
        movement.countedTo?.let { put("countedTo", it) }
        if (movement.note.isNotEmpty()) put("note", movement.note)
        movement.saleId?.let { put("saleId", it) }
    }

    // --- reading --------------------------------------------------------------

    /**
     * Reads a stored movement, or null if it is malformed.
     *
     * A movement that cannot be read is not skipped by the caller: the level is the
     * sum of the ledger, so dropping one would silently misstate what is in storage.
     */
    fun movementFromMap(movementId: String, value: Map<*, *>): StockMovement? {
        val articleId = value["articleId"] as? String ?: return null
        if (articleId.isEmpty()) return null
        val unit = value["unit"] as? String ?: return null
        if (unit.isEmpty()) return null
        val kind = (value["kind"] as? String)?.let { name ->
            StockMovementKind.entries.firstOrNull { it.name == name }
        } ?: return null
        val source = (value["source"] as? String)?.let { name ->
            QuantitySource.entries.firstOrNull { it.name == name }
        } ?: return null
        val countedTo = value.double("countedTo")
        // The same invariant StockMovement's init block enforces; checked here so a
        // malformed record is reported rather than thrown out of a Flow.
        if (kind == StockMovementKind.STOCKTAKE && countedTo == null) return null

        return StockMovement(
            id = movementId,
            articleId = articleId,
            productId = value["productId"] as? String ?: "",
            quantity = value.double("quantity") ?: return null,
            unit = unit,
            kind = kind,
            source = source,
            recordedAt = value.long("recordedAt") ?: return null,
            countedTo = countedTo,
            note = value["note"] as? String ?: "",
            saleId = value["saleId"] as? String
        )
    }

    /** Reads a stored level, or null if it is malformed. */
    fun levelFromMap(articleId: String, value: Map<*, *>): StockLevel? = StockLevel(
        articleId = articleId,
        onHand = value.double(LEVEL_ON_HAND) ?: return null,
        unit = value["unit"] as? String ?: return null,
        lastMovementAt = value.long("lastMovementAt"),
        lastCountedAt = value.long("lastCountedAt"),
        lastSource = (value["lastSource"] as? String)?.let { name ->
            QuantitySource.entries.firstOrNull { it.name == name }
        }
    )

    // The database returns whole numbers as Long, but a Double can come back for
    // values it stored as floating point.
    private fun Map<*, *>.long(key: String): Long? = (this[key] as? Number)?.toLong()
    private fun Map<*, *>.double(key: String): Double? = (this[key] as? Number)?.toDouble()
}
