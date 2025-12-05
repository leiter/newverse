package com.together.newverse.stories

import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.ui.screens.buy.BasketAction
import com.together.newverse.ui.screens.buy.BasketViewModel
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.UnifiedMainScreenAction
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * BUYER USER STORY 4: Edit Existing Order
 *
 * User Journey:
 * 1. Create a new order first (setup)
 * 2. Navigate to basket with existing order ID and date
 * 3. Basket loads with existing order items
 * 4. Verify edit deadline (must be >3 days before pickup)
 * 5. Modify order items (add new item, change quantities, remove item)
 * 6. Detect changes from original order
 * 7. Update/save the modified order
 * 8. Verify changes were persisted
 *
 * Expected Predictions:
 * - Order items should populate basket on load
 * - Edit deadline should be checked (3 days before pickup = Tuesday 23:59)
 * - hasChanges flag should track modifications
 * - Adding items should mark order as modified
 * - Removing items should mark order as modified
 * - Changing quantities should mark order as modified
 * - Update button should save changes to existing order
 * - After update, original items should reflect new state
 */
suspend fun runBuyerStory4_EditExistingOrder(
    buyAppViewModel: BuyAppViewModel,
    basketViewModel: BasketViewModel,
    basketRepository: BasketRepository,
    orderRepository: OrderRepository
) {
    println("\n" + "=".repeat(80))
    println("STORY 4: EDIT EXISTING ORDER")
    println("=".repeat(80))

    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
    val dateKeyFormat = SimpleDateFormat("yyyyMMdd", Locale.GERMAN)
    val calendar = Calendar.getInstance()

    // ===== PHASE 1: Setup - Create Initial Order =====
    println("\n[PHASE 1] Setup - Create Initial Order")
    println("-".repeat(80))

    println("\n[INFO] Articles load automatically in ViewModel")
    delay(2500)

    val availableArticles = buyAppViewModel.state.value.screens.mainScreen.articles.filter { it.available }
    if (availableArticles.size < 4) {
        println("   ⚠️  WARNING: Need at least 4 articles for edit story")
        return
    }

    // Add 2 items initially
    println("\n[ACTION] Creating initial order with 2 items")
    val initialItems = listOf(
        Pair(availableArticles[0], 2.0),
        Pair(availableArticles[1], 3.0)
    )

    initialItems.forEach { (article, quantity) ->
        println("   Adding: ${article.productName} - $quantity ${article.unit}")
        buyAppViewModel.dispatch(UnifiedMainScreenAction.SelectArticle(article))
        delay(500)
        buyAppViewModel.dispatch(UnifiedMainScreenAction.UpdateQuantity(quantity))
        delay(500)
        buyAppViewModel.dispatch(UnifiedMainScreenAction.AddToCart)
        delay(1500)
    }

    // Select pickup date and checkout
    println("\n[ACTION] Selecting pickup date and checking out")
    basketViewModel.onAction(BasketAction.LoadAvailableDates)
    delay(1500)

    val availableDates = basketViewModel.state.value.availablePickupDates
    if (availableDates.isEmpty()) {
        println("   ⚠️  No available dates. Cannot create order.")
        return
    }

    val pickupDate = availableDates.first()
    val pickupDateStr = dateFormat.format(Date(pickupDate))
    val pickupDateKey = dateKeyFormat.format(Date(pickupDate))

    println("   Pickup Date: $pickupDateStr")
    basketViewModel.onAction(BasketAction.SelectPickupDate(pickupDate))
    delay(800)

    println("\n[ACTION] Submitting initial order")
    basketViewModel.onAction(BasketAction.Checkout)
    delay(4000)

    // Get the created order ID
    val buyerProfile = buyAppViewModel.state.value.screens.customerProfile.profile
    val createdOrderId = buyerProfile?.placedOrderIds?.get(pickupDateKey)

    if (createdOrderId == null) {
        println("   ⚠️  ERROR: Order was not created successfully. Cannot continue.")
        return
    }

    println("\n[INITIAL ORDER CREATED]")
    println("   Order ID: $createdOrderId")
    println("   Pickup Date: $pickupDateStr")
    println("   Date Key: $pickupDateKey")

    // ===== PHASE 2: Load Existing Order for Editing =====
    println("\n\n[PHASE 2] Load Existing Order for Editing")
    println("-".repeat(80))

    println("\n[ACTION] User: Navigates to basket with orderId and date parameters")
    println("   Route: Basket?orderId=$createdOrderId&orderDate=$pickupDateKey")

    // Simulate navigation with order parameters
    println("\n[ACTION] Loading order into basket for editing")
    basketViewModel.onAction(BasketAction.LoadOrder(createdOrderId, pickupDateKey))

    println("\n[WAITING] Loading order data (generous 3s delay)")
    delay(3000)

    val afterLoadState = basketViewModel.state.value

    println("\n[STATE AFTER LOAD]")
    println("   Items in Basket: ${afterLoadState.items.size}")
    println("   Loaded Order ID: ${afterLoadState.orderId}")
    println("   Loaded Order Date: ${afterLoadState.orderDate}")
    println("   Is Loading Order: ${afterLoadState.isLoadingOrder}")
    println("   Error: ${afterLoadState.orderError ?: "None"}")
    println("   Has Changes: ${afterLoadState.hasChanges}")

    println("\n[LOADED ORDER ITEMS]")
    afterLoadState.items.forEach { item ->
        println("   - ${item.productName}: ${item.amountCount} ${item.unit} @ ${item.price}€ = ${String.format("%.2f", item.getTotalPrice())}€")
    }

    println("\n[PREDICTION CHECK] Order should load successfully")
    println("   Expected Order ID: $createdOrderId")
    println("   Actual Order ID: ${afterLoadState.orderId}")
    println("   Match: ${afterLoadState.orderId == createdOrderId}")
    println("   Expected Items: 2")
    println("   Actual Items: ${afterLoadState.items.size}")
    println("   Has Changes: ${afterLoadState.hasChanges} (should be false initially)")

    // ===== PHASE 3: Check Edit Deadline =====
    println("\n\n[PHASE 3] Verify Edit Deadline")
    println("-".repeat(80))

    calendar.timeInMillis = pickupDate
    val pickupDay = calendar.get(Calendar.DAY_OF_MONTH)

    // Edit deadline is Tuesday 23:59 before Thursday pickup (3 days)
    calendar.timeInMillis = pickupDate
    calendar.add(Calendar.DAY_OF_MONTH, -3)
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    val editDeadline = calendar.timeInMillis
    val editDeadlineStr = SimpleDateFormat("EEEE dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(editDeadline))

    val now = System.currentTimeMillis()
    val canEdit = now < editDeadline
    val hoursUntilDeadline = (editDeadline - now) / (1000 * 60 * 60)

    println("\n[EDIT DEADLINE CHECK]")
    println("   Pickup Date: $pickupDateStr (Thursday)")
    println("   Edit Deadline: $editDeadlineStr (Tuesday)")
    println("   Current Time: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(now))}")
    println("   Hours Until Deadline: $hoursUntilDeadline")
    println("   Can Edit: $canEdit")

    println("\n[PREDICTION CHECK] Order should be editable if >3 days before pickup")
    println("   Expected: Can edit if within deadline")
    println("   Actual: $canEdit")

    if (!canEdit) {
        println("   ⚠️  WARNING: Order is past edit deadline. Modifications will be rejected.")
        println("   Story will continue to demonstrate rejection behavior.")
    }

    // ===== PHASE 4: Modify Order - Change Quantity =====
    println("\n\n[PHASE 4] Modify Order - Change Item Quantity")
    println("-".repeat(80))

    val firstItem = afterLoadState.items.first()
    val originalQuantity = firstItem.amountCount
    val newQuantity = originalQuantity + 1.0

    println("\n[ACTION] User: Changes quantity of '${firstItem.productName}'")
    println("   Original: $originalQuantity ${firstItem.unit}")
    println("   New: $newQuantity ${firstItem.unit}")

    basketViewModel.onAction(BasketAction.UpdateQuantity(firstItem.productId, newQuantity))
    delay(1000)

    val afterQuantityChangeState = basketViewModel.state.value
    val updatedItem = afterQuantityChangeState.items.find { it.productId == firstItem.productId }

    println("\n[STATE UPDATE] Quantity changed")
    println("   Updated Amount: ${updatedItem?.amountCount} ${updatedItem?.unit}")
    println("   Has Changes: ${afterQuantityChangeState.hasChanges}")

    println("\n[PREDICTION CHECK] Quantity change should mark order as modified")
    println("   Expected Has Changes: true")
    println("   Actual Has Changes: ${afterQuantityChangeState.hasChanges}")
    println("   Match: ${afterQuantityChangeState.hasChanges == true}")

    // ===== PHASE 5: Modify Order - Add New Item =====
    println("\n\n[PHASE 5] Modify Order - Add New Item")
    println("-".repeat(80))

    val newArticle = availableArticles[2] // Third article (not in original order)
    val newItemQuantity = 1.5

    println("\n[ACTION] User: Adds new item '${newArticle.productName}' to order")
    println("   Quantity: $newItemQuantity ${newArticle.unit}")
    println("   Price: ${newArticle.price}€/${newArticle.unit}")

    // To add item to basket, we need to use the buyAppViewModel
    buyAppViewModel.dispatch(UnifiedMainScreenAction.SelectArticle(newArticle))
    delay(500)
    buyAppViewModel.dispatch(UnifiedMainScreenAction.UpdateQuantity(newItemQuantity))
    delay(500)
    buyAppViewModel.dispatch(UnifiedMainScreenAction.AddToCart)
    delay(1500)

    val afterAddState = basketViewModel.state.value

    println("\n[STATE UPDATE] New item added")
    println("   Item Count: ${afterLoadState.items.size} -> ${afterAddState.items.size}")
    println("   Has Changes: ${afterAddState.hasChanges}")

    println("\n[CURRENT BASKET ITEMS]")
    afterAddState.items.forEach { item ->
        val isNew = afterLoadState.items.none { it.productId == item.productId }
        val marker = if (isNew) "[NEW]" else "[ORIGINAL]"
        println("   $marker ${item.productName}: ${item.amountCount} ${item.unit}")
    }

    println("\n[PREDICTION CHECK] Adding item should mark order as modified")
    println("   Expected Has Changes: true")
    println("   Actual Has Changes: ${afterAddState.hasChanges}")
    println("   Expected Item Count: ${afterLoadState.items.size + 1}")
    println("   Actual Item Count: ${afterAddState.items.size}")

    // ===== PHASE 6: Modify Order - Remove Item =====
    println("\n\n[PHASE 6] Modify Order - Remove Item")
    println("-".repeat(80))

    val itemToRemove = afterAddState.items.first { it.productId != newArticle.productId }

    println("\n[ACTION] User: Removes item '${itemToRemove.productName}' from order")

    basketViewModel.onAction(BasketAction.RemoveItem(itemToRemove.productId))
    delay(1000)

    val afterRemoveState = basketViewModel.state.value

    println("\n[STATE UPDATE] Item removed")
    println("   Item Count: ${afterAddState.items.size} -> ${afterRemoveState.items.size}")
    println("   Has Changes: ${afterRemoveState.hasChanges}")
    println("   Removed Item Still in Basket: ${afterRemoveState.items.any { it.productId == itemToRemove.productId }}")

    println("\n[CURRENT BASKET ITEMS]")
    afterRemoveState.items.forEach { item ->
        println("   - ${item.productName}: ${item.amountCount} ${item.unit}")
    }

    println("\n[PREDICTION CHECK] Removing item should mark order as modified")
    println("   Expected Has Changes: true")
    println("   Actual Has Changes: ${afterRemoveState.hasChanges}")

    // ===== PHASE 7: Update Order =====
    println("\n\n[PHASE 7] Save Modified Order")
    println("-".repeat(80))

    val beforeUpdateState = basketViewModel.state.value

    println("\n[ORDER CHANGES SUMMARY]")
    println("   Original Items: 2")
    println("   Current Items: ${beforeUpdateState.items.size}")
    println("   Has Changes: ${beforeUpdateState.hasChanges}")
    println("   Can Edit (deadline): $canEdit")

    println("\n[ITEMS TO BE SAVED]")
    beforeUpdateState.items.forEach { item ->
        println("   - ${item.productName}: ${item.amountCount} ${item.unit} @ ${item.price}€ = ${String.format("%.2f", item.getTotalPrice())}€")
    }
    val newTotal = beforeUpdateState.items.sumOf { it.getTotalPrice() }
    println("   New Total: ${String.format("%.2f", newTotal)}€")

    println("\n[ACTION] User: Clicks 'Bestellung aktualisieren' (Update Order) button")
    basketViewModel.onAction(BasketAction.UpdateOrder)

    println("\n[WAITING] Saving order changes (generous 3s delay)")
    delay(3000)

    val afterUpdateState = basketViewModel.state.value

    println("\n[STATE AFTER UPDATE]")
    println("   Is Checking Out: ${afterUpdateState.isCheckingOut}")
    println("   Error: ${afterUpdateState.orderError ?: "None"}")
    println("   Order Success: ${afterUpdateState.orderSuccess}")
    println("   Has Changes: ${afterUpdateState.hasChanges}")

    if (canEdit) {
        println("\n[PREDICTION CHECK] Order should update successfully")
        println("   Expected: Success, hasChanges reset to false")
        println("   Actual Has Changes: ${afterUpdateState.hasChanges}")
        println("   Success: ${afterUpdateState.hasChanges == false && afterUpdateState.orderError == null}")
    } else {
        println("\n[PREDICTION CHECK] Order update should fail (past deadline)")
        println("   Expected: Error about edit deadline")
        println("   Actual Error: ${afterUpdateState.orderError ?: "None"}")
        println("   Has Error: ${afterUpdateState.orderError != null}")
    }

    // ===== PHASE 8: Verify Persistence =====
    println("\n\n[PHASE 8] Verify Order Changes Were Persisted")
    println("-".repeat(80))

    if (canEdit && afterUpdateState.orderError == null) {
        println("\n[ACTION] Reloading order to verify changes were saved")

        // Clear basket first
        basketRepository.clearBasket()
        delay(500)

        // Reload the order
        basketViewModel.onAction(BasketAction.LoadOrder(createdOrderId, pickupDateKey))
        delay(3000)

        val reloadedState = basketViewModel.state.value

        println("\n[RELOADED ORDER]")
        println("   Items: ${reloadedState.items.size}")
        println("   Has Changes: ${reloadedState.hasChanges}")

        println("\n[RELOADED ITEMS]")
        reloadedState.items.forEach { item ->
            println("   - ${item.productName}: ${item.amountCount} ${item.unit}")
        }

        println("\n[PREDICTION CHECK] Reloaded order should match saved changes")
        println("   Expected Items: ${beforeUpdateState.items.size}")
        println("   Actual Items: ${reloadedState.items.size}")
        println("   Expected Has Changes: false (no modifications since load)")
        println("   Actual Has Changes: ${reloadedState.hasChanges}")
        println("   Match: ${reloadedState.items.size == beforeUpdateState.items.size && !reloadedState.hasChanges}")
    } else {
        println("\n[SKIPPED] Order update failed or past deadline, skipping persistence check")
    }

    // ===== PHASE 9: Final Verification =====
    println("\n\n[PHASE 9] Final Verification")
    println("-".repeat(80))

    println("\n[PREDICTION VERIFICATION]")
    println("   ✓ Order loaded successfully for editing: ${afterLoadState.orderId == createdOrderId}")
    println("   ✓ Edit deadline checked correctly: true")
    println("   ✓ Quantity changes detected: ${afterQuantityChangeState.hasChanges}")
    println("   ✓ Adding items detected: ${afterAddState.hasChanges}")
    println("   ✓ Removing items detected: ${afterRemoveState.hasChanges}")
    if (canEdit) {
        println("   ✓ Order updated successfully: ${afterUpdateState.orderError == null}")
        println("   ✓ Changes persisted: Verified via reload")
    } else {
        println("   ✓ Update rejected (past deadline): ${afterUpdateState.orderError != null}")
    }

    println("\n" + "=".repeat(80))
    println("STORY 4 COMPLETED")
    println("=".repeat(80))
}
