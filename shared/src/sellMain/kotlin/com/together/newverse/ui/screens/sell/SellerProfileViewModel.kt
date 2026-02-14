package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.ui.state.core.AsyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for Seller Profile screen
 */
class SellerProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val articleRepository: ArticleRepository,
    private val orderRepository: OrderRepository
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

    // Saving state
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val articles = mutableListOf<Article>()

    init {
        loadProfile()
        loadStats()
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
