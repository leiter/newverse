# Draft Order Behavior - Specification

## Overview
Focus on orders that are **NOT YET PLACED** (draft orders in basket).

## Core Principles

### 1. User Must Select Pickup Date
- System does NOT auto-assign pickup date
- User must explicitly choose from available dates
- Only valid Thursdays are shown
- No auto-update when user returns later

### 2. Available Pickup Dates
Show only Thursdays where deadline hasn't passed:

**Example (Today is Monday, Nov 10):**
- ✅ Thursday, Nov 13 (deadline: Tuesday Nov 11 at 23:59)
- ✅ Thursday, Nov 20 (deadline: Tuesday Nov 18 at 23:59)
- ✅ Thursday, Nov 27 (deadline: Tuesday Nov 25 at 23:59)
- (And 2-3 more future Thursdays)

**Example (Today is Wednesday, Nov 12):**
- ❌ Thursday, Nov 13 (deadline passed!)
- ✅ Thursday, Nov 20 (deadline: Tuesday Nov 18 at 23:59)
- ✅ Thursday, Nov 27 (deadline: Tuesday Nov 25 at 23:59)

### 3. Draft Order Lifecycle

```
[User adds items to basket]
    ↓
[Basket contains items, NO pickup date set]
    ↓
[User goes to checkout]
    ↓
[System shows available pickup dates]
    ↓
[User selects: Thursday, Nov 20]
    ↓
[Order created with selected date]
    ↓
[User confirms]
    ↓
[Order saved to Firebase with status: PLACED]
```

### 4. Basket State
```kotlin
data class BasketState(
    val items: List<OrderedProduct> = emptyList(),
    val selectedPickupDate: Long? = null,  // null until user selects
    val availablePickupDates: List<Long> = emptyList(),
    val total: Double = 0.0
)
```

### 5. Old Orders Auto-Completion

Orders with pickup date in the past AND total = 0:
- Automatically marked as `COMPLETED`
- Removed from active orders list
- Not loaded on app start

**Logic:**
```kotlin
fun shouldAutoComplete(order: Order, now: Instant): Boolean {
    val pickupPassed = now > Instant.fromEpochMilliseconds(order.pickUpDate)
    val hasNoItems = order.articles.isEmpty()
    val totalIsZero = order.articles.sumOf { it.price * it.amountCount } == 0.0

    return pickupPassed && (hasNoItems || totalIsZero)
}
```

**Example:**
- Order created Monday Nov 10 for Thursday Nov 13
- User removed all items (total = €0)
- Thursday Nov 13 passes
- System auto-marks as COMPLETED
- Order hidden from UI

## Detailed Behavior

### Scenario 1: New User Starts Order

**Monday, 10:00**
1. User adds items to basket
   - Basket: 3 items, €25.50
   - Selected pickup: null
   - Status: DRAFT

2. User goes to checkout
   - System calculates available dates
   - Shows: Nov 13, Nov 20, Nov 27, Dec 4, Dec 11

3. User selects: Thursday Nov 20
   - Basket updated with pickup date
   - Shows: "Pickup: Thursday, 20.11.2025"
   - Shows: "Order by: Tuesday, 18.11.2025 at 23:59"

4. User confirms order
   - Saved to Firebase
   - Status: DRAFT → PLACED

### Scenario 2: User Changes Mind on Date

**Monday, 10:00**
1. User has draft with date: Thursday Nov 13
2. User changes to: Thursday Nov 20
   - Date updated in basket
   - Order NOT yet saved
   - Still status: DRAFT

3. User confirms
   - Saved with Nov 20 date
   - Status: PLACED

### Scenario 3: User Abandons Order

**Monday, 10:00**
1. User adds items, selects Thursday Nov 13
2. User closes app without confirming
3. Items remain in basket (BasketRepository)
4. Pickup date remains selected

**Wednesday, 10:00 (2 days later)**
5. User opens app
6. Basket still has items + selected date (Nov 13)
7. System checks: Can still order for Nov 13?
   - Deadline: Tuesday Nov 11 at 23:59
   - Now: Wednesday, 10:00
   - Result: NO, deadline passed

8. System behavior:
   - Keep items in basket ✅
   - Clear selected pickup date ❌ (now invalid)
   - Show message: "Pickup date no longer available. Please select a new date."

### Scenario 4: Old Empty Order Auto-Complete

**Order History:**
- Order #123: Thursday Oct 31, 2025
  - Created: Oct 28
  - Items: Originally had 2 items
  - User removed all items → Total: €0
  - Status: PLACED

**Today: Monday, Nov 10**
- System checks order #123
- Pickup date (Oct 31) < Now (Nov 10) ✅
- Total = €0 ✅
- Action: Mark as COMPLETED
- Hidden from active orders

### Scenario 5: Valid Old Order (Not Completed)

**Order History:**
- Order #456: Thursday Nov 7, 2025
  - Items: 3 items, €25.50
  - Status: PLACED

**Today: Monday, Nov 10**
- System checks order #456
- Pickup date (Nov 7) < Now (Nov 10) ✅
- Total = €25.50 ❌ (not zero)
- Action: Keep as-is, user may have actually received items
- Show in history as past order

## Implementation

### 1. Available Dates Calculation

```kotlin
object OrderDateUtils {
    /**
     * Get list of available pickup dates (Thursdays)
     * Only includes dates where deadline hasn't passed
     *
     * @param count How many future dates to return (default: 5)
     * @param now Current time
     * @return List of Instants representing available Thursdays
     */
    fun getAvailablePickupDates(
        count: Int = 5,
        now: Instant = Clock.System.now()
    ): List<Instant> {
        val dates = mutableListOf<Instant>()
        var currentDate = now

        while (dates.size < count) {
            val nextThursday = calculateNextPickupDate(currentDate)

            // Check if deadline has passed
            if (canEditOrder(nextThursday, now)) {
                dates.add(nextThursday)
            }

            // Move to next week
            currentDate = nextThursday.plus(1, DateTimeUnit.DAY)
        }

        return dates
    }

    /**
     * Check if a selected pickup date is still valid
     * (deadline hasn't passed)
     */
    fun isPickupDateValid(
        pickupDate: Instant,
        now: Instant = Clock.System.now()
    ): Boolean {
        return canEditOrder(pickupDate, now)
    }
}
```

### 2. Auto-Complete Logic

```kotlin
// In OrderRepository or ViewModel
suspend fun checkAndCompleteOldOrders() {
    val profileResult = profileRepository.getBuyerProfile()
    val buyerProfile = profileResult.getOrNull() ?: return

    buyerProfile.placedOrderIds.forEach { (dateKey, orderId) ->
        val order = orderRepository.loadOrder(sellerId, dateKey, orderId).getOrNull()

        if (order != null && shouldAutoComplete(order)) {
            // Mark as completed
            val completedOrder = order.copy(status = OrderStatus.COMPLETED)
            orderRepository.updateOrder(completedOrder)

            println("✅ Auto-completed order $orderId (pickup passed, zero total)")
        }
    }
}

private fun shouldAutoComplete(order: Order): Boolean {
    val now = Clock.System.now()
    val pickupPassed = now > Instant.fromEpochMilliseconds(order.pickUpDate)
    val total = order.articles.sumOf { it.price * it.amountCount }

    return pickupPassed && total == 0.0
}
```

### 3. Basket State with Date Selection

```kotlin
data class BasketScreenState(
    val items: List<OrderedProduct> = emptyList(),
    val total: Double = 0.0,

    // Pickup date selection
    val selectedPickupDate: Long? = null,
    val availablePickupDates: List<Long> = emptyList(),
    val showDatePicker: Boolean = false,

    // Existing order fields
    val orderId: String? = null,
    val orderDate: String? = null,
    val isEditMode: Boolean = false,
    val canEdit: Boolean = false,

    // UI state
    val isCheckingOut: Boolean = false,
    val orderSuccess: Boolean = false,
    val orderError: String? = null,
    val hasChanges: Boolean = false
)

sealed interface BasketAction {
    // ... existing actions

    data object ShowDatePicker : BasketAction
    data object HideDatePicker : BasketAction
    data class SelectPickupDate(val date: Long) : BasketAction
    data object LoadAvailableDates : BasketAction
}
```

### 4. Checkout Flow

```kotlin
// In BasketViewModel
private fun checkout() {
    viewModelScope.launch {
        // Check if date selected
        val selectedDate = _state.value.selectedPickupDate
        if (selectedDate == null) {
            _state.value = _state.value.copy(
                orderError = "Bitte wählen Sie ein Abholdatum"
            )
            // Show date picker
            _state.value = _state.value.copy(showDatePicker = true)
            return@launch
        }

        // Validate date is still valid
        val isValid = OrderDateUtils.isPickupDateValid(
            Instant.fromEpochMilliseconds(selectedDate)
        )

        if (!isValid) {
            _state.value = _state.value.copy(
                selectedPickupDate = null,
                orderError = "Gewähltes Datum ist nicht mehr verfügbar. Bitte wählen Sie ein neues Datum."
            )
            loadAvailableDates()
            _state.value = _state.value.copy(showDatePicker = true)
            return@launch
        }

        // Proceed with order creation
        createAndPlaceOrder(selectedDate)
    }
}

private fun loadAvailableDates() {
    val dates = OrderDateUtils.getAvailablePickupDates(count = 5)
    _state.value = _state.value.copy(
        availablePickupDates = dates.map { it.toEpochMilliseconds() }
    )
}

private fun selectPickupDate(date: Long) {
    _state.value = _state.value.copy(
        selectedPickupDate = date,
        showDatePicker = false
    )
}
```

### 5. UI - Date Picker

```kotlin
@Composable
fun PickupDateSelector(
    selectedDate: Long?,
    availableDates: List<Long>,
    onDateSelected: (Long) -> Unit,
    onShowPicker: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onShowPicker
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Abholdatum",
                    style = MaterialTheme.typography.labelMedium
                )
                if (selectedDate != null) {
                    val formatted = OrderDateUtils.formatDisplayDate(
                        Instant.fromEpochMilliseconds(selectedDate)
                    )
                    val deadline = OrderDateUtils.calculateEditDeadline(
                        Instant.fromEpochMilliseconds(selectedDate)
                    )
                    val deadlineFormatted = OrderDateUtils.formatDisplayDateTime(deadline)

                    Text(
                        text = "Donnerstag, $formatted",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Bestellbar bis: $deadlineFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        text = "Datum auswählen",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Datum wählen"
            )
        }
    }
}

@Composable
fun DatePickerDialog(
    availableDates: List<Long>,
    selectedDate: Long?,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abholdatum wählen") },
        text = {
            LazyColumn {
                items(availableDates) { date ->
                    val instant = Instant.fromEpochMilliseconds(date)
                    val formatted = OrderDateUtils.formatDisplayDate(instant)
                    val deadline = OrderDateUtils.calculateEditDeadline(instant)
                    val deadlineFormatted = OrderDateUtils.formatDisplayDateTime(deadline)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { onDateSelected(date) }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Donnerstag, $formatted",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Bestellbar bis: $deadlineFormatted",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
```

## State Transitions

### Draft Order States

```
[Empty Basket]
  ↓ add items
[Draft: Items, No Date]
  ↓ select date
[Draft: Items + Date]
  ↓ confirm
[Placed: Editable]
  ↓ Tuesday 23:59 passes
[Placed: Locked]
  ↓ Thursday pickup passes
[Completed]
```

### Auto-Complete Flow

```
[Placed Order: €25.50]
  ↓ user removes items
[Placed Order: €0]
  ↓ Thursday pickup passes
[Auto-check on next app start]
  ↓ pickup passed + total = 0
[Mark as COMPLETED]
  ↓
[Hidden from active orders]
```

## Validation Rules

### 1. Checkout Validation
- ✅ Basket must have items
- ✅ Pickup date must be selected
- ✅ Pickup date must still be valid (deadline not passed)
- ✅ User must be authenticated

### 2. Date Selection Validation
- ✅ Date must be a Thursday
- ✅ Deadline (Tuesday 23:59) must not have passed
- ✅ Date must be in the future

### 3. Auto-Complete Validation
- ✅ Pickup date must be in the past
- ✅ Order total must be exactly €0
- ✅ Order status must be PLACED (not already COMPLETED)

## Error Messages

### No Date Selected
```
"Bitte wählen Sie ein Abholdatum"
"Please select a pickup date"
```

### Date No Longer Valid
```
"Gewähltes Datum ist nicht mehr verfügbar. Bitte wählen Sie ein neues Datum."
"Selected date is no longer available. Please choose a new date."
```

### No Available Dates (Edge case)
```
"Derzeit sind keine Abholtermine verfügbar."
"No pickup dates currently available."
```

## Summary

**Key Decisions:**
1. ✅ User must actively select pickup date
2. ✅ System shows only valid dates (deadline not passed)
3. ✅ Draft orders don't auto-update dates
4. ✅ Old orders with €0 total auto-mark as COMPLETED
5. ✅ Time zone and admin features ignored for now

**Next Steps:**
1. Add `getAvailablePickupDates()` to OrderDateUtils
2. Add date selection to BasketState
3. Implement date picker UI
4. Update checkout flow to require date
5. Add auto-complete check on app start

## Date
2025-11-12
