package com.together.newverse.data.repository

import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.SellerContact
import com.together.newverse.domain.repository.MarketRepository
import com.together.newverse.util.Log
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Markets at `markets/{sellerId}/{marketId}`.
 *
 * Reads tolerate both shapes: the node when it exists, and otherwise the legacy
 * `seller_profile/{sellerId}/markets` list. Writes only ever go to the node, so a
 * seller who edits anything moves themselves forward. The same two-step as
 * `storedOrderOrigin`, and it comes out when the migration script has been run
 * everywhere — see `doc/market-pricing-and-access-design.md` §1.
 */
class GitLiveMarketRepository : MarketRepository {

    private companion object {
        private const val TAG = "MarketRepo"
    }

    private val database = Firebase.database
    private val marketsRef = database.reference("markets")
    private val sellersRef = database.reference("seller_profile")

    override fun observeMarkets(sellerId: String): Flow<List<Market>> {
        if (sellerId.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return marketsRef.child(sellerId).valueEvents
            .map { snapshot -> parseMarkets(snapshot.value).filter { it.active } }
            .catch { e ->
                Log.w(TAG) { "observeMarkets: falling back to the profile list - ${e.message}" }
                emit(legacyMarkets(sellerId).filter { it.active })
            }
    }

    override suspend fun getMarkets(sellerId: String, includeInactive: Boolean): Result<List<Market>> {
        return try {
            if (sellerId.isEmpty()) return Result.success(emptyList())

            val fromNode = parseMarkets(marketsRef.child(sellerId).valueEvents.first().value)
            // An absent node means the seller has not been migrated, not that they
            // have no markets: an empty result there would silently hide their stalls.
            val markets = fromNode.ifEmpty { legacyMarkets(sellerId) }

            Result.success(if (includeInactive) markets else markets.filter { it.active })
        } catch (e: Exception) {
            Log.e(TAG) { "getMarkets: ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun saveMarket(sellerId: String, market: Market): Result<Unit> {
        return try {
            require(market.id.isNotEmpty()) { "a market needs an id before it can be saved" }
            marketsRef.child(sellerId).child(market.id).setValue(marketToMap(market))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG) { "saveMarket: ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun deactivateMarket(sellerId: String, marketId: String): Result<Unit> {
        return try {
            marketsRef.child(sellerId).child(marketId).child("active").setValue(false)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG) { "deactivateMarket: ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun migrateLegacyMarkets(sellerId: String, legacy: List<Market>): Result<Int> {
        return try {
            if (legacy.isEmpty()) return Result.success(0)

            val existing = parseMarkets(marketsRef.child(sellerId).valueEvents.first().value)
                .map { it.id }
                .toSet()

            // Add-only. A market already in the node was written by the app or the
            // migration script, and is newer than whatever the profile list holds.
            val missing = legacy.filter { it.id.isNotEmpty() && it.id !in existing }
            missing.forEach { marketsRef.child(sellerId).child(it.id).setValue(marketToMap(it)) }

            if (missing.isNotEmpty()) {
                Log.d(TAG) { "migrateLegacyMarkets: moved ${missing.size} market(s) for $sellerId" }
            }
            Result.success(missing.size)
        } catch (e: Exception) {
            Log.e(TAG) { "migrateLegacyMarkets: ${e.message}" }
            Result.failure(e)
        }
    }

    private suspend fun legacyMarkets(sellerId: String): List<Market> = try {
        parseMarkets(sellersRef.child(sellerId).child("markets").valueEvents.first().value)
    } catch (e: Exception) {
        Log.w(TAG) { "legacyMarkets: $sellerId unreadable - ${e.message}" }
        emptyList()
    }

    /** Accepts both shapes: the keyed node, and the legacy list. */
    private fun parseMarkets(raw: Any?): List<Market> {
        val rows: List<Any?> = when (raw) {
            is List<*> -> raw
            is Map<*, *> -> raw.values.toList()
            else -> return emptyList()
        }
        return rows.mapNotNull { row ->
            val fields = row as? Map<*, *> ?: return@mapNotNull null
            val id = fields["id"] as? String ?: return@mapNotNull null
            if (id.isEmpty()) return@mapNotNull null
            Market(
                id = id,
                name = fields["name"] as? String ?: "",
                street = fields["street"] as? String ?: "",
                houseNumber = fields["houseNumber"] as? String ?: "",
                city = fields["city"] as? String ?: "",
                zipCode = fields["zipCode"] as? String ?: "",
                dayOfWeek = fields["dayOfWeek"] as? String ?: "",
                begin = fields["begin"] as? String ?: "",
                end = fields["end"] as? String ?: "",
                dayIndex = (fields["dayIndex"] as? Number)?.toInt() ?: -1,
                contacts = parseContacts(fields["contacts"]),
                // Absent means a record written before soft deletion existed, which
                // was by definition a live market.
                active = fields["active"] as? Boolean ?: true
            )
        }
    }

    private fun parseContacts(raw: Any?): List<SellerContact> {
        val rows = when (raw) {
            is List<*> -> raw
            is Map<*, *> -> raw.values.toList()
            else -> return emptyList()
        }
        return rows.mapNotNull { row ->
            val fields = row as? Map<*, *> ?: return@mapNotNull null
            val channel = com.together.newverse.domain.model.ContactChannel
                .fromStored(fields["channel"] as? String) ?: return@mapNotNull null
            val value = fields["value"] as? String ?: return@mapNotNull null
            if (value.isBlank()) return@mapNotNull null
            SellerContact(channel, value, fields["label"] as? String ?: "")
        }
    }

    private fun marketToMap(market: Market): Map<String, Any?> = mapOf(
        "id" to market.id,
        "name" to market.name,
        "street" to market.street,
        "houseNumber" to market.houseNumber,
        "city" to market.city,
        "zipCode" to market.zipCode,
        "dayOfWeek" to market.dayOfWeek,
        "begin" to market.begin,
        "end" to market.end,
        "dayIndex" to market.dayIndex,
        "active" to market.active,
        "contacts" to market.contacts.filter { it.isUsable }.map { contact ->
            mapOf(
                "channel" to contact.channel.name,
                "value" to contact.value,
                "label" to contact.label
            )
        }
    )
}
