package com.together.newverse.ui.screens.buy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_CHANGED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.repository.ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Actions that can be performed on the Products screen
 */
sealed interface ProductsAction {
    data class AddToBasket(val article: Article) : ProductsAction
    data object Refresh : ProductsAction
}

/**
 * State for the Products screen
 */
data class ProductsScreenState(
    val isLoading: Boolean = false,
    val articles: List<Article> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for Products/Browse screen
 */
class ProductsViewModel(
    private val articleRepository: ArticleRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProductsScreenState(isLoading = true))
    val state: StateFlow<ProductsScreenState> = _state.asStateFlow()

    init {
        observeProducts()
    }

    fun onAction(action: ProductsAction) {
        when (action) {
            is ProductsAction.AddToBasket -> addToBasket(action.article)
            ProductsAction.Refresh -> observeProducts()
        }
    }

    private fun observeProducts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            // TODO: Get sellerId from somewhere (settings or selected seller)
            val sellerId = "" // Empty for now, could be a specific seller ID

            articleRepository.getArticles(sellerId)
                .catch { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to load products: ${e.message}"
                    )
                }
                .collect { article ->
                    val currentArticles = _state.value.articles.toMutableList()

                    when (article.mode) {
                        MODE_ADDED -> {
                            // Add new article
                            currentArticles.add(article)
                        }
                        MODE_CHANGED -> {
                            // Update existing article
                            val index = currentArticles.indexOfFirst { it.id == article.id }
                            if (index >= 0) {
                                currentArticles[index] = article
                            }
                        }
                        MODE_REMOVED -> {
                            // Remove article
                            currentArticles.removeAll { it.id == article.id }
                        }
                        // MODE_MOVED typically doesn't affect the list, just ordering
                    }

                    _state.value = _state.value.copy(
                        isLoading = false,
                        articles = currentArticles,
                        error = null
                    )
                }
        }
    }

    private fun addToBasket(article: Article) {
        // TODO: Implement basket functionality
    }

    private fun refresh() {
        observeProducts()
    }
}
