package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_CHANGED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for Seller Overview/Dashboard screen
 */
class OverviewViewModel(
    private val articleRepository: ArticleRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OverviewUiState>(OverviewUiState.Loading)
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    private val articles = mutableListOf<Article>()
    private var activeOrdersCount = 0

    init {
        loadOverview()
    }

    private fun loadOverview() {
        viewModelScope.launch {
            _uiState.value = OverviewUiState.Loading

            // Get current user ID (seller ID)
            val sellerId = authRepository.getCurrentUserId()
            if (sellerId == null) {
                _uiState.value = OverviewUiState.Error("Not authenticated")
                return@launch
            }

            // Observe both articles and orders
            launch {
                // Observe articles for current seller (empty string = current user)
                articleRepository.getArticles("")
                    .catch { e ->
                        _uiState.value = OverviewUiState.Error("Failed to load articles: ${e.message}")
                    }
                    .collect { article ->
                        // Update articles list based on mode
                        when (article.mode) {
                            MODE_ADDED -> articles.add(article)
                            MODE_CHANGED -> {
                                val index = articles.indexOfFirst { it.id == article.id }
                                if (index >= 0) articles[index] = article
                            }
                            MODE_REMOVED -> articles.removeAll { it.id == article.id }
                        }

                        // Update UI state with current data
                        updateUiState()
                    }
            }

            launch {
                // Observe orders for current seller
                orderRepository.observeSellerOrders(sellerId)
                    .catch { e ->
                        println("⚠️ Failed to load orders: ${e.message}")
                        // Don't fail the whole screen, just show 0 orders
                    }
                    .collect { orders ->
                        // Count only active orders (not completed, cancelled, or outdated)
                        activeOrdersCount = orders.count { it.isActiveOrder() }
                        println("📊 Active orders count: $activeOrdersCount (total: ${orders.size})")

                        // Update UI state with current data
                        updateUiState()
                    }
            }
        }
    }

    private fun updateUiState() {
        _uiState.value = OverviewUiState.Success(
            totalProducts = articles.size,
            activeOrders = activeOrdersCount,
            totalRevenue = 0.0, // TODO: Calculate from orders
            recentArticles = articles.toList(),
            recentOrders = emptyList() // TODO: Get from order repository
        )
    }

    fun refresh() {
        articles.clear()
        activeOrdersCount = 0
        loadOverview()
    }

    fun deleteArticles(articleIds: Set<String>) {
        viewModelScope.launch {
            // Get current seller ID
            val sellerId = authRepository.getCurrentUserId()
            if (sellerId == null) {
                println("❌ Cannot delete articles: User not authenticated")
                _uiState.value = OverviewUiState.Error("Authentication required to delete products")
                return@launch
            }

            // Delete each article from Firebase
            articleIds.forEach { articleId ->
                try {
                    println("🗑️ Deleting article from Firebase: $articleId")
                    val result = articleRepository.deleteArticle(sellerId, articleId)
                    result.onSuccess {
                        println("✅ Successfully deleted article: $articleId")
                        // Remove from local list
                        articles.removeAll { it.id == articleId }
                    }.onFailure { error ->
                        println("❌ Failed to delete article $articleId: ${error.message}")
                    }
                } catch (e: Exception) {
                    println("❌ Exception deleting article $articleId: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Update UI state after all deletions
            updateUiState()
        }
    }

    fun updateArticlesAvailability(articleIds: Set<String>, available: Boolean) {
        viewModelScope.launch {
            // Get current seller ID
            val sellerId = authRepository.getCurrentUserId()
            if (sellerId == null) {
                println("❌ Cannot update articles: User not authenticated")
                _uiState.value = OverviewUiState.Error("Authentication required to update products")
                return@launch
            }

            println("📝 Updating ${articleIds.size} articles to available=$available")

            // Update each article's availability in Firebase
            articleIds.forEach { articleId ->
                try {
                    // Find the article in local list
                    val article = articles.find { it.id == articleId }
                    if (article != null) {
                        // Update the article with new availability
                        val updatedArticle = article.copy(available = available)
                        println("📝 Updating article in Firebase: ${article.productName} (id=$articleId) -> available=$available")

                        val result = articleRepository.saveArticle(sellerId, updatedArticle)
                        result.onSuccess {
                            println("✅ Successfully updated article: ${article.productName}")
                            // Update local list
                            val index = articles.indexOfFirst { it.id == articleId }
                            if (index >= 0) {
                                articles[index] = updatedArticle
                            }
                        }.onFailure { error ->
                            println("❌ Failed to update article $articleId: ${error.message}")
                        }
                    } else {
                        println("⚠️ Article $articleId not found in local list")
                    }
                } catch (e: Exception) {
                    println("❌ Exception updating article $articleId: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Update UI state after all updates
            updateUiState()
        }
    }
}

sealed interface OverviewUiState {
    data object Loading : OverviewUiState
    data class Success(
        val totalProducts: Int,
        val activeOrders: Int,
        val totalRevenue: Double,
        val recentArticles: List<Article>,
        val recentOrders: List<Order>
    ) : OverviewUiState
    data class Error(val message: String) : OverviewUiState
}
