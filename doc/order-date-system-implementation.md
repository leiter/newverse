# Order Date System Implementation

## Summary

Implemented a comprehensive order date calculation system based on the business rule:
- **Pickup day:** Always Thursday
- **Edit deadline:** Tuesday 23:59 before pickup

## Files Created/Modified

### New Files

#### 1. `shared/src/commonMain/kotlin/com/together/newverse/util/OrderDateUtils.kt`
Core utility functions for date calculations:

**Key Functions:**
- `calculateNextPickupDate()` - Gets next Thursday from any date
- `calculateEditDeadline()` - Gets Tuesday 23:59 before pickup
- `canEditOrder()` - Checks if order is before deadline
- `getOrderWindowStatus()` - Returns OPEN/DEADLINE_PASSED/PICKUP_PASSED
- `isCurrentOrderingCycle()` - Checks if pickup is for current week's Thursday
- `timeUntilDeadline()` - Returns remaining time as DateTimePeriod
- `formatTimeUntilDeadline()` - Human-readable time remaining
- `getDeadlineWarningLevel()` - Warning level (NONE/INFO/WARNING/URGENT/CRITICAL)

**Enums:**
- `OrderWindowStatus` - Order window state
- `DeadlineWarningLevel` - Proximity to deadline

#### 2. `shared/src/commonMain/kotlin/com/together/newverse/domain/model/OrderStatus.kt`
Order lifecycle states:

```kotlin
enum class OrderStatus {
    DRAFT,      // In basket, not saved
    PLACED,     // Saved, editable
    LOCKED,     // Deadline passed, read-only
    COMPLETED,  // Pickup passed
    CANCELLED   // User cancelled
}
```

Extension functions:
- `isEditable()` - Can be modified
- `isFinalized()` - Cannot be changed
- `isActive()` - Not completed/cancelled

### Modified Files

#### 3. `shared/src/commonMain/kotlin/com/together/newverse/domain/model/Order.kt`
Added:
- `status: OrderStatus` field
- `canEdit()` - Check if editable based on deadline
- `getEditDeadline()` - Get Tuesday 23:59 for this order
- `getWindowStatus()` - Get current window status
- `isCurrentCycle()` - Check if for current Thursday
- `getFormattedPickupDate()` - Display pickup date
- `getFormattedDeadline()` - Display deadline
- `getTimeUntilDeadline()` - Display time remaining

### Documentation Files

#### 4. `doc/ordering-business-rules.md`
Comprehensive business rules documentation:
- Core rules and constraints
- Order state transitions
- Date calculation logic
- Business rules matrix
- Edge cases
- User experience guidelines
- Implementation requirements
- Testing scenarios

#### 5. `doc/order-date-calculations-examples.md`
Code examples demonstrating:
- Calculate next pickup date
- Calculate edit deadline
- Check if order can be edited
- Get order window status
- Format time remaining
- Handle order lifecycle
- 10 usage examples
- 4 testing scenarios with expected results

#### 6. `doc/order-date-system-implementation.md` (this file)
Implementation summary and migration guide

## Business Rules Implemented

### Rule 1: Pickup is Always Thursday
```kotlin
val pickup = OrderDateUtils.calculateNextPickupDate()
// Returns next Thursday at 00:00 local time
```

**Logic:**
- Monday → This Thursday (3 days)
- Tuesday → This Thursday (2 days)
- Wednesday → NEXT Thursday (8 days) ⚠️
- Thursday → Next Thursday (7 days)
- Friday-Sunday → Next Thursday (4-6 days)

**Key Point:** If today is Wednesday or later, pickup is NEXT Thursday, not this Thursday (because Tuesday deadline has passed).

### Rule 2: Edit Deadline is Tuesday 23:59
```kotlin
val deadline = OrderDateUtils.calculateEditDeadline(pickupDate)
// Returns Tuesday at 23:59:59 before the pickup Thursday
```

**Validation:** Function verifies pickup date is actually a Thursday.

### Rule 3: Order Editability
```kotlin
val canEdit = order.canEdit()
```

Considers:
- ✅ Draft orders always editable
- ✅ Placed orders editable until Tuesday 23:59
- ❌ Locked/Completed/Cancelled orders never editable
- ✅ Uses current system time for check

## Key Design Decisions

### 1. Time Zone Handling
- All calculations use `TimeZone.currentSystemDefault()`
- Pickup date stored as epoch milliseconds (at midnight)
- Deadline is 23:59:59 in user's local timezone

**Rationale:** Users expect to order based on their local time.

### 2. Wednesday Cutoff
When creating new order on Wednesday:
- **Could** target this week's Thursday (1 day away)
- **Actually** targets next week's Thursday

**Rationale:** Tuesday deadline has passed, so cannot accept new orders for this week.

### 3. Strict Deadline Enforcement
At Tuesday 23:59:59.999, order is editable.
At Wednesday 00:00:00.000, order is locked.

**No grace period.**

**Rationale:** Clear, predictable rules. System time is source of truth.

### 4. Order Status Field
Added explicit `status` field to track lifecycle.

**Benefits:**
- Clear state management
- Can override time-based logic if needed
- Supports manual admin actions (e.g., force lock)
- Better Firebase queries

### 5. Extension Functions on Order
Rather than separate utility calls, order has built-in methods.

**Benefits:**
- Cleaner API: `order.canEdit()` vs `OrderDateUtils.canEditOrder(order.pickUpDate)`
- Self-contained logic
- Easier to test
- More idiomatic Kotlin

## Usage in Application

### Creating New Order
```kotlin
// ViewModel - checkout flow
private fun createOrder(items: List<OrderedProduct>): Order {
    val now = Clock.System.now()
    val pickupDate = OrderDateUtils.calculateNextPickupDate(now)

    return Order(
        buyerProfile = currentBuyerProfile,
        createdDate = now.toEpochMilliseconds(),
        pickUpDate = pickupDate.toEpochMilliseconds(),
        articles = items,
        status = OrderStatus.DRAFT  // Not saved yet
    )
}
```

### Placing Order
```kotlin
// Save to Firebase
orderRepository.placeOrder(order).onSuccess { savedOrder ->
    // Update status
    val placedOrder = savedOrder.copy(status = OrderStatus.PLACED)
    // Now editable until Tuesday 23:59
}
```

### Checking Editability
```kotlin
// In BasketViewModel
private fun loadOrder(order: Order) {
    val canEdit = order.canEdit()
    val timeRemaining = order.getTimeUntilDeadline()

    _state.value = _state.value.copy(
        orderId = order.id,
        pickupDate = order.pickUpDate,
        canEdit = canEdit,
        timeRemaining = timeRemaining,
        windowStatus = order.getWindowStatus()
    )
}
```

### Showing Warnings
```kotlin
// In UI
@Composable
fun DeadlineWarning(order: Order) {
    val warningLevel = OrderDateUtils.getDeadlineWarningLevel(
        pickupDate = Instant.fromEpochMilliseconds(order.pickUpDate)
    )

    when (warningLevel) {
        DeadlineWarningLevel.URGENT -> {
            Text(
                "⚠️ Order closes in ${order.getTimeUntilDeadline()}!",
                color = Color.Orange
            )
        }
        DeadlineWarningLevel.CRITICAL -> {
            Text(
                "🔴 LAST CHANCE! Closes in ${order.getTimeUntilDeadline()}!",
                color = Color.Red
            )
        }
        else -> { /* No warning */ }
    }
}
```

## Migration Steps

### Step 1: Add Status Field to Firebase Schema
```json
{
  "orders": {
    "sellerId": {
      "20251113": {
        "orderId": {
          "status": "PLACED",  // NEW FIELD
          "createdDate": 1731312000000,
          "pickUpDate": 1731456000000,
          // ... other fields
        }
      }
    }
  }
}
```

**Migration strategy:**
- New orders: Include `status` field
- Existing orders: Default to `PLACED`
- Background job: Add status to old orders

### Step 2: Update BasketViewModel
Replace old date calculations:

**Before:**
```kotlin
// Old: 3 days before pickup
val threeDaysBeforePickup = order.pickUpDate - (3 * 24 * 60 * 60 * 1000)
val canEdit = Clock.System.now().toEpochMilliseconds() < threeDaysBeforePickup
```

**After:**
```kotlin
// New: Tuesday 23:59 before Thursday
val canEdit = order.canEdit()
```

### Step 3: Update Order Creation
Replace development offset:

**Before:**
```kotlin
// DEVELOPMENT: 9 day offset
val devOffsetMillis = 9L * 24 * 60 * 60 * 1000
val tomorrow = Clock.System.now().toEpochMilliseconds() + (24 * 60 * 60 * 1000)
val adjustedPickUpDate = tomorrow + devOffsetMillis
```

**After:**
```kotlin
// PRODUCTION: Next Thursday
val pickupDate = OrderDateUtils.calculateNextPickupDate()
val order = Order(
    // ...
    pickUpDate = pickupDate.toEpochMilliseconds(),
    createdDate = Clock.System.now().toEpochMilliseconds()
)
```

### Step 4: Update UI Components
Show proper dates and deadlines:

```kotlin
// BasketScreen.kt
Text("Pickup: ${order.getFormattedPickupDate()}")
Text("Editable until: ${order.getFormattedDeadline()}")
Text("Time remaining: ${order.getTimeUntilDeadline()}")
```

### Step 5: Add Deadline Warnings
```kotlin
// Show warning as deadline approaches
val warningLevel = OrderDateUtils.getDeadlineWarningLevel(
    pickupDate = Instant.fromEpochMilliseconds(order.pickUpDate)
)

if (warningLevel != DeadlineWarningLevel.NONE) {
    DeadlineWarningBanner(order, warningLevel)
}
```

## Testing Strategy

### Unit Tests Needed
```kotlin
class OrderDateUtilsTest {
    @Test
    fun `calculateNextPickupDate from Monday returns this Thursday`()

    @Test
    fun `calculateNextPickupDate from Wednesday returns next Thursday`()

    @Test
    fun `calculateEditDeadline returns Tuesday 23-59`()

    @Test
    fun `canEditOrder returns false after deadline`()

    @Test
    fun `order with DRAFT status is always editable`()

    @Test
    fun `order with LOCKED status is never editable`()
}
```

### Integration Tests
```kotlin
class OrderLifecycleTest {
    @Test
    fun `create order on Monday for this Thursday`()

    @Test
    fun `create order on Wednesday for next Thursday`()

    @Test
    fun `edit order on Tuesday before deadline succeeds`()

    @Test
    fun `edit order on Wednesday after deadline fails`()
}
```

### Manual Testing Scenarios
See `doc/ordering-business-rules.md` Section 10 for detailed test scenarios.

## Breaking Changes

### ⚠️ Order Model
- Added required `status` field
- Existing code may need updates

### ⚠️ Date Calculations
- Changed from "3 days before" to "Tuesday 23:59"
- Old orders may have different edit windows

### ⚠️ Development Offset Removed
- No more 9-day test offset
- Tests need real date mocking

## Next Steps

### Priority 1: Core Integration (This Sprint)
1. ✅ Implement OrderDateUtils
2. ✅ Add OrderStatus enum
3. ✅ Update Order model
4. ⏳ Update BasketViewModel to use new calculations
5. ⏳ Update UnifiedAppViewModel order creation
6. ⏳ Update UI to show correct dates

### Priority 2: Enhanced UX (Next Sprint)
1. Add deadline warning banners
2. Show countdown timer
3. Add "Last chance" notifications
4. Polish error messages

### Priority 3: Testing (Next Sprint)
1. Write unit tests for OrderDateUtils
2. Write integration tests for order lifecycle
3. Add manual test scenarios
4. Test across different timezones

### Priority 4: Admin Features (Future)
1. Override order status manually
2. Extend deadlines for specific orders
3. Reporting on order timing
4. Analytics dashboard

## References

- **Business Rules:** `doc/ordering-business-rules.md`
- **Code Examples:** `doc/order-date-calculations-examples.md`
- **Implementation:**
  - `OrderDateUtils.kt`
  - `OrderStatus.kt`
  - `Order.kt`

## Date
2025-11-12
