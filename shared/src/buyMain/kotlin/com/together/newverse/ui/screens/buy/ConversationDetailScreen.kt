package com.together.newverse.ui.screens.buy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.together.newverse.ui.components.MessageBubble
import com.together.newverse.ui.components.MessageInput
import com.together.newverse.ui.state.core.AsyncState
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.messaging_blocked
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BuyerConversationDetailScreen(
    conversationId: String,
    otherParticipantName: String = "",
    otherParticipantId: String = "",
    onNavigateBack: () -> Unit,
    viewModel: BuyerConversationViewModel = koinViewModel()
) {
    LaunchedEffect(conversationId) {
        viewModel.setConversationId(conversationId)
    }

    val messagesState by viewModel.messagesState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val myId by viewModel.currentUserId.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messagesState) {
        val state = messagesState
        if (state is AsyncState.Success && state.data.isNotEmpty()) {
            listState.animateScrollToItem(state.data.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Messages list
        Box(modifier = Modifier.weight(1f)) {
            when (val state = messagesState) {
                is AsyncState.Loading, is AsyncState.Initial -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
                is AsyncState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is AsyncState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        state = listState
                    ) {
                        items(state.data, key = { it.id }) { message ->
                            MessageBubble(
                                text = message.text,
                                timestamp = message.timestamp,
                                isFromMe = message.senderId == myId,
                                senderName = message.senderName,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }

        // Bottom input or blocked message
        if (isBlocked) {
            Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.messaging_blocked),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            MessageInput(
                text = inputText,
                onTextChange = { viewModel.updateInputText(it) },
                onSend = { viewModel.sendMessage(otherParticipantId, otherParticipantName) },
                enabled = !isSending
            )
        }
    }
}
