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
import com.together.newverse.util.Log


private const val TAG = "SellerProfileVM"

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
        observeBuyers()
    }

    private fun observeAccessRequests() {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.observeAccessRequests(sellerId)
                .catch { e -> Log.e(TAG) { "observeAccessRequests: ${e.message}" } }
                .collect { requests -> _accessRequests.value = requests }
        }
    }

    /**
     * buyer_access_status is the whole customer roster. Names arrive in the record
     * itself - written by the buyer on request or redemption - so there is nothing to
     * resolve per entry, and both lists come from one observer.
     */
    private fun observeBuyers() {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.observeBuyers(sellerId)
                .catch { e -> Log.e(TAG) { "observeBuyers: ${e.message}" } }
                .collect { buyers ->
                    Log.d(TAG) { "observeBuyers: ${buyers.size} access records" }
                    _customerState.value = CustomerManagementState(
                        approvedBuyers = buyers
                            .filter { it.status == AccessStatus.APPROVED }
                            .map { BuyerEntry(it.buyerId, it.displayName, it.status) },
                        blockedBuyers = buyers
                            .filter { it.status == AccessStatus.BLOCKED }
                            .map { BuyerEntry(it.buyerId, it.displayName, it.status) }
                    )
                }
        }
    }

    /**
     * Mint an invite token and build the link around it. The token is written first
     * and the link only published once it exists, so a scanned code can always be
     * redeemed - the old flow pre-approved a bare uuid in the background, which the
     * buyer then had no way to claim.
     */
    fun generateBuyerLink() {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.createInviteToken(sellerId, QR_LINK_HINT, INVITE_TOKEN_TTL_MILLIS)
                .onSuccess { token ->
                    _generatedBuyerLink.value =
                        "https://cutthecrap.link/connect?seller=$sellerId&token=$token"
                    Log.d(TAG) { "generateBuyerLink: minted a token for sellerId=$sellerId" }
                }
                .onFailure { e -> Log.e(TAG) { "generateBuyerLink: ${e.message}" } }
        }
    }

    fun approveRequest(buyerId: String) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            val displayName = _accessRequests.value.find { it.buyerId == buyerId }?.buyerDisplayName ?: ""
            profileRepository.approveAccessRequest(sellerId, buyerId, displayName)
                .onFailure { e -> Log.e(TAG) { "approveRequest: ${e.message}" } }
        }
    }

    // No optimistic local update: observeBuyers is the single source of truth and
    // reflects the write as soon as Firebase echoes it back.

    fun blockBuyer(buyerId: String) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.blockBuyer(sellerId, buyerId)
                .onFailure { e -> Log.e(TAG) { "blockBuyer: ${e.message}" } }
        }
    }

    fun unblockBuyer(buyerId: String) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.unblockBuyer(sellerId, buyerId)
                .onFailure { e -> Log.e(TAG) { "unblockBuyer: ${e.message}" } }
        }
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
                    // The customer roster is not part of the profile any more;
                    // observeBuyers owns _customerState.
                    _profileState.value = AsyncState.Success(profile)
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
                    Log.e(TAG) { "Failed to save profile: ${e.message}" }
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
                    // The invitation doubles as its own invite token, so accepting it
                    // redeems through exactly the same path as a scanned QR link.
                    launch {
                        profileRepository.createInviteToken(
                            sellerId, "Invitation", INVITE_TOKEN_TTL_MILLIS, token = invitation.id
                        ).onFailure { e -> Log.e(TAG) { "Invite token for invitation failed: ${e.message}" } }
                    }
                },
                onFailure = { e ->
                    Log.e(TAG) { "Failed to generate invitation: ${e.message}" }
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
                    Log.e(TAG) { "Failed to send invitation: ${e.message}" }
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
                    Log.e(TAG) { "Failed to revoke invitation: ${e.message}" }
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
/** Placeholder name on a minted token, until the buyer redeems it and supplies theirs. */
const val QR_LINK_HINT = "QR-Link"

/** Invite links are meant to be scanned at a market stall, not hoarded. */
const val INVITE_TOKEN_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000

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
    val approvedBuyers: List<BuyerEntry> = emptyList(),
    val blockedBuyers: List<BuyerEntry> = emptyList()
)

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
