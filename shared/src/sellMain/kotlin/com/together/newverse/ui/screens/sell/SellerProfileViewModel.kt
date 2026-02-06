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

    private val _uiState = MutableStateFlow(SellerProfileUiState())
    val uiState: StateFlow<SellerProfileUiState> = _uiState.asStateFlow()

    private val articles = mutableListOf<Article>()

    init {
        loadProfile()
        loadStats()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val sellerId = authRepository.getCurrentUserId()
            if (sellerId == null) {
                _uiState.update { it.copy(isLoading = false, error = "Not authenticated") }
                return@launch
            }

            profileRepository.getSellerProfile(sellerId).fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            profile = profile,
                            error = null
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load profile"
                        )
                    }
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
                        _uiState.update { it.copy(productCount = articles.size) }
                    }
            }

            // Load order count
            launch {
                orderRepository.observeSellerOrders(sellerId)
                    .collect { orders ->
                        _uiState.update { it.copy(orderCount = orders.size) }
                    }
            }
        }
    }

    fun saveProfile(profile: SellerProfile) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            profileRepository.saveSellerProfile(profile).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            profile = profile,
                            error = null
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to save profile"
                        )
                    }
                }
            )
        }
    }

    fun addMarket(market: Market) {
        val currentProfile = _uiState.value.profile ?: return
        val updatedMarkets = currentProfile.markets + market
        val updatedProfile = currentProfile.copy(markets = updatedMarkets)
        saveProfile(updatedProfile)
    }

    fun updateMarket(market: Market) {
        val currentProfile = _uiState.value.profile ?: return
        val updatedMarkets = currentProfile.markets.map {
            if (it.id == market.id) market else it
        }
        val updatedProfile = currentProfile.copy(markets = updatedMarkets)
        saveProfile(updatedProfile)
    }

    fun removeMarket(marketId: String) {
        val currentProfile = _uiState.value.profile ?: return
        val updatedMarkets = currentProfile.markets.filter { it.id != marketId }
        val updatedProfile = currentProfile.copy(markets = updatedMarkets)
        saveProfile(updatedProfile)
    }

    fun showMarketDialog(market: Market? = null) {
        _uiState.update {
            it.copy(
                showMarketDialog = true,
                editingMarket = market
            )
        }
    }

    fun hideMarketDialog() {
        _uiState.update {
            it.copy(
                showMarketDialog = false,
                editingMarket = null
            )
        }
    }

    fun showPaymentInfo() {
        _uiState.update { it.copy(showPaymentInfo = true) }
    }

    fun hidePaymentInfo() {
        _uiState.update { it.copy(showPaymentInfo = false) }
    }

    fun refresh() {
        articles.clear()
        loadProfile()
        loadStats()
    }
}

/**
 * UI State for Seller Profile screen
 */
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
