package com.together.newverse.test

import com.together.newverse.domain.model.StockLevel
import com.together.newverse.domain.model.StockMovement
import com.together.newverse.domain.model.toLevels
import com.together.newverse.domain.repository.StockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake implementation of [StockRepository] for testing. Like the real one it only
 * appends: there is no way to change or remove a recorded movement, and the levels
 * are derived from the ledger rather than stored beside it.
 */
class FakeStockRepository : StockRepository {

    private val _movements = MutableStateFlow<List<StockMovement>>(emptyList())
    val movements: List<StockMovement> get() = _movements.value

    var shouldFailRecord = false
    var failureMessage = "Test error"

    private var nextId = 1

    /** Seed already-recorded movements; ids are kept if set. */
    fun setMovements(movements: List<StockMovement>) {
        _movements.value = movements.map {
            if (it.id.isEmpty()) it.copy(id = "fake-movement-${nextId++}") else it
        }
    }

    fun reset() {
        _movements.value = emptyList()
        shouldFailRecord = false
        failureMessage = "Test error"
        nextId = 1
    }

    override fun observeLevels(sellerId: String): Flow<Map<String, StockLevel>> =
        _movements.map { it.toLevels() }

    override fun observeMovements(
        sellerId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Flow<List<StockMovement>> = _movements.map { all ->
        all.filter { it.recordedAt in fromMillis until untilMillis }.sortedByDescending { it.recordedAt }
    }

    override suspend fun movementsForArticle(
        sellerId: String,
        articleId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Result<List<StockMovement>> {
        if (shouldFailRecord) return Result.failure(Exception(failureMessage))
        return Result.success(
            _movements.value
                .filter { it.articleId == articleId && it.recordedAt in fromMillis until untilMillis }
                .sortedByDescending { it.recordedAt }
        )
    }

    override suspend fun recordMovement(
        sellerId: String,
        movement: StockMovement
    ): Result<StockMovement> = recordMovements(sellerId, listOf(movement)).map { it.single() }

    override suspend fun recordMovements(
        sellerId: String,
        movements: List<StockMovement>
    ): Result<List<StockMovement>> {
        if (shouldFailRecord) return Result.failure(Exception(failureMessage))
        val stored = movements.map { it.copy(id = "fake-movement-${nextId++}") }
        _movements.value = _movements.value + stored
        return Result.success(stored)
    }
}
