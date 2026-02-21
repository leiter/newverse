package com.together.newverse.domain.model

data class Conversation(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderId: String = "",
    val unreadCounts: Map<String, Int> = emptyMap()
) {
    companion object {
        fun createId(userA: String, userB: String): String {
            return listOf(userA, userB).sorted().joinToString("_")
        }
    }

    fun getOtherParticipantId(myId: String): String =
        participantIds.firstOrNull { it != myId } ?: ""

    fun getOtherParticipantName(myId: String): String =
        participantNames[getOtherParticipantId(myId)] ?: ""

    fun getUnreadCount(myId: String): Int =
        unreadCounts[myId] ?: 0
}
