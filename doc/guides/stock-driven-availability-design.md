# Stock, Demand and What a Buyer May Order

**Status:** implemented 2026-10-20, verified against `shared/src`. All six phases of
the original design are built; the rules it depends on were already deployed with the
stock ledger. Nothing here is pending deployment.

**Scope:** `shared/src/commonMain` (ledger, demand, derivation, reminder evaluator),
`sellMain` (stock screen, article editor, correction pass), `buyMain` (placement gate,
basket, catalogue band), `androidMain`/`iosMain` (reminder scheduling),
`firebase/database.rules.json`

How the seller learns what to buy before market day, how an article closes itself when
it sells out, and what a buyer is told about both. One calculation underneath all of
it: how much of an article is still *sellable*.

---

## The one number

```
remaining = onHand − committed                  // what is left to sell
shortfall = (committed + reorderLevel) − onHand // what to bring
```

- **`onHand`** comes from the append-only ledger: `/stock_movements/{seller}/{yyyyMM}`
  holds `INTAKE`, `STOCKTAKE`, `SALE` and `LOSS` movements with a signed quantity, and
  `/stock/{seller}/{articleId}` caches the level they add up to, written as a
  server-side increment in the same multi-path update as the movement
  (`StockNodes`, `StockRepository`). Movements are booked on handover
  (`PickupViewModel`), at a walk-in sale (`WalkInSaleViewModel`) and on a stocktake.
- **`committed`** is what buyers have ordered and not yet collected
  (`CommittedDemand.kt`).
- **`reorderLevel`** (`SellerArticleData`, seller-only) is the walk-in **buffer** —
  what to keep on the shelf *on top of* what is already sold. It is not a trigger.

That last point is why an article the seller never configured still speaks up when it
is oversold: `reorderLevel` defaults to `0`, so a purely threshold-based list would
stay silent until every article had been set by hand.

### What counts as committed

`Order.countsAsCommitted()` — `origin.isReal`, and `status` is `PLACED` or `LOCKED`.

- A `DEMO` order is phantom demand, from a buyer the seller has not approved.
  `TRANSITIONED` **does** count: a real order that merely began under `demo_orders/`.
- `COMPLETED` is excluded, and not because it is in the past: handing an order over
  books a `SALE` movement, which has already taken the goods out of `onHand`. Counting
  it as demand too would subtract it twice.

### Refill states

`RefillState`, in urgency order, so the enum sorts the list (`StockRefill.kt`):

| State | Condition | Means |
|-------|-----------|-------|
| `SHORT` | `committed > 0 && committed > onHand` | Sold more than is there — someone is waiting. Independent of `reorderLevel`. |
| `EMPTY` | `onHand <= 0` | Nothing left, nothing sold either. A ledger can stand below zero on its own, when a sale was booked before its intake. |
| `LOW` | `remaining <= reorderLevel` | Buffer breached once orders are filled. |
| `UNCOUNTED` | watched, no movement ever | Has to be counted before anything can be said. |
| `OK` / `UNWATCHED` | — | Nothing to do; nothing the seller asked about. |

`RefillNeed.needsAttention` is wider than `RefillState.needsAttention`: it also covers
a unit mismatch, where the state says nothing is wrong but the numbers still have to
be sorted out.

### Two things the arithmetic refuses

**Units.** An order line's unit is a snapshot from when it was ordered, a level's unit
is from its last movement, and the article's is current. Where the order and the
article disagree, the demand is **dropped** and the article is flagged
(`RefillNeed.unitMismatch`) — kg and Stück have no exchange rate, and a silently wrong
number here becomes a wrong purchase.

**Keys.** `OrderedProduct.articleKey()` is the single rule matching order lines to
catalogue articles: `productId` unless it is empty or `"-1"`, else `id`. Sales booking,
committed demand and the basket checks all use it. A second rule would drift, and the
symptom — quantities attributed to the wrong article — reads as a stock bug long before
anyone suspects the key.

### One pool, many slots

Storage is a single pool keyed by `articleId`; there is no per-market stock. Demand is
per slot, and `committedDemand(pickupDates)` takes an optional date set. The refill
list sums across every outstanding order, because that is what a buying decision wants;
packing one market's goods wants the per-slot split.

---

## The seller's side

### The refill list

`StockViewModel` combines three streams — seller articles, stock levels and seller
orders — and re-derives everything on each emission. Orders are in there because
committed demand rises while the app is closed, so a booking is not the only thing that
can change the answer.

`StockScreen` shows the refill list, the full standing, and — only when something is
`SHORT` — the affected orders. `AppScaffold` carries the count as a badge, so the
seller sees it without opening the screen.

### No observing service

Stock only changes when the seller's own app writes a movement: no buyer and no backend
job depletes it. A background listener on `/stock` would be the device watching for
writes it had just made. `ListenerService` in the sell flavor is what the alternative
costs — a foreground service for orders, plus ~600 lines of `SwitchWorker` /
`StartServiceWorker` / `BootCompletedReceiver` to survive FGS restrictions and Doze.

Evaluation happens on a movement, on an order change, and once per cycle in a worker.

### The refill reminder

`NotificationSettings.lowStockNotifications` switches this, and is the one switch on
that screen wired to something real.

The timing is the design. Until the **edit deadline** passes buyers can still add to
their orders and the buy list can still grow; after it the numbers are frozen until the
market happens. So there is one moment worth notifying, and the reminder is
once-per-cycle by construction — `StockReminderSettings.lastFiredCycle`, no per-article
bookkeeping.

The deadline itself is 23:59, and a shopping list that buzzes at 23:59 is one the seller
switches off. It is posted at **07:00 the following morning** instead — after demand
went final, before the wholesaler — with a 14-hour grace window, because a buy list
stays useful all day. A deadline already before 07:00 posts on its own day
(`StockReminderEvaluator.postAt`).

`StockReminderWorker` reads nothing but `StockReminderStorage`: a worker can start with
no Koin graph, no session and no network, and a buy list cannot be recomputed without
the database. The app leaves the list there on every recalculation
(`StockReminderSnapshots.of`) and the worker only decides whether to post it. A stale
snapshot may name an article since restocked — a glance at the stock screen — and is
dropped once the market it was about has happened.

Never-counted articles are left off the notification: on a fresh ledger that is the
whole catalogue, and a notification cannot ask a question.

**iOS stores the setting and the snapshot and schedules nothing**
(`IosStockReminderScheduler`). Unlike the buyer's order reminder it will not need
`BGTaskScheduler` when it lands — the text is known when the reminder is armed, so
`UNUserNotificationCenter` alone suffices.

### Telling a buyer their order is short

`SHORT` lists the affected **orders**, not only the articles (`OrderShortfalls.kt`):
who, which article, how much they ordered, how much is missing.

Nothing is allocated between them. One kilo left and three buyers wanting five each
means all three are listed with the same total gap, because deciding who goes without —
substitute, part-fill, phone call — is a decision about customers and not a division
sum.

The message is a **draft the seller edits and sends** (`draftShortfallMessage`), never
one the app sends: a shortfall is usually still fixable at 5am, and an automatic "we
cannot deliver your order" is a message that then has to be walked back, which costs
more trust than the silence would have. The draft names what *that buyer* ordered, not
the shop's total gap, and promises only that the seller will be in touch.

`LOW`, `UNCOUNTED` and unit mismatches raise nobody. It travels over the existing
`MessageRepository` conversation; the buyer's way into it is the Messages icon on
Profile and Home, gated on approval like the contact shortcut.

---

## The buyer's side

### `available` is derived, not edited

One boolean cannot hold both the seller's intent and the shelf's state: mark Spargel
off for the season, and the next stocktake would turn it back on. So the public flag
is an **output**.

```
articles/{s}/{a}/available                public, derived, never hand-edited
articles/{s}/{a}/scarcity                 public, derived
seller_articles/{s}/{a}/sellerAvailable   the seller's intent (Boolean?)
seller_articles/{s}/{a}/stockGated        opt-in per article, default false
```

```kotlin
stockPermits = !stockGated || !hasLevel || remaining > 0.0
available    = sellerAvailable && stockPermits
```

Both halves move in one atomic `ArticleNodes` update, so intent and derived flag never
disagree. The seller still toggles availability in the overview and the editor; those
write `sellerAvailable`, and switching **off** publishes at once — narrowing is always
safe, and a seller who hides something expects it gone now. Switching a *gated* article
on waits for the ledger to agree.

`StockViewModel.correctAvailability()` is the only writer of the derived fields
(`publicationCorrections` → `setArticlePublication`). It writes differences only, so a
settled catalogue writes nothing and its own write re-emits the catalogue with nothing
left to do. It runs on the seller's device because only the seller's uid may write
`/articles`.

**Three guards, each load-bearing:**

1. **Closing takes a *known* empty shelf** (`!hasLevel || remaining > 0`). `onHand` is
   `0.0` both for a sold-out article and for one the ledger has never seen; treating
   the second as the first would withdraw an article sitting there in full view — on a
   fresh ledger, the whole catalogue.
2. **`stockGated` is opt-in.** Weighed goods where the seller brings what they bring
   should not be gated at all.
3. **A gated article with no recorded intent is never closed.** `sellerAvailable` falls
   back to the public flag for articles saved before it existed, so closing one would
   make the pass's own write read back as the seller's wish, and nothing could reopen
   it. The editor records intent whenever the gate is set.

### The gate at placement

The flag only ever filtered what the catalogue *showed*. A basket outlives that — it
is persisted as a draft and reordered weeks later — so enforcement is at the point of
ordering (`BasketAvailability.kt`):

- `basketScreenCheckout` and `basketScreenUpdateOrder` refuse a basket holding an
  article the catalogue no longer offers, naming it, and keep the items so the buyer
  can remove them rather than rebuild the order.
- A reorder leaves withdrawn articles behind and says which — the moment the buyer can
  still choose something else.
- The basket marks those rows and says once, above them, why the order will not go.

**An empty catalogue judges nothing.** Articles arrive over a listener, so "nothing
available" and "nothing loaded yet" are the same picture, and refusing someone's whole
basket because the network is slow is the worse failure.

### What this cannot do

A boolean is a latch, not a reservation. With 5 kg on hand and `available = true`, two
buyers can each order 5 kg: database rules cannot sum order children, so there is no
server-side enforcement, and the client check is against a value that may be seconds
stale.

- The flag stops **new** orders once the pool is exhausted.
- The oversell race stays open. `SHORT` is how the seller finds out.
- Do not describe it to the seller as "prevents overselling". It prevents *ordering
  what is not there*, which is weaker and true.

A real reservation would need a server-side counter per article per slot, with rules
enforcing the increment and a story for abandoned baskets. Out of scope; this is the
upgrade path if overselling turns out to happen.

### The scarcity band

A buyer cannot work out `remaining` — `/stock` is seller-only and `committed` would
mean reading other buyers' orders — so the seller's app publishes
`ArticleScarcity`: `UNKNOWN`, `PLENTIFUL`, `LIMITED`, `LAST_FEW`.

**A band, never a number**, for two independent reasons. A quantity read by an app
minutes behind is a promise the shop never made, while "nur noch wenig" stays true
while it is stale; and a number polled daily is a turnover curve for anyone who writes
it down, where three states tell a competitor roughly what walking past the stall
would.

**Thresholds are relative to quantities the seller already set**: `reorderLevel`, else
`packageSize` (the Gebinde from the price-list import). `LAST_FEW` at or below half the
reference, `LIMITED` at or below it, `PLENTIFUL` above. With neither recorded nothing
is published — "few" would then be the code's opinion rather than the seller's. A
constant cannot work: half a kilo is nearly nothing in potatoes and a lot in saffron.

Shown on the catalogue card (`HeroProductCard`), the product detail page, and — 
`LAST_FEW` only — on basket rows that are not already marked withdrawn. `LIMITED` is
browsing information, useful while choosing and noise beside something already chosen.
The basket uses the catalogue's own words with no countdown, no urgency verb and no
error colour: scarcity in a cart is the classic pressure pattern, and the wording is
the whole line between informing and pushing.

It also accelerates depletion — scarcity shown is scarcity acted on — so the oversell
race above is hit more often, not less.

---

## Where the code is

| Concern | File |
|---------|------|
| Ledger, levels | `StockRepository`, `StockNodes`, `StockMovement.kt` |
| Committed demand | `CommittedDemand.kt` |
| Refill states, shortfall | `StockRefill.kt` |
| Derived availability + band | `StockAvailability.kt`, `ArticleScarcity.kt` |
| Basket checks | `BasketAvailability.kt` |
| Affected orders, draft | `OrderShortfalls.kt` |
| Reminder rules | `StockReminderEvaluator.kt`, `StockReminderSnapshots.kt` |
| Reminder plumbing | `StockReminderStorage` (expect/actual), `AndroidStockReminderScheduler`, `StockReminderWorker` |
| Seller UI | `StockScreen.kt`, `StockViewModel.kt`, `CreateProduct*`, `OverviewViewModel` |
| Buyer UI | `BasketScreen.kt`, `ProductDetailScreen.kt`, `HeroProductCard.kt` |
| Rules tests | `firebase/tests/articles.test.js`, `firebase/tests/stock.test.js` |

---

## Decisions

| # | Decision | Why |
|---|----------|-----|
| D1 | `remaining = onHand − committed`; `reorderLevel` is a buffer, not a trigger | One formula serves the buy list, the badge and the gate; works at `reorderLevel = 0` |
| D2 | `SHORT` ranks above `EMPTY` | Sold-but-absent is the only state with a customer already waiting |
| D3 | `COMPLETED` is excluded from `committed` | Handover already booked a `SALE` movement; counting both subtracts twice |
| D4 | Unit mismatch refuses the arithmetic | A wrong number here becomes a wrong purchase |
| D5 | One `articleKey()` for every order-to-catalogue match | A second rule drifts, and the symptom looks like a stock bug |
| D6 | No observing service | Nothing external changes stock; `ListenerService` is the cost of the alternative |
| D7 | Notify once per cycle, after the deadline | Demand is final, the seller can still act, dedup is free |
| D8 | `UNCOUNTED` never notifies | Day-one noise; belongs on the stock screen |
| D9 | `available` is derived from `sellerAvailable && stockPermits` | One field cannot carry two authorities |
| D10 | `stockGated` opt-in, a known level required to close | Otherwise the first run empties the shop |
| D11 | Enforce at `placeOrder`, not only via the flag | The flag is display-only; without this the feature is cosmetic |
| D12 | Buyer-facing scarcity is a band, never a quantity | A stale number is a broken promise; a band leaks about what the stall shows |
| D13 | A shortfall message is drafted, never auto-sent | The gap is often resolvable; a retracted apology costs more than silence |
| D14 | Shortfall notices ride the order-notification channel | One decision about how buyers are told, in one place |
| D15 | Band thresholds relative to `reorderLevel`, then `packageSize`, else silent | No constant means the same in potatoes and saffron |
| D16 | The basket shows `LAST_FEW` only, in the catalogue's words | Nothing reserves the item; silence until checkout refuses the order is worse. `LIMITED` or urgency wording would be pressure |

### Deliberately not done

- **Hysteresis on the band edges.** No margin means the same across kg and Stück, and
  nothing records *why* a level last moved, so "reopen only after an intake" is not
  derivable from `StockLevel`. The correction pass is idempotent instead, so churn
  costs one write per genuine crossing of zero. If flicker appears, the margin belongs
  on `SellerArticleData` beside `reorderLevel`, in the article's own unit.
- **A quantity gate at checkout** (`amountCount <= remaining`). The buyer cannot read
  `remaining`; it would need the reservation counter above.
- **A separate, tighter node for the band.** It would halve an already small leak and
  double the write path. The decision rests on the band staying coarse — publishing a
  quantity there would change the answer.

### Where building corrected the design

Kept because the reasoning is the useful part:

1. **The `hasLevel` formula was inverted.** `hasLevel && remaining > 0` closes every
   never-counted article; it reads `!hasLevel || remaining > 0`.
2. **The migration fallback made closing irreversible** — hence guard 3 above.
3. **"Fire at the deadline" was the wrong time.** 23:59 became 07:00 the next morning.
4. **"Not in the basket" had the reservation argument backwards.** There is no
   reservation, so the band corrects a misconception rather than creating one; it is
   shown, narrowed to `LAST_FEW`.

---

## Still open

1. **Slot horizon.** `committed` spans every outstanding order. The ordering rules only
   ever open the next slot per market, so today these coincide; worth deciding
   explicitly before multi-slot ordering is reconsidered.
2. **Buffer from velocity.** `reorderLevel` is a flat number the seller sets. It could
   be derived from observed walk-in sales once there are enough `SALE` movements — but
   a number the seller set is explainable and a derived one is not.
3. **Should `SHORT` notify immediately** rather than waiting for the deadline? It is
   the one case with a customer already waiting; against, it reopens the per-article
   dedup that D7 avoids.
4. **Partial fulfilment.** The gate stops new orders but says nothing about an existing
   order that can no longer be filled. That is a fulfilment feature — substitutions,
   part-fill — overlapping `doc/design/order-ready-notification-design.md` §A3.
5. **iOS scheduling.** Storage and evaluation are done; the local notification and its
   permission prompt are not.
