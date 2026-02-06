package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.data.repository.GitLiveArticleRepository
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.ErrorState
import com.together.newverse.ui.state.ErrorType
import com.together.newverse.ui.state.ProductFilter
import com.together.newverse.ui.state.BuyMainScreenAction
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Main Screen extension functions for BuyAppViewModel
 *
 * Handles product browsing, selection, cart management, and favourites.
 *
 * Extracted functions:
 * - handleMainScreenAction
 * - Edit lock guard helpers (handleUpdateQuantity, handleUpdateQuantityFromText, handleAddToCart, handleRemoveFromBasket)
 * - showNewOrderSnackbar, dismissNewOrderSnackbar, startNewOrder
 * - setMainScreenFilter
 * - selectMainScreenArticle
 * - updateMainScreenQuantity, updateMainScreenQuantityFromText
 * - addMainScreenToCart, removeMainScreenFromBasket
 * - toggleMainScreenFavourite
 * - refreshMainScreen, loadMainScreenArticles
 * - observeMainScreenBasket
 */

internal fun BuyAppViewModel.handleMainScreenAction(action: BuyMainScreenAction) {
    when (action) {
        is BuyMainScreenAction.SelectArticle -> selectMainScreenArticle(action.article)
        is BuyMainScreenAction.ViewProductDetail -> { /* Handled by navigation layer */ }
        is BuyMainScreenAction.UpdateQuantity -> handleUpdateQuantity(action.quantity)
        is BuyMainScreenAction.UpdateQuantityText -> handleUpdateQuantityFromText(action.text)
        BuyMainScreenAction.AddToCart -> handleAddToCart()
        BuyMainScreenAction.RemoveFromBasket -> handleRemoveFromBasket()
        is BuyMainScreenAction.ToggleFavourite -> toggleMainScreenFavourite(action.articleId)
        is BuyMainScreenAction.SetFilter -> setMainScreenFilter(action.filter)
        is BuyMainScreenAction.UpdateSearchQuery -> updateSearchQuery(action.query)
        BuyMainScreenAction.ClearSearchQuery -> updateSearchQuery("")
        BuyMainScreenAction.Refresh -> refreshMainScreen()
        BuyMainScreenAction.DismissNewOrderSnackbar -> dismissNewOrderSnackbar()
        BuyMainScreenAction.StartNewOrder -> startNewOrder()
    }
}

private fun BuyAppViewModel.handleUpdateQuantity(quantity: Double) {
    if (!_state.value.mainScreen.canEditOrder) {
        showNewOrderSnackbar()
        return
    }
    updateMainScreenQuantity(quantity)
}

private fun BuyAppViewModel.handleUpdateQuantityFromText(text: String) {
    if (!_state.value.mainScreen.canEditOrder) {
        showNewOrderSnackbar()
        return
    }
    updateMainScreenQuantityFromText(text)
}

private fun BuyAppViewModel.handleAddToCart() {
    if (!_state.value.mainScreen.canEditOrder) {
        showNewOrderSnackbar()
        return
    }
    addMainScreenToCart()
}

private fun BuyAppViewModel.handleRemoveFromBasket() {
    if (!_state.value.mainScreen.canEditOrder) {
        showNewOrderSnackbar()
        return
    }
    removeMainScreenFromBasket()
}

private fun BuyAppViewModel.showNewOrderSnackbar() {
    _state.update { current ->
        current.copy(
            mainScreen = current.mainScreen.copy(
                showNewOrderSnackbar = true
            )
        )
    }
}

internal fun BuyAppViewModel.dismissNewOrderSnackbar() {
    _state.update { current ->
        current.copy(
            mainScreen = current.mainScreen.copy(
                showNewOrderSnackbar = false
            )
        )
    }
}

internal fun BuyAppViewModel.startNewOrder() {
    viewModelScope.launch {
        // Clear the basket to start fresh
        basketRepository.clearBasket()

        // Reset canEditOrder to true and hide snackbar
        _state.update { current ->
            current.copy(
                mainScreen = current.mainScreen.copy(
                    canEditOrder = true,
                    showNewOrderSnackbar = false
                ),
                basket = current.basket.copy(
                    currentOrderId = null,
                    currentOrderDate = null
                )
            )
        }
        println("🛒 Started new order - basket cleared")
    }
}

internal fun BuyAppViewModel.setMainScreenFilter(filter: ProductFilter) {
    _state.update { current ->
        current.copy(
            mainScreen = current.mainScreen.copy(
                activeFilter = filter
            )
        )
    }
}

internal fun BuyAppViewModel.updateSearchQuery(query: String) {
    _state.update { current ->
        current.copy(
            mainScreen = current.mainScreen.copy(
                searchQuery = query
            )
        )
    }
}

internal fun BuyAppViewModel.selectMainScreenArticle(article: Article) {
    // Check if this product is already in the basket
    val basketItems = basketRepository.observeBasket().value

    println("🎯 selectMainScreenArticle: Looking for article.id=${article.id} in ${basketItems.size} basket items")
    basketItems.forEach { item ->
        println("🎯   Basket item: id='${item.id}', productId='${item.productId}', name='${item.productName}', qty=${item.amountCount}")
    }

    val existingItem = basketItems.find { it.id == article.id || it.productId == article.id }

    // If it exists, pre-populate the quantity with the existing amount
    val initialQuantity = existingItem?.amountCount ?: 0.0

    _state.update { current ->
        current.copy(
            mainScreen = current.mainScreen.copy(
                selectedArticle = article,
                selectedQuantity = initialQuantity
            )
        )
    }

    println("🎯 BuyAppViewModel.selectMainScreenArticle: Selected ${article.productName}, existingItem=${existingItem != null}, quantity: $initialQuantity")
}

internal fun BuyAppViewModel.updateMainScreenQuantity(quantity: Double) {
    _state.update { current ->
        current.copy(
            mainScreen = current.mainScreen.copy(
                selectedQuantity = quantity.coerceAtLeast(0.0)
            )
        )
    }
}

internal fun BuyAppViewModel.updateMainScreenQuantityFromText(text: String) {
    val quantity = text.replace(",", ".").toDoubleOrNull() ?: 0.0
    updateMainScreenQuantity(quantity)
}

internal fun BuyAppViewModel.addMainScreenToCart() {
    val selectedArticle = _state.value.mainScreen.selectedArticle ?: return
    val quantity = _state.value.mainScreen.selectedQuantity

    if (quantity <= 0.0) {
        // If quantity is 0, remove from basket if it exists
        viewModelScope.launch {
            basketRepository.removeItem(selectedArticle.id)
        }
        return
    }

    // Check if item already exists in basket
    val basketItems = basketRepository.observeBasket().value
    val existingItem = basketItems.find { it.id == selectedArticle.id || it.productId == selectedArticle.id }

    if (existingItem != null) {
        // Update existing item quantity
        viewModelScope.launch {
            basketRepository.updateQuantity(selectedArticle.id, quantity)
        }
        println("🛒 BuyAppViewModel.addMainScreenToCart: Updated ${selectedArticle.productName} to ${quantity} ${selectedArticle.unit}")
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
        println("🛒 BuyAppViewModel.addMainScreenToCart: Added ${selectedArticle.productName} (${quantity} ${selectedArticle.unit}) to basket")
    }
}

internal fun BuyAppViewModel.removeMainScreenFromBasket() {
    val selectedArticle = _state.value.mainScreen.selectedArticle ?: return

    viewModelScope.launch {
        basketRepository.removeItem(selectedArticle.id)
        // Reset quantity to 0 after removing
        _state.update { current ->
            current.copy(
                mainScreen = current.mainScreen.copy(selectedQuantity = 0.0)
            )
        }
    }

    println("🗑️ BuyAppViewModel.removeMainScreenFromBasket: Removed ${selectedArticle.productName} from basket")
}

internal fun BuyAppViewModel.toggleMainScreenFavourite(articleId: String) {
    viewModelScope.launch {
        try {
            println("⭐ BuyAppViewModel.toggleMainScreenFavourite: START - articleId=$articleId")

            // Get current buyer profile
            val profileResult = profileRepository.getBuyerProfile()
            val currentProfile = profileResult.getOrNull()

            if (currentProfile == null) {
                println("❌ BuyAppViewModel.toggleMainScreenFavourite: No buyer profile found")
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

            println("⭐ BuyAppViewModel.toggleMainScreenFavourite: ${if (isFavourite) "Removing" else "Adding"} article")

            // Update profile with new favourites list
            val updatedProfile = currentProfile.copy(favouriteArticles = updatedFavourites)
            val saveResult = profileRepository.saveBuyerProfile(updatedProfile)

            saveResult.onSuccess {
                println("✅ BuyAppViewModel.toggleMainScreenFavourite: Successfully updated favourites")
                // Update local state immediately for instant UI feedback
                _state.update { current ->
                    current.copy(
                        mainScreen = current.mainScreen.copy(
                            favouriteArticles = updatedFavourites
                        )
                    )
                }
            }.onFailure { error ->
                println("❌ BuyAppViewModel.toggleMainScreenFavourite: Failed to save - ${error.message}")
            }

        } catch (e: Exception) {
            println("❌ BuyAppViewModel.toggleMainScreenFavourite: Exception - ${e.message}")
            e.printStackTrace()
        }
    }
}

internal fun BuyAppViewModel.refreshMainScreen() {
    loadMainScreenArticles()
}

/**
 * Load articles for MainScreen
 */
internal fun BuyAppViewModel.loadMainScreenArticles() {
    viewModelScope.launch {
        println("🎬 BuyAppViewModel.loadMainScreenArticles: START")

        _state.update { current ->
            current.copy(
                mainScreen = current.mainScreen.copy(
                    isLoading = true,
                    error = null
                )
            )
        }
        println("🎬 BuyAppViewModel.loadMainScreenArticles: Set loading state to true")

        // Load articles for a specific seller or use empty string for current user
        val sellerId = "" // Empty string for current authenticated user
        println("🎬 BuyAppViewModel.loadMainScreenArticles: Calling articleRepository.getArticles(sellerId='$sellerId')")

        articleRepository.getArticles(sellerId)
            .catch { e ->
                println("❌ BuyAppViewModel.loadMainScreenArticles: ERROR - ${e.message}")
                e.printStackTrace()
                _state.update { current ->
                    current.copy(
                        mainScreen = current.mainScreen.copy(
                            isLoading = false,
                            error = ErrorState(
                                message = e.message ?: "Failed to load articles",
                                type = ErrorType.NETWORK
                            )
                        )
                    )
                }
            }
            .collect { article ->
                println("🎬 BuyAppViewModel.loadMainScreenArticles: Received article event - mode=${article.mode}, id=${article.id}, name=${article.productName}, available=${article.available}")

                // Update state atomically to avoid race conditions
                _state.update { current ->
                    val currentArticles = current.mainScreen.articles.toMutableList()
                    val beforeCount = currentArticles.size

                    when (article.mode) {
                        Article.MODE_ADDED -> {
                            // Check if article already exists to avoid duplicates
                            val existingIndex = currentArticles.indexOfFirst { it.id == article.id }
                            if (existingIndex >= 0) {
                                currentArticles[existingIndex] = article
                                println("🎬 BuyAppViewModel.loadMainScreenArticles: UPDATED existing article '${article.productName}' at index $existingIndex")
                            } else {
                                currentArticles.add(article)
                                println("🎬 BuyAppViewModel.loadMainScreenArticles: ADDED article '${article.productName}' (id=${article.id})")
                            }
                        }
                        Article.MODE_CHANGED -> {
                            val index = currentArticles.indexOfFirst { it.id == article.id }
                            if (index >= 0) {
                                currentArticles[index] = article
                                println("🎬 BuyAppViewModel.loadMainScreenArticles: CHANGED article '${article.productName}' at index $index, available=${article.available}")
                            } else {
                                // Article wasn't in list (maybe was filtered before), add it now
                                currentArticles.add(article)
                                println("🎬 BuyAppViewModel.loadMainScreenArticles: CHANGED but not found, ADDED article '${article.productName}' (id=${article.id}), available=${article.available}")
                            }
                        }
                        Article.MODE_REMOVED -> {
                            currentArticles.removeAll { it.id == article.id }
                            println("🎬 BuyAppViewModel.loadMainScreenArticles: REMOVED article '${article.productName}' (id=${article.id})")
                        }
                        // MODE_MOVED typically doesn't need special handling
                    }

                    val afterCount = currentArticles.size
                    println("🎬 BuyAppViewModel.loadMainScreenArticles: Article count: $beforeCount → $afterCount")

                    current.copy(
                        mainScreen = current.mainScreen.copy(
                            isLoading = false,
                            articles = currentArticles,
                            error = null
                        )
                    )
                }

                // Auto-select first article if none selected (using proper selection logic)
                val currentSelectedArticle = _state.value.mainScreen.selectedArticle
                val currentArticles = _state.value.mainScreen.articles
                if (currentSelectedArticle == null && currentArticles.isNotEmpty()) {
                    val firstArticle = currentArticles.first()
                    println("🎬 BuyAppViewModel.loadMainScreenArticles: Auto-selecting first article: ${firstArticle.productName}")
                    selectMainScreenArticle(firstArticle)  // ✅ Use proper selection method
                }
            }
    }
}

/**
 * Observe basket to update MainScreen cart item count and selected quantity
 */
internal fun BuyAppViewModel.observeMainScreenBasket() {
    viewModelScope.launch {
        basketRepository.observeBasket().collect { basketItems ->
            _state.update { current ->
                // Check if currently selected article is in the basket
                val selectedArticle = current.mainScreen.selectedArticle
                val existingItem = if (selectedArticle != null) {
                    basketItems.find { it.id == selectedArticle.id || it.productId == selectedArticle.id }
                } else null

                // Update quantity if the selected article is in basket
                val updatedQuantity = existingItem?.amountCount
                    ?: current.mainScreen.selectedQuantity

                current.copy(
                    mainScreen = current.mainScreen.copy(
                        cartItemCount = basketItems.size,
                        basketItems = basketItems,
                        selectedQuantity = updatedQuantity
                    )
                )
            }
        }
    }
}
