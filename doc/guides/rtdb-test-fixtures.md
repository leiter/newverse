# RTDB Test Fixtures

**Status:** current — generated and verified on 2026-10-22
**Scope:** `firebase/fixtures/` (three fixtures, a generator, a validator)

Three Realtime Database snapshots you can import at the database root to put the app
into a known state, plus the test cases each one is built to exercise. One seller,
three buyers, one catalogue, three stalls — the same identities in all three, so you
can switch fixtures without re-registering anything.

| File | Fixture | What it is for |
|------|---------|----------------|
| `firebase/fixtures/fixture-1-cold-start.json` | `cold-start` | A shop with a catalogue and three buyers and nothing ordered yet. First-run, onboarding, access gating. |
| `firebase/fixtures/fixture-2-busy-week.json` | `busy-week` | A running week: order history, one editable order, one locked one, per-market pricing, the stock ledger, a booked sale. |
| `firebase/fixtures/fixture-3-legacy-edges.json` | `legacy-edges` | Records written by older app versions, plus the awkward cases: blocked buyer, deactivated market, delivery without an address, missing private article half. |

## Identities

| Role | UID | Who |
|------|-----|-----|
| Seller | `cPkcZSiF3LMXjWoqW6AqpA9paoO2` | BodenSchätze (Eric Dehn) |
| Buyer 1 | `bcQHALrRkuSljXKdO6sxhHlnhNv2` | Anna Berger — approved, complete profile, the order history |
| Buyer 2 | `mX4gbxB5wuNUmfj9U4PWhO7BOJD3` | unnamed — not approved, carries the stored placeholder `Bodenschatz`, orders land in `/demo_orders` |
| Buyer 3 | `jOSoP5UxAohZDtdpluhHg6VrfvQ2` | Tobias Krenz — approved and scoped to specific markets; **BLOCKED in fixture 3** |

These are Firebase Auth uids, and the fixtures do not create auth accounts — only
database records. Each uid needs an existing Auth user (any sign-in method) or the
app will create a different one and read an empty profile. Note that
`jOSoP5UxAohZDtdpluhHg6VrfvQ2` appears as a *seller* in the older dump
`firebase/fire-one-58ddc-export(1).json`; here it is a buyer, which is why you should
import a fixture at the root rather than merging it into an existing tree.

## Markets

| Id | Name | Day | Notes |
|----|------|-----|-------|
| `mkt-thu-onkel-tom` | Wochenmarkt Onkel Toms Hütte | Thursday | the ordinary case; **the only stall that delivers**, matching the dev database |
| `mkt-sat-kranoldplatz` | Markt am Kranoldplatz | Saturday | `priceConditions`: ×1.10 stall fee on everything, +0.20 € transport on `Bananen`; `pricingRevision` 3; **pickup only** |
| `mkt-mon-breitenbachplatz` | Breitenbachplatz | Monday | inactive in fixture 3; **pickup only** |
| `mkt-popup-hofladen` | Hofladen-Abholung (Pop-up) | **tomorrow** | fixtures 2 and 3 only; its day is whatever tomorrow is, so a locked order is always genuinely locked; delivers |

`dayIndex` counts Monday as 0 — the order the market dialog lists the days in. The
live databases stored Sunday=1..Saturday=7 until the migration repaired them, so a
hand-edited fixture copied from an old dump will put every market on the wrong day.

## Uploading one

Firebase console → Realtime Database → ⋮ → **Import JSON**, at the **root**.

> Importing at the root **replaces the whole database**. Export what is there first
> if you care about it. Each fixture is a complete tree, not a patch.

The console import runs as an admin and bypasses `database.rules.json`, which is how
fixture 3 can contain records the current rules would refuse to write — that is the
point of it. If you upload with the Admin SDK instead, `ref("/").set(json)` has the
same effect.

Each file carries a `_test` node at the top with the seller, the buyers, the markets,
the computed date keys and the full list of test cases. Nothing in the app reads it
and the rules deny it, so it cannot be mistaken for data the app owns — it is there
so a fixture explains itself when you open it in the console six weeks from now.

## Regenerating

Dates are relative. `fixture-2` needs one order that is still editable and one whose
deadline has passed, which only holds for the week it was generated in. Regenerate
before a test session:

```bash
python3 firebase/fixtures/generate_fixtures.py              # around today
python3 firebase/fixtures/generate_fixtures.py --today 2026-12-24
python3 firebase/fixtures/validate_fixtures.py              # before uploading
```

The generator derives every order status from its pickup date and the edit deadline
rather than asserting one, so a fixture can never claim a lock the app would not
compute. The validator checks the things that look fine in the console and then
behave oddly in the app: an order pointing at a market that is not there, a pickup
date that is not the market's weekday, a `placedOrderIds` entry under the wrong date
key, a stock level that does not add up to its ledger, a sale with no `sale_index`
entry, an `allowedMarkets` string not in `MarketScope.encode` order.

Field names in the generator were taken from the code that reads them
(`GitLiveOrderRepository`, `GitLiveMarketRepository`, `GitLiveProfileRepository`,
`ArticleNodes`, `StockNodes`, `SaleNodes`) and from `firebase/database.rules.json`.
If a field is renamed in the app, change it in the generator and regenerate rather
than hand-editing the JSON.

---

# Test cases

## Fixture 1 — `cold-start`

Nothing has been ordered. `/orders`, `/demo_orders`, `/stock` and `/sales` are absent
entirely, which is also worth testing: an empty node is not the same shape as a node
that is not there.

| Id | Case | Expected |
|----|------|----------|
| F1-01 | First run as Anna Berger (approved, empty history) | Catalogue shows 8 articles, history is empty, and the slots offered are one per market — Mon/Thu/Sat, the next open one each. |
| F1-02 | Tobias Krenz has no `defaultMarket` | The scaffold asks which market he collects at **once**, writes `buyer_profile/defaultMarket`, and never asks again. |
| F1-03 | The unnamed buyer places an order | The seller's list shows `Bodenschatz`, not a blank — the placeholder is stored on the profile, so it travels into the order. |
| F1-04 | The pending buyer orders | Written to `/demo_orders`, `origin: DEMO`, and absent from the seller's `/orders` subscription. |
| F1-05 | Anna's `allowedMarkets` is `""` | All three markets offered. Empty means *all of them*, not none — `MarketScope.decode`. |
| F1-06 | One published seller button | `Hofladen` in the home top bar, opening `https://bodenschaetze.example/hofladen`. The stored `value` carries no scheme; the rules refuse a `:`. |
| F1-07 | Erdbeeren are `stockGated` with no ledger at all | `available` stays true and the band stays `UNKNOWN`. An `onHand` of 0 on a never-counted article means nothing — contrast F3-08. |

## Fixture 2 — `busy-week`

Anna has three collected Thursdays, one order open for the next one, and two
favourites. Tobias is scoped to the Saturday stall and the pop-up, and has an order
at each. The pending buyer has two demo orders.

| Id | Case | Expected |
|----|------|----------|
| F2-01 | Anna's next-Thursday order vs. Tobias' pop-up order tomorrow | Anna's is editable (deadline is two days before pickup, 23:59); Tobias' is `LOCKED` and read-only while its pickup is still in the future. |
| F2-02 | Anna's order history | Three `COMPLETED` orders plus the open one, resolved through `buyer_profile.placedOrderIds` (`yyyyMMdd` → orderId). |
| F2-03 | Reorder the oldest order | A `DRAFT` with a new id, no pickup date, today's `createdDate`; the original is untouched and prices are re-checked against the catalogue. |
| F2-04 | The Saturday order's price breakdown | Feigenbananen 4,84 € = base 4,20 € + 0,20 € transport + 0,44 € stall fee, with `pricingRevision` 3 on the order. Editing the market's conditions must **not** move these numbers. |
| F2-05 | Tobias' market scope | Only Kranoldplatz and the pop-up are offered; Thursday and Monday are not. |
| F2-06 | Closures | The Thursday four weeks out ("Feiertag", seller-wide) is absent for every market; the Monday two weeks out is absent for Breitenbachplatz only. |
| F2-07 | Scarcity band, Erdbeeren | `onHand` 10 Schale, 3 committed by the open order, `reorderLevel` 6 → remaining 7 reads `PLENTIFUL`. Book 5 more and it must fall to `LIMITED`, then `LAST_FEW`. |
| F2-08 | The ledger vs. the cached level | Kartoffeln: intake 80 kg, then a stocktake to 76,4 kg. The level reads 76,4 and `lastCountedAt` is set — a stocktake is absolute, not a delta. |
| F2-09 | The booked sale | One sale for `ord-f2-hist-3`, two lines, found via `sale_index/{seller}/{orderId}` → `yyyyMM`. Its `SALE` movement carries `saleId`; the 6 Schale sold over the counter carry none. |
| F2-10 | Three audiences | Anna and Tobias (approved) see `Hofladen` plus `Vorbestellung` in the profile bar; the pending buyer sees `Hofladen` plus `Erst freischalten`. |
| F2-12 | A pickup-only market offers no delivery | Kranoldplatz and Breitenbachplatz read *nur Abholung* in the slot list; their checkout states *Dieser Markt liefert nicht* instead of the two chips, and the order is written as PICKUP even though `isSelfPickup` is false. |
| F2-13 | Switching to a pickup-only slot | The basket converts to a pickup and says so once. Switching back does not silently restore delivery. |
| F2-11 | Demo orders in the seller app | The pending buyer's two `/demo_orders` entries do not appear. The seller subscribes to `/orders` only — the path is the boundary, not the flag. |

## Fixture 3 — `legacy-edges`

Everything here exists in or could reach the live database. Three extra articles:
`art-spargel-weiss` (seller switched it off), `art-honig-glas` (gated and genuinely
empty), `art-nuesse-walnuss` (**no `/seller_articles` half at all**).

| Id | Case | Expected |
|----|------|----------|
| F3-01 | `ord-f3-legacy-universe` — pre-market, pre-origin | No market name and no breakdown (empty `marketId`, no `pricingRevision`); amounts render as `2,000 kg` from `amountCount`, not the stored bare `"2.0"`; `piecesCount` 2147483647 never shows. |
| F3-02 | Approve the pending buyer | `ord-f3-demo-legacy` (`isDemoOrder: true` + the retired `status: "DEMO_ORDER"`) moves into `/orders` as `TRANSITIONED` with a real lifecycle status; `ord-f3-demo-stale`, whose pickup has passed, is dropped rather than moved. |
| F3-03 | Tobias is `BLOCKED` | The order write is refused by the rules and the app says so. He also cannot overwrite his own access record. |
| F3-04 | `ord-f3-inactive-market` | Breitenbachplatz still resolves to its name although `active: false`, and is no longer offered as a slot. Markets are deactivated, never deleted, for exactly this. |
| F3-05 | `ord-f3-delivery-no-address` | Flagged as a delivery the seller cannot deliver, rather than a blank address block. |
| F3-06 | `ord-f3-delivery-ok` has a `LOYALTY_BONUS` component | `HANDLING` shows, `LOYALTY_BONUS` is dropped silently (unknown cause → null), and the charged 4,60 € stays as recorded. |
| F3-07 | Weißer Spargel, `sellerAvailable: false` | Booking an intake must not make it available. The seller's intent outranks the ledger; a restock cannot undo "off for the season". |
| F3-08 | Lindenhonig, `onHand` 0 with `lastCountedAt` set | A real zero: `available` false, band `LAST_FEW`. Contrast F1-07. |
| F3-09 | Walnüsse have no private half | Purchase price shows as unknown, never `0,00 €`, and saving must not write a private half full of zeroes. |
| F3-10 | The pre-rename buyer profile | `defaultTime`, `favouriteProductIds`, `anonymous: 0`. It loads, and saving migrates it to the current names. |
| F3-11 | Three invite tokens | `tok-valid-sat` grants `APPROVED` scoped to Kranoldplatz only. `tok-expired` and `tok-redeemed` are refused, and no redemption may widen the granted markets. |
| F3-12 | `act-no-audience` has no `audience` child | Visible to all three buyers. A missing audience reads as `EVERYONE`, not as nobody. |
| F3-13 | Pickup slots this week | The seller-wide closure removes the next Thursday and the Monday stall is inactive, so only Saturday and the pop-up offer a slot. |
| F3-16 | A delivery order outlives the market's delivery | After uploading, set `markets/<seller>/mkt-popup-hofladen/allowsDelivery` to `false` in the console. `ord-f3-delivery-ok` keeps its address and stays a delivery — a capability change does not retract a placed order. Editing it coerces it to a pickup and says so, and the rules refuse any write that keeps it a delivery. |
| F3-15 | Knoblauch carries the legacy `weighPerPiece` spelling | The current reader looks for `weightPerPiece`, so the piece weight is 0,00 kg. Either the reader accepts both spellings or the editor heals it on save — it must not show a wrong weight or crash. Both live dumps have this typo. |
| F3-14 | An order on a day that was closed afterwards | `ord-f3-delivery-no-address` sits on the closed Thursday. It stays listed and openable: a closure removes a day from what is *offered*, it does not retract orders already placed. |

## What the fixtures deliberately do not cover

- **Auth.** No accounts, no linking, no deletion. `doc/guides/auth-and-accounts.md`.
- **Storage.** Article images point at real URLs in the existing
  `fire-one-58ddc` bucket so tiles are not all placeholders; nothing is uploaded.
- **Messages and conversations.** `/conversations`, `/messages`,
  `/user_conversations` and `/buyer_contacts` are empty — the chat feature has its
  own state to set up.
- **`seller_events`.** The channel has no producer yet (`observeEvents` has no
  callers), so seeding it would test nothing.
- **Second seller.** One seller only. Multi-seller buyer state is not modelled.
