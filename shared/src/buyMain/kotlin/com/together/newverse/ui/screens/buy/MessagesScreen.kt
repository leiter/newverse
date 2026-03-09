package com.together.newverse.ui.screens.buy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.together.newverse.ui.components.ConversationItem
import com.together.newverse.ui.state.MessagingScreenState
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.messaging_empty
import org.jetbrains.compose.resources.stringResource

@Composable
fun MessagesScreen(
    state: MessagingScreenState,
    myUserId: String,
    onConversationClick: (String) -> Unit,
    onContactsClick: () -> Unit
) {
    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
    } else if (state.conversations.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.messaging_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.conversations, key = { it.id }) { conversation ->
                ConversationItem(
                    name = conversation.getOtherParticipantName(myUserId),
                    lastMessage = conversation.lastMessage,
                    timestamp = conversation.lastMessageTimestamp,
                    unreadCount = conversation.getUnreadCount(myUserId),
                    onClick = { onConversationClick(conversation.id) }
                )
            }
        }
    }
}
