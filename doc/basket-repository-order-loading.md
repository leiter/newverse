# Basket Repository Order Loading Implementation

## Overview
This document describes the implementation of basket repository updates when an open order exists after app startup.

## Problem Statement
When the app starts and a user has an open/editable order, the basket needs to be populated with the order items. Previously, there were two separate flows that could conflict:

1. **UnifiedAppViewModel** - Loaded open order after authentication
2. **BasketViewModel** - Independently tried to load the most recent editable order

This could lead to:
- Duplicate loading attempts
- Race conditions
- Inconsistent state between ViewModels

## Solution Architecture

### 1. Enhanced BasketRepository Interface
Added new methods to track loaded order metadata:

```kotlin
/**
 * Load items from an existing order
 * Clears basket and adds all order items
 */
suspend fun loadOrderItems(items: List<OrderedProduct>, orderId: String, orderDate: String)

/**
 * Get current loaded order info
 * @return Pair of orderId and orderDate, or null if no order is loaded
 */
fun getLoadedOrderInfo(): Pair<String, String>?
```

### 2. Implementation Details

#### InMemoryBasketRepository
- Added private fields to track loaded order:
  - `_loadedOrderId: String?`
  - `_loadedOrderDate: String?`

- `loadOrderItems()` - Atomically loads order items and stores order metadata
- `getLoadedOrderInfo()` - Returns current order info if available
- `clearBasket()` - Also clears order metadata

#### UnifiedAppViewModel
**Location:** `shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt:95-153`

Changed `loadOpenOrderAfterAuth()` to use `loadOrderItems()`:
```kotlin
basketRepository.loadOrderItems(order.articles, order.id, dateKey)
```

This ensures the basket repository knows which order is currently loaded.

#### BasketViewModel
**Location:** `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/BasketViewModel.kt:93-158`

Enhanced `loadMostRecentEditableOrder()` to check for existing loaded orders:

1. **Check First** - Calls `basketRepository.getLoadedOrderInfo()`
2. **If Order Already Loaded** - Syncs BasketViewModel state with the existing order
3. **If No Order Loaded** - Proceeds with normal order loading flow

Also updated `loadOrder()` method to use `loadOrderItems()`:
```kotlin
basketRepository.loadOrderItems(order.articles, orderId, date)
```

## Flow Diagram

```
App Start
    ↓
UnifiedAppViewModel.initializeApp()
    ↓
observeAuthState() → User Authenticated
    ↓
loadOpenOrderAfterAuth()
    ↓
OrderRepository.getOpenEditableOrder()
    ↓
BasketRepository.loadOrderItems(items, orderId, orderDate) ✓
    ↓
[Basket Repository now has order loaded]
    ↓
BasketViewModel.init() → loadMostRecentEditableOrder()
    ↓
BasketRepository.getLoadedOrderInfo() → Returns (orderId, orderDate)
    ↓
[Skip duplicate loading, sync state only] ✓
```

## Benefits

1. **No Duplicate Loading** - BasketViewModel checks if order already loaded before attempting to load
2. **Centralized Order State** - BasketRepository is the single source of truth for loaded order
3. **Better Coordination** - ViewModels coordinate through BasketRepository
4. **Atomic Operations** - `loadOrderItems()` ensures items and metadata are updated together
5. **Clear Lifecycle** - Order info is cleared when basket is cleared

## Testing Considerations

### Test Scenarios
1. **Cold Start with Open Order**
   - User has an editable order (>3 days before pickup)
   - App starts → Order loads → Basket shows items
   - BasketViewModel initializes → Detects existing order → Syncs state

2. **Navigation to Basket Screen**
   - Order already loaded by UnifiedAppViewModel
   - Navigate to basket → Items displayed correctly
   - Can edit and update order

3. **Clear Basket**
   - Clear basket → Order metadata also cleared
   - `getLoadedOrderInfo()` returns null

4. **Place New Order**
   - Existing order in basket
   - Place new order → Basket updated with new order ID/date

## Files Modified

1. `shared/src/commonMain/kotlin/com/together/newverse/domain/repository/BasketRepository.kt`
   - Added `loadOrderItems()` method
   - Added `getLoadedOrderInfo()` method

2. `shared/src/commonMain/kotlin/com/together/newverse/data/repository/InMemoryBasketRepository.kt`
   - Implemented new methods
   - Added order metadata tracking

3. `shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt`
   - Updated `loadOpenOrderAfterAuth()` to use `loadOrderItems()`

4. `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/BasketViewModel.kt`
   - Enhanced `loadMostRecentEditableOrder()` to check existing order
   - Updated `loadOrder()` to use `loadOrderItems()`

## Related Documentation

- `doc/initialization-flow-implementation-complete.md` - App initialization flow
- `doc/development-order-date-offset.md` - Order date handling for testing

## Date
2025-11-12
