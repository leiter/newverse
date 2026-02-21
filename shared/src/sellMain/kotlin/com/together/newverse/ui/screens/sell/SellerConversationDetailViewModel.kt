package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Message
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.MessageRepository
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.ui.state.core.asAsyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SellerConversationDetailViewModel(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _conversationId = MutableStateFlow("")
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    init {
        viewModelScope.launch {
            _currentUserId.value = authRepository.getCurrentUserId() ?: ""
        }
    }

    val messagesState: StateFlow<AsyncState<List<Message>>> =
        _conversationId.flatMapLatest { convId ->
            if (convId.isEmpty()) {
                flowOf(AsyncState.Success(emptyList()))
            } else {
                messageRepository.observeMessages(convId).asAsyncState()
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AsyncState.Loading
        )

    fun setConversationId(conversationId: String) {
        _conversationId.value = conversationId
        markAsRead()
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun sendMessage(recipientId: String, recipientName: String) {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        val conversationId = _conversationId.value
        val senderId = _currentUserId.value
        if (senderId.isEmpty()) return

        viewModelScope.launch {
            _isSending.value = true

            messageRepository.sendMessage(
                conversationId = conversationId,
                senderId = senderId,
                senderName = "",
                recipientId = recipientId,
                recipientName = recipientName,
                text = text
            ).onSuccess {
                _inputText.value = ""
            }.onFailure { error ->
                println("Failed to send message: ${error.message}")
            }

            _isSending.value = false
        }
    }

    private fun markAsRead() {
        val conversationId = _conversationId.value
        if (conversationId.isEmpty()) return

        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            messageRepository.markAsRead(conversationId, userId)
        }
    }
}
