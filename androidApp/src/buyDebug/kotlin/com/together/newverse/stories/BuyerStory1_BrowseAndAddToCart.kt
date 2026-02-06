package com.together.newverse.stories

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BuyMainScreenAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * BUYER USER STORY 1: Browse Articles and Add Items to Cart
 *
 * User Journey:
 * 1. Launch app and see main screen with articles loading
 * 2. Browse through available articles (skeletons -> real data)
 * 3. Select different articles from the grid
 * 4. Enter quantities for selected articles
 * 5. Add multiple articles to cart
 * 6. Observe cart badge count increasing
 *
 * Expected Predictions:
 * - Articles should load with real-time MODE flags
 * - First available article auto-selected as hero
 * - Quantity input should validate based on unit type (kg vs stk)
 * - Cart badge should reflect total number of items
 * - Each add-to-cart action should update basket state
 */
suspend fun runBuyerStory1_BrowseAndAddToCart(
    viewModel: BuyAppViewModel,
    articleRepository: ArticleRepository,
    basketRepository: BasketRepository
) {
    println("\n" + "=".repeat(80))
    println("STORY 1: BROWSE ARTICLES AND ADD TO CART")
    println("=".repeat(80))

    // ===== PHASE 1: App Launch & Article Loading =====
    println("\n[PHASE 1] App Launch - Loading Articles")
    println("-".repeat(80))

    logState("Initial State", "App just launched, articles loading automatically")
    val initialState = viewModel.state.value.mainScreen
    logStateDetails("Initial", initialState.isLoading, initialState.articles.size,
        initialState.selectedArticle, initialState.cartItemCount)

    // Articles load automatically in ViewModel init
    println("\n[INFO] Articles load automatically in ViewModel initialization")

    // Wait for loading to start
    delay(500)
    logState("Loading Started", "Articles streaming from Firebase")
    val loadingState = viewModel.state.value.mainScreen
    logStateDetails("Loading", loadingState.isLoading, loadingState.articles.size,
        loadingState.selectedArticle, loadingState.cartItemCount)

    // Wait for articles to load
    println("\n[WAITING] Allowing generous time for articles to stream in (3s)")
    delay(3000)

    val loadedState = viewModel.state.value.mainScreen
    println("\n[PREDICTION CHECK] Articles should be loaded with MODE flags")
    println("   Expected: isLoading=false, articles.size > 0, selectedArticle != null")
    println("   Actual: isLoading=${loadedState.isLoading}, articles.size=${loadedState.articles.size}, selectedArticle=${loadedState.selectedArticle?.productName}")

    if (loadedState.articles.isEmpty()) {
        println("   ⚠️  WARNING: No articles loaded! Story cannot continue.")
        return
    }

    logStateDetails("Loaded", loadedState.isLoading, loadedState.articles.size,
        loadedState.selectedArticle, loadedState.cartItemCount)

    // Log article MODE flags
    println("\n[ARTICLE MODE FLAGS]")
    loadedState.articles.take(5).forEach { article ->
        val modeStr = when (article.mode) {
            1 -> "ADDED"
            2 -> "CHANGED"
            3 -> "REMOVED"
            4 -> "MOVED"
            else -> "UNKNOWN"
        }
        println("   ${article.productName} (${article.id}) - MODE: $modeStr, Available: ${article.available}")
    }

    // ===== PHASE 2: Browse and Select Articles =====
    println("\n\n[PHASE 2] User Browses Articles")
    println("-".repeat(80))

    val availableArticles = loadedState.articles.filter { it.available }
    if (availableArticles.size < 3) {
        println("   ⚠️  WARNING: Not enough available articles for story (need 3+)")
    }

    // User browses through first 3 articles
    availableArticles.take(3).forEachIndexed { index, article ->
        println("\n[ACTION] User: Taps on article card '${article.productName}' (${index + 1}/3)")
        println("   Article Details: ID=${article.id}, Unit=${article.unit}, Price=${article.price}€")

        viewModel.dispatch(BuyMainScreenAction.SelectArticle(article))
        delay(1000) // User examines the article

        val afterSelectState = viewModel.state.value.mainScreen
        println("\n[STATE UPDATE] Article selected")
        println("   Selected Article: ${afterSelectState.selectedArticle?.productName}")
        println("   Selected Quantity: ${afterSelectState.selectedQuantity}")
        println("   Cart Count: ${afterSelectState.cartItemCount}")

        println("\n[PREDICTION CHECK] Selected article should update in state")
        println("   Expected: selectedArticle.id = ${article.id}")
        println("   Actual: selectedArticle.id = ${afterSelectState.selectedArticle?.id}")
        println("   Match: ${afterSelectState.selectedArticle?.id == article.id}")
    }

    // ===== PHASE 3: Add Items to Cart =====
    println("\n\n[PHASE 3] User Adds Items to Cart")
    println("-".repeat(80))

    // Prepare items to add
    val itemsToAdd = listOf(
        Pair(availableArticles[0], 2.5), // Weight-based (kg)
        Pair(availableArticles[1], 3.0), // Could be pieces or weight
        Pair(availableArticles[2], 1.0)  // Pieces or weight
    ).take(minOf(3, availableArticles.size))

    itemsToAdd.forEachIndexed { index, (article, quantity) ->
        println("\n[ACTION] User: Adding '${article.productName}' to cart")
        println("   Quantity: $quantity ${article.unit}")
        println("   Price: ${article.price}€/${article.unit}")
        println("   Expected Line Total: ${String.format("%.2f", article.price * quantity)}€")

        // Select article
        viewModel.dispatch(BuyMainScreenAction.SelectArticle(article))
        delay(500)

        // Update quantity
        println("\n[ACTION] User: Enters quantity $quantity")
        viewModel.dispatch(BuyMainScreenAction.UpdateQuantity(quantity))
        delay(800)

        val beforeAddState = viewModel.state.value.mainScreen
        println("   State Before Add: selectedQuantity=${beforeAddState.selectedQuantity}, cartCount=${beforeAddState.cartItemCount}")

        // Add to cart
        println("\n[ACTION] User: Taps 'Add to Cart' button")
        viewModel.dispatch(BuyMainScreenAction.AddToCart)
        delay(1500) // Wait for basket operation

        val afterAddState = viewModel.state.value.mainScreen
        println("\n[STATE UPDATE] Item added to cart")
        println("   Cart Count: ${beforeAddState.cartItemCount} -> ${afterAddState.cartItemCount}")
        println("   Basket Items: ${afterAddState.basketItems.size}")

        println("\n[PREDICTION CHECK] Cart count should increase by 1")
        println("   Expected: ${beforeAddState.cartItemCount + 1}")
        println("   Actual: ${afterAddState.cartItemCount}")
        println("   Match: ${afterAddState.cartItemCount == beforeAddState.cartItemCount + 1}")

        // Log basket contents
        println("\n[BASKET CONTENTS]")
        afterAddState.basketItems.forEach { item ->
            println("   - ${item.productName}: ${item.amountCount} ${item.unit} @ ${item.price}€ = ${item.getTotalPrice()}€")
        }
    }

    // ===== PHASE 4: Final State Verification =====
    println("\n\n[PHASE 4] Final State Verification")
    println("-".repeat(80))

    val finalState = viewModel.state.value.mainScreen
    val basketTotal = finalState.basketItems.sumOf { it.getTotalPrice() }

    println("\n[FINAL STATE SUMMARY]")
    println("   Total Articles Loaded: ${finalState.articles.size}")
    println("   Cart Item Count: ${finalState.cartItemCount}")
    println("   Basket Items: ${finalState.basketItems.size}")
    println("   Basket Total: ${String.format("%.2f", basketTotal)}€")
    println("   Currently Selected: ${finalState.selectedArticle?.productName ?: "None"}")

    println("\n[PREDICTION VERIFICATION]")
    println("   ✓ Articles loaded successfully: ${finalState.articles.isNotEmpty()}")
    println("   ✓ First article auto-selected: ${loadedState.selectedArticle != null}")
    println("   ✓ Multiple items added to cart: ${finalState.cartItemCount >= 3}")
    println("   ✓ Cart badge reflects item count: ${finalState.cartItemCount == finalState.basketItems.size}")

    // Verify against repository
    val repoBasket = basketRepository.observeBasket().first()
    println("\n[REPOSITORY VERIFICATION]")
    println("   Repository Basket Size: ${repoBasket.size}")
    println("   ViewModel Basket Size: ${finalState.basketItems.size}")
    println("   Match: ${repoBasket.size == finalState.basketItems.size}")

    println("\n" + "=".repeat(80))
    println("STORY 1 COMPLETED")
    println("=".repeat(80))
}

// Helper function for state logging
private fun logState(phase: String, description: String) {
    println("\n[$phase] $description")
}

private fun logStateDetails(
    label: String,
    isLoading: Boolean,
    articleCount: Int,
    selectedArticle: Article?,
    cartCount: Int
) {
    println("   [$label State]")
    println("      isLoading: $isLoading")
    println("      articles.size: $articleCount")
    println("      selectedArticle: ${selectedArticle?.productName ?: "null"}")
    println("      cartItemCount: $cartCount")
}
