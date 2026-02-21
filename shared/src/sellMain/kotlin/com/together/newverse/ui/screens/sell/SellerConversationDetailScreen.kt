package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerConversationDetailScreen(
    conversationId: String,
    otherParticipantName: String = "",
    otherParticipantId: String = "",
    onNavigateBack: () -> Unit,
    viewModel: SellerConversationDetailViewModel = koinViewModel()
) {
    LaunchedEffect(conversationId) {
        viewModel.setConversationId(conversationId)
    }

    val messagesState by viewModel.messagesState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val myId by viewModel.currentUserId.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messagesState) {
        val state = messagesState
        if (state is AsyncState.Success && state.data.isNotEmpty()) {
            listState.animateScrollToItem(state.data.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherParticipantName.ifEmpty { "Conversation" }) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            MessageInput(
                text = inputText,
                onTextChange = { viewModel.updateInputText(it) },
                onSend = { viewModel.sendMessage(otherParticipantId, otherParticipantName) },
                enabled = !isSending
            )
        }
    ) { padding ->
        when (val state = messagesState) {
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
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is AsyncState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
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
}
