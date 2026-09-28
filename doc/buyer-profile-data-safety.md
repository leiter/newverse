# Buyer Profile Data Safety Review

**Date:** 2026-09-28
**Scope:** `BuyerProfile` model, Firebase security rules, order serialization, logging

## Data Inventory

`BuyerProfile` stores the following personal data:

| Field | PII? | Notes |
|-------|------|-------|
| `displayName` | Yes | User-chosen name |
| `emailAddress` | Yes | Login email |
| `telephoneNumber` | Yes | Optional contact number |
| `photoUrl` | Yes | Profile photo (social login) |
| `street` | Yes | Delivery address |
| `houseNumber` | Yes | Delivery address |
| `buyerUUID` | Indirect | Stable ID assigned by seller via deep link |
| `placedOrderIds` | Indirect | Map of date → orderId, reveals purchase history |
| `favouriteArticles` | No | List of article IDs |
| `draftBasket` | No | Unsent basket contents |

## What's Protected

- **Firebase rules for `buyer_profile`** are owner-only: `$userId === auth.uid` for
  both read and write. No other user can access the node.
- **Seller cannot read `buyer_profile`** — seller-side code reads buyer info from
  orders and `buyer_access_status`, never from `buyer_profile` directly.
- **Account deletion** (`clearUserData`) cancels future orders, deletes the profile
  node, and clears the local cache.

## Issues

### 1. Buyer Email Embedded in Every Order (Medium)

**Location:** `GitLiveOrderRepository.kt` — `orderToMap()` writes `buyerEmail` into
`/orders/{sellerId}/{date}/{orderId}`.

**Impact:** The seller has full read access to the orders path. The buyer's email
address is permanently stored in every order, readable by the seller indefinitely —
even after account deletion, since order records are not cleaned up.

**Displayed at:** `OrderDetailScreen.kt:182–191` shows email and phone from the order.

**Recommendation:** Remove `buyerEmail` from `orderToMap()`. If seller–buyer contact
is needed, route through the existing messaging system.

### 2. Order History and Favourites Leaked via Order DTO (Low–Medium)

**Location:** `OrderDto.kt` — `BuyerProfileDto` serializes `placedOrderIds` and
`favouriteProductIds` into every order record.

**Impact:** The seller can see the buyer's full order-date history and favourite
product list through the embedded profile snapshot — more data than needed to
fulfil the order.

**Recommendation:** Strip `placedOrderIds` and `favouriteProductIds` from
`BuyerProfileDto.fromDomain()` when used inside `OrderDto`, or create a minimal
DTO that carries only `displayName` and `id`.

### 3. PII Leaked to Logcat via println (Medium)

**Location:** ~495 `println` calls across production code (observation 6994).

Key offenders:
- `GitLiveAuthRepository.kt:101` — logs email on sign-in
- `GitLiveAuthRepository.kt:267` — logs first 50 chars of Apple ID token
- `GitLiveProfileRepository.kt:92` — logs display name on save

**Impact:** Any app on the device (Android pre-13) or anyone with USB debug access
can read buyer PII from logcat.

**Recommendation:** Replace `println` with a logger that is disabled or stripped in
release builds. At minimum, never log email addresses, tokens, or names.

### 4. No Schema Validation on Order Writes (Low–Medium)

**Location:** `firebase/database.rules.json:55` — the `.validate` rule on
`/orders/{sellerId}/{date}/{orderId}` only checks `buyerId === auth.uid`.

**Impact:** A malicious or modified client can write arbitrary extra fields into order
nodes. There is no whitelist of allowed children.

**Recommendation:** Add `.validate` rules that restrict order children to the known
set (`buyerId`, `buyerName`, `createdDate`, `sellerId`, `marketId`, `pickUpDate`,
`message`, `articles`, etc.) and validate their types.

### 5. Draft Basket Stored as Plaintext (Low)

**Location:** `buyer_profile/{userId}/draftBasket` in Firebase RTDB.

**Impact:** Protected by owner-only rules, so exposure is limited to a Firebase
admin or a compromised auth token. Data is plaintext (product names, prices,
quantities) with no application-level encryption.

**Recommendation:** Acceptable given owner-only access. Consider encrypting if the
threat model includes compromised Firebase admin credentials.

## Summary Table

| # | Issue | Severity | Effort | Status |
|---|-------|----------|--------|--------|
| 1 | Email in orders | Medium | Small | Open |
| 2 | Order history in order DTO | Low–Medium | Small | Open |
| 3 | PII in logcat | Medium | Medium | Open |
| 4 | No order schema validation | Low–Medium | Medium | Open |
| 5 | Plaintext draft basket | Low | — | Accepted |
