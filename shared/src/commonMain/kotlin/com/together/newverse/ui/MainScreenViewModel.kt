package com.together.newverse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_CHANGED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * State for MainScreenModern
 */
data class MainScreenState(
    val isLoading: Boolean = true,
    val articles: List<Article> = emptyList(),
    val selectedArticle: Article? = null,
    val selectedQuantity: Int = 0,
    val cartItemCount: Int = 0,
    val error: String? = null
)

/**
 * Actions for MainScreenModern
 */
sealed interface MainScreenAction {
    data class SelectArticle(val article: Article) : MainScreenAction
    data class UpdateQuantity(val quantity: Int) : MainScreenAction
    data object AddToCart : MainScreenAction
    data object Refresh : MainScreenAction
}

/**
 * ViewModel for MainScreenModern
 */
class MainScreenViewModel(
    private val articleRepository: ArticleRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MainScreenState())
    val state: StateFlow<MainScreenState> = _state.asStateFlow()

    init {
        // Wait for authentication before loading articles
        waitForAuthThenLoad()
    }

    /**
     * Wait for user to be authenticated (or become guest) before loading articles
     */
    private fun waitForAuthThenLoad() {
        viewModelScope.launch {
            try {
                println("🎬 MainScreenViewModel.waitForAuthThenLoad: START - Waiting for auth state...")

                // Wait for auth state to be ready (non-null user ID)
                val userId = authRepository.observeAuthState()
                    .filterNotNull()
                    .first()

                println("🎬 MainScreenViewModel.waitForAuthThenLoad: User authenticated with ID: $userId")
                println("🎬 MainScreenViewModel.waitForAuthThenLoad: Calling loadArticles()...")

                // Now that we have an authenticated user, load articles
                loadArticles()
            } catch (e: Exception) {
                println("❌ MainScreenViewModel.waitForAuthThenLoad: ERROR - ${e.message}")
                e.printStackTrace()
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Authentication required to load articles"
                )
            }
        }
    }

    fun onAction(action: MainScreenAction) {
        when (action) {
            is MainScreenAction.SelectArticle -> selectArticle(action.article)
            is MainScreenAction.UpdateQuantity -> updateQuantity(action.quantity)
            MainScreenAction.AddToCart -> addToCart()
            MainScreenAction.Refresh -> refresh()
        }
    }

    private fun loadArticles() {
        viewModelScope.launch {
            println("🎬 MainScreenViewModel.loadArticles: START")

            _state.value = _state.value.copy(isLoading = true, error = null)
            println("🎬 MainScreenViewModel.loadArticles: Set loading state to true")

            // Load articles for a specific seller or use empty string for current user
            // TODO: Get sellerId from somewhere (settings, selected market, etc.)
            val sellerId = "" // Empty string for current authenticated user
            println("🎬 MainScreenViewModel.loadArticles: Calling articleRepository.getArticles(sellerId='$sellerId')")

            articleRepository.getArticles(sellerId)
                .catch { e ->
                    println("❌ MainScreenViewModel.loadArticles: ERROR - ${e.message}")
                    e.printStackTrace()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to load articles: ${e.message}"
                    )
                }
                .collect { article ->
                    println("🎬 MainScreenViewModel.loadArticles: Received article event - mode=${article.mode}, id=${article.id}, name=${article.productName}")

                    val currentArticles = _state.value.articles.toMutableList()
                    val beforeCount = currentArticles.size

                    when (article.mode) {
                        MODE_ADDED -> {
                            currentArticles.add(article)
                            println("🎬 MainScreenViewModel.loadArticles: ADDED article '${article.productName}' (id=${article.id})")
                        }
                        MODE_CHANGED -> {
                            val index = currentArticles.indexOfFirst { it.id == article.id }
                            if (index >= 0) {
                                currentArticles[index] = article
                                println("🎬 MainScreenViewModel.loadArticles: CHANGED article '${article.productName}' at index $index")
                            }
                        }
                        MODE_REMOVED -> {
                            currentArticles.removeAll { it.id == article.id }
                            println("🎬 MainScreenViewModel.loadArticles: REMOVED article '${article.productName}' (id=${article.id})")
                        }
                        // MODE_MOVED typically doesn't need special handling
                    }

                    val afterCount = currentArticles.size
                    println("🎬 MainScreenViewModel.loadArticles: Article count: $beforeCount → $afterCount")

                    // Auto-select first article if none selected
                    val selectedArticle = _state.value.selectedArticle
                        ?: currentArticles.firstOrNull()

                    _state.value = _state.value.copy(
                        isLoading = false,
                        articles = currentArticles,
                        selectedArticle = selectedArticle,
                        error = null
                    )
                }
        }
    }

    private fun selectArticle(article: Article) {
        _state.value = _state.value.copy(
            selectedArticle = article,
            selectedQuantity = 0
        )
    }

    private fun updateQuantity(quantity: Int) {
        _state.value = _state.value.copy(
            selectedQuantity = quantity.coerceAtLeast(0)
        )
    }

    private fun addToCart() {
        // TODO: Implement cart functionality
        val currentCount = _state.value.cartItemCount
        _state.value = _state.value.copy(
            cartItemCount = currentCount + 1,
            selectedQuantity = 0
        )
    }

    private fun refresh() {
        // Cancel current collection and restart
        loadArticles()
    }
}
