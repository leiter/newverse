package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.AccessRequest
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Invitation
import com.together.newverse.domain.model.InvitationStatus
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.InvitationRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.ui.state.core.AsyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel for Seller Profile screen
 */
class SellerProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val articleRepository: ArticleRepository,
    private val orderRepository: OrderRepository,
    private val invitationRepository: InvitationRepository = object : InvitationRepository {
        override suspend fun createInvitation(sellerId: String, sellerDisplayName: String, expiresInMillis: Long, targetBuyerId: String?) = Result.failure<Invitation>(Exception("Not configured"))
        override suspend fun getInvitation(invitationId: String) = Result.failure<Invitation>(Exception("Not configured"))
        override suspend fun acceptInvitation(invitationId: String, buyerId: String) = Result.failure<Invitation>(Exception("Not configured"))
        override suspend fun rejectInvitation(invitationId: String, buyerId: String) = Result.failure<Unit>(Exception("Not configured"))
        override fun observePendingInvitations(buyerId: String) = kotlinx.coroutines.flow.flowOf(emptyList<Invitation>())
        override suspend fun revokeInvitation(invitationId: String) = Result.failure<Unit>(Exception("Not configured"))
    }
) : ViewModel() {

    // Profile state using AsyncState pattern
    private val _profileState = MutableStateFlow<AsyncState<SellerProfile>>(AsyncState.Loading)
    val profileState: StateFlow<AsyncState<SellerProfile>> = _profileState.asStateFlow()

    // Stats state
    private val _statsState = MutableStateFlow(ProfileStats())
    val statsState: StateFlow<ProfileStats> = _statsState.asStateFlow()

    // Dialog/UI state
    private val _dialogState = MutableStateFlow(ProfileDialogState())
    val dialogState: StateFlow<ProfileDialogState> = _dialogState.asStateFlow()

    // Customer management state
    private val _customerState = MutableStateFlow(CustomerManagementState())
    val customerState: StateFlow<CustomerManagementState> = _customerState.asStateFlow()

    // Invitation management state
    private val _invitationState = MutableStateFlow(InvitationManagementState())
    val invitationState: StateFlow<InvitationManagementState> = _invitationState.asStateFlow()

    // Saving state
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // Access requests (live-updating)
    private val _accessRequests = MutableStateFlow<List<AccessRequest>>(emptyList())
    val accessRequests: StateFlow<List<AccessRequest>> = _accessRequests.asStateFlow()

    // Generated buyer link
    private val _generatedBuyerLink = MutableStateFlow<String?>(null)
    val generatedBuyerLink: StateFlow<String?> = _generatedBuyerLink.asStateFlow()

    private val articles = mutableListOf<Article>()

    init {
        loadProfile()
        loadStats()
        observeAccessRequests()
        observeApprovedBuyers()
    }

    private fun observeAccessRequests() {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.observeAccessRequests(sellerId)
                .catch { e -> println("❌ SellerProfileViewModel.observeAccessRequests: ${e.message}") }
                .collect { requests -> _accessRequests.value = requests }
        }
    }

    private fun observeApprovedBuyers() {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.observeApprovedBuyerIds(sellerId)
                .catch { e -> println("❌ SellerProfileViewModel.observeApprovedBuyers: ${e.message}") }
                .collect { map ->
                    println("✅ SellerProfileViewModel.observeApprovedBuyers: ${map.size} approved buyers")
                    val entries = map.map { (id, name) -> BuyerEntry(id, name, AccessStatus.APPROVED) }
                    val enriched = enrichWithDisplayNames(sellerId, entries)
                    _customerState.update { state -> state.copy(approvedBuyers = enriched) }
                }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun generateBuyerLink() {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            val uuid = Uuid.random().toString()
            val link = "https://cutthecrap.link/connect?seller=$sellerId&token=$uuid"
            println("[NV_Seller] generateBuyerLink: sellerId=$sellerId uuid=$uuid link=$link")
            _generatedBuyerLink.value = link
            // Pre-approve in background so the buyer is immediately APPROVED upon scanning/clicking
            launch {
                profileRepository.approveAccessRequestWithTracking(sellerId, uuid, QR_LINK_PLACEHOLDER)
                    .onFailure { e -> println("Pre-approve for buyer link failed: ${e.message}") }
            }
        }
    }

    fun approveRequest(buyerUUID: String) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            val displayName = _accessRequests.value.find { it.buyerUUID == buyerUUID }?.buyerDisplayName ?: ""
            profileRepository.approveAccessRequestWithTracking(sellerId, buyerUUID, displayName)
                .onFailure { e -> println("❌ SellerProfileViewModel.approveRequest: ${e.message}") }
        }
    }

    fun blockBuyer(buyerUUID: String) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.blockBuyer(sellerId, buyerUUID)
                .onFailure { e -> println("❌ SellerProfileViewModel.blockBuyer: ${e.message}") }
        }
    }

    fun blockApprovedBuyer(buyerUUID: String) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            // Grab display name before the repo call removes the entry
            val displayName = _customerState.value.approvedBuyers
                .find { it.id == buyerUUID }?.displayName ?: ""
            profileRepository.blockBuyer(sellerId, buyerUUID)
                .onSuccess {
                    // Only update blockedBuyers; approvedBuyers is handled by the Firebase observer
                    _customerState.update { state ->
                        state.copy(
                            blockedBuyers = state.blockedBuyers + BuyerEntry(
                                buyerUUID, displayName, AccessStatus.BLOCKED
                            )
                        )
                    }
                }
                .onFailure { e -> println("❌ SellerProfileViewModel.blockApprovedBuyer: ${e.message}") }
        }
    }

    fun unblockApprovedBuyer(buyerUUID: String) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.unblockApprovedBuyer(sellerId, buyerUUID)
                .onSuccess {
                    // Only update blockedBuyers; approvedBuyers is handled by the Firebase observer
                    _customerState.update { state ->
                        state.copy(
                            blockedBuyers = state.blockedBuyers.filter { it.id != buyerUUID }
                        )
                    }
                }
                .onFailure { e -> println("❌ SellerProfileViewModel.unblockApprovedBuyer: ${e.message}") }
        }
    }

    private suspend fun enrichWithDisplayNames(sellerId: String, entries: List<BuyerEntry>): List<BuyerEntry> =
        coroutineScope {
            entries.map { entry ->
                if (entry.displayName.isBlank() || entry.displayName == QR_LINK_PLACEHOLDER) {
                    async {
                        val name = profileRepository.getBuyerDisplayName(sellerId, entry.id)
                        val isResolved = name.isNotBlank() && name != QR_LINK_PLACEHOLDER
                        if (isResolved) {
                            // Persist the resolved name so future loads and the observer see the real name
                            profileRepository.correctApprovedBuyerDisplayName(sellerId, entry.id, name)
                        }
                        entry.copy(displayName = if (isResolved) name else entry.displayName)
                    }
                } else {
                    async { entry }
                }
            }.awaitAll()
        }

    fun clearGeneratedLink() {
        _generatedBuyerLink.value = null
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _profileState.value = AsyncState.Loading

            val sellerId = authRepository.getCurrentUserId()
            if (sellerId == null) {
                _profileState.value = AsyncState.Error("Not authenticated")
                return@launch
            }

            profileRepository.getSellerProfile(sellerId).fold(
                onSuccess = { profile ->
                    _profileState.value = AsyncState.Success(profile)
                    val blockedEntries = profile.blockedClientIds.map { (id, name) -> BuyerEntry(id, name, AccessStatus.BLOCKED) }
                    val approvedEntries = profile.approvedBuyerIds.map { (id, name) -> BuyerEntry(id, name, AccessStatus.APPROVED) }
                    val enrichedBlocked = enrichWithDisplayNames(sellerId, blockedEntries)
                    val enrichedApproved = enrichWithDisplayNames(sellerId, approvedEntries)
                    _customerState.value = CustomerManagementState(
                        knownClientIds = profile.knownClientIds,
                        blockedBuyers = enrichedBlocked,
                        approvedBuyers = enrichedApproved
                    )
                },
                onFailure = { e ->
                    _profileState.value = AsyncState.Error(
                        e.message ?: "Failed to load profile",
                        e
                    )
                }
            )
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch

            // Load product count
            launch {
                articleRepository.getArticles(sellerId)
                    .collect { article ->
                        when (article.mode) {
                            Article.MODE_ADDED -> {
                                if (articles.none { it.id == article.id }) {
                                    articles.add(article)
                                }
                            }
                            Article.MODE_REMOVED -> {
                                articles.removeAll { it.id == article.id }
                            }
                        }
                        _statsState.update { it.copy(productCount = articles.size) }
                    }
            }

            // Load order count
            launch {
                orderRepository.observeSellerOrders(sellerId)
                    .collect { orders ->
                        _statsState.update { it.copy(orderCount = orders.size) }
                    }
            }
        }
    }

    fun saveProfile(profile: SellerProfile) {
        viewModelScope.launch {
            _isSaving.value = true

            profileRepository.saveSellerProfile(profile).fold(
                onSuccess = {
                    _profileState.value = AsyncState.Success(profile)
                    _isSaving.value = false
                },
                onFailure = { e ->
                    // Keep the current profile but show error via a snackbar or similar
                    println("❌ Failed to save profile: ${e.message}")
                    _isSaving.value = false
                }
            )
        }
    }

    fun addMarket(market: Market) {
        val currentProfile = (_profileState.value as? AsyncState.Success)?.data ?: return
        val updatedMarkets = currentProfile.markets + market
        val updatedProfile = currentProfile.copy(markets = updatedMarkets)
        saveProfile(updatedProfile)
    }

    fun updateMarket(market: Market) {
        val currentProfile = (_profileState.value as? AsyncState.Success)?.data ?: return
        val updatedMarkets = currentProfile.markets.map {
            if (it.id == market.id) market else it
        }
        val updatedProfile = currentProfile.copy(markets = updatedMarkets)
        saveProfile(updatedProfile)
    }

    fun removeMarket(marketId: String) {
        val currentProfile = (_profileState.value as? AsyncState.Success)?.data ?: return
        val updatedMarkets = currentProfile.markets.filter { it.id != marketId }
        val updatedProfile = currentProfile.copy(markets = updatedMarkets)
        saveProfile(updatedProfile)
    }

    fun showMarketDialog(market: Market? = null) {
        _dialogState.update {
            it.copy(
                showMarketDialog = true,
                editingMarket = market
            )
        }
    }

    fun hideMarketDialog() {
        _dialogState.update {
            it.copy(
                showMarketDialog = false,
                editingMarket = null
            )
        }
    }

    fun showPaymentInfo() {
        _dialogState.update { it.copy(showPaymentInfo = true) }
    }

    fun hidePaymentInfo() {
        _dialogState.update { it.copy(showPaymentInfo = false) }
    }

    fun blockCustomer(buyerId: String) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.blockClient(sellerId, buyerId).onSuccess {
                _customerState.update {
                    it.copy(
                        knownClientIds = it.knownClientIds - buyerId,
                        blockedBuyers = it.blockedBuyers + BuyerEntry(buyerId, "", AccessStatus.BLOCKED)
                    )
                }
            }
        }
    }

    fun unblockCustomer(buyerId: String) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.unblockClient(sellerId, buyerId).onSuccess {
                _customerState.update {
                    it.copy(
                        blockedBuyers = it.blockedBuyers.filter { b -> b.id != buyerId },
                        knownClientIds = it.knownClientIds + buyerId
                    )
                }
            }
        }
    }

    fun generateInvitation(expiryMinutes: Int = 1440) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            val profile = (_profileState.value as? AsyncState.Success)?.data ?: return@launch

            _invitationState.update { it.copy(isGenerating = true) }

            val expiryMillis = expiryMinutes * 60 * 1000L
            invitationRepository.createInvitation(
                sellerId = sellerId,
                sellerDisplayName = profile.displayName,
                expiresInMillis = expiryMillis
            ).fold(
                onSuccess = { invitation ->
                    val deepLink = "newverse://connect?sellerId=${invitation.sellerId}&inviteId=${invitation.id}&expires=${invitation.expiresAt}"
                    _invitationState.update {
                        it.copy(
                            currentInvitation = invitation,
                            deepLink = deepLink,
                            isGenerating = false
                        )
                    }
                    // Pre-approve in background so the buyer is immediately APPROVED upon accepting
                    launch {
                        profileRepository.approveAccessRequestWithTracking(sellerId, invitation.id, "Invitation")
                            .onFailure { e -> println("Pre-approve for invitation failed: ${e.message}") }
                    }
                },
                onFailure = { e ->
                    println("Failed to generate invitation: ${e.message}")
                    _invitationState.update { it.copy(isGenerating = false) }
                }
            )
        }
    }

    fun sendInvitationToBuyer(buyerId: String) {
        if (buyerId.isBlank()) return

        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            val profile = (_profileState.value as? AsyncState.Success)?.data ?: return@launch

            _invitationState.update { it.copy(isSendingToBuyer = true) }

            invitationRepository.createInvitation(
                sellerId = sellerId,
                sellerDisplayName = profile.displayName,
                targetBuyerId = buyerId
            ).fold(
                onSuccess = { invitation ->
                    _invitationState.update {
                        it.copy(
                            lastSentInvitation = invitation,
                            isSendingToBuyer = false
                        )
                    }
                },
                onFailure = { e ->
                    println("Failed to send invitation: ${e.message}")
                    _invitationState.update { it.copy(isSendingToBuyer = false) }
                }
            )
        }
    }

    fun revokeInvitation(invitationId: String) {
        viewModelScope.launch {
            invitationRepository.revokeInvitation(invitationId).fold(
                onSuccess = {
                    _invitationState.update {
                        if (it.currentInvitation?.id == invitationId) {
                            it.copy(currentInvitation = null, deepLink = null)
                        } else {
                            it
                        }
                    }
                },
                onFailure = { e ->
                    println("Failed to revoke invitation: ${e.message}")
                }
            )
        }
    }

    fun refresh() {
        articles.clear()
        loadProfile()
        loadStats()
    }
}

/**
 * Stats data for the profile screen
 */
data class ProfileStats(
    val productCount: Int = 0,
    val orderCount: Int = 0
)

/**
 * Dialog state for the profile screen
 */
data class ProfileDialogState(
    val showMarketDialog: Boolean = false,
    val editingMarket: Market? = null,
    val showPaymentInfo: Boolean = false
)

/** Placeholder display name assigned to QR-link pre-approved buyers before they connect. */
const val QR_LINK_PLACEHOLDER = "QR-Link"

/**
 * A buyer entry with id, display name, and access status.
 */
data class BuyerEntry(
    val id: String,
    val displayName: String,
    val status: AccessStatus
)

/**
 * Customer management state for the seller profile screen
 */
data class CustomerManagementState(
    val knownClientIds: List<String> = emptyList(),
    val approvedBuyers: List<BuyerEntry> = emptyList(),
    val blockedBuyers: List<BuyerEntry> = emptyList()
) {
    val allClientIds: List<String>
        get() = knownClientIds + blockedBuyers.map { it.id }
}

/**
 * Invitation management state for the seller profile screen
 */
data class InvitationManagementState(
    val currentInvitation: Invitation? = null,
    val deepLink: String? = null,
    val isGenerating: Boolean = false,
    val isSendingToBuyer: Boolean = false,
    val lastSentInvitation: Invitation? = null
)

/**
 * @deprecated Use profileState: AsyncState<SellerProfile>, statsState: ProfileStats, dialogState: ProfileDialogState instead
 */
@Deprecated("Use separate state flows: profileState, statsState, dialogState")
data class SellerProfileUiState(
    val isLoading: Boolean = false,
    val profile: SellerProfile? = null,
    val error: String? = null,
    val showMarketDialog: Boolean = false,
    val editingMarket: Market? = null,
    val showPaymentInfo: Boolean = false,
    val productCount: Int = 0,
    val orderCount: Int = 0
)
