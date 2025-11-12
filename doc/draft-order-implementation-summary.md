# Draft Order Implementation - Summary

## Decisions Made

### 1. User Must Select Pickup Date ✅
- System does NOT auto-assign dates
- User explicitly chooses from available Thursdays
- Draft orders don't auto-update when user returns

### 2. Auto-Complete Old Orders ✅
- Orders with passed pickup date AND €0 total → Mark as COMPLETED
- Automatically hidden from active orders
- Prevents clutter from abandoned empty orders

### 3. Ignore (For Now)
- ❌ Time zone edge cases (use device timezone)
- ❌ Admin override features

## Implementation Status

### ✅ Completed

#### 1. OrderDateUtils Enhanced
**File:** `shared/src/commonMain/kotlin/com/together/newverse/util/OrderDateUtils.kt`

**New Functions:**
```kotlin
// Get 5 available Thursdays where deadline hasn't passed
getAvailablePickupDates(count: Int = 5): List<Instant>

// Validate if selected date is still valid
isPickupDateValid(pickupDate: Instant): Boolean
```

**Example Usage:**
```kotlin
// Get next 5 available pickup dates
val dates = OrderDateUtils.getAvailablePickupDates()
// Returns: [Nov 13, Nov 20, Nov 27, Dec 4, Dec 11]

// On Wednesday, same call returns:
// [Nov 20, Nov 27, Dec 4, Dec 11, Dec 18]
// (Nov 13 excluded because deadline passed)
```

#### 2. Documentation Created
- `doc/draft-order-behavior.md` - Complete specification
- `doc/draft-order-implementation-summary.md` - This file

### ⏳ To Be Implemented

#### 1. Update BasketScreenState
**File:** `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/BasketViewModel.kt`

**Changes Needed:**
```kotlin
data class BasketScreenState(
    // ... existing fields

    // NEW: Date selection
    val selectedPickupDate: Long? = null,          // null = not selected yet
    val availablePickupDates: List<Long> = emptyList(),
    val showDatePicker: Boolean = false
)

sealed interface BasketAction {
    // ... existing actions

    // NEW: Date selection actions
    data object ShowDatePicker : BasketAction
    data object HideDatePicker : BasketAction
    data class SelectPickupDate(val date: Long) : BasketAction
    data object LoadAvailableDates : BasketAction
}
```

#### 2. Update Checkout Flow
**File:** `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/BasketViewModel.kt:233-331`

**Changes Needed:**
```kotlin
private fun checkout() {
    viewModelScope.launch {
        // NEW: Check if date selected
        val selectedDate = _state.value.selectedPickupDate
        if (selectedDate == null) {
            _state.value = _state.value.copy(
                orderError = "Bitte wählen Sie ein Abholdatum",
                showDatePicker = true
            )
            return@launch
        }

        // NEW: Validate date still valid
        if (!OrderDateUtils.isPickupDateValid(Instant.fromEpochMilliseconds(selectedDate))) {
            _state.value = _state.value.copy(
                selectedPickupDate = null,
                orderError = "Gewähltes Datum ist nicht mehr verfügbar",
                showDatePicker = true
            )
            loadAvailableDates()
            return@launch
        }

        // EXISTING: Continue with order creation
        // ... (use selectedDate for pickUpDate)
    }
}

// NEW: Load available dates
private fun loadAvailableDates() {
    val dates = OrderDateUtils.getAvailablePickupDates(count = 5)
    _state.value = _state.value.copy(
        availablePickupDates = dates.map { it.toEpochMilliseconds() }
    )
}

// NEW: Select date
private fun selectPickupDate(date: Long) {
    _state.value = _state.value.copy(
        selectedPickupDate = date,
        showDatePicker = false
    )
}
```

#### 3. Create Date Picker UI
**File:** `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/BasketScreen.kt`

**New Composable Needed:**
```kotlin
@Composable
fun PickupDateSelector(
    selectedDate: Long?,
    onShowPicker: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onShowPicker
    ) {
        Row(/* ... */) {
            Column {
                if (selectedDate != null) {
                    Text("Donnerstag, ${formatDate(selectedDate)}")
                    Text("Bestellbar bis: ${formatDeadline(selectedDate)}")
                } else {
                    Text("Abholdatum auswählen")
                }
            }
            Icon(Icons.Default.CalendarMonth)
        }
    }
}

@Composable
fun DatePickerDialog(
    availableDates: List<Long>,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            LazyColumn {
                items(availableDates) { date ->
                    DateOption(date, onDateSelected)
                }
            }
        }
    )
}
```

**Integration in BasketScreen:**
```kotlin
// In BasketContent(), before the items list:

// NEW: Date selector for new orders
if (state.orderId == null) {  // Only for draft orders
    PickupDateSelector(
        selectedDate = state.selectedPickupDate,
        onShowPicker = { onAction(BasketAction.ShowDatePicker) }
    )
    Spacer(modifier = Modifier.height(16.dp))
}

// NEW: Date picker dialog
if (state.showDatePicker) {
    DatePickerDialog(
        availableDates = state.availablePickupDates,
        onDateSelected = { date ->
            onAction(BasketAction.SelectPickupDate(date))
        },
        onDismiss = { onAction(BasketAction.HideDatePicker) }
    )
}
```

#### 4. Initialize Available Dates
**File:** `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/BasketViewModel.kt`

**In init block:**
```kotlin
init {
    // ... existing basket observation

    // NEW: Load available dates on init
    viewModelScope.launch {
        loadAvailableDates()
    }
}
```

#### 5. Auto-Complete Old Orders
**File:** `shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt`

**Add to initialization:**
```kotlin
private fun initializeApp() {
    viewModelScope.launch {
        // ... existing initialization

        // NEW: Auto-complete old empty orders
        when (userState) {
            is UserState.LoggedIn -> {
                // ... existing user loading

                // Check and complete old orders
                autoCompleteOldOrders()
            }
        }
    }
}

// NEW: Auto-complete function
private suspend fun autoCompleteOldOrders() {
    try {
        println("🔄 Checking for old orders to auto-complete...")

        val profileResult = profileRepository.getBuyerProfile()
        val buyerProfile = profileResult.getOrNull() ?: return

        val now = Clock.System.now()

        buyerProfile.placedOrderIds.forEach { (dateKey, orderId) ->
            val orderResult = orderRepository.loadOrder("", dateKey, orderId)

            orderResult.onSuccess { order ->
                val pickupPassed = now > Instant.fromEpochMilliseconds(order.pickUpDate)
                val total = order.articles.sumOf { it.price * it.amountCount }

                if (pickupPassed && total == 0.0 && order.status != OrderStatus.COMPLETED) {
                    println("✅ Auto-completing order $orderId (pickup passed, zero total)")

                    val completedOrder = order.copy(status = OrderStatus.COMPLETED)
                    orderRepository.updateOrder(completedOrder)
                }
            }
        }
    } catch (e: Exception) {
        println("❌ Error auto-completing orders: ${e.message}")
    }
}
```

#### 6. Update Order Loading Logic
**File:** `shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt:95-153`

**In loadOpenOrderAfterAuth():**
```kotlin
orderResult.onSuccess { order ->
    if (order != null) {
        // NEW: Skip completed orders
        if (order.status == OrderStatus.COMPLETED) {
            println("⏭️ Skipping completed order: ${order.id}")
            return@onSuccess
        }

        // NEW: Skip orders with €0 total
        val total = order.articles.sumOf { it.price * it.amountCount }
        if (total == 0.0) {
            println("⏭️ Skipping empty order: ${order.id}")
            return@onSuccess
        }

        // EXISTING: Load order into basket
        // ...
    }
}
```

## User Flow

### New Order Flow
```
1. User adds items to basket
   └→ Basket: 3 items, no date selected

2. User clicks "Checkout" / "Order"
   └→ System: "Please select pickup date"
   └→ Shows date picker

3. User sees available dates:
   - Thursday, 13.11.2025 (Order by: Tuesday 11.11.2025 23:59)
   - Thursday, 20.11.2025 (Order by: Tuesday 18.11.2025 23:59)
   - Thursday, 27.11.2025 (Order by: Tuesday 25.11.2025 23:59)
   - ...

4. User selects: Thursday, 20.11.2025
   └→ Date stored in basket state
   └→ Shows: "Pickup: Thursday, 20.11.2025"
   └→ Shows: "Order by: Tuesday, 18.11.2025 at 23:59"

5. User confirms order
   └→ Order saved to Firebase
   └→ Status: PLACED
   └→ Success message shown
```

### Abandoned Draft Recovery
```
Monday 10:00:
1. User adds items, selects Nov 13 (Thursday)
2. User closes app without confirming

Wednesday 10:00 (2 days later):
3. User reopens app
4. System checks: Nov 13 deadline passed? YES (Tuesday 23:59)
5. System clears invalid date from state
6. User sees: "Selected date no longer available. Please choose new date."
7. Date picker automatically opens
8. User selects Nov 20
9. User confirms order
```

### Auto-Complete Flow
```
Old Order State:
- Order #123: Thursday Oct 31, 2025
- Items: Empty (user removed all)
- Total: €0
- Status: PLACED

App Start (Monday Nov 10):
1. System loads user orders
2. Checks order #123:
   - Pickup date (Oct 31) < Now (Nov 10) ✅
   - Total = €0 ✅
   - Status != COMPLETED ✅
3. Marks order #123 as COMPLETED
4. Order hidden from active list
5. User doesn't see empty old order
```

## Migration Checklist

### Phase 1: Core Date Selection (Priority: HIGH)
- [ ] Add `selectedPickupDate` to BasketScreenState
- [ ] Add `availablePickupDates` to BasketScreenState
- [ ] Add date selection actions to BasketAction
- [ ] Implement `loadAvailableDates()` in BasketViewModel
- [ ] Implement `selectPickupDate()` in BasketViewModel
- [ ] Update `checkout()` to require date selection
- [ ] Update `checkout()` to validate selected date

### Phase 2: UI Components (Priority: HIGH)
- [ ] Create `PickupDateSelector` composable
- [ ] Create `DatePickerDialog` composable
- [ ] Add date selector to BasketScreen
- [ ] Add dialog state handling
- [ ] Test on different screen sizes

### Phase 3: Auto-Complete (Priority: MEDIUM)
- [ ] Add `autoCompleteOldOrders()` to UnifiedAppViewModel
- [ ] Call on app initialization
- [ ] Update order loading to skip completed orders
- [ ] Update order loading to skip zero-total orders
- [ ] Test with various order states

### Phase 4: Polish (Priority: LOW)
- [ ] Add loading states for date picker
- [ ] Add error messages for date validation
- [ ] Add deadline countdown in date picker
- [ ] Add visual indicators for selected date
- [ ] Add confirmation dialog before placing order

## Testing Scenarios

### Test 1: New Order with Date Selection
1. Add items to basket
2. Click "Order"
3. Verify date picker appears
4. Select a date
5. Verify date shows in UI
6. Confirm order
7. Verify order saved with correct date

### Test 2: Invalid Date Handling
1. Add items, select Thursday Nov 13 (Monday)
2. Wait until Wednesday (or manually set system time)
3. Try to checkout
4. Verify error: "Date no longer available"
5. Verify date picker reopens
6. Verify Nov 13 not in list
7. Select Nov 20
8. Verify order succeeds

### Test 3: Auto-Complete
1. Create order for past Thursday
2. Remove all items (total = €0)
3. Restart app
4. Verify order marked as COMPLETED
5. Verify order not loaded into basket
6. Verify order hidden from UI

### Test 4: Available Dates on Wednesday
1. Set system time to Wednesday
2. Load date picker
3. Verify this week's Thursday NOT in list
4. Verify next week's Thursday IS in list
5. Verify all dates have future deadlines

## Estimated Effort

- **Phase 1 (Core):** 3-4 hours
- **Phase 2 (UI):** 2-3 hours
- **Phase 3 (Auto-complete):** 1-2 hours
- **Phase 4 (Polish):** 1-2 hours

**Total:** 7-11 hours (~1.5 days)

## Next Steps

1. **Start with Phase 1** - Add state and logic
2. **Then Phase 2** - Build UI components
3. **Test thoroughly** - All scenarios above
4. **Phase 3** - Auto-complete feature
5. **Phase 4** - Polish and UX improvements

## Date
2025-11-12
