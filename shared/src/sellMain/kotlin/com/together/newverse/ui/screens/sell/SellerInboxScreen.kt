package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.SellerInboxMessage
import com.together.newverse.ui.state.core.AsyncState
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.back
import newverse.shared.generated.resources.inbox_anonymous
import newverse.shared.generated.resources.inbox_delete
import newverse.shared.generated.resources.inbox_empty
import newverse.shared.generated.resources.inbox_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * What customers have sent through a published message box.
 *
 * The seller's end of the wishbox. A flat, newest-first list rather than threads:
 * these are answers to a question the seller asked everyone, not conversations, and
 * there is nothing to reply *to* — a buyer who wants a conversation has the messaging
 * screen. Deleting is the whole lifecycle, as it is for a published row.
 *
 * Reached from the seller's profile, beside the editor that publishes the box, so the
 * question and its answers are in the same place.
 *
 * See `doc/guides/quick-message-wishbox.md`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerInboxScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SellerInboxViewModel = koinViewModel()
) {
    val messagesState by viewModel.messagesState.collectAsState()
    val deleteError by viewModel.deleteError.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.inbox_title),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // A refused delete puts the message straight back, because the list is
            // live. Said out loud so a row that reappears on its own has a reason
            // beside it.
            deleteError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }

            when (val state = messagesState) {
                is AsyncState.Loading, is AsyncState.Initial -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is AsyncState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                is AsyncState.Success -> {
                    val messages = state.data
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.inbox_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(messages, key = { it.id }) { message ->
                                InboxMessageRow(
                                    message = message,
                                    onDelete = { viewModel.deleteMessage(message.id) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One message, with what it was an answer to above it.
 *
 * The question first and smaller, the answer second and larger: the seller already
 * knows what they asked and is reading for what came back, but a list of bare answers
 * to a question that has since been reworded is unreadable — which is why the row
 * carries its own [SellerInboxMessage.prompt] rather than looking one up.
 */
@Composable
private fun InboxMessageRow(
    message: SellerInboxMessage,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (message.prompt.isNotBlank()) {
                Text(
                    text = message.prompt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge
            )
            // The name is what the buyer calls themselves and may be empty — a guest
            // has none to give. The date is the readable half of the two timestamps.
            Text(
                text = listOf(
                    message.buyerName.ifBlank { stringResource(Res.string.inbox_anonymous) },
                    message.timestampIso.take(10)
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(Res.string.inbox_delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
