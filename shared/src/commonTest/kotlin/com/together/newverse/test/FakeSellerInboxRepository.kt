package com.together.newverse.test

import com.together.newverse.domain.model.SellerInboxMessage
import com.together.newverse.domain.repository.SellerInboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory seller inbox.
 *
 * Mints an id and a timestamp the way
 * [com.together.newverse.data.repository.GitLiveSellerInboxRepository] does, so a test
 * can assert on what a sent message *became* rather than only that send was called —
 * the snapshotted question and the sender are the point of the record, and a fake that
 * dropped them would let that break unnoticed.
 */
class FakeSellerInboxRepository : SellerInboxRepository {

    private val messages = MutableStateFlow<Map<String, List<SellerInboxMessage>>>(emptyMap())

    /** Every message sent through this fake, oldest first. */
    val sent: List<SellerInboxMessage> get() = messages.value.values.flatten()

    /** What a seller has already received. */
    fun seed(sellerId: String, vararg received: SellerInboxMessage) {
        messages.value = messages.value + (sellerId to received.toList())
    }

    var shouldFailSend: Boolean = false

    fun reset() {
        messages.value = emptyMap()
        shouldFailSend = false
    }

    override suspend fun send(message: SellerInboxMessage): Result<SellerInboxMessage> {
        if (shouldFailSend) return Result.failure(Exception("Test error"))
        if (!message.isSendable) {
            return Result.failure(IllegalArgumentException("a message needs text and a sender"))
        }
        val existing = messages.value[message.sellerId].orEmpty()
        val stored = message.copy(
            id = message.id.ifEmpty { "msg_${existing.size + 1}" },
            text = message.text.trim().take(SellerInboxMessage.MAX_TEXT_LENGTH),
            timestamp = (existing.size + 1).toLong()
        )
        messages.value = messages.value + (message.sellerId to existing + stored)
        return Result.success(stored)
    }

    override fun observeMessages(sellerId: String): Flow<List<SellerInboxMessage>> =
        messages.asStateFlow().map { stored ->
            stored[sellerId].orEmpty().sortedByDescending { it.timestamp }
        }

    override suspend fun deleteMessage(sellerId: String, messageId: String): Result<Unit> {
        val existing = messages.value[sellerId].orEmpty()
        messages.value = messages.value + (sellerId to existing.filterNot { it.id == messageId })
        return Result.success(Unit)
    }
}
