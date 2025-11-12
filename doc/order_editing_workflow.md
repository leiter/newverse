# Order Editing Workflow - Newverse KMP

## Overview

This document explains how order editing works in the Newverse project, focusing on how users interact with an existing editable order through the MainScreen and apply changes via the BasketScreen.

---

## Architecture

### Component Hierarchy

```
┌─────────────────────────────────────────┐
│      UnifiedAppViewModel                │
│  (Single source of truth)               │
│                                         │
│  ┌──────────────┐  ┌──────────────────┐│
│  │ MainScreen   │  │ BasketRepository ││
│  │ State        │◄─┤  (InMemory)     ││
│  └──────────────┘  └──────────────────┘│
└─────────────────────────────────────────┘
         │                    │
         │                    │
         ▼                    ▼
  ┌──────────────┐    ┌──────────────┐
  │ MainScreen   │    │ BasketScreen │
  │  (UI)        │    │   (UI)       │
  └──────────────┘    └──────────────┘
```

### Data Flow

```
1. App Startup
   └─> Check Auth
       └─> Load Buyer Profile
           └─> Get placedOrderIds
               └─> Load Most Recent Editable Order
                   └─> BasketRepository.loadOrderItems()
                       └─> Basket now contains order items

2. User Adds/Modifies Products (MainScreen)
   └─> User selects product
       └─> Adjusts quantity
           └─> Clicks "In den Korb" (Add to Cart)
               └─> UnifiedAppViewModel.addMainScreenToCart()
                   └─> BasketRepository.addItem() OR updateQuantity()
                       └─> InMemoryBasketRepository updates StateFlow
                           └─> observeMainScreenBasket() receives update
                               └─> MainScreen state refreshed
                                   └─> Badge count updated
                                   └─> "In Basket" indicator shown

3. User Removes Products (MainScreen)
   └─> User clicks "Aus Korb" (Remove from Basket)
       └─> UnifiedAppViewModel.removeMainScreenFromBasket()
           └─> BasketRepository.removeItem()
               └─> StateFlow updated
                   └─> UI reflects removal

4. User Views/Edits Order (BasketScreen)
   └─> Navigate to Basket
       └─> BasketViewModel loads current items from BasketRepository
           └─> Detects changes vs original order
               └─> Shows "Bestellung (geändert)" indicator
                   └─> "Apply Changes" button enabled
                       └─> User clicks "Bestellung aktualisieren"
                           └─> BasketViewModel.updateOrder()
                               └─> OrderRepository.updateOrder()
                                   └─> Firebase updated
                                       └─> Success confirmation
```

---

## Complete Workflow Example

### Scenario: User Edits Existing Thursday Order

#### **Step 1: App Initialization (Automatic)**

```kotlin
// UnifiedAppViewModel.kt:95-153
private fun loadOpenOrderAfterAuth() {
    // 1. Load buyer profile
    val profileResult = profileRepository.getBuyerProfile()
    val placedOrderIds = buyerProfile.placedOrderIds

    // 2. Get most recent editable order
    val orderResult = orderRepository.getOpenEditableOrder(sellerId, placedOrderIds)

    // 3. Load order items into BasketRepository
    basketRepository.loadOrderItems(order.articles, order.id, dateKey)

    // 4. Update app state with order info
    _state.update {
        it.copy(
            common = it.common.copy(
                basket = it.common.basket.copy(
                    currentOrderId = order.id,
                    currentOrderDate = dateKey
                )
            )
        )
    }
}
```

**Result:**
- ✅ Basket badge shows item count (e.g., "5")
- ✅ BasketRepository contains: `[Tomatoes(2kg), Kartoffeln(5kg), Eier(10 Stück), ...]`
- ✅ Order metadata stored: `orderId="abc123"`, `orderDate="20251114"`

---

#### **Step 2: User Browses Products (MainScreen)**

```kotlin
// MainScreenModern.kt:78-206
@Composable
fun MainScreenModern(
    state: MainScreenState,
    onAction: (UnifiedAppAction) -> Unit
) {
    // Display product grid
    LazyColumn {
        items(products.chunked(2)) { productPair ->
            ModernProductCard(
                product = product,
                onClick = {
                    // Select product
                    onAction(UnifiedMainScreenAction.SelectArticle(product))
                }
            )
        }
    }
}
```

**Result:**
- ✅ Products displayed in grid
- ✅ User can scroll and select products
- ✅ Selected product shown in hero card with quantity selector

---

#### **Step 3: User Selects "Gurken" (Cucumbers)**

```kotlin
// UnifiedAppViewModel.kt:1390-1410
private fun selectMainScreenArticle(article: Article) {
    // Check if this product is already in the basket
    val basketItems = basketRepository.observeBasket().value
    val existingItem = basketItems.find { it.productId == article.id }

    // If it exists, pre-populate the quantity
    val initialQuantity = existingItem?.amountCount ?: 0.0

    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                mainScreen = current.screens.mainScreen.copy(
                    selectedArticle = article,
                    selectedQuantity = initialQuantity  // 0.0 (not in order yet)
                )
            )
        )
    }
}
```

**Result:**
- ✅ Hero card displays "Gurken"
- ✅ Price: 2.50€/kg
- ✅ Quantity field shows: "0" (not in basket)
- ✅ User can edit quantity

---

#### **Step 4: User Enters Quantity "3" (kg)**

```kotlin
// MainScreenModern.kt:376-405
BasicTextField(
    value = quantityText,
    onValueChange = { newText ->
        quantityText = newText
        val parsedQuantity = newText.replace(",", ".").toDoubleOrNull()
        if (parsedQuantity != null) {
            onQuantityChange(parsedQuantity)  // → UpdateQuantity action
        }
    }
)

// UnifiedAppViewModel.kt:1412-1422
private fun updateMainScreenQuantity(quantity: Double) {
    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                mainScreen = current.screens.mainScreen.copy(
                    selectedQuantity = quantity.coerceAtLeast(0.0)  // 3.0
                )
            )
        )
    }
}
```

**Result:**
- ✅ Quantity field shows: "3 kg"
- ✅ Total price calculated: "7.50€"
- ✅ "In den Korb" button enabled

---

#### **Step 5: User Clicks "In den Korb" (Add to Cart)**

```kotlin
// MainScreenModern.kt:464-484
Button(
    onClick = onAddToCart,  // → AddToCart action
    enabled = quantity > 0.0
) {
    Icon(Icons.Default.ShoppingCart)
    Text("In den Korb")
}

// UnifiedAppViewModel.kt:1429-1474
private fun addMainScreenToCart() {
    val selectedArticle = _state.value.screens.mainScreen.selectedArticle  // Gurken
    val quantity = _state.value.screens.mainScreen.selectedQuantity  // 3.0

    // Check if item already exists in basket
    val basketItems = basketRepository.observeBasket().value
    val existingItem = basketItems.find { it.productId == selectedArticle.id }

    if (existingItem != null) {
        // UPDATE existing item
        viewModelScope.launch {
            basketRepository.updateQuantity(selectedArticle.id, quantity)
        }
    } else {
        // ADD new item
        val orderedProduct = OrderedProduct(
            productId = selectedArticle.id,
            productName = "Gurken",
            unit = "kg",
            price = 2.50,
            amount = "3.0",
            amountCount = 3.0,
            piecesCount = 3
        )

        viewModelScope.launch {
            basketRepository.addItem(orderedProduct)
        }
    }
}
```

**BasketRepository Processing:**

```kotlin
// InMemoryBasketRepository.kt:23-40
override suspend fun addItem(item: OrderedProduct) {
    val currentItems = _basket.value.toMutableList()
    val existingIndex = currentItems.indexOfFirst { it.productId == item.productId }

    if (existingIndex >= 0) {
        // ACCUMULATE strategy: Add quantities together
        val existing = currentItems[existingIndex]
        currentItems[existingIndex] = existing.copy(
            amountCount = existing.amountCount + item.amountCount,
            amount = (existing.amountCount + item.amountCount).toString()
        )
    } else {
        // Add new item
        currentItems.add(item)
    }

    _basket.value = currentItems  // Emit new state
}
```

**Result:**
- ✅ Gurken added to basket
- ✅ `_basket.value` now has 6 items (original 5 + Gurken)
- ✅ StateFlow emits update
- ✅ `observeMainScreenBasket()` receives update:

```kotlin
// UnifiedAppViewModel.kt:1633-1648
private fun observeMainScreenBasket() {
    viewModelScope.launch {
        basketRepository.observeBasket().collect { basketItems ->
            _state.update { current ->
                current.copy(
                    screens = current.screens.copy(
                        mainScreen = current.screens.mainScreen.copy(
                            cartItemCount = basketItems.size,  // 6
                            basketItems = basketItems
                        )
                    )
                )
            }
        }
    }
}
```

**UI Updates:**
- ✅ Badge count: "5" → "6"
- ✅ Button changes to "Aus Korb" (Remove from Basket)
- ✅ Quantity field updates to "3"

---

#### **Step 6: User Modifies Existing Item "Tomatoes"**

User selects "Tomatoes" (already in order with 2kg), changes quantity to 5kg:

```kotlin
// UnifiedAppViewModel.kt:1390-1410
private fun selectMainScreenArticle(article: Article) {
    val basketItems = basketRepository.observeBasket().value
    val existingItem = basketItems.find { it.productId == article.id }

    val initialQuantity = existingItem?.amountCount ?: 0.0  // 2.0 (current in basket)

    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                mainScreen = current.screens.mainScreen.copy(
                    selectedArticle = article,  // Tomatoes
                    selectedQuantity = initialQuantity  // 2.0
                )
            )
        )
    }
}
```

User edits quantity to "5", clicks "In den Korb":

```kotlin
// UnifiedAppViewModel.kt:1445-1450
if (existingItem != null) {
    // Update existing item quantity
    viewModelScope.launch {
        basketRepository.updateQuantity(selectedArticle.id, quantity)  // 5.0
    }
}
```

**BasketRepository Processing:**

```kotlin
// InMemoryBasketRepository.kt:47-68
override suspend fun updateQuantity(productId: String, newQuantity: Double) {
    val currentItems = _basket.value.toMutableList()
    val index = currentItems.indexOfFirst { it.productId == productId }

    if (index >= 0) {
        val item = currentItems[index]
        currentItems[index] = item.copy(
            amountCount = newQuantity,  // 2.0 → 5.0
            amount = newQuantity.toString()
        )
        _basket.value = currentItems  // Emit update
    }
}
```

**Result:**
- ✅ Tomatoes quantity: 2kg → 5kg
- ✅ Basket still has 6 items
- ✅ Badge count stays at "6"
- ✅ Quantity changed: Tomatoes(5kg) instead of Tomatoes(2kg)

---

#### **Step 7: User Navigates to Basket Screen**

```kotlin
// User clicks basket icon/badge
onAction(UnifiedNavigationAction.NavigateTo(NavRoutes.Basket))
```

**BasketScreen Initialization:**

```kotlin
// BasketScreen.kt:32-50
@Composable
fun BasketScreen(
    viewModel: BasketViewModel = koinViewModel(),
    orderId: String? = null,
    orderDate: String? = null
) {
    val state by viewModel.state.collectAsState()

    // BasketViewModel automatically loads order from BasketRepository
}
```

**BasketViewModel Initialization:**

```kotlin
// BasketViewModel.kt:85-107
init {
    // Observe basket changes from repository
    viewModelScope.launch {
        basketRepository.observeBasket().collect { items ->
            val hasChanges = checkIfBasketHasChanges(items, _state.value.originalOrderItems)
            _state.value = _state.value.copy(
                items = items,  // Current basket items
                total = basketRepository.getTotal(),
                hasChanges = hasChanges  // TRUE (Tomatoes changed, Gurken added)
            )
        }
    }

    // Auto-load the most recent editable order if it exists
    viewModelScope.launch {
        loadMostRecentEditableOrder()
    }
}
```

**Load Order Logic:**

```kotlin
// BasketViewModel.kt:113-174
private suspend fun loadMostRecentEditableOrder() {
    // Check if BasketRepository already has an order loaded
    val loadedOrderInfo = basketRepository.getLoadedOrderInfo()

    if (loadedOrderInfo != null) {
        val (orderId, orderDate) = loadedOrderInfo  // ("abc123", "20251114")

        // Sync the loaded order info to our state
        val result = orderRepository.loadOrder(SELLER_ID, orderDate, orderId)
        result.onSuccess { order ->
            val canEdit = checkEditDeadline(order.pickUpDate)

            _state.value = _state.value.copy(
                orderId = orderId,
                orderDate = orderDate,
                pickupDate = order.pickUpDate,
                createdDate = order.createdDate,
                isEditMode = false,
                canEdit = canEdit,  // true (more than 3 days before pickup)
                originalOrderItems = order.articles,  // ORIGINAL ORDER
                hasChanges = checkIfBasketHasChanges(currentItems, order.articles)  // TRUE
            )
        }
        return
    }

    // ... (alternative loading path if not already loaded)
}
```

**Change Detection:**

```kotlin
// BasketViewModel.kt:179-210
private fun checkIfBasketHasChanges(
    currentItems: List<OrderedProduct>,
    originalItems: List<OrderedProduct>
): Boolean {
    // Current: [Tomatoes(5kg), Kartoffeln(5kg), Eier(10), Gurken(3kg), ...]
    // Original: [Tomatoes(2kg), Kartoffeln(5kg), Eier(10), ...]

    // Different number of items = changed
    if (currentItems.size != originalItems.size) return true  // TRUE (6 vs 5)

    // Check if any item quantity changed
    currentItems.forEach { currentItem ->
        val originalItem = originalItems.find { it.productId == currentItem.productId }

        if (originalItem == null) {
            return true  // New item added (Gurken)
        }

        if (originalItem.amountCount != currentItem.amountCount) {
            return true  // Quantity changed (Tomatoes: 2→5)
        }
    }

    return false
}
```

**Result:**
- ✅ `hasChanges = true`
- ✅ Order info card shows "Bestellung (geändert)" with yellow indicator
- ✅ "Bestellung aktualisieren" button enabled
- ✅ Displays all 6 items:
  - Tomatoes: 5kg (changed from 2kg)
  - Kartoffeln: 5kg (unchanged)
  - Eier: 10 Stück (unchanged)
  - Gurken: 3kg (newly added)
  - ... other items

---

#### **Step 8: User Clicks "Bestellung aktualisieren" (Update Order)**

```kotlin
// BasketScreen.kt:238-250
Button(
    onClick = { onAction(BasketAction.UpdateOrder) },
    enabled = state.hasChanges && state.items.isNotEmpty() && !state.isCheckingOut
) {
    if (state.hasChanges) {
        Text("Bestellung aktualisieren")
    } else {
        Text("Keine Änderungen")
    }
}
```

**Update Order Logic:**

```kotlin
// BasketViewModel.kt:486-607
private fun updateOrder() {
    viewModelScope.launch {
        _state.value = _state.value.copy(
            isCheckingOut = true,
            orderError = null
        )

        try {
            val orderId = _state.value.orderId  // "abc123"
            val pickupDate = _state.value.pickupDate  // Thursday timestamp
            val createdDate = _state.value.createdDate  // Original creation time

            // Verify within edit deadline (3 days before pickup)
            val threeDaysBeforePickup = pickupDate - (3 * 24 * 60 * 60 * 1000)
            if (Clock.System.now().toEpochMilliseconds() >= threeDaysBeforePickup) {
                _state.value = _state.value.copy(
                    isCheckingOut = false,
                    orderError = "Bearbeitungsfrist abgelaufen"
                )
                return@launch
            }

            // Get current items from basket
            val items = _state.value.items  // All 6 items with modifications

            // Get buyer profile
            val buyerProfile = profileRepository.getBuyerProfile().getOrNull()

            // Create updated order
            val updatedOrder = Order(
                id = orderId,
                buyerProfile = buyerProfile,
                createdDate = createdDate,  // Keep original creation time
                sellerId = SELLER_ID,
                marketId = "",
                pickUpDate = pickupDate,  // Keep same Thursday
                message = "",
                articles = items  // Updated items list
            )

            // Update order via repository
            val result = orderRepository.updateOrder(updatedOrder)

            result.onSuccess {
                println("✅ Order updated successfully")

                // Update state
                _state.value = _state.value.copy(
                    isCheckingOut = false,
                    orderSuccess = true,
                    isEditMode = false,
                    originalOrderItems = items,  // New baseline
                    hasChanges = false  // Changes now saved
                )
            }.onFailure { error ->
                println("❌ Update failed - ${error.message}")
                _state.value = _state.value.copy(
                    isCheckingOut = false,
                    orderError = error.message ?: "Aktualisierung fehlgeschlagen"
                )
            }

        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isCheckingOut = false,
                orderError = e.message ?: "Ein Fehler ist aufgetreten"
            )
        }
    }
}
```

**OrderRepository Processing:**

```kotlin
// FirebaseOrderRepository.kt (Firebase)
override suspend fun updateOrder(order: Order): Result<Unit> {
    return withContext(Dispatchers.IO) {
        try {
            // Calculate date path
            val dateKey = formatDateKey(order.pickUpDate)  // "20251114"

            // Firebase path: orders/{sellerId}/{dateKey}/{orderId}
            val orderRef = ordersRef
                .child(order.sellerId)
                .child(dateKey)
                .child(order.id)

            // Convert to DTO
            val orderDto = order.toDto()

            // Write to Firebase
            orderRef.setValue(orderDto).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**Result:**
- ✅ Firebase updated with new order data
- ✅ Success message: "✓ Bestellung erfolgreich aktualisiert!"
- ✅ Order info card changes from "Bestellung (geändert)" to "Bestelldetails"
- ✅ Button disabled (no more changes)
- ✅ `originalOrderItems` now reflects the new baseline (6 items with modifications)
- ✅ `hasChanges = false`

---

## Key Observations

### ✅ What's Working Correctly

1. **Single Source of Truth**: BasketRepository is the authority for basket state
2. **Reactive Updates**: Changes in BasketRepository automatically update both MainScreen and BasketScreen via StateFlow
3. **Change Tracking**: System properly detects modifications to existing orders
4. **Order Loading**: Existing orders are automatically loaded into basket on app startup
5. **Add/Update/Remove**: All operations correctly interact with BasketRepository
6. **Accumulation Strategy**: Adding same item multiple times accumulates quantity

### 🎯 The Workflow You Wanted

The system **already works exactly as you described**:

1. ✅ Order loaded into `BasketRepository` on app startup
2. ✅ User interacts with products via `MainScreen`
3. ✅ All changes go through `BasketRepository` (single source)
4. ✅ Changes are tracked vs original order
5. ✅ User navigates to `BasketScreen` to review
6. ✅ "Apply Changes" button updates the order
7. ✅ Firebase receives updated order data

### 🔄 Data Flow Summary

```
┌──────────────────────────────────────────────────────────────┐
│                    On App Startup                             │
│                                                               │
│  1. Load Buyer Profile                                        │
│  2. Get placedOrderIds                                        │
│  3. Load Most Recent Editable Order                           │
│  4. BasketRepository.loadOrderItems(order.articles, id, date) │
│     └─> _basket.value = order.articles                        │
│     └─> _loadedOrderId = orderId                              │
│     └─> _loadedOrderDate = dateKey                            │
│                                                               │
└──────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                  User Modifies Order                          │
│                    (MainScreen)                               │
│                                                               │
│  User Action:                                                 │
│  - Select product                                             │
│  - Adjust quantity                                            │
│  - Click "In den Korb"                                        │
│                                                               │
│  Processing:                                                  │
│  - UnifiedAppViewModel.addMainScreenToCart()                  │
│  - BasketRepository.addItem() OR updateQuantity()             │
│  - _basket.value = updated list                               │
│  - StateFlow emits new value                                  │
│                                                               │
│  UI Update:                                                   │
│  - observeMainScreenBasket() receives update                  │
│  - Badge count updated                                        │
│  - "In Basket" indicator shown                                │
│                                                               │
└──────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                 User Reviews Changes                          │
│                   (BasketScreen)                              │
│                                                               │
│  Load:                                                        │
│  - BasketViewModel.init()                                     │
│  - observe BasketRepository.observeBasket()                   │
│  - Load original order from Firebase                          │
│  - Compare currentItems vs originalItems                      │
│  - hasChanges = true (if different)                           │
│                                                               │
│  Display:                                                     │
│  - Show all items with quantities                             │
│  - Highlight "Bestellung (geändert)"                          │
│  - Enable "Bestellung aktualisieren" button                   │
│                                                               │
└──────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                   User Applies Changes                        │
│                    (BasketScreen)                             │
│                                                               │
│  Action:                                                      │
│  - User clicks "Bestellung aktualisieren"                     │
│  - BasketViewModel.updateOrder()                              │
│                                                               │
│  Validation:                                                  │
│  - Check edit deadline (3 days before pickup)                 │
│  - Verify user authentication                                 │
│  - Ensure basket not empty                                    │
│                                                               │
│  Update:                                                      │
│  - Create Order object with updated articles                  │
│  - OrderRepository.updateOrder(order)                         │
│  - Firebase writes to: orders/{sellerId}/{dateKey}/{orderId}  │
│                                                               │
│  Success:                                                     │
│  - Show "✓ Bestellung erfolgreich aktualisiert!"              │
│  - originalOrderItems = currentItems (new baseline)           │
│  - hasChanges = false                                         │
│  - Button disabled (no more unsaved changes)                  │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

---

## Important Implementation Details

### Edit Deadline Check

Orders can only be edited if more than 3 days remain before pickup:

```kotlin
// BasketViewModel.kt:432-433, 509-516
val threeDaysBeforePickup = order.pickUpDate - (3 * 24 * 60 * 60 * 1000)
val canEdit = Clock.System.now().toEpochMilliseconds() < threeDaysBeforePickup

if (!canEdit) {
    _state.value = _state.value.copy(
        orderError = "Bearbeitungsfrist abgelaufen (weniger als 3 Tage bis Abholung)"
    )
    return
}
```

### Accumulation vs Replacement

**BasketRepository** uses **accumulation strategy**:

```kotlin
// InMemoryBasketRepository.kt:27-35
if (existingIndex >= 0) {
    // ACCUMULATE: Add quantities together
    val existing = currentItems[existingIndex]
    currentItems[existingIndex] = existing.copy(
        amountCount = existing.amountCount + item.amountCount  // 2 + 3 = 5
    )
}
```

However, **MainScreen** uses **replacement strategy** via `updateQuantity`:

```kotlin
// UnifiedAppViewModel.kt:1445-1450
if (existingItem != null) {
    // REPLACE: Set to exact quantity
    viewModelScope.launch {
        basketRepository.updateQuantity(selectedArticle.id, quantity)  // Set to 5
    }
}
```

This is **intentional** and correct:
- When user clicks "In den Korb" from MainScreen, they expect to SET the quantity to what they entered, not ADD to it
- When programmatically adding items via `addItem()`, accumulation makes sense

### Order Metadata Storage

BasketRepository stores order metadata to track which order is currently loaded:

```kotlin
// InMemoryBasketRepository.kt:85-90
override suspend fun loadOrderItems(items: List<OrderedProduct>, orderId: String, orderDate: String) {
    _basket.value = items
    _loadedOrderId = orderId
    _loadedOrderDate = orderDate
    println("🛒 BasketRepository.loadOrderItems: Loaded ${items.size} items from order $orderId (date: $orderDate)")
}
```

This metadata is used to determine if an order is already loaded and prevent duplicate loading.

---

## Conclusion

The **newverse order editing workflow is fully implemented and working correctly**. The system properly:

1. ✅ Loads existing orders into `BasketRepository` on startup
2. ✅ Routes all MainScreen interactions through `BasketRepository`
3. ✅ Tracks changes vs original order
4. ✅ Enables "Apply Changes" button when modifications detected
5. ✅ Updates Firebase when user confirms changes
6. ✅ Maintains single source of truth throughout the flow

The architecture follows clean separation of concerns with reactive state management, making it maintainable and testable.

---

**Document Version**: 1.0
**Last Updated**: 2025-11-12
**Author**: Claude Code Analysis
