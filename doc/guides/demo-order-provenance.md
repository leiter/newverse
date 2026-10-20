# Demo Orders: Provenance, Path and Transition

**Status:** Implemented 2026-10-15
**Scope:** `shared/src/commonMain` (model + repository), `buyMain` (approval flow),
`sellMain` (filters), `firebase/database.rules.json`

How an order records where it came from, why that is separate from where it is stored,
and what happens to a demo order when the seller approves the buyer.

---

## The rule

> **The path is the access and traffic boundary. The flag is the provenance record.**

| | Stored at | `Order.origin` |
|---|---|---|
| Buyer not approved yet | `demo_orders/{sellerId}/{yyyyMMdd}/{orderId}`, or local storage only | `DEMO` |
| Was a demo order, buyer since approved | `orders/{sellerId}/{yyyyMMdd}/{orderId}` | `TRANSITIONED` |
| Placed by an approved buyer | `orders/{sellerId}/{yyyyMMdd}/{orderId}` | `PRODUCTION` |

`TRANSITIONED` and `PRODUCTION` behave identically everywhere — both are fulfilled, both
are booked. The distinction is kept only so the seller can tell that an order predates
their approval of that buyer.

### Why the path split stays

Merging demo orders into `orders/` behind a flag was considered and rejected:

- **Traffic.** The seller's listener is on `ordersRootRef.child(sellerId)` — the *whole*
  tree (`GitLiveOrderRepository.observeSellerOrders`). Demo orders currently cost the
  seller zero bytes because nobody subscribes to the tree they live in. Behind a flag,
  every demo order from every anonymous device would stream to the seller's phone forever,
  only to be filtered out client-side. RTDB cannot filter that server-side across the
  `$date` level.
- **Rules.** One rule would have to admit unapproved buyers *and* make demo→production
  self-promotion impossible client-side. Expressible, but fiddly, and rule gaps in this
  project have reached production before (the stock rules, fixed 2026-10-10).
- **Cleanup.** `deleteOldDemoOrders` is one subtree removal. Behind a flag it becomes a
  scan over live order data, where a bug deletes real orders.

### Why the flag is still needed

A path cannot carry a lifecycle. Before this change provenance was encoded twice — as
`Order.isDemoOrder` and as the status value `OrderStatus.DEMO_ORDER` — and squatting in
the status slot meant a demo order could never also be `PLACED`, `LOCKED` or `COMPLETED`.
The old `migrateLocalDemoOrdersToProduction()` did `copy(isDemoOrder = false)` and left
the status at `DEMO_ORDER`, and `placeOrder` only rewrites `DRAFT → PLACED`, so a migrated
order landed in `orders/` as a **half-real order**: invisible to its buyer (filtered by
`getBuyerOrders`), visible to the seller and counted in Abrechnung, but unbookable at
pickup (`DEMO_ORDER ∈ PickupViewModel.NOT_BOOKABLE`). That is the hole `TRANSITIONED`
closes.

---

## The transition

`OrderRepository.transitionDemoOrdersToProduction(sellerId, remoteOrderIds, localOrders)`,
called from `BuyAppViewModelAccess` the moment the access status flips to `APPROVED`.

Demo orders live in two places at once and both are handed over in one call:

- **Remote** — the first `DEMO_FIREBASE_WRITE_LIMIT` (2) orders, under `demo_orders/`.
- **Local** — everything after the local-mode switch, in `DemoOrderStorage`.

Both land under `orders/` in **one multi-path update** against the database root: the new
record is written and the `demo_orders/` entry set to `null` in the same operation, the
same shape as the two-half article write in `ArticleNodes`. Either every order moves or
none does. The local copies are cleared only after the write succeeds, so a failure leaves
the buyer's demo orders untouched and the next approval emission retries.

The buyer's `placedOrderIds` are rewritten to the new production ids — without that, their
history would keep looking for production orders under demo ids and find nothing.

Each moved order's `buyerId` is restamped with the current uid. A demo order is usually
placed while anonymous, and an anonymous account that signs in with a provider rather than
being linked comes back with a different uid (hence `migrateAnonymousUser`). Both the write
rule and the buyer's own read rule on `orders/` key on `buyerId === auth.uid`, so a stale
uid would have the whole update rejected.

### Status on arrival

`Order.statusAfterTransition()`:

| Condition | Result |
|---|---|
| Already finalized (`CANCELLED`, `COMPLETED`, `NOT_PICKED_UP`, `LOCKED`) | left alone |
| Still inside the edit window (`canEdit`) | `PLACED` |
| Past the Tuesday 23:59 deadline, pickup still ahead | `LOCKED` |

### What does not move

`Order.canTransitionToProduction()` holds back any order whose pickup day has already
passed. Such an order describes a pickup that never happened, and planting it in `orders/`
would put a sale that was never made into the seller's books — which matters directly for
the CSV tax export. Those records are deleted along with the rest of the demo data, and
the count is logged.

---

## Reading old records

`storedOrderOrigin(raw, legacyIsDemoOrder, legacyStatus)` in `GitLiveOrderRepository.kt`:

1. A known `origin` value wins.
2. Otherwise `isDemoOrder == true` **or** `status == "DEMO_ORDER"` → `DEMO`.
3. Otherwise `PRODUCTION` — including an origin value written by a newer app version.
   Visible and bookable beats silently filtered away.

`storedOrderStatus` already degraded unknown status names to `PLACED`, so a stored
`"DEMO_ORDER"` reads back as `PLACED` with no extra handling. The same two-step applies to
locally stored demo orders via `DemoOrderDto.origin`, which defaults to `DEMO` when absent.

New records no longer write `isDemoOrder`. An older app reading them sees `false`, which is
correct for everything under `orders/`.

---

## Rules

`origin` is validated on both nodes so the path and the flag cannot disagree:

```jsonc
"orders":      { … "origin": { ".validate": "newData.val() === 'PRODUCTION' || newData.val() === 'TRANSITIONED'" } }
"demo_orders": { … "origin": { ".validate": "newData.val() === 'DEMO'" } }
```

A missing `origin` is still accepted, so clients predating this change keep working. Child
validation does not run on deletes, so the `demo_orders/` half of the transition update
passes. Covered by six tests in `firebase/tests/profile-orders-events.test.js`.

---

## Still open

Deliberately out of scope here; each is independent of the model above.

### 1. The demo write budget is not a control

`BuyerSellerConfig.DEMO_FIREBASE_WRITE_LIMIT = 2` is a counter in SharedPreferences /
NSUserDefaults. Clearing app data or reinstalling resets it. The rules place no limit at
all: any signed-in user — typically anonymous — may write unlimited orders under
`demo_orders/{anySellerId}/{anyDate}/{anyOrderId}`. The budget is a courtesy to the
seller's bandwidth, not a defence.

Real options, cheapest first:

- **App Check** — blocks clients that are not the app at all. Highest leverage per unit of
  work, and it protects every other node too.
- **Rule hardening** — constrain `$date` to a plausible window, cap the article count.
  Cheap, partial.
- **Cloud Function** — RTDB rules cannot count, so an actual per-buyer cap needs one.

### 2. `orders/` is not closed to unapproved buyers either

The write rule gates on `… status !== 'BLOCKED'`, and an unapproved buyer's status is
`NONE`, not `BLOCKED`. So the path split is a *traffic* boundary and a convention, not a
write-permission boundary: a demo buyer is not prevented by the rules from writing into
`orders/` directly. The new `origin` validation stops them labelling such an order `DEMO`,
but not from writing one at all. Tightening this to require `APPROVED` is a one-line rule
change plus a check of whether any legitimate pre-approval write depends on the current
behaviour.

### 3. Should `demo_orders/` exist at all?

The two Firebase demo writes exist "for seller observability" — but **nothing in the seller
app ever displays them**. `observeSellerOrders` reads only `orders/`; the sole reference is
the `clearOldDemoOrders` cleanup button in `OrdersViewModel`. The observability benefit is
currently zero.

Making demo orders local-only would delete the node, its rules, the write budget and the
whole abuse question in one move. The cost is giving up a feature that was never built. The
alternative is to build it: surface demo orders in the seller app as a "prospective buyers"
signal. Either is defensible; the current middle state is the one that is not.

### 4. Dead defensive filters in the seller app

`AppScaffold`, `OverviewViewModel`, `AbrechnungViewModel` and `PickupViewModel` all check
`origin.isDemo`. Since the seller only ever reads `orders/`, and `origin: DEMO` is now
rejected there by the rules, those checks can never fire. They are kept as cheap guards
against a future where the trees are merged or a record is written by hand. Worth deleting
if item 3 settles on local-only demo orders.
