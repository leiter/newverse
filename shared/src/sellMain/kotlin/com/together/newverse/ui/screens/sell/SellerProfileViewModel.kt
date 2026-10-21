package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.AccessRequest
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Invitation
import com.together.newverse.domain.model.InvitationStatus
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.MarketClosure
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.SellerContact
import com.together.newverse.domain.model.SellerUiAction
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.InvitationRepository
import com.together.newverse.domain.repository.MarketRepository
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
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.repository.SellerArticleRepository
import com.together.newverse.domain.repository.SellerUiActionRepository
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
    private val marketRepository: MarketRepository,
    /**
     * Supplies the purchase prices the market dialog's below-cost warning needs.
     *
     * Optional so the three existing test fixtures keep constructing this ViewModel
     * unchanged; without it the warning and the preview simply do not appear, which
     * is the same thing an uncosted catalogue produces.
     */
    private val sellerArticleRepository: SellerArticleRepository? = null,
    /**
     * The buttons this seller publishes into the buy app.
     *
     * Optional for the same reason as [sellerArticleRepository]: the existing test
     * fixtures construct this ViewModel unchanged, and without it the section simply
     * does not appear.
     */
    private val sellerUiActionRepository: SellerUiActionRepository? = null,
    private val invitationRepository: InvitationRepository = object : InvitationRepository {
        override suspend fun createInvitation(sellerId: String, sellerDisplayName: String, expiresInMillis: Long, targetBuyerId: String?) = Result.failure<Invitation>(Exception("Not configured"))
        override suspend fun getInvitation(invitationId: String) = Result.failure<Invitation>(Exception("Not configured"))
        override suspend fun acceptInvitation(invitationId: String, buyerId: String) = Result.failure<Invitation>(Exception("Not configured"))
        override suspend fun rejectInvitation(invitationId: String, buyerId: String) = Result.failure<Unit>(Exception("Not configured"))
        override fun observePendingInvitations(buyerId: String) = kotlinx.coroutines.flow.flowOf(emptyList<Invitation>())
        override suspend fun revokeInvitation(invitationId: String) = Result.failure<Unit>(Exception("Not configured"))
    }
) : ViewModel() {

    /**
     * The catalogue with its seller-only half, for pricing the market dialog's preview
     * and finding what a condition would sell below cost.
     *
     * Collected once rather than per dialog opening: the dialog is a composable with no
     * scope of its own, and re-subscribing on every open would re-read the whole
     * catalogue each time the seller edited a market's opening hours.
     */
    private val _pricingArticles = MutableStateFlow<List<SellerArticle>>(emptyList())
    val pricingArticles: StateFlow<List<SellerArticle>> = _pricingArticles.asStateFlow()

    private fun observePricingArticles() {
        val repo = sellerArticleRepository ?: return
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            repo.observeSellerArticles(sellerId)
                .catch { e ->
                    // Non-fatal: the warning and the preview are an aid, and losing
                    // them must not stop a seller editing a market.
                    Log.w(TAG) { "observePricingArticles: ${e.message}" }
                }
                .collect { articles -> _pricingArticles.value = articles }
        }
    }

    // Profile state using AsyncState pattern
    private val _profileState = MutableStateFlow<AsyncState<SellerProfile>>(AsyncState.Loading)
    val profileState: StateFlow<AsyncState<SellerProfile>> = _profileState.asStateFlow()

    // Stats state
    private val _statsState = MutableStateFlow(ProfileStats())
    val statsState: StateFlow<ProfileStats> = _statsState.asStateFlow()

    /**
     * How many live orders sit on each pickup day, keyed `yyyyMMdd`.
     *
     * Counted per date rather than per market: `Order.marketId` is not yet filled in
     * (see `doc/guides/market-pricing-and-access-design.md` §1), so a day is treated as
     * having orders regardless of which stall they belong to. That errs towards
     * refusing a closure, which is the safe direction.
     */
    private val _ordersByCycle = MutableStateFlow<Map<String, Int>>(emptyMap())
    val ordersByCycle: StateFlow<Map<String, Int>> = _ordersByCycle.asStateFlow()

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
        observePricingArticles()
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
                            .map { BuyerEntry(it.buyerId, it.displayName, it.status, it.allowedMarkets) },
                        blockedBuyers = buyers
                            .filter { it.status == AccessStatus.BLOCKED }
                            .map { BuyerEntry(it.buyerId, it.displayName, it.status, it.allowedMarkets) }
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
    /**
     * Mint a buyer link, optionally scoped to particular markets.
     *
     * An empty list invites the buyer to every market, which is what a link meant
     * before scoping existed. Scoping it is how "you are invited to the Kreuzberg
     * stall" is expressed — and the link is meant to be scanned at a stall, so the
     * market is usually known when it is generated.
     */
    fun generateBuyerLink(grantsMarkets: List<String> = emptyList()) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.createInviteToken(
                sellerId, QR_LINK_HINT, INVITE_TOKEN_TTL_MILLIS,
                grantsMarkets = grantsMarkets
            )
                .onSuccess { token ->
                    _generatedBuyerLink.value =
                        "https://cutthecrap.link/connect?seller=$sellerId&token=$token"
                    Log.d(TAG) { "generateBuyerLink: minted a token for sellerId=$sellerId" }
                }
                .onFailure { e -> Log.e(TAG) { "generateBuyerLink: ${e.message}" } }
        }
    }

    /** Narrow or widen where an approved buyer may collect. Empty means everywhere. */
    fun setAllowedMarkets(buyerId: String, markets: List<String>) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            profileRepository.setAllowedMarkets(sellerId, buyerId, markets)
                .onFailure { e -> Log.e(TAG) { "setAllowedMarkets: ${e.message}" } }
        }
    }

    /**
     * Admit a buyer who asked to place real orders.
     *
     * The grant comes from the request: a buyer asks to collect at a particular
     * stall, and approving says yes to that, not to everywhere. [allowedMarkets]
     * overrides it only when the seller deliberately widens or narrows the answer.
     * A request naming no market asks to order anywhere, which is what a
     * single-market seller's buyers mean and what every request made before
     * requests carried a market meant.
     */
    fun approveRequest(buyerId: String, allowedMarkets: List<String>? = null) {
        viewModelScope.launch {
            val sellerId = authRepository.getCurrentUserId() ?: return@launch
            val request = _accessRequests.value.find { it.buyerId == buyerId }
            val granted = allowedMarkets
                ?: listOfNotNull(request?.marketId?.takeIf { it.isNotBlank() })
            profileRepository.approveAccessRequest(
                sellerId, buyerId, request?.buyerDisplayName ?: "", granted
            )
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

            currentSellerId = sellerId

            profileRepository.getSellerProfile(sellerId).fold(
                onSuccess = { profile ->
                    // The customer roster is not part of the profile any more;
                    // observeBuyers owns _customerState.
                    _profileState.value = AsyncState.Success(profile)

                    // A seller whose markets are still in the profile list moves
                    // them across the first time they open this screen, so the app
                    // does not depend on the migration script having run against
                    // their project. Add-only, so re-running changes nothing.
                    marketRepository.migrateLegacyMarkets(sellerId, profile.markets)
                    observeMarketsForSeller(sellerId)
                    observeUiActionsForSeller(sellerId)
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

    /** Set once the profile loads; market writes need it and are refused without it. */
    private var currentSellerId: String? = null

    /**
     * Keep [profileState]'s markets in step with the node.
     *
     * The profile object stays the shape the screen reads, so nothing downstream had
     * to change; only where the list comes from did.
     */
    private fun observeMarketsForSeller(sellerId: String) {
        viewModelScope.launch {
            marketRepository.observeMarkets(sellerId).collect { markets ->
                _profileState.update { state ->
                    val profile = (state as? AsyncState.Success)?.data ?: return@update state
                    AsyncState.Success(profile.copy(markets = markets))
                }
            }
        }
    }

    /**
     * The buttons this seller has published, live.
     *
     * Its own flow rather than a field on the profile, because the actions are their
     * own node: folding them into [SellerProfile] would mean a profile write could
     * silently drop a button, which is exactly the coupling markets were moved out of
     * the profile to escape.
     */
    private val _uiActions = MutableStateFlow<List<SellerUiAction>>(emptyList())
    val uiActions: StateFlow<List<SellerUiAction>> = _uiActions.asStateFlow()

    private fun observeUiActionsForSeller(sellerId: String) {
        val repo = sellerUiActionRepository ?: return
        viewModelScope.launch {
            repo.observeActions(sellerId)
                .catch { e ->
                    // Non-fatal: the editor shows an empty list, and the buy app shows
                    // no buttons. Neither is worth failing the profile screen over.
                    Log.w(TAG) { "observeUiActionsForSeller: ${e.message}" }
                }
                .collect { actions -> _uiActions.value = actions }
        }
    }

    /**
     * Publish or update one button.
     *
     * [onResult] carries the repository's refusal — a full slot, or a row with no
     * label — so the screen can say which it was instead of the save appearing to
     * work and nothing changing. The live flow delivers the new row; nothing is
     * written into [_uiActions] here.
     */
    fun saveUiAction(action: SellerUiAction, onResult: (Result<SellerUiAction>) -> Unit = {}) {
        val repo = sellerUiActionRepository ?: return
        viewModelScope.launch {
            val sellerId = currentSellerId ?: authRepository.getCurrentUserId() ?: return@launch
            val result = repo.saveAction(sellerId, action)
            result.onFailure { e -> Log.w(TAG) { "saveUiAction: refused - ${e.message}" } }
            onResult(result)
        }
    }

    /** Take a button away. A delete, not a flag: nothing references an action by id. */
    fun deleteUiAction(actionId: String) {
        val repo = sellerUiActionRepository ?: return
        viewModelScope.launch {
            val sellerId = currentSellerId ?: authRepository.getCurrentUserId() ?: return@launch
            repo.deleteAction(sellerId, actionId)
                .onFailure { e -> Log.w(TAG) { "deleteUiAction: ${e.message}" } }
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

                        // Which pickup days already have orders on them. Closing one
                        // of those days would send buyers to a market that is not
                        // there, so the closures editor refuses it.
                        _ordersByCycle.value = orders
                            .filter { it.status != OrderStatus.CANCELLED }
                            .groupingBy {
                                MarketClosure.dateKey(
                                    Instant.fromEpochMilliseconds(it.pickUpDate),
                                    TimeZone.currentSystemDefault()
                                )
                            }
                            .eachCount()
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

    /** Replace the business's own contact list — the markets' own lists are untouched. */
    fun saveSellerContacts(contacts: List<SellerContact>) {
        val currentProfile = (_profileState.value as? AsyncState.Success)?.data ?: return
        saveProfile(currentProfile.copy(contacts = contacts))
    }

    /** Replace the list of days on which a market does not happen. */
    fun saveClosures(closures: List<MarketClosure>) {
        val currentProfile = (_profileState.value as? AsyncState.Success)?.data ?: return
        saveProfile(currentProfile.copy(closures = closures))
    }

    /**
     * Markets now live in their own node, so adding and editing one is a single
     * write there rather than a rewrite of the whole profile. The profile's legacy
     * list is left untouched: the migration script moves it, and
     * [observeMarketsForSeller] keeps reading it until that has happened.
     */
    fun addMarket(market: Market) = persistMarket(market)

    fun updateMarket(market: Market) = persistMarket(market)

    private fun persistMarket(market: Market) {
        val sellerId = currentSellerId ?: return
        viewModelScope.launch {
            _isSaving.value = true
            marketRepository.saveMarket(sellerId, market)
                .onFailure { Log.e(TAG) { "persistMarket: ${it.message}" } }
            _isSaving.value = false
        }
    }

    /**
     * Stop offering a market, keeping its row.
     *
     * Deleting it would leave `Order.marketId` on every past order pointing at
     * nothing, and those orders have to stay readable — the seller's books are
     * built from them.
     */
    fun removeMarket(marketId: String) {
        val sellerId = currentSellerId ?: return
        viewModelScope.launch {
            _isSaving.value = true
            marketRepository.deactivateMarket(sellerId, marketId)
                .onFailure { Log.e(TAG) { "removeMarket: ${it.message}" } }
            _isSaving.value = false
        }
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
    val status: AccessStatus,
    /** Which markets this buyer may collect at. Empty means all of them. */
    val allowedMarkets: List<String> = emptyList()
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
