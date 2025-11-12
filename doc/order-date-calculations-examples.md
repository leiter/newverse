# Order Date Calculations - Examples

## Usage Examples

### Example 1: Calculate Next Pickup Date

```kotlin
import com.together.newverse.util.OrderDateUtils
import kotlinx.datetime.*

// From Monday
val monday = LocalDate(2025, 11, 10).atStartOfDayIn(TimeZone.currentSystemDefault())
val pickupFromMonday = OrderDateUtils.calculateNextPickupDate(monday)
// Result: Thursday, Nov 13, 2025 (3 days away)

// From Tuesday
val tuesday = LocalDate(2025, 11, 11).atStartOfDayIn(TimeZone.currentSystemDefault())
val pickupFromTuesday = OrderDateUtils.calculateNextPickupDate(tuesday)
// Result: Thursday, Nov 13, 2025 (2 days away)

// From Wednesday
val wednesday = LocalDate(2025, 11, 12).atStartOfDayIn(TimeZone.currentSystemDefault())
val pickupFromWednesday = OrderDateUtils.calculateNextPickupDate(wednesday)
// Result: Thursday, Nov 20, 2025 (NEXT week - 8 days away)

// From Thursday
val thursday = LocalDate(2025, 11, 13).atStartOfDayIn(TimeZone.currentSystemDefault())
val pickupFromThursday = OrderDateUtils.calculateNextPickupDate(thursday)
// Result: Thursday, Nov 20, 2025 (next week - 7 days away)

// From Friday
val friday = LocalDate(2025, 11, 14).atStartOfDayIn(TimeZone.currentSystemDefault())
val pickupFromFriday = OrderDateUtils.calculateNextPickupDate(friday)
// Result: Thursday, Nov 20, 2025 (6 days away)
```

### Example 2: Calculate Edit Deadline

```kotlin
// For Thursday, Nov 13, 2025 pickup
val pickup = LocalDate(2025, 11, 13).atStartOfDayIn(TimeZone.currentSystemDefault())
val deadline = OrderDateUtils.calculateEditDeadline(pickup)
// Result: Tuesday, Nov 11, 2025 at 23:59:59

println(OrderDateUtils.formatDisplayDateTime(deadline))
// Output: "11.11.2025 23:59"
```

### Example 3: Check if Order Can Be Edited

```kotlin
val order = Order(
    pickUpDate = pickupDate.toEpochMilliseconds(),
    status = OrderStatus.PLACED
)

// On Monday, Nov 10
val monday = LocalDate(2025, 11, 10).atTime(10, 0).toInstant(TimeZone.currentSystemDefault())
val canEditMonday = order.canEdit(monday)
// Result: true (deadline is Tuesday 23:59)

// On Tuesday, Nov 11 at 23:58
val tuesdayEvening = LocalDate(2025, 11, 11).atTime(23, 58).toInstant(TimeZone.currentSystemDefault())
val canEditTuesday = order.canEdit(tuesdayEvening)
// Result: true (2 minutes before deadline!)

// On Wednesday, Nov 12 at 00:01
val wednesday = LocalDate(2025, 11, 12).atTime(0, 1).toInstant(TimeZone.currentSystemDefault())
val canEditWednesday = order.canEdit(wednesday)
// Result: false (deadline passed)
```

### Example 4: Get Order Window Status

```kotlin
val pickupDate = LocalDate(2025, 11, 13).atStartOfDayIn(TimeZone.currentSystemDefault())

// On Monday, Nov 10
val status1 = OrderDateUtils.getOrderWindowStatus(pickupDate, monday)
// Result: OrderWindowStatus.OPEN

// On Wednesday, Nov 12 (after Tuesday 23:59)
val status2 = OrderDateUtils.getOrderWindowStatus(pickupDate, wednesday)
// Result: OrderWindowStatus.DEADLINE_PASSED

// On Friday, Nov 14 (after Thursday pickup)
val friday = LocalDate(2025, 11, 14).atTime(10, 0).toInstant(TimeZone.currentSystemDefault())
val status3 = OrderDateUtils.getOrderWindowStatus(pickupDate, friday)
// Result: OrderWindowStatus.PICKUP_PASSED
```

### Example 5: Check Current Ordering Cycle

```kotlin
val nextPickup = OrderDateUtils.calculateNextPickupDate() // Thursday, Nov 13

// Check if an order is for the current cycle
val order1 = Order(pickUpDate = nextPickup.toEpochMilliseconds())
println(order1.isCurrentCycle())
// Output: true

// Order for next week's Thursday
val nextWeekPickup = LocalDate(2025, 11, 20).atStartOfDayIn(TimeZone.currentSystemDefault())
val order2 = Order(pickUpDate = nextWeekPickup.toEpochMilliseconds())
println(order2.isCurrentCycle())
// Output: false
```

### Example 6: Format Time Until Deadline

```kotlin
val pickupDate = LocalDate(2025, 11, 13).atStartOfDayIn(TimeZone.currentSystemDefault())

// 3 days before deadline
val monday = LocalDate(2025, 11, 10).atTime(10, 0).toInstant(TimeZone.currentSystemDefault())
println(OrderDateUtils.formatTimeUntilDeadline(pickupDate, monday))
// Output: "1 day, 13 hours"

// 6 hours before deadline
val tuesdayEvening = LocalDate(2025, 11, 11).atTime(17, 59).toInstant(TimeZone.currentSystemDefault())
println(OrderDateUtils.formatTimeUntilDeadline(pickupDate, tuesdayEvening))
// Output: "6 hours, 0 minutes"

// After deadline
val wednesday = LocalDate(2025, 11, 12).atTime(10, 0).toInstant(TimeZone.currentSystemDefault())
println(OrderDateUtils.formatTimeUntilDeadline(pickupDate, wednesday))
// Output: "Deadline passed"
```

### Example 7: Get Deadline Warning Level

```kotlin
val pickupDate = LocalDate(2025, 11, 13).atStartOfDayIn(TimeZone.currentSystemDefault())

// 3 days before
val level1 = OrderDateUtils.getDeadlineWarningLevel(pickupDate, monday)
// Result: DeadlineWarningLevel.NONE

// 36 hours before
val sunday = LocalDate(2025, 11, 9).atTime(11, 59).toInstant(TimeZone.currentSystemDefault())
val level2 = OrderDateUtils.getDeadlineWarningLevel(pickupDate, sunday)
// Result: DeadlineWarningLevel.INFO

// 12 hours before
val tuesday = LocalDate(2025, 11, 11).atTime(11, 59).toInstant(TimeZone.currentSystemDefault())
val level3 = OrderDateUtils.getDeadlineWarningLevel(pickupDate, tuesday)
// Result: DeadlineWarningLevel.WARNING

// 3 hours before
val tuesdayLate = LocalDate(2025, 11, 11).atTime(20, 59).toInstant(TimeZone.currentSystemDefault())
val level4 = OrderDateUtils.getDeadlineWarningLevel(pickupDate, tuesdayLate)
// Result: DeadlineWarningLevel.URGENT

// 30 minutes before
val tuesdayVeryLate = LocalDate(2025, 11, 11).atTime(23, 29).toInstant(TimeZone.currentSystemDefault())
val level5 = OrderDateUtils.getDeadlineWarningLevel(pickupDate, tuesdayVeryLate)
// Result: DeadlineWarningLevel.CRITICAL
```

### Example 8: Creating an Order with Proper Dates

```kotlin
import kotlinx.datetime.Clock

fun createNewOrder(items: List<OrderedProduct>): Order {
    val now = Clock.System.now()
    val pickupDate = OrderDateUtils.calculateNextPickupDate(now)

    return Order(
        buyerProfile = currentBuyerProfile,
        createdDate = now.toEpochMilliseconds(),
        pickUpDate = pickupDate.toEpochMilliseconds(),
        articles = items,
        status = OrderStatus.DRAFT
    )
}

// Place the order
fun placeOrder(order: Order): Order {
    return order.copy(
        status = OrderStatus.PLACED
    )
}
```

### Example 9: Using Order Extension Methods

```kotlin
val order = Order(
    id = "order123",
    pickUpDate = pickupDate.toEpochMilliseconds(),
    status = OrderStatus.PLACED,
    articles = listOf(/* ... */)
)

// Check if editable
if (order.canEdit()) {
    println("Order can be edited until ${order.getFormattedDeadline()}")
} else {
    println("Order cannot be edited - deadline passed")
}

// Display order info
println("Pickup: ${order.getFormattedPickupDate()}")
println("Time remaining: ${order.getTimeUntilDeadline()}")
println("Status: ${order.getWindowStatus()}")
```

### Example 10: Handling Order Lifecycle

```kotlin
fun getOrderDisplayInfo(order: Order): String {
    return when (order.getWindowStatus()) {
        OrderWindowStatus.OPEN -> {
            "✅ Order editable until ${order.getFormattedDeadline()}\n" +
            "⏰ Time remaining: ${order.getTimeUntilDeadline()}"
        }
        OrderWindowStatus.DEADLINE_PASSED -> {
            "🔒 Order locked - deadline passed\n" +
            "📦 Pickup: ${order.getFormattedPickupDate()}"
        }
        OrderWindowStatus.PICKUP_PASSED -> {
            "✓ Order completed\n" +
            "📦 Picked up: ${order.getFormattedPickupDate()}"
        }
    }
}
```

## Testing Scenarios

### Scenario 1: Monday Morning Order
```kotlin
// Today: Monday, Nov 10, 2025 at 09:00
val now = LocalDate(2025, 11, 10).atTime(9, 0).toInstant(TimeZone.currentSystemDefault())
val pickup = OrderDateUtils.calculateNextPickupDate(now)
// Pickup: Thursday, Nov 13, 2025

val deadline = OrderDateUtils.calculateEditDeadline(pickup)
// Deadline: Tuesday, Nov 11, 2025 at 23:59:59

val canEdit = OrderDateUtils.canEditOrder(pickup, now)
// Result: true

val timeRemaining = OrderDateUtils.formatTimeUntilDeadline(pickup, now)
// Result: "1 day, 14 hours"
```

### Scenario 2: Tuesday Last Minute Order
```kotlin
// Today: Tuesday, Nov 11, 2025 at 23:57
val now = LocalDate(2025, 11, 11).atTime(23, 57).toInstant(TimeZone.currentSystemDefault())
val pickup = OrderDateUtils.calculateNextPickupDate(now)
// Pickup: Thursday, Nov 13, 2025 (still this week's Thursday!)

val canEdit = OrderDateUtils.canEditOrder(pickup, now)
// Result: true (for 3 more minutes!)

val timeRemaining = OrderDateUtils.formatTimeUntilDeadline(pickup, now)
// Result: "2 minutes"

val warningLevel = OrderDateUtils.getDeadlineWarningLevel(pickup, now)
// Result: DeadlineWarningLevel.CRITICAL
```

### Scenario 3: Wednesday New Order
```kotlin
// Today: Wednesday, Nov 12, 2025 at 10:00
val now = LocalDate(2025, 11, 12).atTime(10, 0).toInstant(TimeZone.currentSystemDefault())
val pickup = OrderDateUtils.calculateNextPickupDate(now)
// Pickup: Thursday, Nov 20, 2025 (NEXT week's Thursday!)
// Reason: This week's Tuesday deadline has passed

val deadline = OrderDateUtils.calculateEditDeadline(pickup)
// Deadline: Tuesday, Nov 18, 2025 at 23:59:59

val canEdit = OrderDateUtils.canEditOrder(pickup, now)
// Result: true (plenty of time)

val timeRemaining = OrderDateUtils.formatTimeUntilDeadline(pickup, now)
// Result: "6 days, 13 hours"
```

### Scenario 4: Trying to Edit Old Order on Wednesday
```kotlin
// Today: Wednesday, Nov 12, 2025 at 10:00
val now = LocalDate(2025, 11, 12).atTime(10, 0).toInstant(TimeZone.currentSystemDefault())

// Existing order for this week's Thursday
val oldOrderPickup = LocalDate(2025, 11, 13).atStartOfDayIn(TimeZone.currentSystemDefault())
val oldOrder = Order(
    pickUpDate = oldOrderPickup.toEpochMilliseconds(),
    status = OrderStatus.PLACED
)

val canEdit = oldOrder.canEdit(now)
// Result: false (deadline was Tuesday 23:59, which has passed)

val status = oldOrder.getWindowStatus(now)
// Result: OrderWindowStatus.DEADLINE_PASSED
```

## Date
2025-11-12
