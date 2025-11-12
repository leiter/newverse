# Ordering Business Rules

## Core Rules

### 1. Pickup Schedule
- **Pickup Day:** Always Thursday
- **Pickup Time:** [To be defined - e.g., 10:00-18:00]
- **Pickup Location:** [To be defined]

### 2. Order Placement Window
- **Target Pickup:** Next Thursday from today
- **Order Deadline:** Tuesday 23:59 before pickup Thursday
- **Example:**
  - Today: Monday, Nov 11
  - Order deadline: Tuesday, Nov 12 at 23:59
  - Pickup date: Thursday, Nov 14

### 3. Order Modification Rules

#### 3.1 Draft Orders (Not Yet Placed)
- **Status:** `DRAFT` or order doesn't exist in Firebase
- **Behavior:**
  - Can be modified freely
  - Auto-updates pickup date to next Thursday
  - No edit deadline applies
  - Stored only in `BasketRepository` (memory)

#### 3.2 Placed Orders (Confirmed)
- **Status:** `PLACED` / `CONFIRMED`
- **Behavior:**
  - Can be modified until Tuesday 23:59 before pickup
  - Cannot change pickup date
  - After deadline: Read-only
  - Stored in Firebase

#### 3.3 Past Orders
- **Status:** `COMPLETED` / `PAST`
- **Behavior:**
  - Read-only (always)
  - Can be cloned to new order

### 4. Order State Transitions

```
[Empty Basket]
    ↓ (add items)
[Draft Order]
    ↓ (place order)
[Placed Order - Editable]
    ↓ (Tuesday 23:59 passes)
[Placed Order - Locked]
    ↓ (Thursday pickup passes)
[Completed Order]
```

### 5. Date Calculation Logic

#### 5.1 Next Pickup Date (Thursday)
From any given day:
- **Monday → Thursday (this week)** - 3 days away
- **Tuesday → Thursday (this week)** - 2 days away
- **Wednesday → Thursday (this week)** - 1 day away
- **Thursday 00:00-23:59 → Thursday (next week)** - 7 days away
- **Friday → Thursday (next week)** - 6 days away
- **Saturday → Thursday (next week)** - 5 days away
- **Sunday → Thursday (next week)** - 4 days away

**Special Cases:**
- If today is Thursday, pickup is NEXT Thursday (not today)
- If today is after Tuesday 23:59, order is for next week's Thursday

#### 5.2 Edit Deadline (Tuesday 23:59)
For a given Thursday pickup:
- **Deadline:** Tuesday at 23:59:59 (same week as pickup)
- **Example:**
  - Pickup: Thursday, Nov 14
  - Deadline: Tuesday, Nov 12 at 23:59:59

#### 5.3 Order Window Status
Given current time and pickup date, determine:
```kotlin
enum class OrderWindowStatus {
    OPEN,           // Can place or edit order
    DEADLINE_PASSED, // Cannot edit (after Tuesday 23:59)
    PICKUP_PASSED   // Order completed (after Thursday)
}
```

### 6. Business Rules Matrix

| Current Day | Time | Can Place Order? | Can Edit Existing? | Pickup Date |
|-------------|------|------------------|-------------------|-------------|
| Monday | Any | ✅ Yes | ✅ Yes | This Thursday |
| Tuesday | 00:00-23:59 | ✅ Yes | ✅ Yes | This Thursday |
| Tuesday | 23:59:59 | ✅ Yes (last second!) | ✅ Yes (last second!) | This Thursday |
| Wednesday | 00:00 onwards | ❌ No (deadline passed) | ❌ No | Next Thursday* |
| Thursday | Any | ✅ Yes | ❌ No | Next Thursday |
| Friday | Any | ✅ Yes | ❌ No | Next Thursday |
| Saturday | Any | ✅ Yes | ❌ No | Next Thursday |
| Sunday | Any | ✅ Yes | ❌ No | Next Thursday |

*For new orders on Wednesday, pickup is next Thursday (not this Thursday, since deadline passed)

### 7. Edge Cases

#### 7.1 Order Placed on Tuesday at 23:58
- Can place order ✅
- Pickup: This Thursday
- Can edit: For 2 minutes only (until 23:59:59)

#### 7.2 User Edits Order on Tuesday at 23:55
- Starts editing at 23:55 ✅
- Clock hits 00:00 (Wednesday) while editing
- What happens?
  - **Option A:** Allow save (grace period)
  - **Option B:** Reject save with error
  - **Recommended:** Option B (strict enforcement)

#### 7.3 Time Zone Handling
- Use **local time zone** (user's device timezone)
- Store pickup date as **midnight UTC of pickup day**
- Store deadline as **23:59:59 in user's timezone**

#### 7.4 Order Exists for Next Thursday
- User has order for Nov 14 (Thursday)
- User tries to add items on Nov 10 (Sunday)
- **Behavior:**
  - Load existing order into basket
  - Show "Editing order for Nov 14"
  - Allow modifications (deadline not passed)

#### 7.5 Multiple Orders
**Rule:** Only one order per pickup date per user
- If order exists for Thursday Nov 14 → Edit it
- If no order exists → Create new one
- Cannot create second order for same pickup date

### 8. User Experience Guidelines

#### 8.1 Deadline Warnings
Show warnings as deadline approaches:
- **48 hours before:** "Order editable for 2 more days"
- **24 hours before:** "Order editable until tomorrow 23:59"
- **6 hours before:** "Order closes at 23:59 today"
- **1 hour before:** "⚠️ Last chance to edit! Closes in 1 hour"

#### 8.2 Clear Communication
Always show:
- ✅ "Pickup: Thursday, Nov 14"
- ✅ "Order deadline: Tuesday, Nov 12 at 23:59"
- ✅ "Time remaining: 2 days, 5 hours"

#### 8.3 Error Messages
When deadline passed:
```
"Order cannot be modified"
"The deadline (Tuesday 23:59) has passed"
"Pickup: Thursday, Nov 14"
"Your order is confirmed and cannot be changed"
```

### 9. Implementation Requirements

#### 9.1 Date Utilities Needed
```kotlin
// Calculate next Thursday from any date
fun calculateNextPickupDate(fromDate: Instant = Clock.System.now()): Instant

// Calculate edit deadline (Tuesday 23:59) for a given pickup date
fun calculateEditDeadline(pickupDate: Instant): Instant

// Check if order can be edited
fun canEditOrder(pickupDate: Instant, now: Instant = Clock.System.now()): Boolean

// Get order window status
fun getOrderWindowStatus(pickupDate: Instant, now: Instant = Clock.System.now()): OrderWindowStatus

// Check if order is for current ordering cycle
fun isCurrentOrderingCycle(pickupDate: Instant, now: Instant = Clock.System.now()): Boolean
```

#### 9.2 Order State Tracking
```kotlin
data class Order(
    // ... existing fields
    val status: OrderStatus,
    val placedAt: Long,      // When order was placed
    val pickupDate: Long,    // Thursday date
    val editDeadline: Long   // Tuesday 23:59
)

enum class OrderStatus {
    DRAFT,      // In basket, not saved
    PLACED,     // Saved to Firebase
    LOCKED,     // Deadline passed, read-only
    COMPLETED,  // Pickup date passed
    CANCELLED   // User cancelled
}
```

### 10. Testing Scenarios

#### Scenario 1: Monday → Place Order
- Today: Monday, Nov 11, 10:00
- Expected pickup: Thursday, Nov 14
- Expected deadline: Tuesday, Nov 12, 23:59
- Can edit: Yes

#### Scenario 2: Tuesday 23:58 → Place Order
- Today: Tuesday, Nov 12, 23:58
- Expected pickup: Thursday, Nov 14
- Expected deadline: Tuesday, Nov 12, 23:59 (2 minutes away!)
- Can edit: Yes (for 2 minutes)

#### Scenario 3: Wednesday → Try to Edit
- Today: Wednesday, Nov 13, 00:01
- Existing order: Thursday, Nov 14
- Deadline passed: Tuesday, Nov 12, 23:59
- Can edit: No

#### Scenario 4: Thursday → Place New Order
- Today: Thursday, Nov 14, 15:00
- Expected pickup: Thursday, Nov 21 (next week)
- Expected deadline: Tuesday, Nov 19, 23:59
- Can edit: Yes

#### Scenario 5: Sunday → Edit Existing Order
- Today: Sunday, Nov 10, 20:00
- Existing order: Thursday, Nov 14
- Deadline: Tuesday, Nov 12, 23:59 (48 hours away)
- Can edit: Yes

### 11. Migration from Current Implementation

#### Current Issues
- Using "3 days before pickup" rule → Should be "Tuesday 23:59"
- Using "tomorrow" as pickup → Should be "next Thursday"
- Using 9-day offset for testing → Should use proper date calculation

#### Changes Needed
1. Replace hardcoded 3-day calculation with Thursday/Tuesday logic
2. Remove development offset (9 days)
3. Add OrderStatus field to Order model
4. Update UI to show correct dates and deadlines
5. Add deadline warnings

## Date
2025-11-12
