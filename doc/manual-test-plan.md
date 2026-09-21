# Newverse — Manual Test Plan (Bug Confirmation Pass)

**Created:** 2026-09-10
**Basis:** static read of `shared/src` at commit `501625d` (branch `main`)
**Purpose:** confirm or reject a set of suspected defects found by code inspection.

Every item below is a *hypothesis derived from reading the source*, not an observed
failure. Run the steps, then mark the verdict box. A rejected item is as valuable as a
confirmed one — please record what you actually saw either way.

---

## How to use this document

Each test case has this shape:

- **Code reference** — the file:line the hypothesis came from.
- **Hypothesis** — what the code appears to do wrong.
- **Steps** — the minimal path to reach it.
- **Expected (correct) behaviour** — what should happen.
- **Suspected actual behaviour** — what the code predicts will happen.
- **Verdict** — `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED` + your notes.

Items marked **Confidence: HIGH** are deterministic in the source — if the test
does not reproduce them, the reader misread something and that is worth knowing.
Items marked **Confidence: NEEDS CONFIRMATION** depend on runtime/backend state.

---

## Test environment setup

```bash
# Buyer app
./gradlew :androidApp:installBuyDebug

# Seller app
./gradlew :androidApp:installSellDebug

# Useful log filters
adb logcat -s "BasketViewModel" -s "GitLiveAuthRepository"
adb logcat | grep -E "🛒|📦|🔐|❌"
```

**Accounts needed**

| Role | Purpose |
|------|---------|
| Buyer A | main ordering account |
| Buyer B | second account, for seller-side aggregation tests |
| Seller S | owns the articles Buyers A/B order from |
| Demo/guest | anonymous flow (demo order migration) |

**Clock manipulation.** Several tests need the device to believe it is a specific
weekday. Use Android *Settings → System → Date & time → Set time automatically = off*,
then set the date manually. Force-stop and relaunch the app after every clock change —
several values are computed once at ViewModel init and will not refresh in place.

**Timezone manipulation.** Section E needs the device timezone changed. Same
settings screen. Again: force-stop and relaunch.

---

## Findings summary

| ID | Area | Suspected defect | Severity | Confidence |
|----|------|------------------|----------|------------|
| A1 | Money | `formatPrice()` shows most prices one cent too low | **S1 Critical** | HIGH |
| A2 | Weights | kg amounts render 1 g short (`1,2 kg` → `1,199 kg`) | S2 Major | HIGH |
| A3 | Money | Seller price auto-calc truncates, same one-cent class | S3 Minor | HIGH |
| A4 | Input | German decimal comma rejected in product price field | S2 Major | HIGH |
| B1 | Scheduling | Pickup date picker only ever offers 2 dates | S2 Major | HIGH |
| B2 | Lifecycle | Order reads as "pickup passed" for all of pickup day | S2 Major | HIGH |
| B3 | Lifecycle | Seller app never advances order status; only buyer does | S2 Major | HIGH |
| C1 | Basket | Re-adding an item corrupts its displayed amount/unit | S2 Major | HIGH |
| C2 | Basket | Quantity edit can set a negative piece count | S3 Minor | HIGH |
| C3 | Basket | Items with a blank/placeholder product id merge together | S2 Major | NEEDS CONFIRMATION |
| D1 | Seller | "Offen/Pending" filter hides LOCKED orders | **S1 Critical** | HIGH |
| D2 | Seller | Abrechnung period tab reports zero revenue | **S1 Critical** | HIGH |
| D3 | Seller | Abrechnung merges distinct products under a blank id | S2 Major | NEEDS CONFIRMATION |
| D4 | Seller | Non-standard tax rate silently miscomputes VAT | S2 Major | NEEDS CONFIRMATION |
| E1 | Data | Basket screen renders dates in Berlin time, rest of app in device time | S2 Major | HIGH |
| E2 | Data | Timezone change can orphan a placed order | **S1 Critical** | NEEDS CONFIRMATION |
| E3 | Data | Failed profile write leaves an order in Firebase but not in the buyer's list | S2 Major | NEEDS CONFIRMATION |
| F1 | Unfinished | "Liefertage speichern" button does nothing | S2 Major | HIGH |
| F2 | Unfinished | Seller-side order cancel action is a no-op stub | S3 Minor | HIGH |
| G1 | i18n | 46 strings untranslated — German text in English UI | S2 Major | HIGH |
| G2 | i18n | Hardcoded English validation errors in a German app | S3 Minor | HIGH |
| G3 | i18n | One English error string in the otherwise-German checkout flow | S3 Minor | HIGH |

---

# Section A — Money and number formatting

## A1 — `formatPrice()` shows most prices one cent too low

**Severity: S1 Critical · Confidence: HIGH**

**Code reference:** `shared/src/commonMain/kotlin/com/together/newverse/util/FormatUtils.kt:33-49`

```kotlin
fun Double.formatPrice(): String {
    val rounded = (this * 100).toLong() / 100.0   // truncates, not rounds
    val intPart = rounded.toLong()
    val decimalPart = ((rounded - intPart) * 100).toLong()  // truncates again
    ...
}
```

**Hypothesis.** Two successive truncations of a binary floating-point value. Because
most decimal prices are not exactly representable as a `Double`, `19.99 * 100` is
`1998.9999...`, which `toLong()` truncates to `1998`. The function is called in **42
places** across 17 files — every product price, every line total, the basket total, the
order history, and the seller's Abrechnung figures.

Roughly **half of all two-decimal prices are affected.**

**Steps.**
1. As Seller S, create products priced exactly: `19.99`, `3.90`, `8.70`, `2.30`, `5.10`, `0.29`, `4.70`, `7.00`.
2. Open the buyer app and view the product list.
3. Record the price shown next to each product.
4. Add one of each to the basket and record the line totals and basket total.
5. Place the order and check the price on the order-history screen and in the seller's order detail.

**Expected (correct) behaviour.**

| Entered | Should display |
|---------|----------------|
| 19.99 | `19,99` |
| 3.90 | `3,90` |
| 8.70 | `8,70` |
| 2.30 | `2,30` |
| 5.10 | `5,10` |
| 0.29 | `0,29` |
| 4.70 | `4,70` |
| 7.00 | `7,00` |

**Suspected actual behaviour.**

| Entered | Predicted display | Wrong? |
|---------|-------------------|--------|
| 19.99 | `19,98` | ✗ |
| 3.90 | `3,89` | ✗ |
| 8.70 | `8,68` | ✗ (two cents) |
| 2.30 | `2,29` | ✗ |
| 5.10 | `5,08` | ✗ (two cents) |
| 0.29 | `0,28` | ✗ |
| 4.70 | `4,70` | ✓ correct |
| 7.00 | `7,00` | ✓ correct |

Also check a four-figure total: `1234.56` is predicted to render as `1.234,55`.

The `4.70` and `7.00` rows are the control cases — they should be correct. If *every*
value including those is wrong, the cause is something else and this hypothesis is
partly rejected; note that.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## A2 — Weight amounts render one gram short

**Severity: S2 Major · Confidence: HIGH**

**Code reference:** `shared/src/commonMain/kotlin/com/together/newverse/domain/model/OrderedProduct.kt:49-70`
(`formatWithDecimals`, reached from `getFormattedAmount()` when `amount` is empty)

**Hypothesis.** Same class as A1 but in the fractional-part extraction:
`((rounded - whole) * 1000).toInt()`. The subtraction loses precision once the whole
part is ≥ 1, so the last digit drops. Sampling 1–50 kg in 10 g steps, **2314 of 5000
values are wrong** — roughly 46%. Values below ~1.13 kg are mostly correct, which is
why this may not have been noticed.

**Steps.**
1. As Buyer A, add a kg-priced product to the basket.
2. Set the quantity to each of: `0,500` · `1,200` · `1,400` · `2,900` · `1,150`.
3. Read the amount label rendered on the basket row after each change.
4. Place the order and re-read the amount in order history and on the seller's order detail.

**Expected (correct) behaviour.** `0,500 kg` · `1,200 kg` · `1,400 kg` · `2,900 kg` · `1,150 kg`.

**Suspected actual behaviour.** `0,500 kg` (correct — control) · `1,199 kg` · `1,399 kg` ·
`2,899 kg` · `1,149 kg`.

**Note.** This formatter is only reached when the stored `amount` string is empty. If
all five values render correctly, check whether `amount` is being populated upstream —
that would mask the bug rather than fix it, and is worth recording. See also C1, which
populates `amount` with a *raw* number and would mask this differently.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## A3 — Seller sell-price auto-calculation truncates

**Severity: S3 Minor · Confidence: HIGH**

**Code reference:** `shared/src/sellMain/kotlin/com/together/newverse/ui/screens/sell/CreateProductViewModel.kt:272-285`
(`recalculateSellPrice`: `(sellPrice * 100).toLong() / 100.0`)

**Hypothesis.** Same truncation. The auto-derived sell price can land one cent below
the mathematically correct value, and that wrong value is what gets **saved to
Firebase** — so unlike A1 this corrupts stored data, not just display.

**Steps.**
1. Seller app → new product.
2. Enter acquire price `2.00`, markup factor `1.3`, tax rate `19%`.
3. Read the auto-filled sell price field.
4. Repeat with acquire `3.00`, factor `1.3`, tax `7%`.

**Expected.** `2.00 × 1.3 × 1.19 = 3.094` → `3.09`. `3.00 × 1.3 × 1.07 = 4.173` → `4.17`.

**Suspected actual.** May be one cent low depending on the float representation. Try
several combinations and record any where the displayed value differs from a
calculator's answer rounded to 2 dp.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## A4 — German decimal comma rejected in the price field

**Severity: S2 Major · Confidence: HIGH**

**Code reference:** `CreateProductViewModel.kt:409-412` — validation uses
`data.price.toDoubleOrNull()`, which only accepts a `.` decimal separator.

**Hypothesis.** The app is German-primary and displays all prices with a comma
(`3,90`). A seller will naturally type `3,90`. `toDoubleOrNull()` returns null, so
validation fails with the message *"Valid price is required"* — which is (a) in
English, and (b) does not tell the user that a dot is required.

**Steps.**
1. Seller app → new product → fill every field.
2. Type `3,90` into the price field.
3. Tap save.
4. Then change it to `3.90` and save again.

**Expected.** `3,90` is accepted, matching the format the app itself displays.

**Suspected actual.** `3,90` is rejected with an English error; `3.90` saves fine.

Repeat for the acquire-price and weight-per-piece fields, which use the same parse.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

# Section B — Order scheduling and lifecycle

## B1 — Pickup date picker only ever offers two dates

**Severity: S2 Major · Confidence: HIGH**

**Code reference:** `shared/src/commonMain/kotlin/com/together/newverse/util/OrderDateUtils.kt`
— `getAvailablePickupDates(count: Int = 5)` builds a list of `count` dates and then
returns `dates.take(2)` at line 298.

Caller: `BuyAppViewModelBasket.kt:1074` — `getAvailablePickupDates(count = 5)`.

**Hypothesis.** The `count` parameter is ignored. The caller asks for 5 pickup dates;
the picker will show 2. This looks like a leftover debug constant.

**Steps.**
1. Buyer app → add any item to the basket.
2. Tap checkout / the pickup-date selector.
3. Count the dates offered.
4. Repeat on a Monday and on a Friday (clock change) to rule out the list being
   genuinely short near the deadline.

**Expected.** Five selectable Thursdays.

**Suspected actual.** Exactly two, on every day of the week.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## B2 — Order reads as "pickup passed" for the whole pickup day

**Severity: S2 Major · Confidence: HIGH**

**Code references:**
- `OrderDateUtils.kt:145-150` — `getOrderWindowStatus`: `now > pickupDate -> PICKUP_PASSED`
- `OrderDateUtils.kt` — `calculateNextPickupDate` returns `pickupDate.atTime(0, 0)`, i.e. **midnight**
- `Order.kt` — `isActiveOrder`: `return now < pickupInstant`
- `BasketScreen.kt:948` — the `PICKUP_PASSED` branch renders a *"Erneut bestellen"* (reorder) button

**Hypothesis.** `pickUpDate` is stored as midnight at the start of pickup day. So from
Thursday 00:00:01 onward, `now > pickupDate` is already true. For the entire day the
customer is actually meant to collect their order, the app treats it as historic:
`isActiveOrder()` returns false and the basket offers "reorder" instead of showing the
pending pickup.

The existing unit test (`OrderDateUtilsTest.kt:172-176`) only checks **Friday**, so this
gap is not covered.

**Steps.**
1. Set the device clock to a Monday. Place an order for the coming Thursday as Buyer A.
2. Confirm the basket shows the order as active with an edit/cancel option.
3. Set the device clock to that **Thursday, 09:00**. Force-stop and relaunch the buyer app.
4. Open the basket screen and the order-history screen.

**Expected.** On pickup day the order still shows as an upcoming/ready pickup — the
customer needs to see it in order to collect it.

**Suspected actual.** The basket shows the *"Erneut bestellen"* reorder button; the
order is treated as past. Also check whether it disappears from any "active orders"
list on the main screen.

5. Advance to Friday and confirm it *then* legitimately reads as passed (control case).

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## B3 — Seller app never advances order status

**Severity: S2 Major · Confidence: HIGH**

**Code reference.** `transitionStatusIfNeeded()` and `updateOrderStatus()` are called
from exactly four places, **all in `buyMain`**:
- `BuyAppViewModelProfile.kt:114`
- `BuyAppViewModelInitialization.kt:509,514,518`
- `BuyAppViewModelBasket.kt:284,300,303,787,791`

There is no caller anywhere in `sellMain` or in any repository/background worker.

**Hypothesis.** An order only moves `PLACED → LOCKED → COMPLETED` when **the buyer who
placed it opens the app and loads that specific order.** If the buyer never reopens the
app after ordering, the order stays `PLACED` in Firebase forever. The seller has no way
to advance it. This is the root cause of D2.

**Steps.**
1. Set the clock to Monday. As Buyer A, place an order for Thursday.
2. In the seller app, note the order's status.
3. Set the clock to **Wednesday** (past the Tuesday 23:59 deadline). Do **not** open the buyer app.
4. Force-stop and relaunch the **seller** app. Check the order's status.
5. Set the clock to **Friday** (past pickup). Still do not open the buyer app.
6. Relaunch the seller app. Check the order's status again.
7. Now open the **buyer** app and navigate to that order. Return to the seller app and re-check.

**Expected.** By step 4 the order should read LOCKED; by step 6, COMPLETED — independent
of buyer activity.

**Suspected actual.** The status stays `PLACED` through steps 4 and 6, and only changes
at step 7 once the buyer opens it.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

# Section C — Basket behaviour

## C1 — Re-adding an item corrupts its displayed amount and loses the unit

**Severity: S2 Major · Confidence: HIGH**

**Code reference:** `shared/src/commonMain/kotlin/com/together/newverse/data/repository/InMemoryBasketRepository.kt:27-45`

```kotlin
currentItems[existingIndex] = existing.copy(
    amountCount = existing.amountCount + item.amountCount,
    amount = (existing.amountCount + item.amountCount).toString()   // raw Double
)
```

**Hypothesis.** `amount` is a *display* string (normally `"1,500 kg"`). On merge it is
overwritten with `Double.toString()` — so `"3.0"`, with a dot, no unit suffix, and
possibly scientific notation. Because `getFormattedAmount()` returns `amount` verbatim
when non-empty, this wrong string then sticks for the rest of the order's life,
including after it is written to Firebase.

**Steps.**
1. Buyer app → add product X (1 kg) to the basket. Note the amount label.
2. Navigate back to the product list and add product X **again** (another 1 kg).
3. Open the basket and read the amount label on the X row.
4. Place the order; check the amount shown in order history and in the seller's order detail.

**Expected.** `2,000 kg` — comma decimal, unit suffix, consistent with step 1.

**Suspected actual.** `2.0` — dot decimal, no `kg`, formatting inconsistent with every
other row.

Also try a fractional case (0.5 + 0.25) where the predicted output is `0.75`.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## C2 — Quantity edit can produce a negative piece count

**Severity: S3 Minor · Confidence: HIGH**

**Code reference:** `InMemoryBasketRepository.kt:52-72`

```kotlin
val newPiecesCount = if (item.amountCount > 0) {
    ((item.piecesCount / item.amountCount) * newQuantity).toInt()
} else { newQuantity.toInt() }
```

**Hypothesis.** `OrderedProduct.piecesCount` defaults to `-1` (meaning "not
applicable"), and `mapSnapshotToOrder` also falls back to `-1`. The ratio then carries
the sign through, so editing the quantity of an item whose `piecesCount` is `-1`
yields a negative piece count, which is written to Firebase.

**Steps.**
1. Buyer app → add a **kg-priced** product (one with no piece count) to the basket.
2. Change its quantity with the +/- control or the quantity field.
3. Place the order.
4. In the seller app, open the order detail and look for a negative or nonsensical piece count.
5. If nothing is visible in the UI, check the raw value in the Firebase console under
   `/sellers/{sellerId}/orders/{date}/{orderId}/articles/*/piecesCount`.

**Expected.** `piecesCount` stays `-1` (or 0) for weight-based products.

**Suspected actual.** A negative value proportional to the quantity, e.g. `-2`.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## C3 — Items with a blank product id merge into one row

**Severity: S2 Major · Confidence: NEEDS CONFIRMATION**

**Code reference:** `InMemoryBasketRepository.kt:29-32` matches on
`it.productId == item.productId`. Note the default `productId` in the domain model is
`"-1"` (`OrderedProduct.kt:11`) but `GitLiveOrderRepository.mapSnapshotToOrder` falls
back to `""` — two different placeholders for "missing".

**Hypothesis.** If any products lack a `productId` (likely for BNN-imported or
hand-created articles where the field was left blank), every such product collides on
the same key. Adding a second one increases the quantity of the first instead of adding
a row. `removeItem` has the same issue and would delete all of them at once.

**Steps.**
1. Seller app → create two products, **leaving the product-id field blank** on both.
   (If the field is mandatory, try the BNN import path instead, or set both to `-1`.)
2. Buyer app → add product 1 to the basket, then product 2.
3. Count the basket rows and read the names/quantities.
4. Remove one row and see whether both disappear.

**Expected.** Two distinct rows; removing one leaves the other.

**Suspected actual.** One row bearing product 1's name with quantity 2; removing it
empties the basket.

**If this cannot be set up** (the id field is mandatory everywhere), mark BLOCKED and
note that — it means the collision is not reachable through the UI, which downgrades
this substantially.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

# Section D — Seller reporting

## D1 — "Offen/Pending" filter hides LOCKED orders

**Severity: S1 Critical · Confidence: HIGH**

**Code reference:** `shared/src/sellMain/kotlin/com/together/newverse/ui/screens/sell/OrdersViewModel.kt:67`

```kotlin
OrderFilter.PENDING -> orders.filter { it.status.isActive() && !it.status.isFinalized() }
```

`isFinalized()` is true for `LOCKED`, `COMPLETED`, `CANCELLED` (`OrderStatus.kt:57-59`).
So PENDING resolves to `{DRAFT, PLACED, DEMO_ORDER}` — **`LOCKED` is excluded.**

**Hypothesis.** `LOCKED` is the state an order occupies from Tuesday 23:59 until pickup
on Thursday — precisely the window in which the seller must pick and pack it. Those
orders are excluded from "Pending", are not `COMPLETED`, and are not `CANCELLED`, so
they appear under **no filter except "Alle"**. A seller working from the Pending tab
would ship nothing.

Note this interacts with B3: because the seller app never performs the transition
itself, orders may sit at `PLACED` and remain visible. The bug surfaces once *any* path
has moved an order to `LOCKED` — e.g. after the buyer reopens the app past the deadline.

**Steps.**
1. Set the clock to Monday. Buyer A places an order for Thursday.
2. Seller app → Orders → "Offen" filter. Confirm the order is listed.
3. Set the clock to **Wednesday**. Open the **buyer** app and view the order (this is
   what performs the `PLACED → LOCKED` transition — see B3).
4. Return to the seller app, relaunch it, and check the "Offen" filter again.
5. Check the "Alle", "Abgeschlossen" and "Storniert" filters.

**Expected.** The order remains visible in "Offen" — it is not finished, it is waiting
to be picked.

**Suspected actual.** It disappears from "Offen" and appears only under "Alle".

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## D2 — Abrechnung period tab reports zero revenue

**Severity: S1 Critical · Confidence: HIGH**

**Code reference:** `shared/src/sellMain/kotlin/com/together/newverse/ui/screens/sell/AbrechnungViewModel.kt:146-148`

```kotlin
val periodOrders = allOrders.filter {
    order.status == OrderStatus.COMPLETED && order.pickUpDate >= cutoffMs
}
```

**Hypothesis.** Combined with B3 — nothing in the seller app ever writes `COMPLETED`,
and the buyer only writes it when they reopen a past order — the period summary will
report `0` orders and `0,00` revenue for most real sellers, even with a full order
history. The Woche/Monat/Alle filters will all be equally empty.

**Steps.**
1. Ensure at least three orders exist with pickup dates in the past week.
2. Seller app → Abrechnung → "Zeitraum" tab.
3. Read order count, customer count, gross total, VAT and net.
4. Switch between Woche / Monat / Alle.
5. Cross-check by counting the same orders under Orders → "Alle" and adding the totals by hand.
6. Now, in the **buyer** app, open each of those past orders (forcing the `COMPLETED`
   transition), then reload the Abrechnung screen.

**Expected.** The period tab reflects all past orders regardless of buyer app activity.

**Suspected actual.** Zero (or heavily under-reported) until step 6, after which the
opened orders appear.

**Also check:** the "Monat" filter uses a fixed 30-day window (`nowMs - 30L * 24 * 3600 * 1000`),
not a calendar month. If the UI labels it as a month, note the discrepancy.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## D3 — Abrechnung merges distinct products under a blank id

**Severity: S2 Major · Confidence: NEEDS CONFIRMATION**

**Code reference:** `AbrechnungViewModel.kt:160-190` — `aggregateItems` groups by
`op.productId` and keeps the **first** product's name, unit, tax rate and acquire price
for the merged row.

**Hypothesis.** Same root cause as C3. Any order lines with a missing `productId`
aggregate into one line labelled with whichever product happened to come first, with
that product's tax rate applied to the whole merged total.

**Steps.**
1. Arrange for two different products with blank/identical product ids to be ordered
   (see C3 setup). Give them **different tax rates** — 7% and 19%.
2. Seller app → Abrechnung → "Abholung" tab.
3. Read the aggregated item list and the VAT breakdown.

**Expected.** Two line items; VAT split correctly between the 7% and 19% buckets.

**Suspected actual.** One line item under a single name; all VAT assigned to one bucket.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## D4 — Non-standard tax rate silently miscomputes VAT

**Severity: S2 Major · Confidence: NEEDS CONFIRMATION**

**Code reference:**
- `TaxRate.kt:15` — `fun fromRate(rate: Double): TaxRate = entries.find { it.rate == rate } ?: REDUCED`
- `AbrechnungViewModel.kt:200-207` — the VAT *amount* uses the raw `item.taxRate`,
  while the *bucket* it lands in uses `fromRate()`.

**Hypothesis.** `fromRate` matches on exact `Double` equality and falls back to
`REDUCED` for anything unrecognised — silently. But the arithmetic
(`totalGross * taxRate / (1 + taxRate)`) uses the raw stored value. If any article ever
has its rate stored as a percentage (`7.0`) rather than a fraction (`0.07`) — from an
import, a migration, or manual Firebase editing — the VAT for that line becomes
`gross × 7 / 8` = **87.5% of gross**, and it is filed under the 7% bucket.

**Steps.**
1. In the Firebase console, set one article's `taxRate` to `7.0` (instead of `0.07`).
2. Have Buyer A order that product; complete the order so it reaches the Abrechnung.
3. Seller app → Abrechnung. Read gross total, VAT 7%, net total.

**Expected.** Either the value is rejected/normalised, or VAT is a sane fraction of gross.

**Suspected actual.** VAT ≈ 87.5% of that line's gross; net total drops close to zero
or goes negative.

**If the app has no path that could ever store a percentage** — check the BNN importer
in `data/parser/BnnParser.kt` and `BnnProductImportService.kt` — then this is
theoretical. Note that finding, it is a legitimate rejection.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

# Section E — Dates, timezones and data integrity

## E1 — Basket screen renders dates in Berlin time, everything else in device time

**Severity: S2 Major · Confidence: HIGH**

**Code reference.** Two hardcoded zones against 32 uses of `TimeZone.currentSystemDefault()`:
- `shared/src/buyMain/kotlin/com/together/newverse/ui/screens/buy/BasketScreen.kt:968` — `TimeZone.of("Europe/Berlin")`
- `BasketScreen.kt:1005` — same
- `shared/src/commonMain/kotlin/com/together/newverse/util/DateFormattingUtils.kt:16` — default parameter `"Europe/Berlin"`

Whereas `OrderDateUtils`, `GitLiveOrderRepository.formatDate` and
`basketScreenFormatDateKey` all use the **device** timezone.

**Hypothesis.** On a device set to any zone more than a few hours from Berlin, the
pickup date *displayed* on the basket screen and the date *used for the Firebase path*
are computed from different zones and can name different calendar days.

**Steps.**
1. Set the device timezone to **Europe/Berlin**. Place an order. Note the pickup date shown on the basket screen.
2. Set the device timezone to **America/Los_Angeles** (−9h). Force-stop and relaunch.
3. Open the basket screen and the order-history screen. Compare the pickup date shown in each.
4. Repeat with **Pacific/Auckland** (+10h).

**Expected.** Both screens agree on the pickup date in every timezone.

**Suspected actual.** The basket screen and the history screen disagree by one day in
at least one of the non-Berlin zones, because one is Berlin-pinned and the other is not.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## E2 — Timezone change can orphan a placed order

**Severity: S1 Critical · Confidence: NEEDS CONFIRMATION**

**Code reference:**
- `GitLiveOrderRepository.kt:525-531` — `formatDate()` derives the Firebase path segment
  `YYYYMMDD` from `pickUpDate` using `TimeZone.currentSystemDefault()`
- `BuyAppViewModelBasket.kt:1479-1485` — `basketScreenFormatDateKey()` does the same for
  the `placedOrderIds` map key
- `GitLiveOrderRepository.observeBuyerOrders` looks orders up by
  `snapshot.child(date).child(orderId)` using those stored keys

**Hypothesis.** `pickUpDate` is a fixed epoch millisecond value representing **midnight
in the timezone the device had when the date was picked**. Re-deriving the calendar day
from that instant in a *different* timezone yields the previous day (any negative-offset
change). The buyer's `placedOrderIds` map still holds the old key, so the lookup path no
longer matches the node the order lives under — the order silently vanishes from the
buyer's list while still existing in Firebase (and still visible to the seller).

This is the realistic scenario for a customer who travels, or whose device timezone is
misconfigured then corrected.

**Steps.**
1. Set device timezone to **Europe/Berlin**. As Buyer A, place an order for the coming Thursday.
2. Confirm it appears in order history and in the basket screen.
3. In the Firebase console, note the exact path: `/sellers/{sellerId}/orders/{YYYYMMDD}/{orderId}`
   and the buyer's `placedOrderIds` entry.
4. Set the device timezone to **America/Los_Angeles**. Force-stop and relaunch the buyer app.
5. Check order history and the basket screen.
6. Check the seller app — the order should still be there.

**Expected.** The order remains visible to the buyer in every timezone.

**Suspected actual.** The order disappears from the buyer's history/basket at step 5 but
is still present in the seller app and in Firebase at step 6.

**Also test the write side:** with the device in Los Angeles, place a *new* order and
compare the Firebase date node against the pickup date the UI displayed.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## E3 — Failed profile write leaves an order in Firebase but not in the buyer's list

**Severity: S2 Major · Confidence: NEEDS CONFIRMATION**

**Code reference:** `GitLiveOrderRepository.kt:265-283`

```kotlin
orderRef.setValue(orderMap)                                        // 1. order written
val buyerProfile = profileRepository.getBuyerProfile().getOrThrow() // 2. can throw
... profileRepository.saveBuyerProfile(updatedProfile)             // 3. index written
```

**Hypothesis.** The order is written to Firebase *before* the buyer profile is read. If
step 2 or 3 fails, the `catch` returns `Result.failure` — the buyer sees "Bestellung
fehlgeschlagen" and the basket is **not** cleared, so they will most likely order again.
But the first order is already committed and visible to the seller. Net effect: a
duplicate order the customer does not know about, and one they cannot see or cancel
because it was never added to `placedOrderIds`.

**Steps.** Needs the profile write to fail while the order write succeeds.
1. Easiest route: temporarily tighten the Firebase security rules to deny writes to
   `/buyers/{buyerId}/profile` while leaving `/sellers/{sellerId}/orders` writable.
2. As Buyer A, place an order.
3. Observe the buyer UI, then check the seller app and the Firebase console.
4. Restore the rules.

Alternative (less reliable): enable airplane mode in the narrow window after tapping
checkout. Firebase RTDB's offline queue may make this unreproducible — if so, use the
rules approach.

**Expected.** Either the whole checkout succeeds, or nothing is written and the buyer
can safely retry.

**Suspected actual.** Buyer sees a failure and a still-full basket; the seller sees the
order; the buyer's order history does not.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

# Section F — Unfinished features presented as working

## F1 — "Liefertage speichern" button does nothing

**Severity: S2 Major · Confidence: HIGH**

**Code reference:** `shared/src/sellMain/kotlin/com/together/newverse/ui/screens/sell/PickDayScreen.kt:75`

```kotlin
Button(
    onClick = { /* TODO: Save delivery days */ },
    ...
) { Text(stringResource(Res.string.pick_day_save)) }
```

**Hypothesis.** The button is enabled and looks functional; tapping it performs no
action, shows no error, and gives no feedback. Selections are lost on navigation away.

**Steps.**
1. Seller app → navigate to the delivery-days / PickDay screen.
2. Select several days. The save button should become enabled.
3. Tap save. Note any feedback.
4. Navigate away and back.

**Expected.** Selection persists; some confirmation is shown.

**Suspected actual.** Nothing happens on tap; selection is empty on return.

**Also note:** confirm whether this screen is reachable through normal navigation at
all. If it is orphaned/unreachable, severity drops to S3 — please record which.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## F2 — Seller-side order cancel action is a no-op stub

**Severity: S3 Minor · Confidence: HIGH**

**Code reference:** `shared/src/sellMain/kotlin/com/together/newverse/ui/state/SellAppViewModel.kt:181`

```kotlin
is SellOrderAction.CancelOrder -> { /* TODO */ }
```

**Hypothesis.** If any seller-side UI dispatches `SellOrderAction.CancelOrder`, it
silently does nothing. Also `OrdersViewModel.markAsFavorite` (`OrdersViewModel.kt:92`)
is a `// TODO: Implement with repository` stub.

**Steps.**
1. Seller app → Orders → open an order → look for a cancel action (button, menu item, swipe).
2. If one exists, use it and check whether the order's status changes in Firebase.
3. Look for a favourite/star action on the orders list; if present, toggle it, relaunch,
   and check whether it persisted.

**Expected.** Either the action works, or it is not offered in the UI.

**Suspected actual.** If the UI offers either action, it silently does nothing.

**If neither action is exposed in the UI, mark REJECTED** — dead code, not a user-facing
bug. That is the likely outcome and worth confirming.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

# Section G — Localization

## G1 — 46 strings untranslated: German text appears in the English UI

**Severity: S2 Major · Confidence: HIGH**

**Code reference:** `shared/src/commonMain/composeResources/values/strings.xml` has 714
strings; `values-en/strings.xml` has 668. The 46 keys present only in the German default
will fall back to German for English-locale users.

**Missing keys, grouped by screen:**

| Area | Keys |
|------|------|
| Seller top bar (15) | `topbar_change_availability`, `topbar_import_products`, `topbar_new_product`, `topbar_notifications`, `topbar_orders`, `topbar_products`, `topbar_profile`, `topbar_select_delete`, `topbar_seller`, `topbar_sortiment` |
| Seller bottom nav (5) | `bottomnav_dashboard`, `bottomnav_demand`, `bottomnav_new`, `bottomnav_products`, `bottomnav_profile` |
| Markets editor (13) | `market_begin`, `market_city`, `market_day`, `market_delete`, `market_edit`, `market_end`, `market_house_number`, `market_name`, `market_save`, `market_street`, `market_validation_fill_all`, `market_validation_time`, `market_zip_code` |
| Buyer product detail (8) | `products_detail_add_to_cart`, `products_detail_category`, `products_detail_description`, `products_detail_not_found`, `products_detail_quantity`, `products_detail_remove_from_cart`, `products_detail_title`, `products_detail_update_cart` |
| Basket cancel dialog (4) | `basket_cancel_confirm_button`, `basket_cancel_confirm_message`, `basket_cancel_confirm_title`, `basket_finish_editing` |
| Seller profile (4) | `seller_profile_add_market`, `seller_profile_markets`, `seller_profile_no_markets`, `seller_profile_payment_cash_only` |
| Other (2) | `button_dismiss`, `overview_total_revenue` |

**Steps.**
1. Set the device language to **English**.
2. Seller app: read the top bar and bottom navigation on every tab.
3. Seller app: open the profile screen → markets section → add/edit a market.
4. Seller app: open the Overview screen and find the total-revenue label.
5. Buyer app: open any product's detail screen.
6. Buyer app: place an order, then open the basket and tap "cancel order" to reach the
   confirmation dialog.

**Expected.** All UI text in English.

**Suspected actual.** German text in each of those places, mixed with English elsewhere
on the same screen. Record a screenshot per screen.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## G2 — Hardcoded English validation errors in a German-primary app

**Severity: S3 Minor · Confidence: HIGH**

**Code reference:** `CreateProductViewModel.kt:398-440` and `:314-395` — validation and
submit errors are string literals, not resources:

- `"Product name is required"`
- `"Search terms are required"`
- `"Valid price is required"`
- `"Unit is required"`
- `"Category is required"`
- `"Image is required"`
- `"Weight per piece is required for countable units"`
- `"User not authenticated"`
- `"Failed to upload image: ..."`
- `"Failed to save product: ..."`

**Steps.**
1. Device language **German**. Seller app → new product.
2. Tap save with every field empty. Read each field's error message.
3. Select a countable unit (Stück) and leave weight-per-piece blank. Save. Read the error.
4. Fill everything validly but with no image. Save. Read the error.

**Expected.** German error messages, consistent with the rest of the form.

**Suspected actual.** English messages on a German form.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

## G3 — One English error string inside the German checkout flow

**Severity: S3 Minor · Confidence: HIGH**

**Code reference:** `BuyAppViewModelBasket.kt:583` — `setBasketError("You have been blocked by this seller")`,
sitting among German literals at `:575` (`"Bitte melden Sie sich an..."`), `:590`
(`"Warenkorb ist leer"`), `:597` (`"Bitte wählen Sie ein Abholdatum"`) and `:612`
(`"Gewähltes Datum ist nicht mehr verfügbar."`).

All of these are hardcoded rather than localized, but the blocked-buyer one is also the
wrong language for the default locale.

**Steps.**
1. As Seller S, block Buyer A (seller profile → clients → block).
2. As Buyer A on a German-locale device, add an item and attempt checkout.
3. Read the error message.
4. While there, trigger the other checkout errors — empty basket, no date selected — and
   confirm whether they are localized or hardcoded German.

**Expected.** A German (ideally localized) message.

**Suspected actual.** *"You have been blocked by this seller"* in English.

> **Verdict:** `[ ] CONFIRMED  [ ] REJECTED  [ ] BLOCKED`
> Notes:

---

# Appendix — checked and found sound

Recorded so this ground is not re-covered.

| Area | Finding |
|------|---------|
| `OrderDateUtils.calculateNextPickupDate` | Weekday arithmetic is correct, including the "on the deadline day itself the deadline has not yet passed" case. Well covered by `OrderDateUtilsTest`. |
| `OrderDateUtils.calculateEditDeadline` | Correct, but its `require(pickupLocalDate.dayOfWeek == config.pickupDay)` guard is **commented out**. A malformed `pickUpDate` (e.g. `0L`) therefore produces a nonsense deadline near the epoch instead of failing loudly. Not user-reachable that we could see — noted, not tested. |
| `CreateProductViewModel.saveProduct` | `formData.price.toDouble()` looked like a crash risk, but validation guarantees it parses first. Safe. |
| `formatQuantity` (`QuantityUtils.kt`) | Truncates rather than rounds, but only at the 4th decimal — below display precision. Not a defect in practice. |
| `GitLiveStorageRepository.uploadImage` | Unconditionally throws `UnsupportedOperationException`. **Dead code** — DI binds `StorageRepository` to `PlatformStorageRepository` (`AndroidDomainModule.kt:61`) and the `useGitLiveStorage` flag is never read. Not user-reachable. Worth deleting. |
| `TaxRate` / VAT extraction | The gross-inclusive formula `gross × rate / (1 + rate)` is correct for German gross pricing. Only the unrecognised-rate fallback is a concern (D4). |
| `OrderStatus.isActive` / `isFinalized` | Individually correct; only their **combination** in the PENDING filter is wrong (D1). |
| `Order.prepareForReorder` | Correctly resets id, pickup date, status and draft basket. |

## Documentation drift (no test needed)

`CLAUDE.md` documents three feature flags under "Feature Flags":
`authProvider`, `useGitLiveStorage`, `gitLiveRolloutPercentage`.

`FeatureFlags.kt` actually contains only `enableAuthDebugLogging` and
`useGitLiveStorage` — and `useGitLiveStorage` is never read anywhere. `authProvider` and
`gitLiveRolloutPercentage` do not exist. Worth correcting in `CLAUDE.md`.

---

## Suggested execution order

Highest information per unit of setup effort:

1. **A1** — no setup, affects everything, decides whether this is a release blocker.
2. **B1** — one tap, immediately visible.
3. **D1** and **D2** — the two seller-workflow criticals; share a clock-manipulation setup with B3.
4. **B3** — establishes the root cause behind D2.
5. **C1**, **A2** — quick basket checks, no clock work.
6. **G1** — one language switch, then a walkthrough.
7. **B2**, **E1** — clock and timezone work.
8. **E2**, **E3** — need Firebase console access; run last.
9. **C3**, **D3**, **D4** — may prove unreachable; a REJECTED verdict here is a good result.
