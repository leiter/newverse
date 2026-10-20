package com.together.newverse.domain.repository

import com.together.newverse.domain.model.Market
import kotlinx.coroutines.flow.Flow

/**
 * A seller's markets, as their own node at `markets/{sellerId}/{marketId}`.
 *
 * They used to be a list on the seller profile, which could carry nothing of its own:
 * rules, pricing and eligibility all need somewhere to hang off a single market, and a
 * list index is not a name. See `doc/guides/market-pricing-and-access-design.md` §1.
 *
 * Reads fall back to that legacy list while both shapes exist, so a seller whose data
 * has not been migrated yet still sees their markets.
 */
interface MarketRepository {
    /** Live markets, newest shape first and the profile list as fallback. */
    fun observeMarkets(sellerId: String): Flow<List<Market>>

    /** One read, same fallback. Active markets only unless [includeInactive]. */
    suspend fun getMarkets(sellerId: String, includeInactive: Boolean = false): Result<List<Market>>

    /** Create or replace one market. */
    suspend fun saveMarket(sellerId: String, market: Market): Result<Unit>

    /**
     * Stop offering a market without deleting it.
     *
     * Orders reference a market by id, so removing the row would leave
     * `Order.marketId` dangling and historical orders unreadable.
     */
    suspend fun deactivateMarket(sellerId: String, marketId: String): Result<Unit>

    /**
     * Copy the seller's legacy profile list into the node, skipping any id already
     * there. Returns how many were written.
     *
     * The migration script does this for existing data; this covers a seller whose
     * app writes a market before the script has run against their project.
     */
    suspend fun migrateLegacyMarkets(sellerId: String, legacy: List<Market>): Result<Int>
}
