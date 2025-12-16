package com.together.newverse.stories

import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.UnifiedBasketScreenAction
import com.together.newverse.ui.state.UnifiedMainScreenAction
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * BUYER USER STORY 3: Complete Checkout Flow with New Order
 *
 * User Journey:
 * 1. Add multiple items to cart from main screen
 * 2. Navigate to basket/cart screen
 * 3. Review basket contents and total
 * 4. Load available pickup dates (Thursdays only)
 * 5. Select a pickup date
 * 6. Submit order (checkout)
 * 7. Verify order was created successfully
 * 8. Verify basket is cleared after checkout
 *
 * Expected Predictions:
 * - Available pickup dates should only be Thursdays
 * - Should have at least 5 upcoming Thursday options
 * - Total should match sum of all item prices
 * - After checkout, order should have unique ID
 * - After checkout, basket should be empty
 * - Order should have status SUBMITTED or DRAFT
 * - Created order should contain all basket items
 */
suspend fun runBuyerStory3_CheckoutNewOrder(
    buyAppViewModel: BuyAppViewModel,
    basketRepository: BasketRepository,
    orderRepository: OrderRepository
) {
    println("\n" + "=".repeat(80))
    println("STORY 3: COMPLETE CHECKOUT FLOW WITH NEW ORDER")
    println("=".repeat(80))

    // ===== PHASE 1: Add Items to Cart =====
    println("\n[PHASE 1] Add Items to Cart")
    println("-".repeat(80))

    println("\n[INFO] Articles load automatically in ViewModel")
    delay(2500)

    val mainState = buyAppViewModel.state.value.screens.mainScreen
    val availableArticles = mainState.articles.filter { it.available }

    if (availableArticles.size < 3) {
        println("   ⚠️  WARNING: Need at least 3 articles for checkout story")
        return
    }

    println("\n[ACTION] User: Adding 3 items to cart")

    val itemsToAdd = listOf(
        Triple(availableArticles[0], 2.0, "First item"),
        Triple(availableArticles[1], 1.5, "Second item"),
        Triple(availableArticles[2], 3.0, "Third item")
    )

    itemsToAdd.forEach { (article, quantity, description) ->
        println("\n   Adding: $description - '${article.productName}'")
        println("      Quantity: $quantity ${article.unit}")
        println("      Price: ${article.price}€/${article.unit}")

        buyAppViewModel.dispatch(UnifiedMainScreenAction.SelectArticle(article))
        delay(500)

        buyAppViewModel.dispatch(UnifiedMainScreenAction.UpdateQuantity(quantity))
        delay(500)

        buyAppViewModel.dispatch(UnifiedMainScreenAction.AddToCart)
        delay(1500)

        val currentBasket = buyAppViewModel.state.value.screens.mainScreen.basketItems
        println("      ✓ Added. Cart now has ${currentBasket.size} items")
    }

    val preCheckoutBasket = buyAppViewModel.state.value.screens.mainScreen.basketItems
    val expectedTotal = preCheckoutBasket.sumOf { it.getTotalPrice() }

    println("\n[BASKET BEFORE CHECKOUT]")
    println("   Item Count: ${preCheckoutBasket.size}")
    preCheckoutBasket.forEach { item ->
        println("   - ${item.productName}: ${item.amountCount} ${item.unit} @ ${item.price}€ = ${String.format("%.2f", item.getTotalPrice())}€")
    }
    println("   Expected Total: ${String.format("%.2f", expectedTotal)}€")

    // ===== PHASE 2: Navigate to Basket Screen =====
    println("\n\n[PHASE 2] Navigate to Basket Screen")
    println("-".repeat(80))

    println("\n[ACTION] User: Navigates to Basket/Cart screen")
    println("   Simulating navigation - BasketViewModel should be initialized")

    delay(800)

    // BuyAppViewModel includes basket screen state
    val basketState = buyAppViewModel.state.value.screens.basketScreen
    println("\n[BASKET SCREEN STATE]")
    println("   Items: ${basketState.items.size}")
    println("   Total: ${String.format("%.2f", basketState.total)}€")
    println("   Is Loading Order: ${basketState.isLoadingOrder}")
    println("   Order ID: ${basketState.orderId ?: "None (new order)"}")

    println("\n[PREDICTION CHECK] Basket screen should show cart items")
    println("   Expected Item Count: ${preCheckoutBasket.size}")
    println("   Actual Item Count: ${basketState.items.size}")
    println("   Match: ${basketState.items.size == preCheckoutBasket.size}")

    // ===== PHASE 3: Load and Select Pickup Date =====
    println("\n\n[PHASE 3] Load Available Pickup Dates")
    println("-".repeat(80))

    println("\n[ACTION] Loading available pickup dates (should be Thursdays only)")
    buyAppViewModel.dispatch(UnifiedBasketScreenAction.LoadAvailableDates)
    delay(1500)

    val afterDatesState = buyAppViewModel.state.value.screens.basketScreen
    val availableDates = afterDatesState.availablePickupDates

    println("\n[AVAILABLE PICKUP DATES]")
    println("   Count: ${availableDates.size}")

    val dateFormat = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMAN)
    val calendar = Calendar.getInstance()

    availableDates.take(5).forEach { dateMillis ->
        calendar.timeInMillis = dateMillis
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dayName = dateFormat.format(Date(dateMillis))
        val isThursday = dayOfWeek == Calendar.THURSDAY

        println("   - $dayName ${if (isThursday) "✓" else "✗ NOT THURSDAY!"}")
    }

    println("\n[PREDICTION CHECK] All dates should be Thursdays")
    val allThursdays = availableDates.all { dateMillis ->
        calendar.timeInMillis = dateMillis
        calendar.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY
    }
    println("   Expected: All Thursdays")
    println("   Actual: ${if (allThursdays) "All Thursdays ✓" else "Contains non-Thursdays ✗"}")
    println("   Expected Count: >= 5")
    println("   Actual Count: ${availableDates.size}")

    if (availableDates.isEmpty()) {
        println("   ⚠️  WARNING: No pickup dates available! Cannot continue checkout.")
        return
    }

    // ===== PHASE 4: Select Pickup Date =====
    println("\n\n[PHASE 4] Select Pickup Date")
    println("-".repeat(80))

    val selectedPickupDate = availableDates.first()
    calendar.timeInMillis = selectedPickupDate
    val selectedDateStr = dateFormat.format(Date(selectedPickupDate))

    println("\n[ACTION] User: Selects pickup date")
    println("   Date: $selectedDateStr")
    println("   Timestamp: $selectedPickupDate")

    buyAppViewModel.dispatch(UnifiedBasketScreenAction.SelectPickupDate(selectedPickupDate))
    delay(800)

    val afterSelectDateState = buyAppViewModel.state.value.screens.basketScreen

    println("\n[STATE UPDATE] Pickup date selected")
    println("   Selected Date: ${afterSelectDateState.selectedPickupDate}")
    println("   Matches Selection: ${afterSelectDateState.selectedPickupDate == selectedPickupDate}")

    println("\n[PREDICTION CHECK] Selected date should be stored")
    println("   Expected: $selectedPickupDate")
    println("   Actual: ${afterSelectDateState.selectedPickupDate}")
    println("   Match: ${afterSelectDateState.selectedPickupDate == selectedPickupDate}")

    // ===== PHASE 5: Review Before Checkout =====
    println("\n\n[PHASE 5] Review Order Before Checkout")
    println("-".repeat(80))

    val preCheckoutState = buyAppViewModel.state.value.screens.basketScreen

    println("\n[ORDER REVIEW]")
    println("   Items: ${preCheckoutState.items.size}")
    println("   Pickup Date: $selectedDateStr")
    println("   Total: ${String.format("%.2f", preCheckoutState.total)}€")
    println("   User State: ${buyAppViewModel.state.value.common.user}")

    println("\n[ITEMS TO BE ORDERED]")
    preCheckoutState.items.forEachIndexed { index, item ->
        println("   ${index + 1}. ${item.productName}")
        println("      Amount: ${item.amountCount} ${item.unit}")
        println("      Price: ${item.price}€/${item.unit}")
        println("      Line Total: ${String.format("%.2f", item.getTotalPrice())}€")
    }

    // ===== PHASE 6: Submit Order (Checkout) =====
    println("\n\n[PHASE 6] Submit Order")
    println("-".repeat(80))

    println("\n[ACTION] User: Clicks 'Checkout' / 'Bestellung aufgeben' button")
    println("   Expected: New order created with unique ID")
    println("   Expected: Basket cleared after successful checkout")

    buyAppViewModel.dispatch(UnifiedBasketScreenAction.Checkout)

    println("\n[WAITING] Processing order submission (generous 4s delay)")
    delay(4000)

    val afterCheckoutState = buyAppViewModel.state.value.screens.basketScreen

    println("\n[STATE AFTER CHECKOUT]")
    println("   Items in Basket: ${afterCheckoutState.items.size}")
    println("   Is Checking Out: ${afterCheckoutState.isCheckingOut}")
    println("   Error: ${afterCheckoutState.orderError ?: "None"}")
    println("   Order Success: ${afterCheckoutState.orderSuccess}")

    println("\n[PREDICTION CHECK] Basket should be cleared")
    println("   Expected Items: 0")
    println("   Actual Items: ${afterCheckoutState.items.size}")
    println("   Success: ${afterCheckoutState.items.isEmpty()}")

    // Verify in repository
    delay(500)
    val repoBasket = basketRepository.observeBasket().value

    println("\n[REPOSITORY VERIFICATION]")
    println("   Repository Basket Size: ${repoBasket.size}")
    println("   Expected: 0 (cleared)")
    println("   Match: ${repoBasket.isEmpty()}")

    // ===== PHASE 7: Verify Order Creation =====
    println("\n\n[PHASE 7] Verify Order Was Created")
    println("-".repeat(80))

    println("\n[ACTION] Checking if order was created successfully")
    delay(1000)

    // The order should now exist in the buyer's profile placedOrderIds
    val buyerProfile = buyAppViewModel.state.value.screens.customerProfile.profile

    println("\n[BUYER PROFILE ORDERS]")
    println("   Total Orders: ${buyerProfile?.placedOrderIds?.size ?: 0}")

    if (buyerProfile != null && buyerProfile.placedOrderIds.isNotEmpty()) {
        val dateKey = SimpleDateFormat("yyyyMMdd", Locale.GERMAN).format(Date(selectedPickupDate))
        val orderId = buyerProfile.placedOrderIds[dateKey]

        println("   Date Key: $dateKey")
        println("   Order ID: ${orderId ?: "Not found"}")

        if (orderId != null) {
            println("\n[ORDER DETAILS]")
            println("   ✓ Order created successfully!")
            println("   Order ID: $orderId")
            println("   Pickup Date Key: $dateKey")
            println("   Pickup Date: $selectedDateStr")

            // Try to load the order details
            println("\n[LOADING ORDER DETAILS]")
            try {
                val ordersResult = orderRepository.getBuyerOrders("", mapOf(dateKey to orderId))
                val orders = ordersResult.getOrNull() ?: emptyList()
                val createdOrder = orders.firstOrNull()

                if (createdOrder != null) {
                    println("   ✓ Order loaded successfully")
                    println("   Order ID: ${createdOrder.id}")
                    println("   Status: ${createdOrder.status}")
                    println("   Items: ${createdOrder.articles.size}")
                    println("   Total: ${String.format("%.2f", createdOrder.articles.sumOf { it.getTotalPrice() })}€")
                    println("   Pickup Date: ${SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(Date(createdOrder.pickUpDate))}")

                    println("\n[ORDER ITEMS]")
                    createdOrder.articles.forEach { item ->
                        println("   - ${item.productName}: ${item.amountCount} ${item.unit} @ ${item.price}€")
                    }
                } else {
                    println("   ⚠️  Order not found in repository")
                }
            } catch (e: Exception) {
                println("   ⚠️  Error loading order: ${e.message}")
            }
        } else {
            println("   ⚠️  Order ID not found in profile for date $dateKey")
        }
    } else {
        println("   ⚠️  No orders found in buyer profile")
    }

    // ===== PHASE 8: Final Verification =====
    println("\n\n[PHASE 8] Final Verification")
    println("-".repeat(80))

    println("\n[PREDICTION VERIFICATION]")
    println("   ✓ Available dates were Thursdays only: $allThursdays")
    println("   ✓ Had at least 5 pickup date options: ${availableDates.size >= 5}")
    println("   ✓ Pickup date was selectable: ${afterSelectDateState.selectedPickupDate != null}")
    println("   ✓ Basket cleared after checkout: ${afterCheckoutState.items.isEmpty()}")
    println("   ✓ Order created with ID: ${buyerProfile?.placedOrderIds?.isNotEmpty() == true}")

    println("\n" + "=".repeat(80))
    println("STORY 3 COMPLETED")
    println("=".repeat(80))
}
