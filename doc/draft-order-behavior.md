# Draft Order Behavior

## Core Principles

1. **User Must Select Pickup Date** - No auto-assignment
2. **Only Valid Thursdays Shown** - Where deadline hasn't passed
3. **Draft Orders Don't Auto-Update** - User controls date selection

## Available Pickup Dates

```kotlin
fun getAvailablePickupDates(count: Int = 5): List<Instant> {
    // Returns Thursdays where deadline (Tuesday 23:59) hasn't passed
}
```

**Example (Today Monday Nov 10):**
- Nov 13, Nov 20, Nov 27, Dec 4, Dec 11

**Example (Today Wednesday Nov 12):**
- Nov 20, Nov 27, Dec 4, Dec 11 (Nov 13 deadline passed!)

## Draft Order Lifecycle

```
[Add items] → [Select pickup date] → [Confirm] → [PLACED]
```

## State

```kotlin
data class BasketState(
    val items: List<OrderedProduct> = emptyList(),
    val selectedPickupDate: Long? = null,  // null until selected
    val availablePickupDates: List<Long> = emptyList()
)
```

## Abandoned Draft Handling

If user returns after deadline passed for selected date:
1. Keep items in basket
2. Clear invalid pickup date
3. Show: "Pickup date no longer available. Please select a new date."

## Old Order Auto-Completion

Orders with pickup passed AND total = 0:
- Auto-mark as `COMPLETED`
- Hide from active orders

```kotlin
fun shouldAutoComplete(order: Order): Boolean {
    val pickupPassed = Clock.System.now() > Instant.fromEpochMilliseconds(order.pickUpDate)
    val total = order.articles.sumOf { it.price * it.amountCount }
    return pickupPassed && total == 0.0
}
```
