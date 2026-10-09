package com.together.newverse.domain.repository

import com.together.newverse.domain.model.StockLevel
import com.together.newverse.domain.model.StockMovement
import kotlinx.coroutines.flow.Flow

/**
 * The seller's stock ledger. Movements are only ever added: a recorded movement
 * cannot be changed or deleted, a mistake is corrected by recording a further one
 * (see [StockMovement]).
 *
 * Levels are derived from the movements. They are observed separately from the
 * ledger because the stall needs the current number on every catalog screen, while
 * the movements behind it are only read when the seller asks how a number came
 * about.
 */
interface StockRepository {

    /**
     * What is in storage for every article that has ever had a movement, keyed by
     * article id. Re-emits when a movement is recorded.
     */
    fun observeLevels(sellerId: String): Flow<Map<String, StockLevel>>

    /**
     * Movements recorded in the period [fromMillis, untilMillis), newest first.
     * Re-emits when a movement in the period is added.
     */
    fun observeMovements(sellerId: String, fromMillis: Long, untilMillis: Long): Flow<List<StockMovement>>

    /**
     * Movements recorded for one article in the period [fromMillis, untilMillis),
     * newest first — how a level came about.
     *
     * Bounded by a period on purpose. The ledger is keyed by month and has no
     * per-article index, so "every movement ever" would mean scanning every month
     * node that exists. The question this answers is why the number is what it is
     * today, which recent history covers; a second index node to serve the
     * unbounded version would double the write path for no one's benefit.
     */
    suspend fun movementsForArticle(
        sellerId: String,
        articleId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Result<List<StockMovement>>

    /** Records one movement and returns it with its assigned id. */
    suspend fun recordMovement(sellerId: String, movement: StockMovement): Result<StockMovement>

    /**
     * Records several movements as one unit, for a sale's lines or a stocktake over
     * many articles. Either all of them land or none do.
     */
    suspend fun recordMovements(sellerId: String, movements: List<StockMovement>): Result<List<StockMovement>>
}
