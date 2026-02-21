package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.together.newverse.ui.components.ConversationItem
import com.together.newverse.ui.state.core.AsyncState
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.messaging_empty
import newverse.shared.generated.resources.nav_messages
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (String) -> Unit,
    viewModel: ConversationListViewModel = koinViewModel()
) {
    val conversationsState by viewModel.conversationsState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.nav_messages)) }
            )
        }
    ) { padding ->
        when (val state = conversationsState) {
            is AsyncState.Loading, is AsyncState.Initial -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            is AsyncState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is AsyncState.Success -> {
                val conversations = state.data
                if (conversations.isEmpty()) {
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
                    val myId = viewModel.conversationsState.value.let {
                        // The seller ID is available from auth
                        ""
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding)
                    ) {
                        items(conversations, key = { it.id }) { conversation ->
                            val sellerId = conversation.participantIds.firstOrNull() ?: ""
                            val otherName = conversation.participantNames.entries
                                .firstOrNull { it.key != sellerId }?.value
                                ?: conversation.participantNames.values.firstOrNull()
                                ?: ""
                            val unread = conversation.unreadCounts.values.firstOrNull() ?: 0

                            ConversationItem(
                                name = otherName,
                                lastMessage = conversation.lastMessage,
                                timestamp = conversation.lastMessageTimestamp,
                                unreadCount = unread,
                                onClick = { onConversationClick(conversation.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
