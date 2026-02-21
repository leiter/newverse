package com.together.newverse.data.repository

import com.together.newverse.domain.model.Conversation
import com.together.newverse.domain.model.Message
import com.together.newverse.domain.repository.MessageRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.DataSnapshot
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Firebase Realtime Database implementation of MessageRepository.
 *
 * Firebase structure:
 * - conversations/{conversationId}/ - conversation metadata
 * - messages/{conversationId}/{messageId}/ - individual messages
 * - user_conversations/{userId}/{conversationId}: true - index for user's conversations
 */
class GitLiveMessageRepository : MessageRepository {

    private val database = Firebase.database
    private val conversationsRef = database.reference("conversations")
    private val messagesRef = database.reference("messages")
    private val userConversationsRef = database.reference("user_conversations")
    private val buyerBlockedRef = database.reference("buyer_blocked")

    override fun observeConversations(userId: String): Flow<List<Conversation>> {
        return userConversationsRef.child(userId).valueEvents.map { snapshot ->
            if (!snapshot.exists) return@map emptyList()

            val conversations = mutableListOf<Conversation>()
            snapshot.children.forEach { child ->
                val conversationId = child.key ?: return@forEach
                try {
                    val convSnapshot = getConversationSnapshot(conversationId)
                    if (convSnapshot != null) {
                        conversations.add(parseConversation(conversationId, convSnapshot))
                    }
                } catch (e: Exception) {
                    println("Failed to fetch conversation $conversationId: ${e.message}")
                }
            }
            conversations.sortedByDescending { it.lastMessageTimestamp }
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        return messagesRef.child(conversationId).valueEvents.map { snapshot ->
            if (!snapshot.exists) return@map emptyList()

            val messages = mutableListOf<Message>()
            snapshot.children.forEach { child ->
                try {
                    messages.add(parseMessage(conversationId, child))
                } catch (e: Exception) {
                    println("Failed to parse message: ${e.message}")
                }
            }
            messages.sortedBy { it.timestamp }
        }
    }

    override fun observeUnreadCount(userId: String): Flow<Int> {
        return userConversationsRef.child(userId).valueEvents.map { snapshot ->
            if (!snapshot.exists) return@map 0

            var totalUnread = 0
            snapshot.children.forEach { child ->
                val conversationId = child.key ?: return@forEach
                try {
                    val convSnapshot = getConversationSnapshot(conversationId)
                    if (convSnapshot != null) {
                        val conv = parseConversation(conversationId, convSnapshot)
                        totalUnread += conv.getUnreadCount(userId)
                    }
                } catch (e: Exception) {
                    // Skip failed conversations
                }
            }
            totalUnread
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        senderName: String,
        recipientId: String,
        recipientName: String,
        text: String
    ): Result<Message> {
        return try {
            // Check if sender is blocked by recipient
            val isBlocked = checkIfBlocked(senderId, recipientId)
            if (isBlocked) {
                return Result.failure(BlockedException())
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val messageId = Uuid.random().toString()

            val message = Message(
                id = messageId,
                conversationId = conversationId,
                senderId = senderId,
                senderName = senderName,
                text = text,
                timestamp = now,
                isRead = false
            )

            // Write message
            val msgRef = messagesRef.child(conversationId).child(messageId)
            msgRef.child("id").setValue(messageId)
            msgRef.child("senderId").setValue(senderId)
            msgRef.child("senderName").setValue(senderName)
            msgRef.child("text").setValue(text)
            msgRef.child("timestamp").setValue(now)
            msgRef.child("isRead").setValue(false)

            // Update conversation metadata
            val convRef = conversationsRef.child(conversationId)
            convRef.child("lastMessage").setValue(text)
            convRef.child("lastMessageTimestamp").setValue(now)
            convRef.child("lastMessageSenderId").setValue(senderId)

            // Ensure participant info is set
            convRef.child("participantIds").child("0").setValue(
                listOf(senderId, recipientId).sorted()[0]
            )
            convRef.child("participantIds").child("1").setValue(
                listOf(senderId, recipientId).sorted()[1]
            )
            convRef.child("participantNames").child(senderId).setValue(senderName)
            convRef.child("participantNames").child(recipientId).setValue(recipientName)

            // Increment unread count for recipient
            incrementUnreadCount(conversationId, recipientId)

            // Ensure user_conversations index entries exist
            userConversationsRef.child(senderId).child(conversationId).setValue(true)
            userConversationsRef.child(recipientId).child(conversationId).setValue(true)

            println("Message sent: $messageId in conversation $conversationId")
            Result.success(message)
        } catch (e: Exception) {
            println("Failed to send message: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun markAsRead(conversationId: String, userId: String): Result<Unit> {
        return try {
            // Reset unread count for this user
            conversationsRef.child(conversationId)
                .child("unreadCounts").child(userId).setValue(0)

            // Mark individual messages as read
            val snapshot = getMessagesSnapshot(conversationId)
            snapshot?.children?.forEach { child ->
                try {
                    val msgSenderId = child.child("senderId").value<String>()
                    val isRead = try {
                        child.child("isRead").value<Boolean>()
                    } catch (e: Exception) { false }

                    // Mark as read if message is from other user and not yet read
                    if (msgSenderId != userId && !isRead) {
                        val msgId = child.key ?: return@forEach
                        messagesRef.child(conversationId).child(msgId)
                            .child("isRead").setValue(true)
                    }
                } catch (e: Exception) {
                    // Skip malformed messages
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to mark as read: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getOrCreateConversation(
        userAId: String,
        userAName: String,
        userBId: String,
        userBName: String
    ): Result<Conversation> {
        return try {
            val conversationId = Conversation.createId(userAId, userBId)

            // Check if conversation already exists
            val existing = getConversationSnapshot(conversationId)
            if (existing != null) {
                // Update names in case they changed
                val convRef = conversationsRef.child(conversationId)
                convRef.child("participantNames").child(userAId).setValue(userAName)
                convRef.child("participantNames").child(userBId).setValue(userBName)

                return Result.success(parseConversation(conversationId, existing))
            }

            // Create new conversation
            val sortedIds = listOf(userAId, userBId).sorted()
            val convRef = conversationsRef.child(conversationId)
            convRef.child("participantIds").child("0").setValue(sortedIds[0])
            convRef.child("participantIds").child("1").setValue(sortedIds[1])
            convRef.child("participantNames").child(userAId).setValue(userAName)
            convRef.child("participantNames").child(userBId).setValue(userBName)
            convRef.child("lastMessage").setValue("")
            convRef.child("lastMessageTimestamp").setValue(0L)
            convRef.child("lastMessageSenderId").setValue("")
            convRef.child("unreadCounts").child(userAId).setValue(0)
            convRef.child("unreadCounts").child(userBId).setValue(0)

            // Add to user_conversations index
            userConversationsRef.child(userAId).child(conversationId).setValue(true)
            userConversationsRef.child(userBId).child(conversationId).setValue(true)

            val conversation = Conversation(
                id = conversationId,
                participantIds = sortedIds,
                participantNames = mapOf(userAId to userAName, userBId to userBName),
                lastMessage = "",
                lastMessageTimestamp = 0L,
                lastMessageSenderId = "",
                unreadCounts = mapOf(userAId to 0, userBId to 0)
            )

            println("Conversation created: $conversationId")
            Result.success(conversation)
        } catch (e: Exception) {
            println("Failed to create conversation: ${e.message}")
            Result.failure(e)
        }
    }

    // ===== Private helpers =====

    private suspend fun getConversationSnapshot(conversationId: String): DataSnapshot? {
        var result: DataSnapshot? = null
        conversationsRef.child(conversationId).valueEvents.collect { snap ->
            result = if (snap.exists) snap else null
            return@collect
        }
        return result
    }

    private suspend fun getMessagesSnapshot(conversationId: String): DataSnapshot? {
        var result: DataSnapshot? = null
        messagesRef.child(conversationId).valueEvents.collect { snap ->
            result = if (snap.exists) snap else null
            return@collect
        }
        return result
    }

    private suspend fun incrementUnreadCount(conversationId: String, userId: String) {
        try {
            val countRef = conversationsRef.child(conversationId)
                .child("unreadCounts").child(userId)
            var currentCount = 0
            countRef.valueEvents.collect { snap ->
                currentCount = try {
                    if (snap.exists) snap.value<Int>() else 0
                } catch (e: Exception) { 0 }
                return@collect
            }
            countRef.setValue(currentCount + 1)
        } catch (e: Exception) {
            println("Failed to increment unread count: ${e.message}")
        }
    }

    private suspend fun checkIfBlocked(senderId: String, recipientId: String): Boolean {
        return try {
            var blocked = false
            buyerBlockedRef.child(recipientId).child(senderId).valueEvents.collect { snap ->
                blocked = snap.exists
                return@collect
            }
            blocked
        } catch (e: Exception) {
            false
        }
    }

    private fun parseConversation(id: String, snapshot: DataSnapshot): Conversation {
        val participantIds = mutableListOf<String>()
        try {
            snapshot.child("participantIds").children.forEach { child ->
                try {
                    participantIds.add(child.value<String>())
                } catch (e: Exception) { /* skip */ }
            }
        } catch (e: Exception) { /* no participants */ }

        val participantNames = mutableMapOf<String, String>()
        try {
            snapshot.child("participantNames").children.forEach { child ->
                val key = child.key ?: return@forEach
                try {
                    participantNames[key] = child.value<String>()
                } catch (e: Exception) { /* skip */ }
            }
        } catch (e: Exception) { /* no names */ }

        val unreadCounts = mutableMapOf<String, Int>()
        try {
            snapshot.child("unreadCounts").children.forEach { child ->
                val key = child.key ?: return@forEach
                try {
                    unreadCounts[key] = child.value<Int>()
                } catch (e: Exception) { /* skip */ }
            }
        } catch (e: Exception) { /* no unread counts */ }

        return Conversation(
            id = id,
            participantIds = participantIds,
            participantNames = participantNames,
            lastMessage = try { snapshot.child("lastMessage").value<String>() } catch (e: Exception) { "" },
            lastMessageTimestamp = try { snapshot.child("lastMessageTimestamp").value<Long>() } catch (e: Exception) { 0L },
            lastMessageSenderId = try { snapshot.child("lastMessageSenderId").value<String>() } catch (e: Exception) { "" },
            unreadCounts = unreadCounts
        )
    }

    private fun parseMessage(conversationId: String, snapshot: DataSnapshot): Message {
        return Message(
            id = try { snapshot.child("id").value<String>() } catch (e: Exception) { snapshot.key ?: "" },
            conversationId = conversationId,
            senderId = snapshot.child("senderId").value<String>(),
            senderName = try { snapshot.child("senderName").value<String>() } catch (e: Exception) { "" },
            text = snapshot.child("text").value<String>(),
            timestamp = snapshot.child("timestamp").value<Long>(),
            isRead = try { snapshot.child("isRead").value<Boolean>() } catch (e: Exception) { false }
        )
    }
}

class BlockedException : Exception("Message could not be sent - contact is not available")
