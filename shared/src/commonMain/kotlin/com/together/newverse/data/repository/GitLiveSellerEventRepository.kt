package com.together.newverse.data.repository

import com.together.newverse.domain.model.SellerEvent
import com.together.newverse.domain.model.SellerEventType
import com.together.newverse.domain.repository.SellerEventRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.DataSnapshot
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Firebase Realtime Database implementation of [SellerEventRepository].
 *
 * Firebase structure: `seller_events/{sellerId}/{eventId}`
 *
 * The database rules make this append-only for buyers: an event can be created
 * but never modified or removed once written.
 */
class GitLiveSellerEventRepository : SellerEventRepository {

    private val database = Firebase.database
    private val eventsRef = database.reference("seller_events")

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun logEvent(
        sellerId: String,
        type: SellerEventType,
        buyerId: String,
        firebaseUserId: String,
        buyerUUID: String,
        buyerName: String,
        buyerEmail: String,
        cancelledOrderCount: Int,
        details: String
    ): Result<SellerEvent> {
        // Book keeping must never break the operation it records, so every
        // failure is captured and returned instead of thrown.
        return try {
            if (sellerId.isEmpty()) {
                return Result.failure(IllegalArgumentException("Cannot log event without a sellerId"))
            }

            val now = Clock.System.now()
            val event = SellerEvent(
                id = Uuid.random().toString(),
                sellerId = sellerId,
                type = type,
                buyerId = buyerId,
                firebaseUserId = firebaseUserId,
                buyerUUID = buyerUUID,
                buyerName = buyerName,
                buyerEmail = buyerEmail,
                timestamp = now.toEpochMilliseconds(),
                timestampIso = now.toString(),
                cancelledOrderCount = cancelledOrderCount,
                details = details
            )

            // Written as a single map so the append-only rule sees the complete
            // record in one shot; field-by-field writes would trip `!data.exists()`.
            eventsRef.child(sellerId).child(event.id).setValue(eventToMap(event))

            println("📒 GitLiveSellerEventRepository.logEvent: ${type.name} for buyer $buyerId → seller $sellerId")
            Result.success(event)
        } catch (e: Exception) {
            println("⚠️ GitLiveSellerEventRepository.logEvent: Failed - ${e.message}")
            Result.failure(e)
        }
    }

    private fun eventToMap(event: SellerEvent): Map<String, Any?> {
        return mapOf(
            "id" to event.id,
            "sellerId" to event.sellerId,
            "type" to event.type.name,
            "buyerId" to event.buyerId,
            "firebaseUserId" to event.firebaseUserId,
            "buyerUUID" to event.buyerUUID,
            "buyerName" to event.buyerName,
            "buyerEmail" to event.buyerEmail,
            "timestamp" to event.timestamp,
            "timestampIso" to event.timestampIso,
            "cancelledOrderCount" to event.cancelledOrderCount,
            "details" to event.details
        )
    }

    override fun observeEvents(sellerId: String, limit: Int): Flow<List<SellerEvent>> {
        return eventsRef.child(sellerId).valueEvents
            .map { snapshot ->
                snapshot.children
                    .mapNotNull { parseEvent(it) }
                    .sortedByDescending { it.timestamp }
                    .take(limit)
            }
            .catch { e ->
                println("⚠️ GitLiveSellerEventRepository.observeEvents: Failed - ${e.message}")
                emit(emptyList())
            }
    }

    private fun parseEvent(snapshot: DataSnapshot): SellerEvent? {
        return try {
            val data = snapshot.value as? Map<*, *> ?: return null
            val typeName = data["type"] as? String ?: return null
            val type = SellerEventType.entries.firstOrNull { it.name == typeName } ?: return null

            SellerEvent(
                id = data["id"] as? String ?: snapshot.key ?: "",
                sellerId = data["sellerId"] as? String ?: "",
                type = type,
                buyerId = data["buyerId"] as? String ?: "",
                firebaseUserId = data["firebaseUserId"] as? String ?: "",
                buyerUUID = data["buyerUUID"] as? String ?: "",
                buyerName = data["buyerName"] as? String ?: "",
                buyerEmail = data["buyerEmail"] as? String ?: "",
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                timestampIso = data["timestampIso"] as? String ?: "",
                cancelledOrderCount = (data["cancelledOrderCount"] as? Number)?.toInt() ?: 0,
                details = data["details"] as? String ?: ""
            )
        } catch (e: Exception) {
            println("⚠️ GitLiveSellerEventRepository.parseEvent: Skipping malformed event - ${e.message}")
            null
        }
    }
}
