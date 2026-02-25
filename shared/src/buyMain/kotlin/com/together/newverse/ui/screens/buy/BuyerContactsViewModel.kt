package com.together.newverse.ui.screens.buy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.BuyerContact
import com.together.newverse.domain.model.Conversation
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.BuyerContactRepository
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
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class BuyerContactsViewModel(
    private val buyerContactRepository: BuyerContactRepository,
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    private val _isAnonymous = MutableStateFlow(true)
    val isAnonymous: StateFlow<Boolean> = _isAnonymous.asStateFlow()

    init {
        viewModelScope.launch {
            _currentUserId.value = authRepository.getCurrentUserId() ?: ""
            _isAnonymous.value = authRepository.isAnonymous()
        }
    }

    val contactsState: StateFlow<AsyncState<List<BuyerContact>>> =
        _currentUserId.flatMapLatest { userId ->
            if (userId.isEmpty() || _isAnonymous.value) {
                // Don't try to load contacts for anonymous/guest users
                flowOf(AsyncState.Success(emptyList()))
            } else {
                buyerContactRepository.observeContacts(userId).asAsyncState()
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AsyncState.Loading
        )

    fun addContact(contactUserId: String, displayName: String) {
        val myId = _currentUserId.value
        if (myId.isEmpty() || contactUserId == myId) return

        viewModelScope.launch {
            buyerContactRepository.addContact(
                buyerId = myId,
                contact = BuyerContact(
                    userId = contactUserId,
                    displayName = displayName,
                    addedAt = Clock.System.now().toEpochMilliseconds()
                )
            )
        }
    }

    fun removeContact(contactUserId: String) {
        val myId = _currentUserId.value
        if (myId.isEmpty()) return

        viewModelScope.launch {
            buyerContactRepository.removeContact(myId, contactUserId)
        }
    }

    fun blockContact(contactUserId: String) {
        val myId = _currentUserId.value
        if (myId.isEmpty()) return

        viewModelScope.launch {
            buyerContactRepository.blockContact(myId, contactUserId)
            buyerContactRepository.removeContact(myId, contactUserId)
        }
    }

    suspend fun startConversation(contactUserId: String, contactName: String): String {
        val myId = _currentUserId.value
        if (myId.isEmpty()) return ""

        val result = messageRepository.getOrCreateConversation(
            userAId = myId,
            userAName = "",
            userBId = contactUserId,
            userBName = contactName
        )
        return result.getOrNull()?.id ?: ""
    }
}
