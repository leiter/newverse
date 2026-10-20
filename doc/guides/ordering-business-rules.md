# Ordering Business Rules

**Status:** current — verified against `shared/src` on 2026-10-20
**Scope:** `OrderDateUtils`, `PickupSlots`, `Market.scheduleConfig`,
`BuyAppViewModelBasket`, `BasketRepository`
**Supersedes:** `archive/draft-order-behavior.md`,
`archive/order_editing_workflow.md`

## Core Rules

### 1. Pickup Schedule
- **Pickup Day:** per market (`Market.dayOfWeek`); Thursday where a seller runs one market
- **Pickup Time:** [To be defined - e.g., 10:00-18:00]
- **Pickup Location:** the market the slot names (`Order.marketId`)

### 2. Order Placement Window

**Decided 2026-10-16: a buyer may order for the next open slot only.** One slot per
eligible market — its next date that is not closed and whose deadline has not passed —
and nothing beyond it. The seller cannot promise a catalogue or a price weeks ahead,
and `OrderedProduct` snapshots both at order time, so a longer horizon makes a promise
the seller has no way to keep. A buyer who misses this week orders next week.

There is no dead window: `calculateNextPickupDate` rolls past the deadline, so from
Wednesday 00:00 the next open slot is already the following week's. Exactly one cycle
is orderable at any moment.

**Implemented** (commit `b35f181`). `PickupSlots.nextPerMarket(markets, closures)`
returns one slot per eligible market and no more; with no market configured it falls
back to `OrderDateUtils.getAvailablePickupDates(count = 1, closures)`.
`availablePickupDates` is still populated alongside `availableSlots`, for the callers
that read dates without a market.

- **Target Pickup:** the next open slot
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

**Superseded 2026-10-16.** The rule below used to read "use the user's device timezone"
and "store the deadline at 23:59:59 in the user's timezone". That is the cause of
manual-test-plan **E2 (S1 critical)**: `pickUpDate` is an instant, and re-deriving a
calendar day from it in a different zone yields a different `yyyyMMdd`, so the
`/orders/{sellerId}/{yyyyMMdd}` path and the `placedOrderIds` key stop agreeing and the
order vanishes from the buyer's list while the seller still sees it.

The decided rule:

- **Pickup day, deadline and date keys are computed in the seller's zone**
  (Europe/Berlin). A market happens at a place, on that place's calendar; a buyer's
  device clock does not move it.
- **Display** may use the device zone — only the arithmetic is pinned.
- Store the pickup date as **midnight of the pickup day in the seller's zone**, and the
  deadline as **23:59:59.999 of the deadline day in the seller's zone**.

**Still not implemented — re-verified 2026-10-20.** Production mixes both, and the
split runs exactly along the arithmetic/display line the rule above draws, only the
wrong way round from what is wanted:

| | Zone | Where |
|---|---|---|
| Deadline and pickup arithmetic | **device** | `OrderDateUtils.kt` — 14 uses of `TimeZone.currentSystemDefault()`, all as default parameters |
| Slot dates | **device** | `PickupSlot.kt:41`, `:83`, `:110` |
| The `yyyyMMdd` order path segment | **device** | `GitLiveOrderRepository.kt:533`, `:881` |
| Basket date display | Berlin | `BasketScreen.kt:1138`, `:1175` |
| Shared date formatting | Berlin | `DateFormattingUtils.kt:16` (default parameter) |

42 non-test uses of `currentSystemDefault()` in all, against three Berlin pins — and
the three pins are the *display* side, which the rule says may stay on the device zone.
The arithmetic, which the rule says must be pinned, is the part that is not. That
inversion is manual-test-plan E1; E2 (S1 critical) is its consequence.

The order path segment is the one to be careful with: it decides *where an order lives*,
so changing how it is derived orphans any order whose key was written in another zone.
Inventory before changing it.

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

> **The code does not do this — undecided, needs a ruling.**
>
> §7.4 and §7.5 say a second basket for a date that already has an order cannot exist:
> load the order and edit it. The app instead builds a second basket and resolves it at
> checkout with a merge dialog — `BuyAppViewModelBasket.kt:695` (and a second path from
> the order history, `BuyAppViewModelHistory.kt:109`), over the conflicts computed by
> `basketScreenCalculateMergeConflicts`.
>
> It matters now because with one orderable slot (§2) the collision stops being an edge
> case: a buyer's second checkout in a week *always* lands on the order they already
> have, so whichever answer is right becomes the main path.
>
> - **Follow the rule:** route into edit mode, delete both merge paths and the conflict
>   model. Simpler, and it matches what §3.2 already says about a placed order.
> - **Follow the code:** amend §7.4/§7.5 to allow a second basket, and keep merging as
>   a real feature. It is built, tested and listed as implemented in `doc/plans/TODO.md`.
>
> Recommendation: follow the rule — but this is a behaviour change for buyers who use
> merging today, so it is a decision, not a cleanup.

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

## Delivery

**Built 2026-10-20.** An order is either collected at a market or delivered to an
address, and it carries which — plus the address — as a **snapshot**.

### Why a snapshot rather than a lookup

The seller cannot read `buyer_profile` at all: its rule is
`".read": "auth != null && $userId === auth.uid"`. So an order that does not carry its
own destination has none, and there is no path by which the seller could find one.

Before this, an order carried `buyerId`, `buyerName` and `buyerEmail` and nothing else.
A delivery order was indistinguishable from a pickup, and since `BuyerProfile.isSelfPickup`
defaults to **false**, every buyer who never touched that toggle was nominally a delivery
customer the seller knew nothing about.

Snapshotting also settles the awkward case. A buyer who moves house, or switches their
preference, must not re-aim an order the seller has already packed for the stall or
loaded onto a van. The order records what was true at the moment of commitment — the
same discipline as `OrderedProduct.price` and `Order.pricingRevision`.

### The choice is per order

`Fulfilment` defaults from the profile (`Fulfilment.fromProfile`) and is changed at the
checkout confirmation, not in the profile. A buyer may collect this week and want it
brought next, and a one-off delivery somewhere else must not require rewriting their
address — nor should rewriting their address re-aim orders already placed. Choosing for
one order never writes the profile.

Switching to delivery seeds the address from the profile when nothing has been typed,
so the common case is one tap rather than retyping what the app already holds.

### A delivery with no address cannot be placed

`basketScreenRequestCheckout` refuses it before the confirmation can be accepted. This
is the last line: there is no recovery after the write. `Order.isDeliveryWithoutAddress`
exists for orders that predate the field — the seller's list and detail screen say so in
red rather than showing a blank where an address belongs.

### What the seller sees

- **Order list:** a *Lieferung* badge and the address on the row, so a day's orders can
  be split into what stays on the stall and what travels. Also in the row's merged
  accessibility description, since a badge is invisible to a screen reader.
- **Order detail:** a *Abholung oder Lieferung* section above the items, with the
  address and any note the buyer added.

### A placed order, and a preference changed afterwards

The order keeps its snapshot. The buyer is **told**, by
`notifyProfileFulfilmentDiffersFromOrder`, and pointed at the edit path — which
re-snapshots the order's fulfilment and respects the deadline like any other change.
Past the deadline the message says only that the order stands.

The alternative, propagating to any still-editable order, was rejected: it changes an
order the seller has already seen without the seller acting, and the seller has no
notification channel to learn about it (`doc/design/order-ready-notification-design.md`
is unbuilt).

### Storage

`fulfilment` is written on every order; `deliveryAddress` **only on a delivery** — a
buyer's postal address is not data to scatter into orders it is irrelevant to, which is
the class of finding in `doc/reports/buyer-profile-data-safety.md`. Both validated in
`firebase/database.rules.json` on `orders` and `demo_orders`. **Not yet deployed.**

### Still open

- The seller cannot filter or group the order list by delivery, only see the badges.
- Nothing routes or orders deliveries — no round planning, no sequence.
- `CheckoutData.deliveryAddress` remains dead code from an older design;
  `BuyOrderAction.PlaceOrder` is a no-op in both flavors.

## Order Editing

An editable order is loaded into the basket at startup, so editing an order and
building a new one are the same screen and the same repository.

```
Startup → auth → buyer profile → placedOrderIds
        → load most recent editable order
        → BasketRepository.loadOrderItems(items, orderId, orderDate)

Buyer changes quantities → BasketRepository.addItem/updateQuantity/removeItem
        → observeBasket() StateFlow emits → badge and totals update

BasketScreen compares current vs. original items
        → hasChanges → "Bestellung aktualisieren" enabled
        → deadline re-checked → OrderRepository.updateOrder()
```

`BasketRepository` is the single source of truth for basket contents, whether they
came from an order or from browsing:

```kotlin
fun observeBasket(): StateFlow<List<OrderedProduct>>
suspend fun addItem(item: OrderedProduct)
suspend fun updateQuantity(productId: String, newQuantity: Double)
suspend fun removeItem(productId: String)
suspend fun clearBasket()
suspend fun loadOrderItems(items: List<OrderedProduct>, orderId: String, orderDate: String)
fun getLoadedOrderInfo(): Pair<String, String>?       // orderId to orderDate, or null
fun hasDraftBasket(): Boolean
suspend fun loadFromProfile(draftBasket: DraftBasket)
fun toDraftBasket(selectedPickupDate: String? = null, selectedMarketId: String = ""): DraftBasket
```

The draft basket round-trips through the buyer profile (`loadFromProfile` /
`toDraftBasket`), carrying the selected market as well as the date — so a basket
abandoned on one device reappears on the next with its pickup choice intact.

**Change detection** compares the live basket against the items the order was loaded
with: a differing size, a missing product, or a changed `amountCount` all count as a
change. **The deadline is re-validated at submit time**, not only when the button is
drawn, because the basket can sit open across the deadline.

Where the buyer is collecting is resolved basket-first: `contextualMarketId` reads
`basketScreen.selectedMarketId` before falling back to the profile default
(commit `8d04158`). `BuyerProfile.defaultMarket` currently has no UI write path — it
is read-only in the app as it stands.

## Document history

Rules dated 2025-11-12 originally; rewritten for per-market scheduling and the
single-slot decision in October 2026. The old "3 days before pickup" and
"tomorrow as pickup" implementations, and the 9-day development offset, are all
gone — if you find one, it is a bug, not a baseline.
