# Markets: Pricing, Access, Scheduling and Communication

**Status:** §1 (markets as an entity), §3 (eligibility) and §4 (schedules, slots,
closures) are implemented, 2026-10-19. §2 (pricing) and production requests are
design only.
**Scope (proposed):** `shared/src/commonMain` (model + pricing + schedule + repository),
`buyMain` (slot picker, price breakdown), `sellMain` (market, condition and closure
editing), `firebase/database.rules.json`

Why a market is not yet an entity, what has to change before per-market pricing,
per-market access or per-market pickup days can be built, how a buyer chooses where and
when to collect, and how a price difference gets explained rather than merely applied.

---

## Where it stands today

`Market` exists as a data class and nothing else:

```kotlin
data class Market(
    val id: String = "",          // stable UUID, generated in SellerProfileScreen
    val name: String = "", val street: String = "", val houseNumber: String = "",
    val city: String = "", val zipCode: String = "",
    val dayOfWeek: String = "", val begin: String = "", val end: String = "",
    val dayIndex: Int = -1,
)
```

| Place markets appear | What it is |
|---|---|
| `SellerProfile.markets: List<Market>` | the only storage — a **list**, not a keyed map, under `seller_profile/{sellerId}/markets` |
| `Order.marketId: String` | written as `""` by the buyer app (`BuyAppViewModelBasket.kt:692`, `:942`); only the mock data in `GitLiveOrderRepository` ever sets `"market_001"` |
| `BuyerProfile.defaultMarket: String` | free-form, never resolved against a `Market.id` |
| `UserProfile.marketLocation: String` | a third, unrelated free-form string in `SharedStateTypes.kt:135` |

So markets are editable by the seller (`SellerProfileViewModel.addMarket` /
`updateMarket` / `removeMarket`), displayed, and then referenced nowhere that matters.
Two of the three reference fields are strings that nothing validates, and the one on
`Order` is dead.

Two facts that help:

- `Market.id` is already a stable `Uuid.random()`, assigned once and preserved on edit.
  References to it will not rot.
- The field-level read rule on `seller_profile` (`"$field": { ".read": "auth != null &&
  $field !== 'knownClientIds' && …" }`) already lets any signed-in user read
  `seller_profile/{sellerId}/markets`. Buyers can see the market list today.

Pricing is one number. `Article.price` is public; the seller half
(`SellerArticleData.acquirePrice`, `markupFactor`) lives in `seller_articles` and
derives it. Access is one status per (seller, buyer) in `BuyerAccess` at
`buyer_access_status/{sellerId}/{buyerId}`, with `invite_tokens/{sellerId}/{token}`
granting it. Neither knows markets exist.

---

## The rule

> **A market is the unit of logistics. Price differences are stated as causes, never as
> numbers. Approval is per seller; eligibility is per market. A pickup slot is a market
> and a date together, never two separate choices.**

Four separable claims, in the order they have to be built.

---

## 1. Promote `Market` to an entity first — built

Nothing else here is buildable while a market id means nothing. The step is small and
correct on its own merits:

- Move markets out of the profile blob to `markets/{sellerId}/{marketId}` — a **keyed
  map**, so rules, pricing and eligibility can hang off a market without rewriting a list
  whose indices shift on every delete. Read: any signed-in user. Write: the seller.
- Make `Order.marketId` real: required, validated against an existing market, actually
  written at checkout. A buyer with one market gets it implicitly; a buyer with several
  picks.
- Resolve `BuyerProfile.defaultMarket` to a `Market.id`, and either delete
  `UserProfile.marketLocation` or point it at the same id. Three spellings of one
  concept is already one too many.

### What was built

`markets/{sellerId}/{marketId}`, read by any signed-in user and written by the seller
(`MarketRepository` / `GitLiveMarketRepository`). The rules key each record by its own
id, reject unknown fields, and bound `dayIndex` to the week.

`Order.marketId` is filled at checkout from the buyer's contextual market and
**validated against the node**, so an order cannot name a market that does not exist.
Empty stays legal — the buyer of a multi-market seller has nothing to choose with until
the slot picker lands in §3, and orders written before the field existed carry it.

`BuyerProfile.defaultMarket` is documented as a `Market.id` and resolved against the
node rather than taken on trust. `UserProfile.marketLocation`, the third unrelated
spelling of the same concept, was deleted: nothing read it.

**Markets are deactivated, never deleted.** `Market.active` defaults to true and absent
means true, so every legacy record reads as live. `removeMarket` flips the flag instead
of dropping the row, because orders reference a market by id and the seller's books are
built from those orders. This settles open question 5.

### Deployed and migrated

| | Rules | Migration |
|---|---|---|
| dev `fire-one-58ddc` | deployed 2026-10-19 | applied 2026-10-19, 6 markets |
| prod `bodenschaetze-a988e` | deployed 2026-10-19 | applied 2026-10-19, 3 markets |

Both live rulesets were byte-identical to the repo before the change and to each other,
so the deploy was purely additive: the `markets` node and the `orders/marketId`
validator.

Both were read back afterwards and the migration re-run against each: the second pass
moved nothing and skipped everything, so it is idempotent. Every record's key matches
its own id, carries `active: true`, and has the repaired `dayIndex` (3/4/5 for
Donnerstag/Freitag/Samstag). The profile list is still in place in both projects, as the
fallback needs.

Prod went through the console rather than the CLI, from the file `--out` produces. That
flag writes the whole planned node, so importing it at `/markets` is a replace rather
than a merge — equivalent only while nothing else has written there, which was the case.

**Order matters.** The rules go first: an app writing to `/markets` against a ruleset
without the node gets permission denied. Deploying the rules before such an app exists
is harmless.

### Migration

Two paths, deliberately. `firebase/scripts/migrate-markets-node.mjs` moves existing data
— dry run by default, `--apply` to write, `--from-export` to rehearse the whole plan
offline. And `MarketRepository.migrateLegacyMarkets`, called the first time a seller
opens their profile, so the app does not depend on the script having been run against
their project. Both are **add-only**: a market already in the node is newer than the
profile list and is reported and skipped, never merged into.

A list entry with no `id` cannot be moved — the id becomes the key, and minting a fresh
one would not match what an order or closure already refers to. Those are reported for
the seller to fix in the app.

**`dayIndex` was wrong in both live databases.** The dry run against dev and prod found
every market storing Sunday=1..Saturday=7, while the market dialog has counted
Monday=0..Sunday=6 for as long as it has existed:

| Stored | `dayOfWeek` | Correct |
|---|---|---|
| 5 | Donnerstag | 3 |
| 6 | Freitag | 4 |
| 7 | Samstag | 5 |

Two consequences, both silent until now. The new rules cap the field at 6, so a Saturday
market would have been refused on the seller's next edit. And `expandClosureRange` reads
the index to decide which days a vacation covers (§4), so closing a fortnight would have
picked the wrong days for every market in both databases.

The migration repairs it from `dayOfWeek`, which is what a human typed and is therefore
authoritative, and reports every correction. Both German and English names are
recognised, `Sonnabend` included. A record whose day name is unrecognisable keeps its
index and is reported separately — written as it stands, but the rules will refuse the
seller's next edit of it. Surrounding whitespace is trimmed on the way through; prod and
dev both carry a `"Fürstenwalde "` with a trailing space in its name and city.

Reads fall back to `seller_profile/{sellerId}/markets` while both shapes exist. The
profile list is deliberately **not** deleted by the migration: a released app still reads
it. `--drop-legacy` clears it in a second pass, once that fallback comes out.

---

## 2. Pricing: model the difference, not the result

### Rejected: per-market prices on the article

The obvious shape — `Article.prices: Map<marketId, Double>` — is wrong here:

- Every article write multiplies by the number of markets, across both halves of
  `ArticleNodes`. The Terra catalog is 317 rows; four markets makes every upload and
  every availability sweep four times the writes.
- A seller maintaining N prices per article by hand will not keep them consistent.
- It stores the *outcome* and discards the *reason* — and the reason is precisely what
  the buyer has to be told. The explanation would then have to be authored separately
  and kept in sync with the numbers, which it would not be.

### Proposed: conditions at `market_pricing/{sellerId}/{marketId}`

A market carries a handful of conditions, each a cause + a scope + an effect:

```kotlin
enum class PriceCause { STALL_FEE, TRANSPORT, HANDLING, NEIGHBORHOOD_RATE, VOLUME_COMMITMENT }

sealed interface PriceScope {
    data object AllArticles : PriceScope
    data class Category(val name: String) : PriceScope
    data class SingleArticle(val articleId: String) : PriceScope
}

sealed interface PriceEffect {
    data class Factor(val value: Double) : PriceEffect       // 1.08
    data class Surcharge(val amount: Double) : PriceEffect   // +0.25 EUR
    data class Discount(val amount: Double) : PriceEffect    // -0.15 EUR
}

data class PriceCondition(
    val cause: PriceCause,
    val scope: PriceScope,
    val effect: PriceEffect,
    val note: String = ""        // the seller's own words, shown verbatim to the buyer
)

data class MarketPricing(
    val marketId: String = "",
    val revision: Long = 0L,     // bumped on every change; snapshotted into orders
    val conditions: List<PriceCondition> = emptyList()
)
```

Three to five conditions per market instead of 317 prices.

### Resolution is a pure function

In `commonMain`, with no Firebase and no coroutines, so it is testable the way
`OrderDateUtils` is:

```kotlin
data class PriceComponent(val cause: PriceCause, val amount: Double, val note: String)

data class ResolvedPrice(
    val base: Double,
    val gross: Double,
    val components: List<PriceComponent>
)

fun resolvePrice(
    article: Article,
    pricing: MarketPricing,
    terms: BuyerTerms = BuyerTerms.Standard
): ResolvedPrice
```

Rules to pin down in the implementation, because they are the whole of the behaviour:

- **Order of application.** Factors before absolute amounts, or the result depends on
  condition ordering in the database. Proposal: all factors multiply against the base,
  then surcharges and discounts sum.
- **Scope precedence.** `SingleArticle` beats `Category` beats `AllArticles` for the
  *same cause*; different causes always stack.
- **Rounding.** Once, at the end, to cents. Components are rounded for display only and
  must be shown so they visibly sum to the gross — a breakdown that does not add up is
  worse than no breakdown.
- **Floor.** A discount may not take gross below `acquirePrice`. The seller app should
  warn at edit time, not silently clamp at read time.

### Communicating the difference

This falls out of `components` — no separate explanation text to maintain:

```
Möhren, 1 KG                                    3,20 €
  2,80 € Grundpreis
+ 0,25 € Standgebühr Markt Kreuzberg
+ 0,15 € Transport
```

Two levels in the buy app:

- **Market-level banner** on the catalog: *"Preise für Markt Kreuzberg — inkl.
  Standgebühr und Transport."* Set once, read once, not per row.
- **Per-article affordance** that expands into the breakdown above. Only for articles
  whose `components` is non-empty, so an unmodified market shows no extra chrome at all.

### The basket landmine

A buyer who switches markets with a full basket gets different totals. This must be an
explicit step — *"4 Artikel ändern den Preis: … Weiter / Abbrechen"* — not a silent
recalculation. Silent recalculation of a basket the buyer already reviewed is the single
most damaging thing this feature could do to trust, and it is easy to ship by accident.

### Orders snapshot, never reference

`OrderedProduct.price` already stores a number, which is right. Extend it with the
resolved components and store `MarketPricing.revision` on the `Order`. Without that:

- re-opening a two-week-old order recomputes it against today's conditions,
- `/sales/{sellerId}/{yyyyMM}/{saleId}` and the order it came from stop reconciling,
- and the CSV tax export reports figures that were never charged.

This matters more than the pricing model itself. The same discipline already governs the
demo-order transition, which refuses to move an order whose pickup has passed for exactly
this reason (`doc/demo-order-provenance.md`).

---

## 3. Access: approval per seller, eligibility per market — built

### Rejected: `BuyerAccess` keyed by (seller, market)

`AccessStatus.BLOCKED` is a trust judgement about a person. Making it per-market means a
buyer can be blocked at one stall and welcome at the next, which is not what blocking
means, and it multiplies every path that already consults access: the `orders` and
`demo_orders` write rules, `seller_events` eligibility, the demo→production transition in
`BuyAppViewModelAccess`, and `access_requests`.

### Proposed: one status, plus an eligibility list

```kotlin
data class BuyerAccess(
    val buyerId: String = "",
    val displayName: String = "",
    val status: AccessStatus = AccessStatus.NONE,   // unchanged: may they order at all
    val allowedMarkets: List<String> = emptyList(), // empty = all markets
    val updatedAt: Long = 0L
)
```

`status` answers *whether*, `allowedMarkets` answers *where*. The second is logistics and
can be edited freely by the seller without touching the trust state machine. Empty means
all, so every existing record keeps its current meaning with no migration.

### Invitation carries the market

`invite_tokens/{sellerId}/{token}` already holds `createdAt`, `expiresAt`, `redeemedBy`,
`displayNameHint`, with `"$other": { ".validate": false }` locking the shape. Add
`grantsMarkets` (a comma-joined id list or a child map) and extend the validator. The
redemption rule already pins every pre-existing field to its stored value so a redeemer
cannot alter the token; `grantsMarkets` must join that list.

An invite then means *"you are invited to the Kreuzberg stall"* — which is how a
neighborhood-scoped invitation already works socially. Invite links are described in the
code as meant to be scanned at a stall (`SellerProfileViewModel.kt:419`), so the market is
usually known at the moment the link is generated.

The self-write rule on `buyer_access_status` lets a buyer create their own `APPROVED`
record when `viaToken` names a token they redeemed. That rule now also requires
`allowedMarkets` to equal the token's `grantsMarkets`, because without it redeeming a
stall-scoped invite would be an invitation to grant yourself every market.

### What was built, and the two holes it closed

`BuyerAccess.allowedMarkets`, **empty meaning all** — so every record written before
scoping keeps its meaning and no migration was needed. The buy app filters its markets
through `BuyAppState.eligibleMarkets`, which feeds the slot generator and the contextual
market, so a stall the buyer is not scoped to offers them nothing.

The scope is stored as **one comma-joined string**, sorted and deduplicated, not a child
map. RTDB rules can compare a string for equality but cannot walk a map to prove two of
them match, and that comparison is the whole enforcement. `MarketScope` owns the
encoding, and a test pins that two sellers ticking the same boxes in a different order
produce the same string.

Two escalations the rules now deny, both found while writing them:

- **Widening on redemption.** A buyer writes their own `APPROVED` record, so without the
  equality check they could claim `"m1,m2"` — or, worse, `""`, which means *every*
  market — from a token granting only `m1`.
- **Widening afterwards.** The update branch of that rule only pinned `status`, so an
  approved buyer could rewrite their own `allowedMarkets` at any time. It now pins the
  scope too, while still allowing the display-name change the buy app makes.

A token minted before scoping has no `grantsMarkets`; redeeming it grants every market,
which is what it always meant. `market-eligibility.test.js` covers all of this — 14
tests, including each escalation attempt.

### Production requests

A buyer committing to volume on something not normally stocked, with a longer lead time,
in exchange for a better price. This is an **order-level** condition, not an article
property:

- a lead time longer than the ordinary Tuesday deadline,
- a committed quantity the buyer cannot reduce after the deadline,
- and a `VOLUME_COMMITMENT` condition applied at resolution.

It belongs beside `OrderOrigin` as a separate axis — an order can be a production request
*and* `TRANSITIONED` — not as another `OrderStatus` value. Squatting in the status slot is
the mistake `OrderOrigin` was extracted to undo.

*This section is the least certain in this document; see open question 1.*

---

## 4. Scheduling: one slot picker, and days without a market

### Per-market pickup day

`Market.dayOfWeek` and `dayIndex` already exist and already contradict the global Thursday
rule. Multi-market makes that contradiction load-bearing.

`OrderDateUtils` is config-driven (`OrderScheduleConfig`, `DefaultOrderScheduleConfig`),
so the plumbing exists — but the config is resolved from a single default today, and every
caller would have to resolve it **per market** instead:

- `Order.canEdit()`, `getEditDeadline()`, `getWindowStatus()` — all currently
  market-blind, all called from the basket, the order list and the seller's pickup screen.
- `OrderDateUtils.calculateNextPickupDate()` — no longer a single answer for a buyer.

`/orders/{sellerId}/{yyyyMMdd}/` is unaffected — it is a date either way.

### Rejected: a market picker beside the date picker

The buy app picks a date today in `DatePickerDialog` (`BasketScreen.kt:1202`): a
`LazyColumn` of selectable cards over `OrderDateUtils.getAvailablePickupDates(count = 5)`,
validated by `isPickupDateValid`, stored in `basketScreen.selectedPickupDate`.

Adding a second, parallel dialog for the market is wrong, because **market and date are
not independent**. Once each market has its own weekday, the market determines which dates
exist. Two pickers would let a buyer choose Neukölln and then a Thursday Neukölln does not
have, and the fix would be cross-validation between two dialogs forbidding combinations
that should never have been offered.

### Proposed: the slot is the unit

```kotlin
/** A market and one of its pickup dates. Never chosen separately. */
data class PickupSlot(val marketId: String, val date: Long)

fun getAvailableSlots(
    markets: List<Market>,
    closures: MarketClosures,
    count: Int = 5,
    now: Instant = Clock.System.now(),
): List<PickupSlot>
```

One chronological list, the existing dialog, one extra line of text per card:

```
Kreuzberg · Do 16.10.
Neukölln  · Sa 18.10.
Kreuzberg · Do 23.10.
Neukölln  · Sa 25.10.
```

`Order.marketId` is then filled from the chosen slot — which is what finally makes the
field real (today it is written as `""`).

**With one market the picker degenerates to today's date list**, and the market shows as a
label rather than a control. Most buyers will be in that case; the market row is therefore
conditional on more than one market being on offer, not always present.

### A saved basket remembers its slot — built

`DraftBasket` stores `selectedPickupDate` plus `selectedMarketId`. The market needed **no
migration**: absent means "none chosen", which is exactly what every draft written before
the picker meant and what a single-market seller's draft still means.

On restore the slot is re-checked rather than trusted — the market still offered, the day
not closed, the deadline not passed. A basket sits in `buyer_profile` across all three of
those changing. Any of them drops the slot and keeps the items, so the buyer picks again
instead of discovering at checkout that the old one was dead; the reasons are
distinguished in the log, because "your pickup was cleared" is a different conversation
depending on which it was.

### Two different picks

"One or more markets" and "where is this order picked up" are separate choices with
separate lifetimes, and only the second resembles the date picker:

| | Which markets am I a customer of | Where do I collect *this* order |
|---|---|---|
| Cardinality | one or more | exactly one |
| Lives in | `BuyerProfile` (`defaultMarket` exists, unused) | basket / `DraftBasket` |
| Changes | rarely | per order |
| Shape | multi-select in the profile | the slot picker |

An order has exactly one pickup location, so the slot picker is always single-select. The
profile choice only narrows the slot list from every market the seller runs to the ones
this buyer visits.

Filter chain: **seller's markets → `BuyerAccess.allowedMarkets` → buyer's subscribed
markets → closures → slots**.

The profile multi-select may not be worth v1; see open question 6.

### Days without a market

A market day can fail to happen: a public holiday, the seller's vacation, a cancelled
stall. The seller needs to maintain those days, and they must disappear from the offered
slots.

```kotlin
/**
 * A day on which a market does not take place.
 * [marketId] null means every market — the seller is away entirely.
 */
data class MarketClosure(
    val marketId: String? = null,
    val date: String = "",       // yyyyMMdd, the same key format as /orders
    val reason: String = "",     // shown to the buyer: "Feiertag", "Urlaub"
)
```

**BUILT DIFFERENTLY — stored as `seller_profile/{sellerId}/closures`, a list.**

The design called for a node of its own at `market_closures/{sellerId}/{yyyyMMdd}/{marketId}`,
keyed by date so the filter would be a child lookup rather than a scan. Building the
contact feature first showed the better answer: the field-level read rule on
`seller_profile` already lets any signed-in buyer read an individual child, so a
`closures` list beside `contacts` needs **no new node, no rules block and no rules
deploy**. A seller has perhaps ten to thirty closures a year, so the scan the keyed
layout was avoiding is a list of a few dozen entries read once and indexed in memory by
`MarketClosures`. The empty `marketId` replaces the literal `ALL` key.

Two choices from the design survived:

- **`yyyyMMdd` strings, not epoch millis.** Matches the existing `/orders` key
  convention, carries no timezone, and is legible in the Firebase console.
- **Discrete dates stored, ranges only in the UI.** A fortnight of vacation is two to
  four actual market days, and `expandClosureRange` stores exactly those: the days in the
  range that are market days for the chosen scope — one market contributes its own
  weekday, a seller-wide closure every market's, falling back to the configured pickup
  day when no markets exist yet. A single date is taken at face value even when it is not
  a market day, since the seller may know something the schedule does not. The expansion
  is capped at `MAX_RANGE_DAYS` (two years) so a mistyped year cannot write thousands of
  records.

Closures are a filter on the generated slots, not new machinery:

```
for each eligible market:
    generate the next occurrences of its day
    drop any that are closed (market-specific or ALL)
    drop any whose edit deadline has passed
merge chronologically, take count
```

**The weekday label was a literal.** `DateOption` printed `day_thursday` beside every
date, which was true only while every pickup was a Thursday. It now derives the weekday
from the date, and shows the market's name when there is more than one to choose
between.

**The generator needed a horizon — fixed.** `getAvailablePickupDates` looped
`while (dates.size < count)` with no iteration bound. That terminated only because every
Thursday eventually qualified; once a seller can close days, a sufficiently closed
calendar made it spin forever. It now stops after `MAX_PICKUP_CANDIDATES` (26) and
returns a short or empty list. `MarketClosureTest` closes every candidate in the horizon
and asserts an empty result, so the hang cannot come back.

`isPickupDateValid` consults closures too: a draft basket can hold a date chosen days
before the seller closed it, and the existing stale-date path in the basket already
resets to the picker with an error.

### Closing a day that already has orders — built

The hard case, and the one most likely to be skipped. The seller blacks out 23.10 and
fourteen orders already exist at `/orders/{sellerId}/20241023/`.

Silently stranding them is not acceptable: the buyers would arrive at a market that is not
there. The closure is therefore **refused at the moment of entry**, naming the count, and
is not writable from the UI at all — `ClosureAddition.HasOrders`. A range is refused whole
rather than part-applied, because closing half a vacation silently is worse than refusing
it.

`SellerProfileViewModel.ordersByCycle` counts live orders per `yyyyMMdd` off the order
stream the screen already observes, dropping `CANCELLED`. It counts **per date, not per
market**, because `Order.marketId` is still empty (§1): a day is treated as having orders
whichever stall they belong to, which errs towards refusing, the safe direction. Narrowing
it to the market waits on §1.

What is *not* built is the other half the design asked for: an explicit choice between
cancelling those orders and moving them to the next open slot. Today the seller is told no
and has to resolve the orders by hand first.

Refusing at entry also removes the worst case, where a closure lands after the edit
deadline and the affected buyers cannot move their own orders because `canEdit()` is
already false. Telling buyers about a cancellation still needs the notification engine in
`doc/order-ready-notification-design.md`, which is not built — but with the refusal in
place there is no longer a path that silently cancels on them.

### The reminder worker reads closures too — built

`OrderReminderEvaluator.hasOrderedForUpcomingPickup` derived the upcoming pickup cycle
from the schedule config alone, so on exactly the weeks the seller was away it would nag
buyers to order for a market day that was not going to happen.

Both it and `isDue` now go through `nextOpenPickupCycle`, which skips closed days and
returns null when the whole horizon is shut — then no reminder fires at all. A buyer who
ordered for a day that has since been closed is *not* treated as covered: their order is
for a market that will not happen, so the reminder about the next open day is exactly
what they need.

The worker has no Firebase when it wakes, so closures travel the way `placedCycles`
already does: `OrderReminderStorage.cacheClosedCycles`, written by the buy app whenever
it loads them, scoped to the buyer's contextual market so another stall's closure does
not silence their reminder.

---

## Suggested order of work

1. ~~**Market as an entity** (§1).~~ **Done 2026-10-19**, with both migration paths.
   The profile-list fallback and `--drop-legacy` remain for a later release.
2. ~~**Closures** (§4).~~ **Done 2026-10-19**, including the order-conflict refusal,
   range entry and the reminder worker. One piece of the design remains: offering the
   seller a *choice* between cancelling and rescheduling the orders that block a closure,
   rather than only refusing. That needs buyer notification, so it waits on
   `doc/order-ready-notification-design.md`.
3. **Per-market schedule and the slot picker** (§4). **Partly done 2026-10-19**: a
   market carries its own pickup day and deadline, `PickupSlots.next` merges each
   market's dates into one chronological list, and the basket offers and selects
   slots — picking a date picks the stall it belongs to. Still open: the persisted
   `DraftBasket.selectedPickupDate` is still a bare date rather than a slot, and
   `Order.canEdit`/`getEditDeadline`/`getWindowStatus` still use the app-wide
   schedule rather than the order's own market.
4. ~~**Eligibility + invite scoping** (§3).~~ **Done 2026-10-19.** Rules written and
   tested but **not deployed**: `grantsMarkets`, `allowedMarkets` and the tightened
   `buyer_access_status` write rule are in the repo only.
5. **Pricing conditions and the breakdown UI** (§2).
6. **Production requests.** Last — it depends on both the schedule decision and the
   condition model.

Step 2 is the one to start with. It is small, it needs none of the model changes above,
and it is the only item here that a seller would notice next week.

---

## Still open

### 1. What exactly is a production request?

The reading in §3 — pre-order of unstocked goods, volume commitment, longer lead time, a
better price — is an interpretation and may be wrong. If it instead means the seller
requesting production from a supplier, it is a sourcing feature closer to
`doc/stock-keeping-design.md` and belongs in `seller_articles`, not in the order model at
all. Settle this before anything in §3 is built.

### 2. ~~One basket or one per market?~~ — settled

Answered by the slot model in §4: a basket is pinned to one slot, and a slot names one
market, so there is exactly one basket. A buyer wanting to order at two markets in the
same week places two orders, which is what they are.

`DraftBasket.selectedPickupDate: String?` therefore becomes a slot, and it is persisted
under `buyer_profile`, so it needs the usual read-both-shapes migration.
`BuyAppViewModelInitialization.kt:415` already clears a stored date that no longer
validates; a slot has more ways to go stale — market deleted, market deactivated,
eligibility revoked, **day since closed** — and all of them land in that same path.

### 3. Are prices public per market, or per eligible buyer?

`market_pricing` has to be readable by any signed-in user for the catalog to price itself
before approval — the same openness `articles` already has. That means a buyer can read
the prices of a market they are not eligible for. Probably fine, possibly not if
neighborhood rates are meant to be discreet. If they are not public, pricing has to move
behind the access check and the demo/unapproved catalog needs a fallback price.

### 4. Does `BuyerTerms` exist, or is it premature?

`resolvePrice` takes a `terms` parameter above so that buyer-specific conditions (a
wholesale rate, a long-standing customer) have somewhere to go. Nothing requires it today.
It may be better to drop the parameter and add it when a real case appears, rather than
ship an always-`Standard` argument.

### 5. ~~Orphan markets~~ — settled

Markets are deactivated, not deleted: `Market.active`, flipped by `removeMarket`. The row
survives so `Order.marketId` keeps resolving and historical orders stay readable, and an
inactive market is simply not offered.

### 6. Is the profile-level market multi-select worth v1?

The slot picker could simply list every market the buyer is eligible for, with
`BuyerProfile.defaultMarket` pre-selecting one. That is simpler and fine at two or three
markets; a standing subscription only earns its place when a seller runs five or six
stalls and the picker gets long. Leaning toward skipping it initially.

### 7. How far ahead may a closure be entered, and who sees it?

A closure entered beyond the slot horizon is invisible to buyers until the day comes into
range, which is harmless but means the seller gets no confirmation that it took. A closure
entered for a day inside the horizon silently shortens the buyer's list. Neither is
obviously wrong; the question is whether the buy app should *show* a closed day greyed out
with its reason ("Do 23.10. — Feiertag, kein Markt") rather than omit it. Showing it is
more informative and stops a buyer wondering where their usual day went, at the cost of a
longer list.

### 8. Do closures need to survive the market being deleted?

A closure keyed by `marketId` under a market that is later soft-deleted becomes dead data.
Harmless, but it accumulates, and `ALL` closures are not tied to a market at all. Worth
deciding whether closure cleanup belongs with market deactivation or is left alone.
