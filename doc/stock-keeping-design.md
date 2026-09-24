# Stock Keeping — Design Sketch

**Status:** Rough plan / not yet implemented
**Goal:** Coordinate product availability from the seller side. When an order
is placed or cancelled, decrement/restore a per-article stock count, so
buyers stop seeing sold-out items as available. Not every article needs this
— goods that are easily restocked from Terra (well-storable produce) should
be able to skip counting entirely.

This is intentionally a rough sketch to work from later, not a finished
design. The trickiest part (write access for a buyer-triggered decrement) is
flagged below rather than resolved.

---

## Current State

- `Article` (`shared/src/commonMain/kotlin/com/together/newverse/domain/model/Article.kt:9-31`)
  has only a boolean `available` — no count of remaining units.
- `ArticleRepository` (`domain/repository/ArticleRepository.kt`) /
  `GitLiveArticleRepository` (`data/repository/GitLiveArticleRepository.kt`):
  `saveArticle` writes only the public half via
  `articleRef.updateChildren(ArticleNodes.publicFields(article))`
  (`GitLiveArticleRepository.kt:215-216`) — this repository never touches
  seller-only data.
- `ArticleNodes` (`data/firebase/ArticleNodes.kt`) is the multi-path-update
  helper that keeps `/articles/{sellerId}/{articleId}` (public) and
  `/seller_articles/{sellerId}/{articleId}` (seller-only) in sync:
  `saveUpdate` (63-70) and `deleteUpdate` (73-79) write/delete both halves
  atomically.
- Orders: `GitLiveOrderRepository.placeOrder`
  (`data/repository/GitLiveOrderRepository.kt:220-287`) writes the order via
  `orderRef.setValue(orderMap)` at line 266. `cancelOrder` (338-380) doesn't
  delete the order, it flips `status` to `CANCELLED` and writes it back.
  Buyer-side call sites are in
  `shared/src/buyMain/kotlin/com/together/newverse/ui/state/buy/BuyAppViewModelBasket.kt`
  (checkout ~640-704, cancel ~480-483 and ~1018-1030).
- Rules (`firebase/database.rules.json`): `articles/$sellerId` is
  `.read: auth != null`, `.write: auth.uid === $sellerId` — buyers currently
  have **no write access at all** into `articles` or `seller_articles`.

---

## A. Data Model

### A1. `stockQuantity: Int?` on `Article` — recommended

- `null` = untracked, always orderable (this *is* the "skip decrement" state
  — no separate flag needed for the article's stock behavior itself).
- `Int` = tracked count, decremented on order placement, restored on
  cancellation.
- Lives in the **public** half (`ArticleNodes.publicFields`), since buyers
  need to see remaining count / sold-out state, not just the seller.

### A2. `trackStock: Boolean` seller toggle — per article, not hardcoded

Originally sketched as a hardcoded list of "storable" BNN categories
(apples, citrus, root veg, nuts/dried goods vs. berries, salads, herbs,
sprouts, mushrooms). **Rejected** — sellers know their own restock cadence
better than a category heuristic, and Terra availability varies week to
week. Instead:

- Add `trackStock: Boolean` (default `false`) to `Article`, set by the
  seller per article in the product-editing screen, alongside price/markup.
- When `trackStock == false`, `stockQuantity` is ignored entirely — order
  placement/cancellation never touches it, article always shows available
  (subject to the existing manual `available` flag).
- When `trackStock == true`, `stockQuantity` must be set and is
  decremented/restored per order.
- This keeps the BNN-category table from the earlier sketch only as a
  **default suggestion** in the seller UI (e.g. pre-check `trackStock` for
  berries/salads/sprouts, leave unchecked for apples/citrus/root veg), never
  as enforced logic.

---

## B. Write Path

The hard part: decrementing `stockQuantity` happens as a side effect of a
**buyer** placing an order, but `articles/$sellerId` write access is
currently seller-only.

### B1. Client-side RTDB transaction + scoped rule (v1)

- After `orderRef.setValue(orderMap)` succeeds in `placeOrder`, for each
  `OrderedProduct` whose `Article.trackStock == true`, run a transaction on
  `articles/{sellerId}/{articleId}/stockQuantity`:
  `current -> max(0, current - orderedQty)`.
- `cancelOrder` does the inverse (`current -> current + orderedQty`) when it
  flips status to `CANCELLED`, only for tracked articles.
- RTDB transactions retry automatically on conflicting concurrent writes —
  this is what protects against two buyers grabbing the last unit at once,
  without needing a separate lock.
- Requires a new, narrowly scoped rule change: allow an authenticated buyer
  to write `articles/$sellerId/$articleId/stockQuantity` specifically via a
  transaction that only decreases the value and keeps it `>= 0`. Everything
  else under `articles/$sellerId` stays seller-write-only. This rule needs
  careful design/testing (see `firebase/tests`) — RTDB rules validate each
  transaction attempt, not the transaction as a whole, so the validate
  expression needs to be safe against retries.

### B2. Cloud Function on order write (hardening, later)

- Keep buyer write surface unchanged; a function triggered on
  `orders/{sellerId}/{date}/{orderId}` create/update does the
  increment/decrement server-side instead.
- Pro: stock stays fully seller/server-authoritative, no new buyer write
  rule needed.
- Con: no Cloud Functions infra exists in this project today — new
  deployment surface, added latency, cold starts to consider.
- Recommended as the follow-up once B1 is proven, not the starting point.

---

## Open Questions

- Should `available` auto-flip to `false` when `stockQuantity` hits 0 for a
  tracked article, or stay a separate manual seller override layered on top
  of the count? Needs a decision before the rule/transaction shape is final.
- Where does the seller set/see `stockQuantity` day-to-day — product edit
  screen only, or a lighter "restock" quick-action given how often Terra
  deliveries change availability?
- Should `trackStock` have a UI default suggested per BNN category (from the
  rough table below) to reduce seller setup friction, without ever being
  enforced in code?

### Reference: rough storable/perishable split (UI suggestion only)

| BNN category | Example | Suggested default |
|---|---|---|
| 301 Kernobst | apples, pears | `trackStock = false` |
| 302 Zitrusfrüchte | citrus | `trackStock = false` |
| 312 Wurzelgemüse | carrots, root veg | `trackStock = false` |
| 311/315 Kohl(rabi) | cabbage family | `trackStock = false` |
| 351 Nüsse/verarbeitet | chestnuts, dried | `trackStock = false` |
| 241 Eier | eggs | `trackStock = false` |
| 303 Beeren/Trauben | berries, grapes | `trackStock = true` |
| 304 exotics | melons, avocado, kiwi | `trackStock = true` |
| 313/316 Salate/Blütengemüse | cress, artichokes | `trackStock = true` |
| 314 Stängelgemüse | celery | `trackStock = true` |
| 321 Kräuter | parsley etc. | `trackStock = true` |
| 331 Sprossen | sprouts | `trackStock = true` |
| 341 Pilze | mushrooms | `trackStock = true` |

This table is a heuristic from category + general shelf-life knowledge, not
authoritative Terra data — a starting suggestion for the seller UI, never
hardcoded logic.
