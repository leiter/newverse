package com.together.newverse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_CHANGED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.ProfileRepository
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
    val selectedQuantity: Double = 0.0,
    val cartItemCount: Int = 0,
    val basketItems: List<OrderedProduct> = emptyList(),
    val favouriteArticles: List<String> = emptyList(), // List of favourite article IDs
    val error: String? = null
)

/**
 * Actions for MainScreenModern
 */
sealed interface MainScreenAction {
    data class SelectArticle(val article: Article) : MainScreenAction
    data class UpdateQuantity(val quantity: Double) : MainScreenAction
    data class UpdateQuantityText(val text: String) : MainScreenAction
    data object AddToCart : MainScreenAction
    data object RemoveFromBasket : MainScreenAction
    data class ToggleFavourite(val articleId: String) : MainScreenAction
    data object Refresh : MainScreenAction
}

/**
 * ViewModel for MainScreenModern
 */
class MainScreenViewModel(
    private val articleRepository: ArticleRepository,
    private val authRepository: AuthRepository,
    private val basketRepository: BasketRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MainScreenState())
    val state: StateFlow<MainScreenState> = _state.asStateFlow()

    init {
        // Wait for authentication before loading articles
        waitForAuthThenLoad()

        // Observe basket to update cart item count
        observeBasket()

        // Observe buyer profile to update favourite articles
        observeBuyerProfile()
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
            is MainScreenAction.UpdateQuantityText -> updateQuantityFromText(action.text)
            MainScreenAction.AddToCart -> addToCart()
            MainScreenAction.RemoveFromBasket -> removeFromBasket()
            is MainScreenAction.ToggleFavourite -> toggleFavourite(action.articleId)
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
        // Check if this product is already in the basket
        val basketItems = basketRepository.observeBasket().value
        val existingItem = basketItems.find { it.productId == article.id }

        // If it exists, pre-populate the quantity with the existing amount
        val initialQuantity = existingItem?.amountCount ?: 0.0

        _state.value = _state.value.copy(
            selectedArticle = article,
            selectedQuantity = initialQuantity
        )

        println("🎯 MainScreenViewModel.selectArticle: Selected ${article.productName}, existing quantity: $initialQuantity")
    }

    private fun updateQuantity(quantity: Double) {
        _state.value = _state.value.copy(
            selectedQuantity = quantity.coerceAtLeast(0.0)
        )
    }

    private fun updateQuantityFromText(text: String) {
        val quantity = text.replace(",", ".").toDoubleOrNull() ?: 0.0
        updateQuantity(quantity)
    }

    private fun addToCart() {
        val selectedArticle = _state.value.selectedArticle ?: return
        val quantity = _state.value.selectedQuantity

        if (quantity <= 0.0) {
            // If quantity is 0, remove from basket if it exists
            viewModelScope.launch {
                basketRepository.removeItem(selectedArticle.id)
            }
            return
        }

        // Check if item already exists in basket
        val basketItems = basketRepository.observeBasket().value
        val existingItem = basketItems.find { it.productId == selectedArticle.id }

        if (existingItem != null) {
            // Update existing item quantity
            viewModelScope.launch {
                basketRepository.updateQuantity(selectedArticle.id, quantity)
            }
            println("🛒 MainScreenViewModel.addToCart: Updated ${selectedArticle.productName} to ${quantity} ${selectedArticle.unit}")
        } else {
            // Create new OrderedProduct from selected article and quantity
            val orderedProduct = OrderedProduct(
                id = "", // Will be generated when order is placed
                productId = selectedArticle.id,
                productName = selectedArticle.productName,
                unit = selectedArticle.unit,
                price = selectedArticle.price,
                amount = quantity.toString(),
                amountCount = quantity,
                piecesCount = if (selectedArticle.unit.lowercase() in listOf("kg", "g")) {
                    (quantity / selectedArticle.weightPerPiece).toInt()
                } else {
                    quantity.toInt()
                }
            )

            // Add to basket via BasketRepository
            viewModelScope.launch {
                basketRepository.addItem(orderedProduct)
            }
            println("🛒 MainScreenViewModel.addToCart: Added ${selectedArticle.productName} (${quantity} ${selectedArticle.unit}) to basket")
        }

    }

    private fun removeFromBasket() {
        val selectedArticle = _state.value.selectedArticle ?: return

        viewModelScope.launch {
            basketRepository.removeItem(selectedArticle.id)
            // Reset quantity to 0 after removing
            _state.value = _state.value.copy(selectedQuantity = 0.0)
        }

        println("🗑️ MainScreenViewModel.removeFromBasket: Removed ${selectedArticle.productName} from basket")
    }

    private fun observeBasket() {
        viewModelScope.launch {
            basketRepository.observeBasket().collect { basketItems ->
                _state.value = _state.value.copy(
                    cartItemCount = basketItems.size,
                    basketItems = basketItems
                )
            }
        }
    }

    private fun observeBuyerProfile() {
        viewModelScope.launch {
            profileRepository.observeBuyerProfile().collect { profile ->
                _state.value = _state.value.copy(
                    favouriteArticles = profile?.favouriteArticles ?: emptyList()
                )
            }
        }
    }

    private fun toggleFavourite(articleId: String) {
        viewModelScope.launch {
            try {
                println("⭐ MainScreenViewModel.toggleFavourite: START - articleId=$articleId")

                // Get current buyer profile
                val profileResult = profileRepository.getBuyerProfile()
                val currentProfile = profileResult.getOrNull()

                if (currentProfile == null) {
                    println("❌ MainScreenViewModel.toggleFavourite: No buyer profile found")
                    return@launch
                }

                // Check if article is already a favourite
                val isFavourite = currentProfile.favouriteArticles.contains(articleId)
                val updatedFavourites = if (isFavourite) {
                    // Remove from favourites
                    currentProfile.favouriteArticles.filter { it != articleId }
                } else {
                    // Add to favourites
                    currentProfile.favouriteArticles + articleId
                }

                println("⭐ MainScreenViewModel.toggleFavourite: ${if (isFavourite) "Removing" else "Adding"} article")

                // Update profile with new favourites list
                val updatedProfile = currentProfile.copy(favouriteArticles = updatedFavourites)
                val saveResult = profileRepository.saveBuyerProfile(updatedProfile)

                saveResult.onSuccess {
                    println("✅ MainScreenViewModel.toggleFavourite: Successfully updated favourites")
                }.onFailure { error ->
                    println("❌ MainScreenViewModel.toggleFavourite: Failed to save - ${error.message}")
                }

            } catch (e: Exception) {
                println("❌ MainScreenViewModel.toggleFavourite: Exception - ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun refresh() {
        // Cancel current collection and restart
        loadArticles()
    }
}
