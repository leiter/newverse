# Hero Product Card State Synchronization

## Current Implementation

### State Flow

```
User Clicks Product (in basket)
    ↓
UnifiedMainScreenAction.SelectArticle(product)
    ↓
UnifiedAppViewModel.selectMainScreenArticle()
    ├─> basketRepository.observeBasket().value  // Get current basket
    ├─> basketItems.find { it.productId == article.id }  // Find item
    ├─> initialQuantity = existingItem?.amountCount ?: 0.0  // Extract quantity
    └─> _state.update { selectedQuantity = initialQuantity }  // Update state
    ↓
MainScreenModern re-renders
    ├─> quantity = state.selectedQuantity  // From ViewModel
    ├─> basketItems = state.basketItems  // From basket observer
    ├─> basketItem = basketItems.find { it.productId == product.id }
    ├─> originalQuantity = basketItem?.amountCount ?: 0.0  // From current basket
    └─> HeroProductCard(quantity, originalQuantity)
    ↓
HeroProductCard calculates
    └─> hasChanges = isInBasket && (quantity != originalQuantity)
```

### Key Components

#### 1. **UnifiedAppViewModel.selectMainScreenArticle()** (Line 1390-1410)

```kotlin
private fun selectMainScreenArticle(article: Article) {
    // Check if this product is already in the basket
    val basketItems = basketRepository.observeBasket().value
    val existingItem = basketItems.find { it.productId == article.id }

    // If it exists, pre-populate the quantity with the existing amount
    val initialQuantity = existingItem?.amountCount ?: 0.0

    _state.update { current ->
        current.copy(
            screens = current.screens.copy(
                mainScreen = current.screens.mainScreen.copy(
                    selectedArticle = article,
                    selectedQuantity = initialQuantity  // ✅ Set from basket
                )
            )
        )
    }

    println("🎯 UnifiedAppViewModel.selectMainScreenArticle: Selected ${article.productName}, existing quantity: $initialQuantity")
}
```

**Behavior:**
- ✅ Reads current basket state
- ✅ Sets `selectedQuantity` to basket quantity (or 0.0 if not in basket)
- ✅ Logs the selected product and quantity

#### 2. **MainScreenModern UI** (Line 120-134)

```kotlin
selectedProduct?.let { product ->
    val basketItem = basketItems.find { it.productId == product.id }
    val isInBasket = basketItem != null
    val originalQuantity = basketItem?.amountCount ?: 0.0  // ✅ From current basket
    val isFavourite = state.favouriteArticles.contains(product.id)

    HeroProductCard(
        product = product,
        quantity = quantity,  // From state.selectedQuantity
        originalQuantity = originalQuantity,  // From current basketItems
        isInBasket = isInBasket,
        isFavourite = isFavourite,
        // ... actions
    )
}
```

**Behavior:**
- ✅ Recalculates `originalQuantity` from current basket on every render
- ✅ Passes both `quantity` (from state) and `originalQuantity` (from basket)
- ✅ Reactive to basket changes via `basketItems`

#### 3. **HeroProductCard** (Line 212-233)

```kotlin
@Composable
private fun HeroProductCard(
    product: Article,
    quantity: Double,  // From state
    originalQuantity: Double,  // From basket
    isInBasket: Boolean,
    // ...
) {
    // Check if quantity has changed from original
    val hasChanges = isInBasket && quantity != originalQuantity

    // Local state for text field
    var quantityText by remember(quantity, product.id) {
        mutableStateOf(formatQuantity(quantity, isWeightBased))
    }

    // ...
}
```

**Behavior:**
- ✅ Calculates `hasChanges` based on comparison
- ✅ `quantityText` remembers value keyed by `quantity` and `product.id`
- ✅ Re-initializes when `quantity` or `product.id` changes

## Expected Behavior

### Scenario 1: Select Product in Basket

**Initial State:**
- Basket: [Tomatoes(2kg)]
- Selected: None

**User Action:** Click Tomatoes

**Expected Result:**
1. ✅ `selectMainScreenArticle(Tomatoes)` called
2. ✅ `initialQuantity = 2.0` (from basket)
3. ✅ `selectedQuantity = 2.0` (in state)
4. ✅ UI renders with:
   - `quantity = 2.0`
   - `originalQuantity = 2.0`
   - `hasChanges = false` (2.0 == 2.0)
   - `quantityText = "2"`
   - Button: "Ändern" (DISABLED)

### Scenario 2: Modify Quantity

**Current State:**
- Selected: Tomatoes
- quantity: 2.0
- originalQuantity: 2.0
- hasChanges: false

**User Action:** Type "5" in quantity field

**Expected Result:**
1. ✅ `onQuantityChange(5.0)` called
2. ✅ `selectedQuantity = 5.0` (in state)
3. ✅ UI re-renders with:
   - `quantity = 5.0`
   - `originalQuantity = 2.0` (unchanged)
   - `hasChanges = true` (5.0 != 2.0)
   - `quantityText = "5"`
   - Button: "Ändern" (ENABLED)

### Scenario 3: Apply Changes

**Current State:**
- Selected: Tomatoes
- quantity: 5.0
- originalQuantity: 2.0
- hasChanges: true

**User Action:** Click "Ändern" button

**Expected Result:**
1. ✅ `onAddToCart()` called
2. ✅ `basketRepository.updateQuantity("tomatoes", 5.0)`
3. ✅ Basket updates: [Tomatoes(5kg)]
4. ✅ `observeMainScreenBasket()` receives update
5. ✅ `basketItems` updated in state
6. ✅ UI re-renders with:
   - `quantity = 5.0` (unchanged in state)
   - `originalQuantity = 5.0` (NOW updated from new basket)
   - `hasChanges = false` (5.0 == 5.0)
   - `quantityText = "5"`
   - Button: "Ändern" (DISABLED)

### Scenario 4: Cancel Changes

**Current State:**
- Selected: Tomatoes
- quantity: 5.0
- originalQuantity: 2.0
- hasChanges: true

**User Action:** Click X (cancel) button

**Expected Result:**
1. ✅ `onQuantityChange(originalQuantity)` called
2. ✅ `onQuantityChange(2.0)` executed
3. ✅ `selectedQuantity = 2.0` (in state)
4. ✅ UI re-renders with:
   - `quantity = 2.0` (reset)
   - `originalQuantity = 2.0`
   - `hasChanges = false` (2.0 == 2.0)
   - `quantityText = "2"` (remembers with new key)
   - Button: "Ändern" (DISABLED)

## Potential Issues & Solutions

### Issue 1: Quantity Not Updating When Basket Changes

**Symptom:** User clicks product in basket, but quantity shows 0 or wrong value.

**Cause:** `selectMainScreenArticle` not being called or basket not populated yet.

**Debug Steps:**
1. Check console for: `🎯 UnifiedAppViewModel.selectMainScreenArticle: Selected ...`
2. Verify `basketRepository.observeBasket().value` has items
3. Ensure `basketItems` state is populated before selection

**Solution:**
- ✅ Already implemented correctly in `selectMainScreenArticle`
- Logs should show the quantity being set

### Issue 2: hasChanges Always True or Always False

**Symptom:** Button never enables or never disables.

**Cause:** Mismatch between `quantity` and `originalQuantity` calculation.

**Debug Steps:**
1. Add logging in HeroProductCard:
```kotlin
LaunchedEffect(quantity, originalQuantity) {
    println("🔍 HeroCard: quantity=$quantity, originalQuantity=$originalQuantity, hasChanges=$hasChanges")
}
```

2. Verify `basketItems` in MainScreenModern is current
3. Check if `basketItem.amountCount` matches expected value

**Solution:**
- ✅ `originalQuantity` recalculated on every render from current `basketItems`
- ✅ `quantity` comes from state which is set by `selectMainScreenArticle`

### Issue 3: Quantity Resets Unexpectedly

**Symptom:** User types quantity, but it resets while typing.

**Cause:** Observer updating `selectedQuantity` while user is editing.

**Current Implementation:**
- ✅ `observeMainScreenBasket` does NOT modify `selectedQuantity`
- ✅ Only updates `basketItems` and `cartItemCount`
- ✅ User input preserved

### Issue 4: Changes Not Applied to Basket

**Symptom:** Click "Ändern" but basket doesn't update.

**Cause:** `addMainScreenToCart` not calling repository correctly.

**Debug Steps:**
1. Check console for: `🛒 UnifiedAppViewModel.addMainScreenToCart: Updated ...`
2. Verify `basketRepository.updateQuantity` being called
3. Check `observeBasket()` emitting new values

**Solution:**
- ✅ Implemented correctly in `addMainScreenToCart`
- Logs should confirm update

## Debug Checklist

If state is inconsistent, check these in order:

1. **Is basketRepository populated?**
   ```
   Console: "🛒 BasketRepository.loadOrderItems: Loaded N items"
   ```

2. **Is product selection working?**
   ```
   Console: "🎯 UnifiedAppViewModel.selectMainScreenArticle: Selected [product], existing quantity: [qty]"
   ```

3. **Is basket observer working?**
   ```
   Check badge count updates when basket changes
   ```

4. **Is originalQuantity calculated correctly?**
   ```kotlin
   // Add in MainScreenModern.kt
   LaunchedEffect(basketItems, selectedProduct) {
       val item = basketItems.find { it.productId == selectedProduct?.id }
       println("🔍 MainScreen: basketItems has ${basketItems.size}, selectedProduct=${selectedProduct?.productName}, foundItem=${item?.amountCount}")
   }
   ```

5. **Is hasChanges calculated correctly?**
   ```kotlin
   // Add in HeroProductCard
   LaunchedEffect(quantity, originalQuantity, isInBasket) {
       println("🔍 HeroCard: qty=$quantity, orig=$originalQuantity, inBasket=$isInBasket, hasChanges=$hasChanges")
   }
   ```

## Conclusion

The current implementation should work correctly with the following guarantees:

1. ✅ **Selecting product** → Sets quantity from basket
2. ✅ **Modifying quantity** → Enables "Ändern" button
3. ✅ **Applying changes** → Updates basket, disables button
4. ✅ **Canceling changes** → Resets to original, disables button
5. ✅ **Basket updates** → `originalQuantity` recalculates, `hasChanges` updates

If issues persist, use the debug checklist above to trace the exact point of failure.

---

**Version**: 1.0
**Date**: 2025-11-12
**Status**: ✅ Implemented
