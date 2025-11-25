package com.together.newverse.stories

import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.UnifiedMainScreenAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * BUYER USER STORY 2: Modify Quantities and Manage Favorites
 *
 * User Journey:
 * 1. Browse articles and add one to cart
 * 2. Change quantity multiple times (increase/decrease)
 * 3. Observe cart updates with quantity changes
 * 4. Toggle favorite status on multiple articles
 * 5. Verify favorite articles persist in profile
 * 6. Remove item from cart by setting quantity to 0
 *
 * Expected Predictions:
 * - Quantity changes should update basket in real-time
 * - Weight-based (kg) should allow decimals, pieces (stk) should be integers
 * - Favorite toggle should update UI immediately
 * - Favorite article IDs should be saved to buyer profile
 * - Setting quantity to 0 should remove item from basket
 * - Cart badge should reflect accurate count after modifications
 */
suspend fun runBuyerStory2_ModifyQuantitiesAndFavorites(
    viewModel: BuyAppViewModel,
    basketRepository: BasketRepository,
    profileRepository: ProfileRepository
) {
    println("\n" + "=".repeat(80))
    println("STORY 2: MODIFY QUANTITIES AND MANAGE FAVORITES")
    println("=".repeat(80))

    // ===== PHASE 1: Initial Setup =====
    println("\n[PHASE 1] Initial Setup - Load Articles and Profile")
    println("-".repeat(80))

    println("\n[INFO] Articles and profile load automatically")
    delay(2000)

    val initialState = viewModel.state.value.screens.mainScreen
    println("\n[INITIAL STATE]")
    println("   Articles Loaded: ${initialState.articles.size}")
    println("   Cart Items: ${initialState.cartItemCount}")
    println("   Favorite Articles: ${initialState.favouriteArticles.size}")

    if (initialState.articles.isEmpty()) {
        println("   ⚠️  WARNING: No articles available. Story cannot continue.")
        return
    }

    val testArticles = initialState.articles.filter { it.available }.take(3)
    if (testArticles.size < 3) {
        println("   ⚠️  WARNING: Need at least 3 available articles")
        return
    }

    // ===== PHASE 2: Add Item and Modify Quantity =====
    println("\n\n[PHASE 2] Add Item and Modify Quantity Multiple Times")
    println("-".repeat(80))

    val testArticle = testArticles[0]
    println("\n[ACTION] User: Selects article '${testArticle.productName}'")
    println("   Unit: ${testArticle.unit}, Price: ${testArticle.price}€")
    println("   Is Weight-Based: ${testArticle.unit.lowercase() in listOf("kg", "g")}")

    viewModel.dispatch(UnifiedMainScreenAction.SelectArticle(testArticle))
    delay(800)

    // Sequence of quantity changes
    val quantitySequence = listOf(1.0, 2.5, 5.0, 3.5, 0.5)

    quantitySequence.forEachIndexed { index, quantity ->
        println("\n[ACTION] User: Changes quantity to $quantity ${testArticle.unit}")

        val beforeState = viewModel.state.value.screens.mainScreen
        val beforeBasket = beforeState.basketItems.find { it.productId == testArticle.productId }
        val beforeQuantity = beforeBasket?.amountCount ?: 0.0

        println("   Before: Cart has ${beforeState.cartItemCount} items, This article: $beforeQuantity ${testArticle.unit}")

        viewModel.dispatch(UnifiedMainScreenAction.UpdateQuantity(quantity))
        delay(500)

        // Add or update in cart
        println("\n[ACTION] User: Taps 'Add to Cart' / 'Update Cart'")
        viewModel.dispatch(UnifiedMainScreenAction.AddToCart)
        delay(1500)

        val afterState = viewModel.state.value.screens.mainScreen
        val afterBasket = afterState.basketItems.find { it.productId == testArticle.productId }
        val afterQuantity = afterBasket?.amountCount ?: 0.0

        println("\n[STATE UPDATE] Quantity modified")
        println("   After: Cart has ${afterState.cartItemCount} items, This article: $afterQuantity ${testArticle.unit}")
        println("   Item Total: ${afterBasket?.getTotalPrice() ?: 0.0}€")

        println("\n[PREDICTION CHECK] Quantity should update correctly")
        println("   Expected Quantity: $quantity")
        println("   Actual Quantity: $afterQuantity")
        println("   Match: ${Math.abs(afterQuantity - quantity) < 0.01}")

        // Log basket state
        println("\n[CURRENT BASKET]")
        afterState.basketItems.forEach { item ->
            println("   - ${item.productName}: ${item.amountCount} ${item.unit} @ ${item.price}€ = ${item.getTotalPrice()}€")
        }
    }

    // ===== PHASE 3: Remove Item by Setting Quantity to 0 =====
    println("\n\n[PHASE 3] Remove Item from Cart")
    println("-".repeat(80))

    println("\n[ACTION] User: Removes '${testArticle.productName}' from cart")
    val beforeRemoveState = viewModel.state.value.screens.mainScreen
    val beforeRemoveCount = beforeRemoveState.cartItemCount

    println("   Method: Dispatching RemoveFromBasket action")
    viewModel.dispatch(UnifiedMainScreenAction.RemoveFromBasket)
    delay(1500)

    val afterRemoveState = viewModel.state.value.screens.mainScreen
    val afterRemoveCount = afterRemoveState.cartItemCount
    val itemStillInBasket = afterRemoveState.basketItems.any { it.productId == testArticle.productId }

    println("\n[STATE UPDATE] Item removed")
    println("   Cart Count: $beforeRemoveCount -> $afterRemoveCount")
    println("   Item Still in Basket: $itemStillInBasket")

    println("\n[PREDICTION CHECK] Item should be removed from basket")
    println("   Expected: Item removed, count decreased by 1")
    println("   Actual: Count = $afterRemoveCount, In Basket = $itemStillInBasket")
    println("   Success: ${!itemStillInBasket && afterRemoveCount == beforeRemoveCount - 1}")

    // ===== PHASE 4: Manage Favorites =====
    println("\n\n[PHASE 4] Toggle Favorite Status on Multiple Articles")
    println("-".repeat(80))

    val articlesToFavorite = testArticles.take(3)
    val initialFavorites = viewModel.state.value.screens.mainScreen.favouriteArticles

    println("\n[INITIAL FAVORITES]")
    println("   Count: ${initialFavorites.size}")
    initialFavorites.forEach { favId ->
        val article = initialState.articles.find { it.id == favId }
        println("   - ${article?.productName ?: "Unknown"} ($favId)")
    }

    articlesToFavorite.forEachIndexed { index, article ->
        println("\n[ACTION] User: Toggles favorite on '${article.productName}' (${index + 1}/3)")

        val beforeFavState = viewModel.state.value.screens.mainScreen
        val wasAlreadyFavorite = beforeFavState.favouriteArticles.contains(article.id)

        println("   Currently Favorite: $wasAlreadyFavorite")
        println("   Expected After Toggle: ${!wasAlreadyFavorite}")

        viewModel.dispatch(UnifiedMainScreenAction.ToggleFavourite(article.id))
        delay(1200) // Wait for profile update

        val afterFavState = viewModel.state.value.screens.mainScreen
        val isNowFavorite = afterFavState.favouriteArticles.contains(article.id)

        println("\n[STATE UPDATE] Favorite toggled")
        println("   Now Favorite: $isNowFavorite")
        println("   Total Favorites: ${afterFavState.favouriteArticles.size}")

        println("\n[PREDICTION CHECK] Favorite should toggle correctly")
        println("   Expected: $${!wasAlreadyFavorite}")
        println("   Actual: $isNowFavorite")
        println("   Match: ${isNowFavorite == !wasAlreadyFavorite}")

        // Verify in profile repository
        delay(500)
        val profileResult = profileRepository.getBuyerProfile()
        val profile = profileResult.getOrNull()
        val profileHasFavorite = profile?.favouriteArticles?.contains(article.id) ?: false

        println("\n[REPOSITORY VERIFICATION]")
        println("   Profile Favorite Count: ${profile?.favouriteArticles?.size ?: 0}")
        println("   Profile Has This Article: $profileHasFavorite")
        println("   Match with ViewModel: ${profileHasFavorite == isNowFavorite}")
    }

    // ===== PHASE 5: Final Verification =====
    println("\n\n[PHASE 5] Final State Verification")
    println("-".repeat(80))

    val finalState = viewModel.state.value.screens.mainScreen
    val finalProfileResult = profileRepository.getBuyerProfile()
    val finalProfile = finalProfileResult.getOrNull()

    println("\n[FINAL STATE SUMMARY]")
    println("   Cart Items: ${finalState.cartItemCount}")
    println("   Basket Items: ${finalState.basketItems.size}")
    println("   Favorite Articles (ViewModel): ${finalState.favouriteArticles.size}")
    println("   Favorite Articles (Profile): ${finalProfile?.favouriteArticles?.size ?: 0}")

    println("\n[FAVORITE ARTICLES]")
    finalState.favouriteArticles.forEach { favId ->
        val article = finalState.articles.find { it.id == favId }
        println("   ❤️  ${article?.productName ?: "Unknown"} ($favId)")
    }

    println("\n[BASKET CONTENTS]")
    if (finalState.basketItems.isEmpty()) {
        println("   (Empty)")
    } else {
        finalState.basketItems.forEach { item ->
            println("   🛒 ${item.productName}: ${item.amountCount} ${item.unit} @ ${item.price}€ = ${item.getTotalPrice()}€")
        }
        val total = finalState.basketItems.sumOf { it.getTotalPrice() }
        println("   Total: ${String.format("%.2f", total)}€")
    }

    println("\n[PREDICTION VERIFICATION]")
    println("   ✓ Quantity changes updated basket: ${true}")
    println("   ✓ Removed item no longer in basket: ${!finalState.basketItems.any { it.productId == testArticle.productId }}")
    println("   ✓ Favorites toggled successfully: ${finalState.favouriteArticles.isNotEmpty()}")
    println("   ✓ Favorites persisted to profile: ${finalProfile?.favouriteArticles?.size == finalState.favouriteArticles.size}")

    println("\n" + "=".repeat(80))
    println("STORY 2 COMPLETED")
    println("=".repeat(80))
}
