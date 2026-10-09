package com.together.newverse.data.repository

import com.together.newverse.data.firebase.StockNodes
import com.together.newverse.domain.model.StockLevel
import com.together.newverse.domain.model.StockMovement
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.StockRepository
import com.together.newverse.util.Log
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone

/**
 * GitLive implementation of [StockRepository]. Layout and mapping come from
 * [StockNodes].
 *
 * A movement and the level it changes are written in one multi-path update, so a
 * level never reflects a movement the ledger does not have. `onHand` goes in as
 * [ServerValue.increment] rather than a value read and written back, which is what
 * makes two devices booking at the same stall safe: the server applies both deltas.
 * A stocktake is the exception — it sets `onHand` to what was counted, because that
 * is the point of counting, and it doubles as the repair when a level has drifted.
 *
 * @param timeZone The seller's time zone, which decides a movement's month node.
 */
class GitLiveStockRepository(
    private val authRepository: AuthRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : StockRepository {

    private companion object {
        private const val TAG = "StockRepo"
    }

    private val database = Firebase.database

    override fun observeLevels(sellerId: String): Flow<Map<String, StockLevel>> {
        require(sellerId.isNotEmpty()) { "sellerId must not be empty" }

        return database.reference(StockNodes.levelsPath(sellerId)).valueEvents.map { snapshot ->
            snapshot.children.mapNotNull { child ->
                val articleId = child.key ?: return@mapNotNull null
                // A level is a cache, so an unreadable one is dropped rather than
                // failed on: the ledger behind it is still intact, and a stocktake
                // will rewrite it. The ledger reads below do fail instead.
                val value = child.value as? Map<*, *> ?: return@mapNotNull null
                val level = StockNodes.levelFromMap(articleId, value)
                if (level == null) Log.w(TAG) { "observeLevels: level for $articleId is unreadable" }
                level?.let { articleId to it }
            }.toMap()
        }
    }

    override fun observeMovements(
        sellerId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Flow<List<StockMovement>> {
        require(sellerId.isNotEmpty()) { "sellerId must not be empty" }

        val months = StockNodes.monthKeys(fromMillis, untilMillis, timeZone).map { month ->
            database.reference(StockNodes.monthPath(sellerId, month)).valueEvents.map { snapshot ->
                snapshot.children.map { child ->
                    val id = child.key ?: error("Movement without key in month $month")
                    // The level is the sum of the ledger, so a movement that cannot be
                    // read fails the whole read: skipping it would misstate storage.
                    (child.value as? Map<*, *>)?.let { StockNodes.movementFromMap(id, it) }
                        ?: error("Movement $id in month $month is unreadable")
                }
            }
        }
        if (months.isEmpty()) return flowOf(emptyList())

        return combine(months) { perMonth ->
            perMonth.flatMap { it }
                .filter { it.recordedAt in fromMillis until untilMillis }
                .sortedByDescending { it.recordedAt }
        }
    }

    override suspend fun movementsForArticle(
        sellerId: String,
        articleId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Result<List<StockMovement>> {
        return try {
            val months = StockNodes.monthKeys(fromMillis, untilMillis, timeZone)
            val movements = months.flatMap { month ->
                val snapshot = database.reference(StockNodes.monthPath(sellerId, month))
                    .valueEvents.first()
                snapshot.children.mapNotNull { child ->
                    val id = child.key ?: return@mapNotNull null
                    val value = child.value as? Map<*, *> ?: return@mapNotNull null
                    StockNodes.movementFromMap(id, value)
                        ?: error("Movement $id in month $month is unreadable")
                }
            }
            Result.success(
                movements
                    .filter { it.articleId == articleId && it.recordedAt in fromMillis until untilMillis }
                    .sortedByDescending { it.recordedAt }
            )
        } catch (e: Exception) {
            Log.e(TAG) { "movementsForArticle: Error - ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun recordMovement(
        sellerId: String,
        movement: StockMovement
    ): Result<StockMovement> =
        recordMovements(sellerId, listOf(movement)).map { it.single() }

    override suspend fun recordMovements(
        sellerId: String,
        movements: List<StockMovement>
    ): Result<List<StockMovement>> {
        if (movements.isEmpty()) return Result.success(emptyList())
        return try {
            if (authRepository.getCurrentUserId() == null) {
                return Result.failure(Exception("User not authenticated"))
            }

            val update = mutableMapOf<String, Any?>()
            val recorded = movements.map { movement ->
                val month = StockNodes.monthKey(movement.recordedAt, timeZone)
                val id = database.reference(StockNodes.monthPath(sellerId, month)).push().key
                    ?: throw IllegalStateException("Failed to generate movement ID")
                update += StockNodes.appendUpdate(sellerId, id, movement, timeZone)
                movement.copy(id = id)
            }

            // Levels are aggregated over the whole batch: one path can be written
            // once, so two movements of the same article have to arrive as one change.
            update += StockNodes.levelFields(sellerId, movements)
            StockNodes.levelChanges(movements).forEach { (articleId, change) ->
                update[StockNodes.levelOnHandPath(sellerId, articleId)] = when (change) {
                    is StockNodes.LevelChange.SetTo -> change.onHand
                    is StockNodes.LevelChange.Adjust -> ServerValue.increment(change.delta)
                }
            }

            database.reference().updateChildren(update)

            Log.d(TAG) {
                "recordMovements: ${recorded.size} movement(s) — " +
                    recorded.joinToString { "${it.kind} ${it.quantity} ${it.unit} of ${it.articleId}" }
            }
            Result.success(recorded)
        } catch (e: Exception) {
            Log.e(TAG) { "recordMovements: Error - ${e.message}" }
            Result.failure(e)
        }
    }
}
