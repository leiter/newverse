package com.together.newverse.domain.repository

import com.together.newverse.domain.model.Conversation
import com.together.newverse.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeConversations(userId: String): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    fun observeUnreadCount(userId: String): Flow<Int>

    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        senderName: String,
        recipientId: String,
        recipientName: String,
        text: String
    ): Result<Message>

    suspend fun markAsRead(conversationId: String, userId: String): Result<Unit>

    suspend fun getOrCreateConversation(
        userAId: String,
        userAName: String,
        userBId: String,
        userBName: String
    ): Result<Conversation>
}
