package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_CHANGED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.repository.ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel for Seller Overview/Dashboard screen
 */
class OverviewViewModel(
    private val articleRepository: ArticleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OverviewUiState>(OverviewUiState.Loading)
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    private val articles = mutableListOf<Article>()

    init {
        loadOverview()
    }

    private fun loadOverview() {
        viewModelScope.launch {
            _uiState.value = OverviewUiState.Loading

            // Observe articles for current seller (empty string = current user)
            articleRepository.getArticles("")
                .catch { e ->
                    _uiState.value = OverviewUiState.Error("Failed to load overview: ${e.message}")
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
                    _uiState.value = OverviewUiState.Success(
                        totalProducts = articles.size,
                        activeOrders = 0, // TODO: Get from order repository
                        totalRevenue = 0.0, // TODO: Calculate from orders
                        recentArticles = articles.takeLast(5),
                        recentOrders = emptyList() // TODO: Get from order repository
                    )
                }
        }
    }

    fun refresh() {
        articles.clear()
        loadOverview()
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
