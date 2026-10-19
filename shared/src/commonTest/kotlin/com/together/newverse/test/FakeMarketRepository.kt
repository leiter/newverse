package com.together.newverse.test

import com.together.newverse.domain.model.Market
import com.together.newverse.domain.repository.MarketRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory markets, keyed by seller the way the node is.
 *
 * [legacy] stands in for a seller whose markets are still in the profile list, so
 * the migration path can be exercised without a database.
 */
class FakeMarketRepository : MarketRepository {

    private val markets = MutableStateFlow<Map<String, Map<String, Market>>>(emptyMap())

    var shouldFailSave: Boolean = false
    var migrateCalls: Int = 0
        private set

    override fun observeMarkets(sellerId: String): Flow<List<Market>> =
        markets.map { all -> all[sellerId].orEmpty().values.filter { it.active } }

    override suspend fun getMarkets(sellerId: String, includeInactive: Boolean): Result<List<Market>> {
        val all = markets.value[sellerId].orEmpty().values.toList()
        return Result.success(if (includeInactive) all else all.filter { it.active })
    }

    override suspend fun saveMarket(sellerId: String, market: Market): Result<Unit> {
        if (shouldFailSave) return Result.failure(Exception("save failed"))
        markets.value = markets.value.toMutableMap().apply {
            put(sellerId, (this[sellerId].orEmpty() + (market.id to market)))
        }
        return Result.success(Unit)
    }

    override suspend fun deactivateMarket(sellerId: String, marketId: String): Result<Unit> {
        val existing = markets.value[sellerId]?.get(marketId) ?: return Result.success(Unit)
        return saveMarket(sellerId, existing.copy(active = false))
    }

    override suspend fun migrateLegacyMarkets(sellerId: String, legacy: List<Market>): Result<Int> {
        migrateCalls++
        val present = markets.value[sellerId].orEmpty().keys
        val missing = legacy.filter { it.id.isNotEmpty() && it.id !in present }
        missing.forEach { saveMarket(sellerId, it) }
        return Result.success(missing.size)
    }

    /** Seed the node directly, bypassing the migration path. */
    fun seed(sellerId: String, vararg seeded: Market) {
        markets.value = markets.value.toMutableMap().apply {
            put(sellerId, seeded.associateBy { it.id })
        }
    }

    fun stored(sellerId: String): List<Market> = markets.value[sellerId].orEmpty().values.toList()

    fun reset() {
        markets.value = emptyMap()
        shouldFailSave = false
        migrateCalls = 0
    }
}
