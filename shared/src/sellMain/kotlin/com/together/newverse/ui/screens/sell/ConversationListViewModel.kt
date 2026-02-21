package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Conversation
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.MessageRepository
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.ui.state.core.asAsyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationListViewModel(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val conversationsState: StateFlow<AsyncState<List<Conversation>>> =
        flow { emit(authRepository.getCurrentUserId() ?: "") }
            .flatMapLatest { sellerId ->
                if (sellerId.isEmpty()) {
                    flowOf(AsyncState.Error("Not authenticated", retryable = true))
                } else {
                    messageRepository.observeConversations(sellerId).asAsyncState()
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AsyncState.Loading
            )
}
