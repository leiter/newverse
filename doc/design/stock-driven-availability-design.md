# Stock-Driven Refill and Availability

> **DESIGN — Phases 1–5 built; only the scarcity band (§4f) is not** (as of 2026-10-20). The stock ledger
> it builds on was already there: `StockRepository`, `StockMovement`/`StockLevel`,
> `StockRefill.kt`, `StockScreen` and the refill badge, all in the database rules.
> **Phases 1 and 2 (§8) are now implemented**: `CommittedDemand.kt`,
> `RefillState.SHORT`, `RefillNeed.committed`/`remaining`/`unitMismatch` and
> `StockViewModel` reading orders alongside stock; plus `BasketAvailability.kt`, the
> refusal at checkout and at order edit, withdrawn articles dropped from reorders,
> and the basket marking them; plus `StockAvailability.kt`, `sellerAvailable` and
> `stockGated` on the private half, the correction pass in `StockViewModel`, and the
> gate's switch in the article editor; plus `StockReminderEvaluator`,
> `StockReminderSnapshots`, the WorkManager chain, and the seller's refill
> notification — so `lowStockNotifications` finally switches something real; plus
> `OrderShortfalls.kt`, the affected-orders section with its seller-sent draft, and
> the buyer's restored messages entry point. Still a proposal: the public scarcity
> band (§4f). §4b's formula and §4a's migration note were corrected against what
> building it showed, and §3's posting time is now the morning after the deadline
> rather than the deadline itself.

**Date:** 2026-10-20
**Status:** Design — not implemented
**Related:** `doc/guides/ordering-business-rules.md`,
`doc/design/order-ready-notification-design.md`,
`doc/guides/market-pricing-and-access-design.md`, `firebase/database.rules.json`,
`doc/guides/demo-order-provenance.md`

---

## The goal, in the seller's words

Four wishes, which turn out to be one calculation:

1. Tell me what to buy before I go to the wholesaler.
2. Judge that on **what customers have ordered**, not only on what is left in
   storage from last week.
3. Stop a buyer ordering something that is not there.
4. When it is tight, or when an order cannot be filled, let the customer know.

All four need the same number — how much of an article is still *sellable* — so
they share a model rather than four features reading stock four ways. Wishes 1–3
stay inside the seller's app; wish 4 crosses to the buyer, which is why §5 treats
it separately and more cautiously.

---

## 1. Current State

### What exists

| Piece | Where | Does |
|-------|-------|------|
| Append-only ledger | `StockRepository`, `/stock_movements/{seller}/{yyyyMM}` | `INTAKE`, `STOCKTAKE`, `SALE`, `LOSS` movements with a signed quantity |
| Derived levels | `/stock/{seller}/{articleId}` | `onHand`, `unit`, `lastMovementAt`, `lastCountedAt`, `lastSource`; written as a server-side increment in the same multi-path update as the movement |
| Reorder threshold | `SellerArticleData.reorderLevel` | Seller-only, `0` = not watched |
| Refill evaluation | `StockRefill.kt` | `RefillState` (`EMPTY`/`LOW`/`UNCOUNTED`/`OK`/`UNWATCHED`), `RefillNeed.shortfall`, `refillNeeds()` sorted by urgency |
| Surfaces | `StockScreen`, badge in `AppScaffold.kt:88` | The refill list, and its count in the seller top bar |
| Booking sites | `PickupViewModel`, `WalkInSaleViewModel`, `StockBooking.kt` | Movements written on handover, walk-in sale, stocktake |

Movements are already written today, so levels are real data, not a new node.

### What does not exist

- **No notion of committed demand.** Nothing sums what buyers have ordered.
  `AbrechnungViewModel.aggregateItems()` (`:264`) does exactly this sum, per
  article, across a set of orders — but privately, inside a ViewModel, for the
  books.
- **No delivery out of the app.** `NotificationSettings.lowStockNotifications`
  exists, defaults to `true`, is rendered in `NotificationsScreen.kt:77`, and is
  wired to nothing. The switch currently lies to the seller.
- **`available` is cosmetic.** It filters display — `SharedStateTypes.kt:549`,
  `FavoritesScreen.kt:75` — and nothing else. There is **no availability check at
  order placement**. The reorder paths deliberately keep unavailable items:
  `BuyAppViewModelBasket.kt:565`, `:1549` and `BuyAppViewModelHistory.kt:138` all
  read `if (article.available) { refresh it } else item`, so a stale item survives
  into the basket. The draft basket is persisted, so an article can sit in a
  basket for days after the flag flips.

That last point decides the shape of §4: flipping the flag hides an article from
browsing and nothing more. **The gate at placement is the feature; the flag is the
hint.**

---

## 2. One Number: What Is Still Sellable

### Committed demand

`committed(article)` = Σ `OrderedProduct.amountCount` over the orders that the
seller still has to fulfil at the slot being asked about.

Which orders count:

- `order.origin.isReal` — demo orders are phantom demand, written by buyers the
  seller has not approved. `TRANSITIONED` orders **do** count: they are real
  orders that merely started life in `/demo_orders`. See
  `doc/guides/demo-order-provenance.md`.
- `status` in `PLACED`, `LOCKED` — not `DRAFT` (not ordered yet), not
  `CANCELLED`/`NOT_PICKED_UP`/`COMPLETED`.
- Pickup at the slot(s) being asked about, via `PickupSlots.nextPerMarket`.

`COMPLETED` is excluded for a reason worth stating: handover already books a
`SALE` movement (`PickupViewModel`), which has taken the goods out of `onHand`.
Counting a fulfilled order as demand as well subtracts it twice. The rule is
therefore **`committed` holds unfulfilled orders only; the ledger has the rest.**

### The formula

```
remaining = onHand − committed                  // sellable now
shortfall = (committed + reorderLevel) − onHand // what to bring
```

`reorderLevel` stops being a trigger and becomes what its name says: the buffer to
keep on the shelf for walk-in trade on top of what is already sold. With
`reorderLevel = 0` the formula degrades cleanly to pure demand coverage — which
means **an unwatched article still warns when a buyer has already paid for it.**
That matters: `reorderLevel` defaults to `0`, so a purely threshold-based feature
ships silent until the seller sets levels article by article.

### States

`RefillState` gains one value, ranked above `EMPTY`, because it is the only state
in which the seller has already sold something that does not exist:

| State | Condition | Means |
|-------|-----------|-------|
| `SHORT` | `hasLevel && committed > onHand` | Sold more than is there. Fires regardless of `reorderLevel`. |
| `EMPTY` | `hasLevel && onHand <= 0` | Nothing left, nothing sold either. |
| `LOW` | `remaining <= reorderLevel` | Buffer breached once orders are filled. |
| `UNCOUNTED` | watched, `!hasLevel` | Has to be counted before anything can be said. |
| `OK` / `UNWATCHED` | as today | |

`RefillNeed` gains `committed: Double` and `remaining: Double`. `hasLevel` already
exists and already carries the "0.0 means empty *or* unknown" distinction.

### Three units, one sum

`OrderedProduct.unit` is a snapshot taken when the item was ordered.
`StockLevel.unit` is the unit of the newest movement. `Article.unit` is current.
They can all disagree, and `StockMovement`'s own doc comment already says a unit
change with stock on hand should be prevented in the UI rather than guessed at
here.

**Rule: if the order's unit and the article's unit differ, refuse the arithmetic.**
Emit the need with a `unitMismatch` marker and let the stock screen show it as a
question rather than adding kg to Stück. Silently wrong numbers here become wrong
purchases.

### One keying rule, not a third one

The ledger keys by article `id`. `OrderedProduct.productId` is documented as the
ordered article's *database* id (not its BNN number, despite the name) and
defaults to `"-1"`. `Sale.kt:176` already resolves this:

```kotlin
private fun OrderedProduct.articleKey(): String =
    productId.takeIf { it.isNotEmpty() && it != "-1" } ?: id
```

Lift it to a shared location and use it. A second keying rule will drift from the
first, and the symptom — demand attributed to the wrong article — looks like a
stock bug for a long time before anyone suspects the key.

### Stock is one pool; demand is per slot

There is no per-market stock, and this design does not add any. Storage is one
pool keyed by `articleId`.

Demand is not: orders live under a pickup date, and a date can host more than one
market. For **deciding what to buy**, summing every upcoming slot is right. For
**loading the van**, it is not. So the aggregator takes a slot set, the buy list
sums across slots, and the stock screen can show the per-slot split — which is
what the seller actually packs against.

---

## 3. Where It Is Evaluated

### Not in a service

An always-on observer is the wrong shape here, for a reason specific to this app:
**stock only changes when the seller's own app writes a movement.** No buyer, no
backend job depletes it. A background listener on `/stock` would be the seller's
device watching for writes it had just made itself.

The precedent is also the warning. `ListenerService` in the sell flavor is a
foreground `Service` holding an RTDB listener for orders, and it costs ~600 lines
of `SwitchWorker` / `StartServiceWorker` / `BootCompletedReceiver` plumbing to
survive FGS type restrictions and Doze. A second one would double that for a
notification that is never urgent to the minute.

### Three triggers instead

1. **On a movement (in-process).** After `recordMovement(s)` succeeds,
   re-evaluate the touched articles. This catches the real moment — the seller
   closing out a market day — with no background work at all.
2. **On a demand change (in-process).** `committed` rises without the seller
   doing anything, so re-evaluate on each `observeSellerOrders` emission and on
   app start. The seller app is normally running; this covers the normal case.
3. **At the edit deadline (scheduled).** See below.

### The edit deadline is the notification moment

Demand is not final until the edit deadline — the same distance before pickup that
Tuesday 23:59 is before Thursday (`Market.scheduleConfig`,
`OrderScheduleConfig.deadlineDay/Hour/Minute`, `Order.getEditDeadline()`). Before
it, `committed` can still grow. After it, the number is frozen and will not change
again.

That gives the feature a natural trigger:

- **Before the deadline:** live `SHORT`/`LOW` on `StockScreen` and in the badge.
  Informational.
- **At the deadline:** one notification per market per cycle — *"Einkaufsliste für
  22.10.: Äpfel 12 kg, Möhren 3 kg"*. It is a complete, final buy list, it arrives
  while the seller can still act on it, and it is **once per cycle by construction**
  (`StockReminderSettings.lastFiredCycle`), which removes the need for
  `lastNotifiedAt` dedup bookkeeping.

  Built with one correction: the deadline is 23:59, and a shopping list that buzzes
  at 23:59 is one the seller switches off. It is posted at 07:00 the following
  morning instead — after demand went final, before the wholesaler — with a 14-hour
  grace window, because a buy list stays useful all day. A seller whose deadline
  already falls before 07:00 gets it on the deadline's own day
  (`StockReminderEvaluator.postAt`).

Implementation mirrors the shipped reminder pattern rather than inventing one: a
pure `StockReminderEvaluator` in `commonMain` (every input a parameter, no clock,
no repository — exactly as `OrderReminderEvaluator` is built, for the same
testability reason) plus an `expect` scheduler, WorkManager on Android
(`AndroidOrderReminderScheduler` is the template), `UNUserNotificationCenter` on
iOS. Reuse `OrderReminderScheduler`'s `canDeliver()` idea so the settings switch
cannot look on while notifications are off for the app.

**`UNCOUNTED` is excluded from notifications.** Any watched article with no
movement is "needs attention", which is honest on the stock screen and a 40-item
dump as a push on day one. The stock screen handles the uncounted backlog.

---

## 4. Letting Stock Drive `available`

This is the part that writes to the **public, buyer-facing** node, and it needs
two things the refill list does not.

### 4a. One boolean cannot hold two authorities

The seller already writes `available` by hand
(`OverviewViewModel.updateArticlesAvailability`). If the ledger writes it too, the
two fight: the seller marks Spargel off for the season, and the next stocktake
movement turns it back on.

Arbitrating between two writers of one field is the wrong fix. Make the public
flag an **output**:

```
articles/{s}/{a}/available                 derived; public; never hand-edited
seller_articles/{s}/{a}/sellerAvailable    the seller's own intent; private
seller_articles/{s}/{a}/stockGated         opt-in per article; default false
```

```kotlin
// Closing takes a *known* empty shelf. `hasLevel && remaining > 0` — the formula
// this design first carried — closes every never-counted article instead, which is
// the opposite of what the hasLevel guard below is for.
val stockPermits = !stockGated || !hasLevel || remaining > 0.0
val available    = sellerAvailable && stockPermits
```

Both halves of an article already move in one atomic multi-path update
(`ArticleNodes`), so there is no window in which the intent and the derived flag
disagree. The seller's manual toggle keeps working — it writes `sellerAvailable`,
and `available` follows.

Migration: `sellerAvailable` is seeded from the current `available` on first write;
absent, it reads as the current `available`, so nothing changes for an article
nobody has touched.

That fallback has a consequence found while building Phase 3, and it is the reason
the correction pass skips a gated article with no recorded intent: closing one would
make the pass's *own* write read back as the seller's intent next time, and nothing
could ever reopen it. The gate therefore only acts once an intent exists, which the
article editor records whenever the gate is set.

### 4b. Two guards that are not optional

- **`hasLevel`.** `onHand` is `0.0` both for "known empty" and for "nothing is
  known" — precisely what `RefillNeed.hasLevel` was added to distinguish. Without
  this guard, shipping the feature makes the entire catalogue unavailable at the
  moment it first runs.
- **`stockGated` defaults to `false`.** Opt-in per article, like `reorderLevel`.
  Weighed goods where the seller brings what they bring should not be gated at
  all, and the seller should choose which articles are inventory-like.

**Hysteresis — deferred, deliberately.** The intent was: flip off at
`remaining <= 0`, back on only at `remaining >= someMargin`. There is no margin to
pick that means anything across units — 0.5 is a lot of Knoblauch and nothing in
Kartoffeln — and no field records *why* a level last moved, so "reopen only after an
intake" is not derivable from `StockLevel` either. What is implemented instead is an
**idempotent pass**: only differences are written, so a settled catalogue writes
nothing, and churn costs one write per genuine crossing of zero rather than one per
evaluation. If flicker shows up in practice, the margin belongs on
`SellerArticleData` next to `reorderLevel`, in the article's own unit, where the
seller can see it.

### 4c. The gate at placement is the actual feature

Because `available` only filters display, the enforcement work is in the buy
flavor:

1. Check at `placeOrder`: every item's article still available, and — for a gated
   article — `amountCount <= remaining`. Reject with a German message naming the
   article, not a generic failure.
2. Fix the three `else item` branches (`BuyAppViewModelBasket.kt:565`, `:1549`,
   `BuyAppViewModelHistory.kt:138`) so a reorder **drops or flags** an unavailable
   item instead of carrying it forward.
3. Show it in the basket, not only at the end: an item that went unavailable while
   the basket sat there needs to say so before the buyer taps "order".

### 4d. What this cannot do, stated plainly

A boolean is a latch, not a reservation. With 5 kg on hand and `available = true`,
two buyers can each order 5 kg before anything flips. RTDB security rules cannot
sum order children, so there is **no server-side enforcement available**, and the
client-side gate in 4c is a check against a value that may be seconds stale.

Therefore:

- The flag stops **new** orders once the pool is exhausted.
- The oversell race stays open. `SHORT` is how the seller finds out, and resolving
  it (substitute, part-fill, call the buyer) stays a human job.
- Do not describe this to the seller as "prevents overselling". It prevents
  *ordering what is not there*, which is a weaker and true claim.

A real reservation would need a server-side counter per article per slot — a
transaction on a `reserved` node written by the buyer, with rules enforcing the
increment. That is a larger change, has its own abandoned-basket cleanup problem,
and is out of scope here. Noted as the upgrade path if overselling turns out to
happen in practice.

### 4e. Only the seller may write it

Rules line 5: `/articles/{sellerId}` is writable by `auth.uid === $sellerId`
alone. Evaluation therefore lives on the seller's device, which is consistent for
the stock half (only the seller moves stock) but not for the demand half: a buyer
ordering while the seller app is closed raises `committed`, and the derived flag
goes stale until the seller app next runs. Trigger 2 in §3 is what closes this,
and the staleness window is a known, accepted gap — not something a service would
fix, since the seller's device has to be awake either way.

### 4f. Showing the buyer that supply is tight

A binary flag only speaks at the moment supply hits zero. A buyer deciding how
much to order would rather know beforehand, which argues for publishing something
about `remaining` — and that runs straight into §4e: the buyer can compute
nothing. `/stock` is seller-read-only (rules `:65`), and `committed` would mean
reading other buyers' orders. Any signal has to be *published* by the seller's
device into a buyer-readable node, and `/articles/{sellerId}` is readable by
**every signed-in user** (rules `:4`) — competitors included, who over a few days
of polling would be reading not a stock level but a turnover curve.

So publish a band, never a quantity:

```
articles/{s}/{a}/scarcity = PLENTIFUL | LIMITED | LAST_FEW   (derived, public)
```

Two independent reasons the band has to be coarse:

- **Staleness makes a number into a broken promise.** The value is written by the
  seller's device and read by buyers who may be minutes or hours behind.
  *"Nur noch 2 kg"* that is wrong is a commitment the shop did not keep.
  *"Nur noch wenig verfügbar"* survives being stale and stays true.
- **Leakage.** A band tells the buyer what they need and a competitor almost
  nothing. A number tells both everything.

Derived the same way and under the same guards as `available` (§4b): only for
`stockGated` articles, only with `hasLevel`, written in the same multi-path update,
with hysteresis on the band edges. An ungated article publishes no `scarcity` at
all, which is the honest answer for weighed goods where the seller brings what
they bring.

Two consequences to accept rather than design around:

- **It accelerates depletion.** Scarcity shown is scarcity acted on, so the
  oversell race in §4d will be hit *more* often, not less.
- **"Last few" is not a reservation.** With persisted baskets and no reserved
  node, a buyer can read `LAST_FEW` for goods already sitting in someone else's
  basket. The band's wording must stay qualitative for exactly this reason; it
  describes the shop, not the buyer's chances.

---

## 5. Telling the Customer When an Order Cannot Be Filled

§4f is about the catalogue before ordering. This is the other direction: an order
already placed that `SHORT` says cannot be met. It is the more valuable half and
the safer one — it needs **no public inventory field at all**, because the gap is
detected seller-side and the message goes to one buyer about their own order.

### The channel exists

`MessageRepository` (`observeConversations`, `observeMessages`,
`observeUnreadCount`, `sendMessage`, `getOrCreateConversation`), `Conversation`,
`Message`, with `ConversationDetailScreen`/`MessagesScreen` on the buy side and
`ConversationListScreen`/`SellerConversationDetailScreen` on the sell side. There
is nothing to build on the transport.

**Dependency:** the buyer's messages top-bar entry was deliberately removed in May
2026, so buyers may currently have no visible route into a conversation. That
entry point has to be restored before this ships, or the message lands unread and
the seller believes the buyer was told.

### Propose, do not auto-send

A shortfall is resolvable. The seller may buy more at 5am, substitute a comparable
article, or part-fill and adjust. An automatic *"Ihre 5 kg Äpfel können wir nicht
liefern"* at the Tuesday deadline is a message the seller then has to walk back,
and the retraction costs more trust than the silence would have.

The flow is therefore seller-driven:

1. `SHORT` on `StockScreen` lists the affected **orders**, not only the articles —
   who is affected, by how much, for which slot. The per-slot split from §2 is
   what makes this possible.

   Built with nothing allocated between them: with 1 kg left and three buyers
   wanting 5 kg each, all three are listed and each line reports the *total* gap.
   Dividing it is a decision about customers, not a division sum. What the draft
   tells a buyer is what *they* ordered, never the shop's total shortfall, which
   would read as a complaint.
2. One tap drafts a German message per affected order, pre-filled with the
   article and the shortfall.
3. The seller edits and sends. Substitution and part-fill are judgement, not
   arithmetic, so the decision stays human.

### Not a second notification mechanism

`doc/design/order-ready-notification-design.md` §B is already deciding how a buyer
is told something about their order. A shortfall notice is one more payload on
that channel, not a parallel one. If that design lands on a different transport
than messages, this follows it rather than keeping its own.

---

## 6. Decisions

| # | Decision | Why |
|---|----------|-----|
| D1 | `remaining = onHand − committed`; `reorderLevel` is a buffer, not a trigger | One formula serves the buy list, the badge and the gate; works with `reorderLevel = 0` |
| D2 | Add `SHORT`, ranked above `EMPTY` | Sold-but-absent is the only state with a customer already waiting |
| D3 | Exclude `COMPLETED` from `committed` | Handover already booked a `SALE` movement; counting both subtracts twice |
| D4 | Refuse arithmetic on a unit mismatch | A wrong number here becomes a wrong purchase |
| D5 | Reuse `Sale.kt`'s `articleKey()` | A second keying rule drifts, and the symptom looks like a stock bug |
| D6 | No observing service; evaluate on movement, on order change, at the deadline | Nothing external changes stock; `ListenerService` is the cost of the alternative |
| D7 | Notify at the edit deadline, once per market per cycle | Demand is final, the seller can still act, dedup is free |
| D8 | `UNCOUNTED` never notifies | Day-one noise; belongs on the stock screen |
| D9 | Public `available` becomes derived from private `sellerAvailable && stockPermits` | One field cannot carry two authorities |
| D10 | `stockGated` opt-in, `hasLevel` required | Otherwise the first run empties the shop |
| D11 | Enforce at `placeOrder`, not only via the flag | The flag is display-only today; without this the feature is cosmetic |
| D12 | Buyer-facing scarcity is a 3-state band, never a quantity | A stale number is a broken promise; a band leaks almost nothing to competitors |
| D13 | A shortfall message is drafted for the seller, never auto-sent | The gap is often resolvable; a retracted apology costs more than silence |
| D14 | Shortfall notices ride the order-notification channel, not their own | One decision about how buyers are told, taken in one place |

---

## 7. Open Questions

1. **Does `committed` span all upcoming slots or only the next one?** The buy list
   wants all of them; the ordering rules only ever open the next slot per market
   (`doc/guides/ordering-business-rules.md`), so in practice these coincide today.
   Worth an explicit decision before multi-slot ordering is ever reconsidered.
2. **Walk-in trade vs. the buffer.** `reorderLevel` as a buffer is a flat number
   per article. Should it instead be derived from observed walk-in velocity once
   there are enough `SALE` movements? Probably later; a flat number the seller
   sets is explainable, a derived one is not.
3. **Should `SHORT` notify immediately rather than at the deadline?** An oversell
   is the one case with a customer already waiting. Argument for: the seller may
   be able to buy more. Against: it reopens per-article dedup, which D7 avoided.
4. **Partial-fulfilment vocabulary.** The gate stops new orders but says nothing
   about an existing order that can no longer be filled. That is a fulfilment
   feature (substitutions, part-fill), overlapping
   `doc/design/order-ready-notification-design.md` §A3.
5. **iOS scheduling fidelity.** `UNUserNotificationCenter` can place a local
   notification at the deadline, but recomputing demand at fire time needs the app
   awake. Accept a notification built from the last known numbers, or skip when
   stale?
6. **Where the scarcity band's thresholds live.** `LAST_FEW` as a fraction of
   `reorderLevel`, as an absolute per unit, or seller-set per article? A fraction
   is self-tuning but meaningless where `reorderLevel = 0`, which is the default.
7. **Does the band belong on `/articles` at all?** The alternative is a separate
   buyer-readable node with its own rules, so publishing scarcity does not widen
   what an article's node exposes. More paths to keep in step against a narrower
   leak — probably not worth it, but it is the one way to publish scarcity without
   making the catalogue itself more revealing.
8. **Should the buyer see the band in their basket as well as the catalogue?** It
   is the point at which it would change a decision, and also the point at which a
   stale band is most likely to be read as a promise.

---

## 8. Phases

Each phase stands on its own and is useful shipped alone.

**Phase 1 — the pure half (no decisions above needed) — BUILT**
Lift the demand aggregator out of `AbrechnungViewModel` into a `commonMain`
function over `(orders, slots) -> Map<articleId, Double>`, using the shared
`articleKey()`. Extend `StockRefill.kt` with `committed`, `remaining`, `SHORT` and
the unit-mismatch marker. Show it on `StockScreen` and in the badge. All
`commonMain`, all unit-testable, no notification, no public write.

**Phase 2 — the placement gate — BUILT**
`placeOrder` validation, the three `else item` branches, basket-level messaging.
Fixes a real hole that exists today independently of this design: the flag is
already filtered on, already toggled by the seller, and already not enforced.

**Phase 3 — derived availability — BUILT**
`sellerAvailable` / `stockGated` on the private half, derived `available` written
in the same multi-path update as the movement, hysteresis, rules tests under
`firebase/tests`, a per-article toggle in the article editor.

**Phase 4 — the seller's notification — BUILT**
`StockReminderEvaluator` in `commonMain`, `expect` scheduler, WorkManager and
`UNUserNotificationCenter` implementations, and finally wire
`NotificationSettings.lowStockNotifications` to something real.

**Phase 5 — telling the customer (§5) — BUILT**
Affected orders on the `SHORT` list, the drafted message, the seller's send. The
buyer's messages entry point is restored with it, gated on approval like the contact
shortcut.

**Phase 6 — the scarcity band (§4f)**
Derived `scarcity` on the public article, band thresholds, hysteresis, rules
tests, buyer-side display. Deliberately last: it is the only phase that publishes
business data outward, and it is the easiest to defer if the rest proves enough.

Two ordering notes. **Phase 2 is the one not to postpone** — until it lands, every
other phase moves a flag that nothing enforces. And **Phase 6 should not precede
Phase 3**: publishing "nur noch wenig" while nothing stops the order that empties
the shelf is the worst of both, a signal with no gate behind it.
