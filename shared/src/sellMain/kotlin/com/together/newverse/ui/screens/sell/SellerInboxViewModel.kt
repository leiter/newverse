package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.SellerInboxMessage
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.SellerInboxRepository
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.ui.state.core.asAsyncState
import com.together.newverse.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What buyers have sent through a published message box.
 *
 * Shaped after [ConversationListViewModel], which has the same job — one live list
 * keyed on the signed-in seller — and for the same reason reads the seller's own uid
 * rather than taking it from a screen: the node is readable only by its owner, so
 * there is no other id that would work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SellerInboxViewModel(
    private val inboxRepository: SellerInboxRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private companion object {
        private const val TAG = "SellerInboxVM"
    }

    val messagesState: StateFlow<AsyncState<List<SellerInboxMessage>>> =
        flow { emit(authRepository.getCurrentUserId() ?: "") }
            .flatMapLatest { sellerId ->
                if (sellerId.isEmpty()) {
                    flowOf(AsyncState.Error("Not authenticated", retryable = true))
                } else {
                    inboxRepository.observeMessages(sellerId).asAsyncState()
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AsyncState.Loading
            )

    private val _deleteError = MutableStateFlow<String?>(null)

    /**
     * Why the last delete failed, if it did.
     *
     * Reported rather than swallowed: the list is live, so a refused delete puts the
     * message straight back, and a row that reappears on its own needs a reason
     * beside it.
     */
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId()
            if (sellerId.isNullOrEmpty()) return@launch
            inboxRepository.deleteMessage(sellerId, messageId)
                .onFailure { e ->
                    Log.w(TAG) { "deleteMessage: $messageId refused - ${e.message}" }
                    _deleteError.value = e.message
                }
                .onSuccess { _deleteError.value = null }
        }
    }
}
