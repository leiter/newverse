package com.together.newverse.ui.screens.buy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.together.newverse.domain.model.Conversation
import com.together.newverse.ui.components.ConversationItem
import com.together.newverse.ui.state.MessagingScreenState
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.contacts_title
import newverse.shared.generated.resources.messaging_empty
import newverse.shared.generated.resources.nav_messages
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    state: MessagingScreenState,
    myUserId: String,
    onConversationClick: (String) -> Unit,
    onContactsClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.nav_messages)) },
                actions = {
                    IconButton(onClick = onContactsClick) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = stringResource(Res.string.contacts_title)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (state.conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.messaging_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
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
}
