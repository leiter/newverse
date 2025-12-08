# Order Editing Workflow

## Overview

Orders are loaded into `BasketRepository` on app startup, enabling users to modify existing orders via MainScreen.

## Data Flow

```
App Startup
  → Check Auth → Load Buyer Profile
  → Get placedOrderIds → Load Most Recent Editable Order
  → BasketRepository.loadOrderItems()
  → Basket contains order items

User Adds/Modifies (MainScreen)
  → Select product → Adjust quantity → "In den Korb"
  → BasketRepository.addItem() / updateQuantity()
  → StateFlow emits update → Badge count updated

User Reviews (BasketScreen)
  → BasketViewModel observes BasketRepository
  → Compares current vs original items
  → hasChanges = true if different
  → "Bestellung aktualisieren" button enabled

User Applies Changes
  → Validates edit deadline
  → OrderRepository.updateOrder()
  → Firebase updated → Success confirmation
```

## Key Components

### BasketRepository (Single Source of Truth)

```kotlin
interface BasketRepository {
    fun observeBasket(): StateFlow<List<OrderedProduct>>
    suspend fun addItem(item: OrderedProduct)
    suspend fun updateQuantity(productId: String, newQuantity: Double)
    suspend fun removeItem(productId: String)
    suspend fun loadOrderItems(items: List<OrderedProduct>, orderId: String, orderDate: String)
}
```

### Change Detection

```kotlin
fun checkIfBasketHasChanges(
    currentItems: List<OrderedProduct>,
    originalItems: List<OrderedProduct>
): Boolean {
    if (currentItems.size != originalItems.size) return true
    return currentItems.any { current ->
        val original = originalItems.find { it.productId == current.productId }
        original == null || original.amountCount != current.amountCount
    }
}
```

### Edit Deadline

Orders can only be edited until Tuesday 23:59 before pickup Thursday.

```kotlin
val deadline = OrderDateUtils.calculateEditDeadline(pickupDate)
val canEdit = Clock.System.now() < deadline
```

## Implementation

**Key Files:**
- `UnifiedAppViewModel.kt:95-153` - Order loading
- `BasketViewModel.kt:135-194` - Change detection
- `BasketViewModel.kt:443-539` - Order update
- `InMemoryBasketRepository.kt` - Basket state management
