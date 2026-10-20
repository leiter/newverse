package com.together.newverse.test

import com.together.newverse.domain.model.Conversation
import com.together.newverse.domain.model.Message
import com.together.newverse.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An in-memory [MessageRepository] that records what was sent. */
class FakeMessageRepository : MessageRepository {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    private val _unread = MutableStateFlow(0)

    /** Every message handed to [sendMessage], in order. */
    val sentMessages: List<Message> get() = _messages.value

    /** Conversations [getOrCreateConversation] was asked for, as (userA, userB) pairs. */
    private val _requestedConversations = mutableListOf<Pair<String, String>>()
    val requestedConversations: List<Pair<String, String>> get() = _requestedConversations.toList()

    var shouldFailSend = false
    var shouldFailConversation = false
    var failureMessage = "Test error"

    private var nextId = 1

    fun reset() {
        _conversations.value = emptyList()
        _messages.value = emptyList()
        _unread.value = 0
        _requestedConversations.clear()
        shouldFailSend = false
        shouldFailConversation = false
        failureMessage = "Test error"
        nextId = 1
    }

    fun setUnreadCount(count: Int) {
        _unread.value = count
    }

    override fun observeConversations(userId: String): Flow<List<Conversation>> =
        _conversations.asStateFlow()

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        _messages.asStateFlow()

    override fun observeUnreadCount(userId: String): Flow<Int> = _unread.asStateFlow()

    override suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        senderName: String,
        recipientId: String,
        recipientName: String,
        text: String
    ): Result<Message> {
        if (shouldFailSend) return Result.failure(Exception(failureMessage))
        val message = Message(
            id = "m${nextId++}",
            conversationId = conversationId,
            senderId = senderId,
            senderName = senderName,
            text = text,
            timestamp = nextId.toLong()
        )
        _messages.value = _messages.value + message
        return Result.success(message)
    }

    override suspend fun markAsRead(conversationId: String, userId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun getOrCreateConversation(
        userAId: String,
        userAName: String,
        userBId: String,
        userBName: String
    ): Result<Conversation> {
        if (shouldFailConversation) return Result.failure(Exception(failureMessage))
        _requestedConversations.add(userAId to userBId)
        val conversation = Conversation(
            id = Conversation.createId(userAId, userBId),
            participantIds = listOf(userAId, userBId),
            participantNames = mapOf(userAId to userAName, userBId to userBName)
        )
        _conversations.value = (_conversations.value + conversation).distinctBy { it.id }
        return Result.success(conversation)
    }
}
