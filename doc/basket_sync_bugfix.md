# Basket Synchronization Bug Fix

## Problem

When users added or modified items on MainScreen and then navigated to BasketScreen, the changes were not visible and the "Save Changes" button was not enabled.

## Root Cause

The issue was in `BasketViewModel` initialization. When loading an existing order, it was **overwriting the basket state** with the original order items, losing any modifications made on MainScreen.

### The Bug Flow

```
1. User adds "Gurken (3kg)" on MainScreen
   └─> BasketRepository.addItem(Gurken)
       └─> Basket now has: [Tomatoes, Kartoffeln, Eier, Gurken]  ✅

2. User navigates to BasketScreen
   └─> BasketViewModel.init()
       └─> loadMostRecentEditableOrder()
           └─> Detects order already loaded
               └─> orderRepository.loadOrder(orderId, date)
                   └─> Sets state with: items = order.articles  ❌
                       └─> Overwrites basket with original: [Tomatoes, Kartoffeln, Eier]
                           └─> hasChanges = false  ❌ (hardcoded)

3. Result: Gurken is LOST, button disabled
```

## The Fix

### Location 1: `loadMostRecentEditableOrder()` (Line 123-146)

**Before:**
```kotlin
result.onSuccess { order ->
    val threeDaysBeforePickup = order.pickUpDate - (3 * 24 * 60 * 60 * 1000)
    val canEdit = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() < threeDaysBeforePickup

    _state.value = _state.value.copy(
        orderId = orderId,
        orderDate = orderDate,
        pickupDate = order.pickUpDate,
        createdDate = order.createdDate,
        isEditMode = false,
        canEdit = canEdit,
        originalOrderItems = order.articles,
        hasChanges = false  // ❌ HARDCODED - ignores basket modifications
    )
}
```

**After:**
```kotlin
result.onSuccess { order ->
    val threeDaysBeforePickup = order.pickUpDate - (3 * 24 * 60 * 60 * 1000)
    val canEdit = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() < threeDaysBeforePickup

    // ✅ Get current basket items from repository (may have been modified on MainScreen)
    val currentBasketItems = basketRepository.observeBasket().value

    // ✅ Check if basket has changes compared to original order
    val hasChanges = checkIfBasketHasChanges(currentBasketItems, order.articles)

    _state.value = _state.value.copy(
        orderId = orderId,
        orderDate = orderDate,
        pickupDate = order.pickUpDate,
        createdDate = order.createdDate,
        isEditMode = false,
        canEdit = canEdit,
        originalOrderItems = order.articles,
        hasChanges = hasChanges  // ✅ Calculate from current basket state
    )
    println("🛒 BasketViewModel.loadMostRecentEditableOrder: Synced state with loaded order - hasChanges=$hasChanges")
}
```

### Location 2: `loadOrder()` (Line 434-476)

**Before:**
```kotlin
result.onSuccess { order ->
    println("✅ BasketViewModel.loadOrder: Order loaded successfully")

    val threeDaysBeforePickup = order.pickUpDate - (3 * 24 * 60 * 60 * 1000)
    val canEdit = Clock.System.now().toEpochMilliseconds() < threeDaysBeforePickup

    // ❌ Always overwrites basket
    basketRepository.loadOrderItems(order.articles, orderId, date)

    _state.value = _state.value.copy(
        orderId = orderId,
        orderDate = date,
        pickupDate = order.pickUpDate,
        createdDate = order.createdDate,
        isEditMode = false,
        canEdit = canEdit,
        isLoadingOrder = false,
        items = order.articles,  // ❌ Uses original order, not basket
        total = order.articles.sumOf { it.price * it.amountCount },
        originalOrderItems = order.articles,
        hasChanges = false  // ❌ HARDCODED
    )
}
```

**After:**
```kotlin
result.onSuccess { order ->
    println("✅ BasketViewModel.loadOrder: Order loaded successfully")

    val threeDaysBeforePickup = order.pickUpDate - (3 * 24 * 60 * 60 * 1000)
    val canEdit = Clock.System.now().toEpochMilliseconds() < threeDaysBeforePickup

    // ✅ Get current basket items BEFORE loading order
    val currentBasketItems = basketRepository.observeBasket().value

    // ✅ Only load order items into basket if it's empty or different order
    // This prevents overwriting user's MainScreen modifications
    val shouldLoadOrderItems = currentBasketItems.isEmpty() ||
        basketRepository.getLoadedOrderInfo()?.first != orderId

    if (shouldLoadOrderItems) {
        println("🛒 BasketViewModel.loadOrder: Loading order items into basket")
        basketRepository.loadOrderItems(order.articles, orderId, date)
    } else {
        println("🛒 BasketViewModel.loadOrder: Basket already has items, preserving user modifications")
    }

    // ✅ Get final basket items (either just loaded or existing with modifications)
    val finalBasketItems = basketRepository.observeBasket().value

    // ✅ Check if there are changes compared to original order
    val hasChanges = checkIfBasketHasChanges(finalBasketItems, order.articles)

    _state.value = _state.value.copy(
        orderId = orderId,
        orderDate = date,
        pickupDate = order.pickUpDate,
        createdDate = order.createdDate,
        isEditMode = false,
        canEdit = canEdit,
        isLoadingOrder = false,
        items = finalBasketItems,  // ✅ Use actual basket items, not original order
        total = finalBasketItems.sumOf { it.price * it.amountCount },
        originalOrderItems = order.articles,
        hasChanges = hasChanges  // ✅ Calculate from actual basket state
    )
    println("🛒 BasketViewModel.loadOrder: State updated - hasChanges=$hasChanges, items=${finalBasketItems.size}")
}
```

## Key Changes

### 1. Preserve Basket Modifications
- **Before**: Always called `basketRepository.loadOrderItems()`, overwriting any changes
- **After**: Check if basket already has items for this order, only load if empty or different order

### 2. Calculate `hasChanges` Dynamically
- **Before**: Hardcoded to `false`
- **After**: Call `checkIfBasketHasChanges(currentBasket, originalOrder)` to properly detect modifications

### 3. Use Actual Basket State
- **Before**: Set `items = order.articles` (original order)
- **After**: Set `items = finalBasketItems` (current basket state with modifications)

## Testing Scenarios

### Scenario 1: Add New Item on MainScreen
```
1. App loads order: [Tomatoes(2kg), Kartoffeln(5kg)]
2. User selects "Gurken" on MainScreen
3. User enters quantity: 3kg
4. User clicks "In den Korb"
   ✅ BasketRepository: [Tomatoes(2kg), Kartoffeln(5kg), Gurken(3kg)]
5. User navigates to BasketScreen
   ✅ Shows all 3 items
   ✅ hasChanges = true (3 items vs 2 original)
   ✅ "Bestellung aktualisieren" button ENABLED
```

### Scenario 2: Modify Existing Item on MainScreen
```
1. App loads order: [Tomatoes(2kg), Kartoffeln(5kg)]
2. User selects "Tomatoes" (already in order)
3. Quantity field shows: "2" (current amount)
4. User changes to: "5kg"
5. User clicks "In den Korb"
   ✅ BasketRepository: [Tomatoes(5kg), Kartoffeln(5kg)]
6. User navigates to BasketScreen
   ✅ Shows Tomatoes with 5kg (not 2kg)
   ✅ hasChanges = true (quantity changed)
   ✅ "Bestellung aktualisieren" button ENABLED
```

### Scenario 3: Remove Item on MainScreen
```
1. App loads order: [Tomatoes(2kg), Kartoffeln(5kg), Eier(10)]
2. User selects "Eier"
3. User clicks "Aus Korb"
   ✅ BasketRepository: [Tomatoes(2kg), Kartoffeln(5kg)]
4. User navigates to BasketScreen
   ✅ Shows only 2 items (Eier removed)
   ✅ hasChanges = true (2 items vs 3 original)
   ✅ "Bestellung aktualisieren" button ENABLED
```

### Scenario 4: No Changes
```
1. App loads order: [Tomatoes(2kg), Kartoffeln(5kg)]
2. User browses products but doesn't add/remove
3. User navigates to BasketScreen
   ✅ Shows 2 items (original)
   ✅ hasChanges = false
   ✅ "Bestellung aktualisieren" button DISABLED
   ✅ Shows "Keine Änderungen"
```

## Fixed Flow

```
1. User adds "Gurken (3kg)" on MainScreen
   └─> UnifiedAppViewModel.addMainScreenToCart()
       └─> BasketRepository.addItem(Gurken)
           └─> _basket.value = [...original items..., Gurken]
               └─> StateFlow emits update
                   └─> observeMainScreenBasket() updates badge

2. User navigates to BasketScreen
   └─> BasketViewModel.init()
       └─> observeBasket().collect { items ->
           │   hasChanges = checkIfBasketHasChanges(items, originalOrderItems)
           │   _state.value = _state.value.copy(items, hasChanges)
           └─> }
       └─> loadMostRecentEditableOrder()
           └─> Detects order already loaded
               └─> orderRepository.loadOrder(orderId, date)
                   └─> Get currentBasketItems from repository  ✅
                       └─> Check hasChanges = checkIfBasketHasChanges(currentBasketItems, order.articles)  ✅
                           └─> Set state with hasChanges = true  ✅

3. Result:
   ✅ All items displayed including Gurken
   ✅ hasChanges = true
   ✅ "Bestellung (geändert)" indicator shown
   ✅ "Bestellung aktualisieren" button ENABLED
```

## Implementation Notes

### Why Check Before Loading?
The condition:
```kotlin
val shouldLoadOrderItems = currentBasketItems.isEmpty() ||
    basketRepository.getLoadedOrderInfo()?.first != orderId
```

This ensures:
1. **First load**: If basket is empty, load the order
2. **Different order**: If user switches to a different order, reload
3. **Same order with modifications**: Don't overwrite user's changes

### Observer Pattern
The `observeBasket()` in `init {}` continues to work correctly:
```kotlin
basketRepository.observeBasket().collect { items ->
    val hasChanges = checkIfBasketHasChanges(items, _state.value.originalOrderItems)
    _state.value = _state.value.copy(
        items = items,
        total = basketRepository.getTotal(),
        hasChanges = hasChanges
    )
}
```

This reactive observer ensures that:
- Any basket changes trigger state updates
- `hasChanges` is recalculated on every change
- UI stays in sync with basket state

## Benefits

1. **Data Preservation**: User modifications on MainScreen are never lost
2. **Accurate Change Detection**: `hasChanges` reflects actual basket state
3. **Better UX**: Users see their changes immediately when navigating to BasketScreen
4. **Single Source of Truth**: BasketRepository remains authoritative
5. **Reactive Updates**: Changes propagate automatically via StateFlow

## Files Modified

- `BasketViewModel.kt:123-146` - Fixed `loadMostRecentEditableOrder()`
- `BasketViewModel.kt:434-476` - Fixed `loadOrder()`

## Testing Commands

```bash
# Build the project
./gradlew :shared:build

# Run on Android
./gradlew :androidApp:installDebug

# Test the fix:
# 1. Sign in as buyer
# 2. Wait for existing order to load (check badge count)
# 3. Add a new product from MainScreen
# 4. Navigate to basket (click badge)
# 5. Verify: New product visible, hasChanges=true, button enabled
```

---

**Bug Fix Version**: 1.0
**Date**: 2025-11-12
**Fixed By**: Claude Code Analysis
