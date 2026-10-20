# Markets: Pricing, Access, Scheduling and Communication

**Status:** implemented 2026-10-19 — §1 markets as an entity, §3 eligibility and
production requests, §4 schedules, slots and closures.

**Rules deployed 2026-10-20** to both `fire-one-58ddc` (dev) and
`bodenschaetze-a988e` (prod), after all 188 tests in `firebase/tests` passed against
the emulator. Everything previously held in the repo only — `grantsMarkets`,
`allowedMarkets`, the tightened `buyer_access_status` write rule, `priceConditions`,
`pricingRevision` on markets and on orders, and the rule validating a request's
market — is now live. Nothing in this file is pending deployment.

**§2 pricing** is built except for the explanation UI (2026-10-20). The adjusted price
is resolved, charged and snapshotted onto the order with the revision it came from, and
the basket reprices when it moves market. What is **not** built: the catalog banner and
the per-article breakdown — `PriceBreakdown.kt` exists and nothing renders it — and any
announcement when the seller changes conditions under a placed order. §2a covers the
market pick in demo mode and onboarding. See **§2 status** below.
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

### Built: conditions on the market

**BUILT DIFFERENTLY — stored on the market, not at `market_pricing/{sellerId}/{marketId}`.**
Once §1 made a market a real node there was nowhere better: a condition belongs to the
stall whose costs it describes, the node is already readable by any signed-in user, and
the alternative was a third node mirroring the same relationship with a foreign key. The
market's `$other: false` rule means the two new fields had to be declared, which is how
`priceConditions` and `pricingRevision` get validated per field.

The design below is otherwise what was built.

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

### How those four turned out

**Rounding went the other way from the design.** "Round once at the end, round components
for display" can produce a breakdown that does not add up. Instead every component is
rounded to cents and **the gross is their sum** — so the explanation is arithmetically
true on screen, at a cost of at most a cent against the unrounded total. A breakdown whose
figures disagree with the price is worse than no breakdown.

**Factors are not compounded.** Each is taken against the base and the deltas sum, so a
seller setting 8% and 5% means 13% of the base rather than 13.4%, and — the real reason —
the answer cannot depend on what order the conditions happen to be stored in. A test pins
that reordering changes nothing.

**The floor is zero, not `acquirePrice`.** The purchase price is seller-only data the buy
app never sees, so it cannot be enforced in `resolvePrice`. Zero is.

**`BuyerTerms` was dropped**, settling open question 4: it would have been an
always-`Standard` argument. `resolvePrice(article, pricing)` is the whole signature.

A component that rounds away to nothing is omitted rather than rendered, because
"+0,00 € Transport" explains nothing.

### §2 status, 2026-10-20

| Piece | State |
|-------|-------|
| `resolvePrice` + `priceChanges` engine | **Built**, ~15 tests in `MarketPricingTest` |
| Seller condition editor, `pricingRevision` bump | **Built** (`PriceConditionEditor`, `SellerProfileScreen.kt:889`) |
| Persistence + rules validation | **Built** |
| Market-switch warning before applying | **Built end to end** — `slotPriceChanges` → `PendingSlotPriceChange` → `PriceChangeDialog` → confirm/dismiss |
| Catalog banner + per-article breakdown | **Not wired.** `PriceBreakdown.kt` exists, nothing renders it |
| Adjusted price actually charged | **Built** — both add-to-basket paths use `priceOf()`; `OrderedProduct` carries `basePrice` + `priceComponents` |
| Basket repriced on a market change | **Built** — `BasketRepository.repriceItems`, via `basketScreenApplySlot` |
| `pricingRevision` snapshotted on the order | **Built** — on `Order`, written and read back, validated in the rules |
| Seller price change announced to buyers | **No.** No `SellerEventType` for it; needs the notification engine |
| First-pick dialog suppressed | **Built** — `slotPriceChanges` returns empty from an unset market |
| Transition refuses an order at an ungranted market | **Built** — `DemoTransitionResult.notEligibleMarketIds` |
| Market pick during onboarding | **Built** — `MarketChoiceDialog`, `BuyAppViewModelMarket.kt`; writes `BuyerProfile.defaultMarket` |
| Demo provisional-pricing line | **Built** — `adjustedPricingMarketName` on `DemoOrderNotice` |
| Checkout confirmation of slot and market | **Built** — `CheckoutConfirmDialog`; §4 |

What remains is the *explanation*, not the money: a buyer is charged the market's price
and the order records why, but nothing on screen yet tells them that before checkout
beyond the switch dialog. `PriceBreakdown` is written and waiting for a caller.

A legacy order — written before `basePrice` existed — reads back with `basePrice = 0.0`,
which `OrderedProduct.basePriceOrPrice` resolves to the charged price. It reads as
unadjusted, never as free; `OrderPriceSnapshotTest` pins that.

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

### 2a. The market pick in demo mode and onboarding

**Status:** **built** 2026-10-20 — the onboarding pick, both guards, and the
provisional-pricing line.

Prices follow the market, so *when* the buyer names a market decides which prices they
ever see. Today that happens late — at the slot picker, on the way to checkout — and
three things follow from it.

**Demo does not restrict the market.** `eligibleMarkets` falls back to every seller
market when `allowedMarkets` is empty, and an unapproved buyer has no access record, so
their scope is empty and they may browse and order at any stall. "Empty means all" is
right and has to stay — it is what keeps records written before market scoping meaning
what they meant — but the consequence is that demo mode is not a narrower world, only an
unapproved one.

**The buyer's demo browsing decides their eligibility, and nothing says so.** The access
request carries `contextualMarketId` (`BuyAppViewModelAccess.kt:134`), and
`approveRequest` grants exactly that, defaulting to empty — anywhere — when the request
named no market. So a demo buyer who never opened the slot picker asks to collect
anywhere and is approved for everywhere; one who picked Neukölln asks for Neukölln and
gets Neukölln. The most consequential field in the request is filled in as a side effect
of something the buyer did for another reason.

**`BuyerProfile.defaultMarket` had no write path.** It was read
(`BuyAppState.contextualMarketId`) and persisted (`GitLiveProfileRepository.kt:663`), and
nothing in either app ever set it — the middle rung of the resolution ladder was dead.
The onboarding pick below is now its one writer.

#### The shape: ask once, early — built

`MarketChoiceDialog`, dispatched through `BuySellerAction.ChooseDefaultMarket` and
handled in `BuyAppViewModelMarket.kt`. One question — *where do you collect?* — asked
before the buyer has a basket worth anything, which:

- writes `BuyerProfile.defaultMarket`, giving that field the write path it lacks;
- fills the access request's `marketId` from a deliberate answer instead of a side
  effect;
- makes the market the buyer browsed, the prices they saw, the market they requested and
  the market they are granted the same market **by construction**, so the demo→production
  transition has nothing to contradict.

A single-market seller has nothing to ask: `contextualMarketId` already resolves to "the
only market there is", and the step collapses to the checkout confirmation §4 describes.
`BuyAppState.needsMarketChoice` is what decides, and it is false for one market, for a
seller whose markets have not loaded, and once the question is answered or deferred — so
this is silent for every seller today.

Four things the implementation settles that the sketch above did not:

- **Asked at the scaffold, not on a screen.** The answer changes prices on the catalogue
  and in the basket alike, so the question has to reach the buyer wherever onboarding left
  them rather than waiting for a visit to the profile.
- **Dismissal defers, it does not waive.** `marketChoiceDeferred` is not persisted, so the
  question returns next launch. It is a prompt, never a wall: until it is answered the
  earlier behaviour stands, and the first slot pick settles the market.
- **A stated default is re-checked against `eligibleMarkets`.** The seller can narrow a
  buyer's scope after they answered, and `chosenDefaultMarket` returns null for a market
  they may no longer use rather than pricing the catalogue against an unreachable stall.
- **Answering reprices the basket, unless the basket already names a market.** A slot the
  buyer picked is the stronger claim — the resolution order `contextualMarketId` uses — so
  repricing to a freshly stated default would quietly overrule a choice they made.

Changing the answer later: the profile's **Abholort** card, shown only when there is more
than one market. It is placed above the access card deliberately, because the market is
what the access request asks for.

#### Demo prices are the chosen market's, marked provisional — built

Not base prices. A demo order priced without the market's conditions sets an expectation
that the real order breaks, which is the same damage as silently recalculating a basket,
only deferred past the point where the buyer could have objected. And not "no prices until
approved" either — the catalog has to be browsable to be worth approving for.

So: the real prices of the chosen market, with the provisional state said out loud. The
line sits on `DemoOrderNotice` in the basket, next to the existing *"Demo-Bestellung —
unverbindlich"*, and reads *"Preise für <Markt>"*.

`BuyAppState.adjustedPricingMarketName` is empty for a market with no conditions, so the
line appears only once a seller actually prices one stall differently — naming a market
that changes nothing is noise. The dialog itself carries the same note before the choice
is made, since what the buyer is picking is which real prices they will see.

#### Guard 1: the first pick is not a change — built

`slotPriceChanges` returns empty when `selectedMarketId` is still empty. Moving from no
market to a real one does shift every number, but there is no total the buyer committed
to, and a dialog reading *"4 articles change price"* before they have chosen anything
warns them about something they did not do. The prices still move —
`basketScreenApplySlot` reprices whenever the market changes, dialog or no dialog. A
second pick, market to market, asks as before.

#### Guard 2: the transition refuses rather than moves — built

`transitionDemoOrdersToProduction` now takes the granted markets and leaves behind any
order naming one outside them, reporting it as
`DemoTransitionResult.notEligibleMarketIds`.

An order carries the price it was placed at, and that price came from a market the buyer
turns out not to have. Moving it charges one stall's prices for a stall they cannot reach;
repricing it silently changes a total they already confirmed. Neither is the transition's
call, so the order stays in `demo_orders/`, the local demo orders are **not** cleared, and
the buyer is told which market was refused. This is the same discipline that already drops
an order whose pickup has passed, applied to a different way of being unmovable.

Two details worth keeping:

- The grant is read fresh with `ProfileRepository.getAllowedMarkets`, not taken from
  state. The status and the scope arrive on two separate flows and the transition fires
  the moment the status turns APPROVED, which can be before the scope has caught up.
  Reading state there would let an order through at a market that was just narrowed away.
- An order with an **empty** `marketId` is never refused. That is every order from a
  single-market seller and every order written before markets were an entity; refusing
  them would strand exactly the orders this guard is not about.

#### Still open

A buyer may change their stated market freely, and that is right — the grant is the
seller's, so changing a default narrows or widens nothing by itself. What is **not**
handled: an approved buyer who switches their default to a market outside their grant
is not told that ordering there will be refused. `chosenDefaultMarket` filters it out of
`eligibleMarkets`, so they cannot pick it at all today; the gap only opens if that filter
is ever relaxed.

`BuyerProfile.defaultMarket` now has exactly one writer. If a second appears, the
re-pricing in `chooseDefaultMarket` has to move with it.

### Orders snapshot, never reference

`OrderedProduct.price` already stores a number, which is right. Extend it with the
resolved components and store `MarketPricing.revision` on the `Order`. Without that:

- re-opening a two-week-old order recomputes it against today's conditions,
- `/sales/{sellerId}/{yyyyMM}/{saleId}` and the order it came from stop reconciling,
- and the CSV tax export reports figures that were never charged.

This matters more than the pricing model itself. The same discipline already governs the
demo-order transition, which refuses to move an order whose pickup has passed for exactly
this reason (`doc/guides/demo-order-provenance.md`).

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

### Production requests — and what they actually are

**This section used to describe something else entirely.** It read a production request
as a buyer committing to volume on unstocked goods for a better price, and designed a
`VOLUME_COMMITMENT` condition around it. That was a guess, flagged at the time as the
least certain part of this document, and it was wrong.

A **production request is a buyer asking to be able to place real orders** — to stop
being a demo buyer. "Production" is `OrderOrigin.PRODUCTION`: a real order rather than a
demo one. It is the access request flow, and it has existed since well before this
document:

| | |
|---|---|
| Buyer asks | `BuySellerAction.RequestAccess` → `submitAccessRequest` → `access_requests/{sellerId}/{buyerId}`, status `PENDING` |
| Seller sees it | `AccessRequestsCard` |
| Seller approves | `approveAccessRequest` → `APPROVED`, request removed |
| Effect | orders go to `/orders` as `PRODUCTION`, and existing demo orders transition — `doc/guides/demo-order-provenance.md` |

### A request is bound to a market

What asking the question *did* find was a gap this document opened. A buyer asks to
collect **at a stall** — usually the one they are standing in front of — so
`AccessRequest.marketId` records it and approving grants exactly that market. Before this,
an invite link could be scoped to one stall but approving a request silently admitted the
buyer to every market: two routes to the same `APPROVED` state that granted different
things.

The seller can still override the scope when approving, to widen or narrow the answer.
A request naming no market asks to order anywhere, which is what a single-market seller's
buyers mean and what every request written before this one meant. The rules reject a
request naming a market that does not exist, so an approval cannot grant a dangling id.

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

### One slot per market, confirmed at checkout — slot count built 2026-10-19

The list is **one row per eligible market — its next open date — and nothing further**,
not five dates each. `doc/guides/ordering-business-rules.md` §2 has the reasoning: the seller
cannot promise a catalogue or a price weeks out, and the order snapshots both.

What that does to this section:

- **Built.** `PickupSlots.nextPerMarket` offers each market its next open day and
  nothing beyond it; `next(count)` keeps its old meaning for any caller that wants a
  run of dates, and takes a `perMarket` flag so the merged list is not truncated when
  every market is to keep its one. A market whose next day is closed contributes its
  following open day rather than dropping out of the list. The basket calls it at
  `BuyAppViewModelBasket.kt:1157`, with the no-markets fallback asking for one date.
- **With one market there is nothing to pick**, so the step becomes a *confirmation at
  checkout* rather than a picker. **Built 2026-10-20** as `CheckoutConfirmDialog`:
  pressing Bestellen validates and opens it instead of writing, and
  `BuyBasketScreenAction.ConfirmCheckout` is what writes.

  It shows the pickup date with its weekday, the total, the edit deadline and the time
  left — `formatTimeUntilDeadline` and `getDeadlineWarningLevel`, which §4 noted existed
  and went unused at exactly this moment, now colour the remaining time by urgency. The
  market is named only when the seller runs more than one, since with a single stall the
  row says nothing the buyer does not know. One line for demo buyers.

  The deadline goes through `OrderDateUtils.scheduleConfigFor(market)`, which **comes
  out identical to the app-wide schedule for every market today** — see the correction
  under step 3 below. It is threaded through because it is then right by construction
  rather than by coincidence, not because it fixes a wrong figure.

  *Ändern* reopens the slot picker rather than offering a second control: the picker is
  the only place date and market move together, so changing either from the confirmation
  goes through the same price-change warning as changing it earlier would. It closes the
  confirmation on the way, so that warning cannot open underneath a dialog still showing
  the old slot.

  Only two things are checked before the dialog is shown — an empty basket, and a
  missing date, which opens the picker instead. The deadline and the date's availability
  are deliberately re-checked at the moment of writing, because the confirmation can sit
  open across a deadline.
- Edit mode gets no confirmation — the date is fixed by the order being edited — and
  shows the deadline inline on the basket instead.
- `basketScreen.availablePickupDates: List<Long>` plus a confirmed flag is the open part
  of the state shape: a single confirmed slot was the Thursday answer, but multi-market
  needs N rows, so this needs settling before the UI is written.

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
`doc/design/order-ready-notification-design.md`, which is not built — but with the refusal in
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
   `doc/design/order-ready-notification-design.md`.
3. **Per-market schedule and the slot picker** (§4). **Partly done 2026-10-19**: a
   market carries its own pickup day and deadline, `PickupSlots.next` merges each
   market's dates into one chronological list, and the basket offers and selects
   slots — picking a date picks the stall it belongs to. The checkout confirmation
   followed on 2026-10-20.

   Still open: the persisted `DraftBasket.selectedPickupDate` is a bare date rather
   than a slot, so a saved basket remembers *when* but not *where*.

   **Corrected 2026-10-20.** This item used to also list
   `Order.canEdit`/`getEditDeadline`/`getWindowStatus` as "still using the app-wide
   schedule rather than the order's own market". They do pass no config — and it makes
   no difference to any figure. `calculateEditDeadline` reads only the *lead distance*
   out of a config, `daysBetween(deadlineDay, pickupDay)`, and subtracts it from the
   real pickup date; `Market.scheduleConfig` is built to preserve that distance and the
   cut-off time. Verified: a Saturday pickup yields Thursday 23:59 Berlin from either
   config, a Monday pickup yields Saturday 23:59 from either. **The deadline is already
   market-correct everywhere.**

   It stops being equivalent only if a market gains its own lead time or cut-off hour.
   That is the trigger to thread the config through the 14 call sites, and not before:
   doing it now would be 14 edits that change no behaviour.
4. ~~**Eligibility + invite scoping** (§3).~~ **Done 2026-10-19**, rules deployed
   2026-10-20: `grantsMarkets`, `allowedMarkets` and the tightened
   `buyer_access_status` write rule are live on dev and prod.
5. ~~**Pricing conditions** (§2).~~ **Done 2026-10-19**, rules deployed 2026-10-20:
   `priceConditions` and `pricingRevision` are live. This was the urgent one — the
   market rule rejects unknown fields, so until the deploy a seller editing price
   conditions was having the write refused. The **breakdown UI** is still not built;
   see the §2 status table.
6. ~~**Production requests.**~~ Not a feature: it is the access request flow, which was
   already built. Binding a request to a market, and approving what was asked for rather
   than everything, was done 2026-10-19. Rules deployed 2026-10-20.

Steps 1, 2, 4, 5 and 6 are done, and 3 is down to one item — the draft basket
remembering its slot rather than only its date. *(The line that used to stand here, "step
2 is the one to start with", predates step 2 being finished on 2026-10-19.)*

The next thing in this area is not in this list: pinning the pickup, deadline and
date-key arithmetic to the seller's zone. See `doc/guides/ordering-business-rules.md`
§7.3 — it is decided, unimplemented, and the cause of manual-test-plan E1/E2.

---

## Still open

### 1. ~~What exactly is a production request?~~ — settled

A buyer asking to be able to place real orders: the access request flow, which already
existed. Both readings offered here — volume commitment, and seller-to-supplier sourcing —
were wrong. See §3.

### 2. ~~One basket or one per market?~~ — settled

Answered by the slot model in §4: a basket is pinned to one slot, and a slot names one
market, so there is exactly one basket. A buyer wanting to order at two markets in the
same week places two orders, which is what they are.

`DraftBasket.selectedPickupDate: String?` therefore becomes a slot, and it is persisted
under `buyer_profile`, so it needs the usual read-both-shapes migration.
`BuyAppViewModelInitialization.kt:415` already clears a stored date that no longer
validates; a slot has more ways to go stale — market deleted, market deactivated,
eligibility revoked, **day since closed** — and all of them land in that same path.

### 3. ~~Are prices public per market, or per eligible buyer?~~ — settled

**Public.** Decided 2026-10-20. Conditions live on the market row, which every signed-in
user can already read — the same openness `articles` has — so a buyer can see the prices
of a market they are not eligible for, and that is accepted. A discreet neighborhood rate
is not a goal worth moving pricing behind the access check for: it would leave the demo
and unapproved catalog needing a fallback price, which is a second price for the same
article and the start of the drift this design exists to avoid.

This is what makes §2a below possible: a demo buyer can be shown the real prices of the
market they chose, rather than a placeholder that the real order would then contradict.

### 4. ~~Does `BuyerTerms` exist, or is it premature?~~ — settled

Dropped. It would have been an always-`Standard` argument; the signature is
`resolvePrice(article, pricing)`. Buyer-specific rates can be added when a real case
appears.

### 5. ~~Orphan markets~~ — settled

Markets are deactivated, not deleted: `Market.active`, flipped by `removeMarket`. The row
survives so `Order.marketId` keeps resolving and historical orders stay readable, and an
inactive market is simply not offered.

### 6. Is the profile-level market multi-select worth v1?

The slot picker could simply list every market the buyer is eligible for, with
`BuyerProfile.defaultMarket` pre-selecting one. That is simpler and fine at two or three
markets; a standing subscription only earns its place when a seller runs five or six
stalls and the picker gets long. Leaning toward skipping it initially.

### 7. ~~How far ahead may a closure be entered, and who sees it?~~ — settled

A closure entered beyond the slot horizon is invisible to buyers until the day comes into
range, which is harmless but means the seller gets no confirmation that it took.

**The buy app shows a closed day with its reason** ("Do 23.10. — Feiertag, kein Markt")
rather than omitting it. Settled by the one-slot-per-market decision above: when five
dates were offered, omitting one was invisible and the shorter list cost nothing. With a
single slot per market, omitting it leaves the buyer looking at either nothing at all or
a date a week later than they expected, with no stated reason — and no second date to
fall back on. The reason has to be on screen.

The cost that argued against showing it — a longer list — no longer exists, because the
list is one row per market either way.

Not built: `MarketClosure.reason` is stored and the seller enters it, but nothing in the
buy app renders it.

### 8. Do closures need to survive the market being deleted?

A closure keyed by `marketId` under a market that is later soft-deleted becomes dead data.
Harmless, but it accumulates, and `ALL` closures are not tied to a market at all. Worth
deciding whether closure cleanup belongs with market deactivation or is left alone.
