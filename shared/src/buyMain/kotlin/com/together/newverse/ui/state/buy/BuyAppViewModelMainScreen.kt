package com.together.newverse.ui.state.buy

import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.state.BasketScreenState
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.ErrorState
import com.together.newverse.ui.state.PendingBasketAdd
import com.together.newverse.ui.state.ErrorType
import com.together.newverse.ui.state.ProductFilter
import com.together.newverse.ui.state.BuyMainScreenAction
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.together.newverse.util.Log


private const val TAG = "BuyVMMain"

/**
 * Main Screen extension functions for BuyAppViewModel
 *
 * Handles product browsing, selection, cart management, and favourites.
 *
 * Extracted functions:
 * - handleMainScreenAction
 * - Edit lock guards (handleAddToCart, handleRemoveFromBasket) and applyPendingAdd
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
        is BuyMainScreenAction.UpdateQuantity -> updateMainScreenQuantity(action.quantity)
        is BuyMainScreenAction.UpdateQuantityText -> updateMainScreenQuantityFromText(action.text)
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

/**
 * Add while the placed order is locked: refuse the write, but keep the add.
 *
 * The buyer picked an article and an amount. Showing the snackbar and dropping both
 * meant the new order they were just offered started empty, and they had to dial the
 * same thing again. The intent is recorded instead and [startNewOrder] puts it in the
 * fresh draft.
 */
private fun BuyAppViewModel.handleAddToCart() {
    if (!_state.value.mainScreen.canEditOrder) {
        val main = _state.value.mainScreen
        val article = main.selectedArticle
        val pending = if (article != null && main.selectedQuantity > 0.0) {
            PendingBasketAdd(articleId = article.id, quantity = main.selectedQuantity)
        } else null
        showNewOrderSnackbar(pending)
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

private fun BuyAppViewModel.showNewOrderSnackbar(pending: PendingBasketAdd? = null) {
    _state.update { current ->
        current.copy(
            mainScreen = current.mainScreen.copy(
                showNewOrderSnackbar = true,
                pendingAdd = pending ?: current.mainScreen.pendingAdd
            )
        )
    }
}

internal fun BuyAppViewModel.dismissNewOrderSnackbar() {
    // Letting the snackbar go is declining the new order, so the add it was holding
    // goes with it rather than surfacing in some later draft.
    _state.update { current ->
        current.copy(
            mainScreen = current.mainScreen.copy(
                showNewOrderSnackbar = false,
                pendingAdd = null
            )
        )
    }
}

/**
 * Leave the locked order where it is and start an empty draft for the next open slot.
 *
 * The order whose deadline passed is untouched — it is still owed, still in the
 * order history, and still the seller's to fulfil. Only the basket moves on.
 *
 * Every field the basket screen reads off the old order has to go with it.
 * `basketScreen.orderId` is the switch that screen keys on: left set, it keeps
 * drawing the locked order's card, whose only action is Update, which
 * [basketScreenUpdateOrder] refuses because the deadline has passed — a draft with
 * no way to send it. So the screen state is rebuilt rather than patched, and a field
 * added to [BasketScreenState] later cannot be forgotten here.
 *
 * Carried over: the slot catalogue, which is a property of the seller's calendar and
 * not of the abandoned order, and the fulfilment choice, which is seeded from the
 * profile once at startup and would otherwise silently fall back to pickup.
 */
internal fun BuyAppViewModel.startNewOrder() {
    viewModelScope.launch {
        // Clear the basket to start fresh
        basketRepository.clearBasket()

        _state.update { current ->
            val previous = current.basketScreen
            current.copy(
                mainScreen = current.mainScreen.copy(
                    canEditOrder = true,
                    showNewOrderSnackbar = false
                ),
                basket = current.basket.copy(
                    currentOrderId = null,
                    currentOrderDate = null
                ),
                basketScreen = BasketScreenState(
                    availableSlots = previous.availableSlots,
                    availablePickupDates = previous.availablePickupDates,
                    marketNames = previous.marketNames,
                    fulfilment = previous.fulfilment,
                    deliveryAddress = previous.deliveryAddress
                )
            )
        }

        // The slots were computed against the old order's deadline; recompute so the
        // draft offers the next open day rather than the one that just closed.
        basketScreenLoadAvailableDates()

        Log.d(TAG) { "Started new order - basket and order context cleared" }

        // What the buyer was trying to add when they hit the lock. Applied after the
        // reset, so it lands in the new draft rather than in the order they can no
        // longer change.
        applyPendingAdd()
    }
}

/**
 * Put the add the lock refused into the draft that has just been started.
 *
 * Looked up in the current catalogue rather than trusted: between the refusal and
 * the new order the seller may have withdrawn the article or sold it out, and an
 * order that cannot be placed is worse than one item short. Dropped silently in
 * that case — the basket is on screen and shows what it holds.
 */
private fun BuyAppViewModel.applyPendingAdd() {
    val pending = _state.value.mainScreen.pendingAdd ?: return
    val article = _state.value.mainScreen.articles
        .firstOrNull { it.id == pending.articleId && it.available }

    _state.update { current ->
        current.copy(
            mainScreen = current.mainScreen.copy(
                pendingAdd = null,
                selectedArticle = article ?: current.mainScreen.selectedArticle,
                selectedQuantity = if (article != null) pending.quantity else current.mainScreen.selectedQuantity
            )
        )
    }

    if (article == null) {
        Log.d(TAG) { "applyPendingAdd: ${pending.articleId} is no longer orderable, dropped" }
        return
    }

    // Reuses the normal add so the new order's item is priced and built exactly like
    // any other: market price, components, piece count.
    addMainScreenToCart()
    Log.d(TAG) { "applyPendingAdd: carried ${pending.quantity} of ${article.productName} into the new order" }
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

    Log.d(TAG) { "selectMainScreenArticle: Looking for article.id=${article.id} in ${basketItems.size} basket items" }
    basketItems.forEach { item ->
        Log.d(TAG) { "Basket item: id='${item.id}', productId='${item.productId}', name='${item.productName}', qty=${item.amountCount}" }
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

    Log.d(TAG) { "selectMainScreenArticle: Selected ${article.productName}, existingItem=${existingItem != null}, quantity: $initialQuantity" }
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
    val existingItem = basketItems.find { it.productId == selectedArticle.id }

    if (existingItem != null) {
        // Update existing item quantity
        viewModelScope.launch {
            basketRepository.updateQuantity(selectedArticle.id, quantity)
        }
        Log.d(TAG) { "addMainScreenToCart: Updated ${selectedArticle.productName} to ${quantity} ${selectedArticle.unit}" }
    } else {
        // Create new OrderedProduct from selected article and quantity
        // The market's price, not the catalogue's: `price` is what gets charged and
        // written to the order. Identical to the base price for a market with no
        // conditions, which is every single-market seller today.
        val resolved = _state.value.priceOf(selectedArticle)
        val orderedProduct = OrderedProduct(
            id = "", // Will be generated when order is placed
            productId = selectedArticle.id,
            productName = selectedArticle.productName,
            unit = selectedArticle.unit,
            price = resolved.gross,
            basePrice = resolved.base,
            priceComponents = resolved.components,
            // Left empty on purpose: OrderedProduct.getFormattedAmount() derives the
            // display string from amountCount and the unit. Putting quantity.toString()
            // here is what showed the seller "1.0" instead of "1,000 kg".
            amount = "",
            amountCount = quantity,
            // weightPerPiece defaults to 0.0, and an article may simply not have one.
            // Dividing by it gave Infinity, and Infinity.toInt() is Int.MAX_VALUE — a
            // piece count of 2147483647 written to the order. -1 means "not applicable",
            // which is what a weight-priced item without a piece weight is.
            piecesCount = if (selectedArticle.unit.lowercase() in listOf("kg", "g")) {
                if (selectedArticle.weightPerPiece > 0.0) {
                    (quantity / selectedArticle.weightPerPiece).toInt()
                } else {
                    -1
                }
            } else {
                quantity.toInt()
            }
        )

        // Add to basket via BasketRepository
        viewModelScope.launch {
            basketRepository.addItem(orderedProduct)
        }
        Log.d(TAG) { "addMainScreenToCart: Added ${selectedArticle.productName} (${quantity} ${selectedArticle.unit}) to basket" }
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

    Log.d(TAG) { "removeMainScreenFromBasket: Removed ${selectedArticle.productName} from basket" }
}

internal fun BuyAppViewModel.toggleMainScreenFavourite(articleId: String) {
    viewModelScope.launch {
        try {
            Log.d(TAG) { "toggleMainScreenFavourite: START - articleId=$articleId" }

            // Get current buyer profile
            val profileResult = profileRepository.getBuyerProfile()
            val currentProfile = profileResult.getOrNull()

            if (currentProfile == null) {
                Log.e(TAG) { "toggleMainScreenFavourite: No buyer profile found" }
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

            Log.d(TAG) { "toggleMainScreenFavourite: ${if (isFavourite) "Removing" else "Adding"} article" }

            // Update profile with new favourites list
            val updatedProfile = currentProfile.copy(favouriteArticles = updatedFavourites)
            val saveResult = profileRepository.saveBuyerProfile(updatedProfile)

            saveResult.onSuccess {
                Log.d(TAG) { "toggleMainScreenFavourite: Successfully updated favourites" }
                // Update local state immediately for instant UI feedback
                _state.update { current ->
                    current.copy(
                        mainScreen = current.mainScreen.copy(
                            favouriteArticles = updatedFavourites
                        )
                    )
                }
            }.onFailure { error ->
                Log.e(TAG) { "toggleMainScreenFavourite: Failed to save - ${error.message}" }
            }

        } catch (e: Exception) {
            Log.e(TAG) { "toggleMainScreenFavourite: Exception - ${e.message}" }
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
        Log.d(TAG) { "loadMainScreenArticles: START" }

        _state.update { current ->
            current.copy(
                mainScreen = current.mainScreen.copy(
                    isLoading = true,
                    error = null
                )
            )
        }
        Log.d(TAG) { "loadMainScreenArticles: Set loading state to true" }

        // Load articles for a specific seller or use empty string for current user
        val sellerId = sellerConfig.sellerId
        Log.d(TAG) { "loadMainScreenArticles: Calling articleRepository.getArticles(sellerId='$sellerId')" }

        articleRepository.getArticles(sellerId)
            .catch { e ->
                Log.e(TAG) { "loadMainScreenArticles: ERROR - ${e.message}" }
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
                Log.d(TAG) { "loadMainScreenArticles: Received article event - mode=${article.mode}, id=${article.id}, name=${article.productName}, available=${article.available}" }

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
                                Log.d(TAG) { "loadMainScreenArticles: UPDATED existing article '${article.productName}' at index $existingIndex" }
                            } else {
                                currentArticles.add(article)
                                Log.d(TAG) { "loadMainScreenArticles: ADDED article '${article.productName}' (id=${article.id})" }
                            }
                        }
                        Article.MODE_CHANGED -> {
                            val index = currentArticles.indexOfFirst { it.id == article.id }
                            if (index >= 0) {
                                currentArticles[index] = article
                                Log.d(TAG) { "loadMainScreenArticles: CHANGED article '${article.productName}' at index $index, available=${article.available}" }
                            } else {
                                // Article wasn't in list (maybe was filtered before), add it now
                                currentArticles.add(article)
                                Log.d(TAG) { "loadMainScreenArticles: CHANGED but not found, ADDED article '${article.productName}' (id=${article.id}), available=${article.available}" }
                            }
                        }
                        Article.MODE_REMOVED -> {
                            currentArticles.removeAll { it.id == article.id }
                            Log.d(TAG) { "loadMainScreenArticles: REMOVED article '${article.productName}' (id=${article.id})" }
                        }
                        // MODE_MOVED typically doesn't need special handling
                    }

                    val afterCount = currentArticles.size
                    Log.d(TAG) { "loadMainScreenArticles: Article count: $beforeCount → $afterCount" }

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
                    Log.d(TAG) { "loadMainScreenArticles: Auto-selecting first article: ${firstArticle.productName}" }
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
