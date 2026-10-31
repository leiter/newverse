package com.together.newverse.data.repository

import com.together.newverse.domain.model.ActionKind
import com.together.newverse.domain.model.SellerInboxMessage
import com.together.newverse.domain.model.SellerUiAction
import com.together.newverse.domain.repository.SellerInboxRepository
import com.together.newverse.util.Log
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
 * Messages buyers send through a published box, at `seller_inbox/{sellerId}/{messageId}`.
 *
 * Writes report their failure, unlike `GitLiveSellerUiActionRepository`'s reads: the
 * buyer has just pressed send and is owed an answer either way. Reads never throw,
 * for that repository's reason — an empty inbox and an unreadable one are the same
 * thing to a screen, and neither is worth failing on.
 */
class GitLiveSellerInboxRepository : SellerInboxRepository {

    private companion object {
        private const val TAG = "SellerInboxRepo"
    }

    private val database = Firebase.database
    private val inboxRef = database.reference("seller_inbox")

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun send(message: SellerInboxMessage): Result<SellerInboxMessage> {
        return try {
            if (message.sellerId.isEmpty()) {
                return Result.failure(IllegalArgumentException("a message needs a seller to go to"))
            }
            if (!message.isSendable) {
                return Result.failure(IllegalArgumentException("a message needs text and a sender"))
            }

            val now = Clock.System.now()
            val stored = message.copy(
                id = message.id.ifEmpty { Uuid.random().toString() },
                // Capped here as well as in the field, so a caller that did not
                // truncate cannot have its whole row refused by the rules instead.
                text = message.text.trim().take(SellerInboxMessage.MAX_TEXT_LENGTH),
                buyerName = message.buyerName.trim().take(SellerInboxMessage.MAX_NAME_LENGTH),
                prompt = message.prompt.trim().take(SellerUiAction.MAX_VALUE_LENGTH),
                timestamp = now.toEpochMilliseconds(),
                timestampIso = now.toString(),
            )

            // One map in one write. The rules make this create-only for a buyer, so a
            // field-by-field write would be refused for not being a complete record.
            inboxRef.child(stored.sellerId).child(stored.id).setValue(messageToMap(stored))

            Log.d(TAG) { "send: ${stored.id} from ${stored.buyerId} to ${stored.sellerId}" }
            Result.success(stored)
        } catch (e: Exception) {
            Log.e(TAG) { "send: ${e.message}" }
            Result.failure(e)
        }
    }

    override fun observeMessages(sellerId: String): Flow<List<SellerInboxMessage>> {
        if (sellerId.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return inboxRef.child(sellerId).valueEvents
            .map { snapshot ->
                snapshot.children
                    .mapNotNull { parseMessage(it) }
                    // Newest first: an inbox is read from the top, and the seller is
                    // looking for what came in since they last looked.
                    .sortedByDescending { it.timestamp }
            }
            .catch { e ->
                Log.w(TAG) { "observeMessages: $sellerId unreadable - ${e.message}" }
                emit(emptyList())
            }
    }

    override suspend fun deleteMessage(sellerId: String, messageId: String): Result<Unit> {
        return try {
            require(sellerId.isNotEmpty() && messageId.isNotEmpty()) {
                "a message needs a seller and an id before it can be removed"
            }
            inboxRef.child(sellerId).child(messageId).removeValue()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG) { "deleteMessage: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Read one stored message, skipping anything this app cannot show.
     *
     * A row with no text is nothing to read, and one whose [ActionKind] this version
     * does not know came from a box a newer app published — the same degradation
     * `parseActions` applies, so an older seller app shows what it understands rather
     * than guessing at the rest.
     */
    private fun parseMessage(snapshot: DataSnapshot): SellerInboxMessage? {
        return try {
            val data = snapshot.value as? Map<*, *> ?: return null
            val text = (data["text"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            // Absent reads as the one kind that existed before the field did; an
            // unknown *name* skips the row.
            val kind = if (data["requestKind"] == null) ActionKind.QUICK_MESSAGE
            else ActionKind.fromStored(data["requestKind"] as? String) ?: return null

            SellerInboxMessage(
                id = data["id"] as? String ?: snapshot.key ?: return null,
                sellerId = data["sellerId"] as? String ?: "",
                buyerId = data["buyerId"] as? String ?: "",
                buyerName = data["buyerName"] as? String ?: "",
                requestKind = kind,
                requestId = data["requestId"] as? String ?: "",
                prompt = data["prompt"] as? String ?: "",
                text = text,
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                timestampIso = data["timestampIso"] as? String ?: "",
            )
        } catch (e: Exception) {
            Log.w(TAG) { "parseMessage: skipping malformed row - ${e.message}" }
            null
        }
    }

    private fun messageToMap(message: SellerInboxMessage): Map<String, Any?> = mapOf(
        "id" to message.id,
        "sellerId" to message.sellerId,
        "buyerId" to message.buyerId,
        "buyerName" to message.buyerName,
        "requestKind" to message.requestKind.name,
        "requestId" to message.requestId,
        "prompt" to message.prompt,
        "text" to message.text,
        "timestamp" to message.timestamp,
        "timestampIso" to message.timestampIso,
    )
}
