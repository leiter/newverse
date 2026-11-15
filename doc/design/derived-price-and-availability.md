# Derived Price and Availability — Where the Public Catalogue Comes From

> **PROPOSAL — §4 and §5 are not built** (as of 2026-11-15). §3 options (1) and (3)
> **are** built, and are marked DONE in place; sections that say "today" describe code
> in `main` and are verified against it.
> Two questions need the seller's answer before any of it is worth planning;
> they are at the end, under "What needs deciding".

**Status:** Partly built. The disclosure boundary (§2) is decided — it is a constraint,
not a preference. The propagation fix within reach (§3.1) and its documented bound
(§3.3) shipped 2026-11-15. The price direction (§4) and the below-cost check (§5) are
open.
**Date:** 2026-11-15
**Scope:** `/articles` vs `/seller_articles` vs `/stock`; who computes `price` and
`available`, where, and how a change reaches a buyer's screen.
**Occasion:** The production catalogue now has cost data for 92 of 104 articles
(`firebase/scripts/sync-bnn-costs.mjs`, applied 2026-11-14), so margins are visible
for the first time and repricing is the next job. Before repricing, the question was
raised whether `price` and `available` should be fields in `/articles` at all, or
derived from `/seller_articles` and `/stock`.

---

## 1. What is true today

Both fields are *already* computed rather than merely stored. The difference between
them is which side of the fence does the computing.

### `available` is already derived, and published

`StockAvailability.kt:36`:

```kotlin
fun SellerArticle.derivedAvailability(level: StockLevel?, demand: ArticleDemand?) =
    sellerAvailable && stockPermitsOrders(level, demand)
```

`/articles/{seller}/{id}.available` is the **output** of that function, not an input.
`SellerArticle.kt:81` warns in as many words never to read `sellerAvailable`
directly. `scarcity` works the same way. The seller edits intent
(`SellerArticleData.sellerAvailable`) and a gate (`stockGated`); the shelf narrows it;
the result is written to the public node by `publicationCorrections` →
`SellerArticleRepository.setArticlePublication`.

`CreateProductViewModel.kt:426` keeps the form out of that field's way: a gated
article's public flag is left at whatever is stored, and only *switching off*
is published straight away, because narrowing is always safe.

### `price` is authored, and the margin is derived from it

The opposite direction. `ProductPricing` holds the two inverse functions:

```
sellPrice    = acquirePrice × markupFactor × (1 + taxRate)
markupFactor = sellPrice / (acquirePrice × (1 + taxRate))
```

and the seller form latches on the first of them only while the price field is still
empty. `CreateProductViewModel.kt:276` states the rule: **the selling price leads.**

| Field edited | Effect |
|---|---|
| `price` | markup re-derives; the price stands |
| `markupPercent` | price re-derives |
| `acquirePrice` | markup re-derives if a price exists, else the price does |
| `taxRate` | same as `acquirePrice` |

So on an existing article a new purchase price moves the **margin**, never the price
tag. `sync-bnn-costs.mjs` obeys the same invariant deliberately — it refuses to write
`price` and recomputes `markupFactor` instead — which is why the 2026-11-14 production
run changed 92 margins and not one shelf price.

### The per-market price is resolved live, from stored causes

`resolvePrice(article, market.pricing)` (`MarketPricing.kt:131`) takes
`/articles.price` as the base and applies the market's `PriceCondition`s at read time.
What is stored on the market is the **cause** (`STALL_FEE`, `TRANSPORT`, `HANDLING`,
`NEIGHBORHOOD_RATE`, `VOLUME_COMMITMENT`), its scope and its effect — never the
resulting number, so the breakdown the buyer reads cannot drift from the total it
explains. `state.priceOf(article)` recomputes on every market change; `pricedWith`
snapshots gross, base and components onto the order; `pricingRevision` records which
conditions it was placed against.

**So per-market pricing already works the way the original question asked for.** It is
computed, not stored, and it propagates the moment the seller changes a condition.

---

## 2. The buy app cannot read either input — decided

The proposal's literal form — the buy app computing `price` and `available` from
`/seller_articles` and `/stock` — is not available at any price. Checked against
`firebase/database.rules.json`:

| Node | `.read` |
|---|---|
| `/articles/$sellerId` | `auth != null` — **every signed-in user** |
| `/seller_articles/$sellerId` | `auth.uid === $sellerId` |
| `/stock/$sellerId` | `auth.uid === $sellerId` |

Anyone can create an account, including a competitor at the next stall. To compute
`price` a buyer's device needs `acquirePrice` and `markupFactor` — that publishes the
cost base and the margin on all 104 articles. To compute `available` it needs `onHand`
plus committed demand — that publishes the order book. Neither is a refactor; both are
disclosures, and irreversible ones, because what has been readable has been read.

This is the one firm conclusion in this document. **Buyer-side derivation is out.**
Writes to `/articles` from a buyer's device are out for the same reason.

What remains, and what both fields should be understood as, is a **published
projection**: `/articles` holds the result of a computation over private data, carrying
nothing a buyer may not see, cheap for a buyer to subscribe to. That is already an
honest description of `available` and `scarcity`. It is not yet true of `price`.

---

## 3. The real defect: the projection has no writer when no seller device is awake

`correctAvailability()` lives in `StockViewModel` (`StockViewModel.kt:143`). Its own
comment names why — only the seller's device may write `/articles`, and stock only
moves when that app writes a movement — and then names the exception that undoes it:

> Demand is the exception — a buyer ordering raises it while the app is closed.

The pass runs on every catalogue, level and order emission **of that one screen's
collector**. So:

- a buyer orders the last two kilos of a gated article at 21:00;
- committed demand rises, `stockPermitsOrders` would now return false;
- nobody computes that, because no seller device has the stock screen open;
- `/articles.available` stays `true` until the seller next opens that screen,
  and further buyers order against a shelf that is gone.

This is the thing to fix. It is also the reason a *derived* `price` cannot simply be
dropped into the same place: it would inherit the same hole, and a stale price is worse
than a stale availability — it is a number the buyer was quoted.

### Three ways out

**(1) Lift the pass out of the screen. — DONE 2026-11-15.** `correctAvailability` left
`StockViewModel` and became `AvailabilityPublisher`: a `single` in the sell Koin module,
started by `SellAppViewModel` and stopped in its `onCleared`, following
`observeAuthState()` rather than one `getCurrentUserId()` at construction. It runs
whenever the seller app is alive rather than when one screen is, and is now the only
writer of the derived fields. Thirteen tests in `AvailabilityPublisherTest` — the eight
that moved with it, plus five about being app-scoped.

A ViewModel was chosen over a foreground service, but not for kill-resistance: a
service is in fact *more* survivable, and a ViewModel dies with the process like any
singleton. A service is Android-only, where this is KMP and iOS would get nothing; it
needs a notification; and it computes nothing while the app is closed either, so it buys
no propagation. The choice bought simplicity and platform-neutrality, and the bound in
(3) is unchanged by it.

**(2) Server-side projection.** A trusted process triggered on `/orders` and
`/stock_movements` writes recomputes `available`, `scarcity` and — if §4 goes that way
— `price`, and writes the public half. This is the only option that propagates with no
seller device involved, and the only one on which a strictly derived `price` can be
honest. The trusted side may legitimately read both halves, so no rule has to be
loosened.

Cost: there is **no Cloud Functions setup in this project at all** — no `firebase.json`,
no functions directory, no deploy path, no emulator wiring for it. This is a project,
not an afternoon, and it adds a second place where catalogue logic lives, which has to
be kept honest against `StockAvailability.kt` or it will drift.

**(3) Accept the ceiling and write it down. — DONE 2026-11-15.** The bound is now a
"Known bound" block in `doc/guides/stock-driven-availability-design.md` § "`available`
is derived, not edited": availability is eventually consistent with the shelf, bounded
by the next time the seller app runs. For a market stall whose seller opens the app
daily that may well be the right trade; what was not acceptable was leaving it
implicit.

---

## 4. Which way should `price` flow?

Two coherent designs. They differ in what happens on an existing article when a cost
changes.

### (A) `price` becomes a projection

`/articles.price` gets exactly one writer, like `available` already has, computed as
`acquirePrice × markupFactor × (1 + taxRate)`. The seller's form edits cost and markup;
the price tag is an output. Hand-editing `/articles.price` stops being a thing.

What it buys: one rule for the whole public node — everything in `/articles` is derived
from `/seller_articles` and `/stock`, nothing in it is authored. That is a genuinely
simpler thing to reason about, and it is what the original question was reaching for.

What it costs, unless guarded: next week's BNN file moves every shelf price. A
wholesaler's list becomes the price-setter, prices change under buyers between opening
the app and checking out, and `derivePrice()` picks the endings — 2,53 €, which is not a
number a market stall shouts. `WinterCatalogJsonTest`'s header makes this argument at
length and it is the right argument.

### (B) `price` stays the decision

Today's design. The seller types the price tag; `markupFactor` is derived to match;
a cost change moves the margin. The price is a number a person chose and can defend.

### Recommendation: A, with B's guard

They are reconcilable, and the reconciliation is already written and running:

> **`price` is derived and has one writer. When a cost changes, `markupFactor`
> absorbs it and the price holds, until the seller decides otherwise.**

Which is exactly `deriveMarkup()` in the form (`CreateProductViewModel.kt:321`) and
exactly what `sync-bnn-costs.mjs` does to 92 articles without touching a price. Under
this rule a derived `price` is stable by construction: the only thing that moves it is
a seller's decision, expressed as a new price or a new markup. Repricing stays an
explicit act, which — given that it is the next job — is the property that matters most.

The change from today is therefore smaller than it sounds. It is not "compute the price
from cost"; that is already how the numbers relate. It is **"make `/articles.price` a
field with one writer and no hand-editing"**, so that the public node has a single
discipline and a projection pass (§3) can own all of it.

### What this does *not* mean: per-market markups

Folding the market adjustment into the markup — a markup per market, resolved per
market — would drop the breakdown. `PriceCondition` stores causes rather than numbers
precisely so the buyer can be told *why* the price differs at one stall, and
`ResolvedPrice` guarantees that base plus components equals gross exactly. Keep
**base + conditions**. The base may become derived; the overlay should not become
arithmetic.

---

## 5. A gap worth closing either way

`pricingBelowCost` (`PricingFloor.kt:47`) is the only below-cost check in the app, and
it runs in **`PriceConditionEditor`** (`:102`) — the market editor. Nothing checks the
floor where the price is actually set, in `CreateProductViewModel`'s validation.

So a price typed under cost warns nobody, and a cost that rises past a price warns
nobody either. Production has four articles in exactly that state as of 2026-11-14:
Paprika Rot, Spitzpaprika and Mango below cost outright, and Spinat at a 1.13 ×
factor — selling at cost once VAT is paid. None of them warned anyone, because none of
them went through a market's price conditions.

The same check belongs in the article form, against the article's own `acquirePrice`.
It is independent of §3 and §4, it is small, and it is the only item here that would
have caught a live mistake.

---

## 6. What needs deciding

Only two answers are needed, and neither blocks the catalogue work:

1. **Price direction.** A-with-guard (recommended), or B (keep today's). This decides
   whether the repricing job ahead edits `price` or edits `markupFactor`.
2. **Are we prepared to add Cloud Functions?** If yes, §3(2) is the propagation answer
   and a derived `price` is safe. If no, §3(1) is the ceiling, and that bound gets
   written into the availability guide rather than left implicit.

Neither affects the cost data already in production: `price` was deliberately never
written, so every option is still open on the live catalogue.

## See also

- `doc/guides/stock-driven-availability-design.md` — the derivation and the refill engine
- `doc/guides/market-pricing-and-access-design.md` — causes, scopes, the access model
- `doc/guides/ordering-business-rules.md` — snapshotting onto orders, `pricingRevision`
- `firebase/scripts/sync-bnn-costs.mjs` — the header argues the same invariant from the
  import side
