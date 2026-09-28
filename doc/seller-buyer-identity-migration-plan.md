# Identity Rework & Sync Channel — Implementation Plan

**Date:** 2026-09-28
**Status:** Plan — nothing implemented
**Design:** `doc/seller-buyer-sync-channel-design.md`
**Related:** `doc/buyer-profile-data-safety.md`

## Context: no production data

Confirmed 2026-09-28 — everything in both Firebase projects is test and developer data.
**There is no migration and no backfill.** Access records are wiped and re-established by
using the app. This removed the highest-risk part of the original plan: no rekey script,
no database export, no dry-run review, no `--apply`.

Consequences:

- `firebase/scripts/backfill-authuid.mjs` is obsolete — it exists only to repair
  pre-hardening records, and those records are being deleted. Delete the script.
- Outstanding QR links are invalidated rather than supported. No compatibility path.
- The rules rekey and the code that uses it land together; there is nothing to preserve
  between them.

## What this plan delivers

1. Makes seller pre-approval (QR link *and* invitation) actually redeemable — it is
   currently impossible, verified on the emulator.
2. Splits `buyerUUID` into a throwaway invite token; every durable record keys on the
   Firebase auth uid.
3. Builds the buyer→seller sync channel on `seller_events`, so a rename reaches the seller
   without the buyer writing into the seller's access-control records.

## Scope

**223 references** to `buyerUUID` / `buyerToken` / `BuyerUUIDStorage` across **28 files**.
The wipe removes the data problem, not the code problem — this part is unchanged.

| Area | Files |
|---|---|
| Storage (expect/actual, 4 platforms) | `data/config/BuyerUUIDStorage.kt` × commonMain, androidMain, iosMain, jsMain |
| DI wiring | `di/AndroidDomainModule.kt`, `di/IosDomainModule.kt`, `di/WebDomainModule.kt`, `buyMain/di/AppModule.kt`, `sellMain/di/AppModule.kt` |
| Repository API (13 buyerUUID-keyed methods) | `domain/repository/ProfileRepository.kt`, `data/repository/GitLiveProfileRepository.kt`, `MockProfileRepository.kt`, `commonTest/FakeProfileRepository.kt` |
| Domain models | `BuyerProfile.kt`, `AccessRequest.kt`, `SellerEvent.kt` |
| Buyer flows | `BuyAppViewModelAccess.kt`, `BuyAppViewModelSeller.kt`, `BuyAppViewModelAuth.kt`, `BuyAppViewModel.kt`, `BuyAction.kt`, `buyMain/ui/navigation/NavGraph.kt`, `CustomerProfileScreenModern.kt` |
| Deep link entry | `androidApp/src/buy/.../BuyMainActivity.kt` |
| Seller flows | `SellerProfileViewModel.kt`, `SellerProfileScreen.kt`, `CustomerDetailViewModel.kt` |
| Event log | `domain/repository/SellerEventRepository.kt`, `GitLiveSellerEventRepository.kt` |
| Rules + tests | `firebase/database.rules.json`, `firebase/tests/access.test.js`, `firebase/tests/profile-orders-events.test.js` |

## Decisions still open

1. **Drain trigger** — live listener for the whole seller session, or on foreground only?
   (`observeAccessRequestCount` at `SellAppViewModel.kt:129` is the existing precedent for
   a per-seller job.)

**Resolved:** token expiry is enforced *in the rules* via `now < data.expiresAt`
(Phase 1). A stale token is not redeemable even if the app forgets to check.

## Phase 1 — Prove the redeem-once rule ✅ DONE (2026-09-28)

**Outcome: the assumption holds.** RTDB expresses "writable because unclaimed" cleanly.
The seller-side-redemption fallback is not needed, so Phases 2–4 proceed as written.

Implemented in `firebase/database.rules.json` (`invite_tokens`), with 17 cases in
`firebase/tests/invite-tokens.test.js`. Full suite **91/91 green**, nothing deployed and
no app code touched.

Verified by mutation: deleting the `!data.child('redeemedBy').exists()` clause from the
write rule makes exactly one test fail — *"denies a second buyer redeeming an already
redeemed token"* — and no others. The suite discriminates on the load-bearing clause
rather than passing vacuously.

What the rules guarantee:

- seller mints, lists and revokes tokens in their own namespace only
- a stranger cannot enumerate a seller's tokens (no read at the `$sellerId` level)
- a buyer holding a token may read it while unredeemed, and redeem it exactly once
- a second buyer cannot re-redeem; nobody can redeem in another uid's name
- the redeemer retains read access afterwards; third parties lose it
- expired tokens are rejected by the rules
- a buyer cannot extend expiry, rewrite `displayNameHint`, mint a token from nothing,
  delete a token, or add unknown fields (`$other: false`)

**Accepted property:** an *unredeemed* token is readable by any authenticated user who
knows the token string. That is inherent to a bearer ticket — whoever holds the QR can
read it — and enumeration is blocked, so the exposure is limited to a guessed or
intercepted UUID.

### Original phase brief (for reference)

```
invite_tokens/{sellerId}/{token}
  { createdAt, expiresAt, redeemedBy: null, displayNameHint }
  .read   auth != null && (auth.uid === $sellerId
                           || !data.child('redeemedBy').exists()
                           || data.child('redeemedBy').val() === auth.uid)
  .write  seller: auth.uid === $sellerId
          buyer:  redeem once —
                  !data.child('redeemedBy').exists()
                  && newData.child('redeemedBy').val() === auth.uid
                  && newData.child('createdAt').val() === data.child('createdAt').val()
```

The redeem clause is permitted *because* the record is unclaimed. That is precisely what
the current `buyer_access_status` rules cannot express, and why pre-approval deadlocks
today: they require `data.authUID === auth.uid`, so a buyer must already own a record in
order to claim it.

The fallback that would have applied if this had not worked: seller-side redemption, where
the buyer writes a request and the seller's drain completes the binding. Not needed.

## Phase 2 — Wipe, rekey, and move the app over

One release. Rules, code, and data wipe together.

### 2a. Wipe

Delete on `fire-one-58ddc` (and check `bodenschaetze-a988e`, the release project):

```
buyer_access_status   access_requests   invitations   buyer_invitations
seller_events         buyer_profile     orders        demo_orders
sales                 sale_index        conversations messages
user_conversations    buyer_contacts    buyer_blocked
seller_profile/<sellerUid>/{knownClientIds,blockedClientIds,approvedBuyerIds}
```

`sales` and `sale_index` must go together — the rules require them to point at each other.

**Keep:** `articles`, `seller_articles` (expensive to rebuild; written and deleted as a
pair via `ArticleNodes`, so removing one half breaks that invariant), the rest of
`seller_profile`, and Firebase Storage.

**Do not delete the seller's Firebase Auth account** — `sellerId === auth.uid` and the
whole catalog is keyed under it.

Also clear app data on buyer test devices: `BuyerUUIDStorage` persists the buyerUUID
locally, so a wiped server plus a stale device still presents a dead UUID.

### 2b. Rules

- `buyer_access_status` keyed by auth uid: `$buyerUid === auth.uid` replaces the
  `data.child('authUID').val() === auth.uid` indirection throughout. Target the shape the
  `orders` rules already have — one clause, no indirection.
- Re-gate `seller_events` write on `approvedBuyerIds[auth.uid]`. **The `knownClientIds`
  eligibility blocker dissolves here** — no option A/B/C decision, no `addKnownClient`
  backfill, no key bridging in the drain.
- Update rule tests to the new key shape.

### 2c. Code

- `generateBuyerLink()` (`SellerProfileViewModel.kt:119`) writes an `invite_tokens` entry
  instead of calling `approveAccessRequestWithTracking`. `QR_LINK_PLACEHOLDER` (`:485`)
  and the placeholder-resolution dance at `:199` both disappear — the token carries
  `displayNameHint` and the real name arrives on redemption.
- `connectWithToken` (`BuyAppViewModelAccess.kt:236`) redeems the token, then writes
  `buyer_access_status/{sellerId}/{authUid}`. The `getAccessStatus == NONE` fallback that
  currently masks the permission denial goes away.
- `applyPreApprovedAccess` (`:220`) redeems through the same path — one code path for QR
  and invitation instead of two. It currently only sets local storage, calls
  `saveBuyerUUID`, and observes; it never writes `authUID`, which is why the invitation
  path is broken too.
- Collapse the 13 buyerUUID-keyed `ProfileRepository` methods onto uid-keyed equivalents.
  `getBuyerAuthUID` becomes unnecessary — the key *is* the uid. Delete it.
- `BuyerProfile.buyerUUID` → drop. Rename `BuyerUUIDStorage` to
  `PendingInviteTokenStorage`, holding a token only until redemption.
- `CustomerDetailViewModel` already filters orders by `buyerProfile.id == authUID`
  (`:108`) — no bridging needed once the access list is uid-keyed.
- Orders need no change: `saveBuyerProfile` already forces `id = userId`, so `orders` is
  already uid-keyed.

**Verify:** emulator suite green; unit tests; then **on a device** — generate a QR on the
seller app, scan with the buyer app, confirm the buyer lands APPROVED with no manual
approval step. This will be the first time that flow has ever worked, so exercise it for
real on both flavors.

**Risk:** rules deploy. Deploy rules and ship the matching app build together — per
`firebase/tests/README.md`, a deployed ruleset that disagrees with the app rejects writes
outright. Deploy needs explicit go-ahead.

## Phase 3 — Sync channel

- `SellerEventType += PROFILE_CHANGED`; `SellerEvent += changedFields: Map<String, String>`.
  `displayName` only to start. **Do not include email** — per
  `doc/buyer-profile-data-safety.md` finding 1 the seller already receives more buyer PII
  than needed; this channel must not widen that.
- Extend the `seller_events` `.validate` to accept `changedFields` and keep rejecting
  unknown top-level fields.
- `seller_event_cursor/{sellerId}` as a new owner-only node. **Not** in `seller_profile` —
  that node's `$field` rule grants read to any authenticated user for every field except
  the three client lists, so anything added there is world-readable to signed-in users.
- Buyer emits `PROFILE_CHANGED` on rename, **in addition to** the existing
  `syncDisplayNameToLinkedSellers` write. Both run; nothing regresses.
- Seller-side drain: observe → apply oldest-first by `timestamp` → advance cursor →
  delete applied `PROFILE_CHANGED` events (RTDB has no TTL and buyers cannot delete). Must
  be idempotent so a lost cursor write is harmless. Wire it in `SellAppViewModel` alongside
  `observeAccessRequestCount` (`:129`); register in `sellMain/di/AppModule.kt`. Retain
  lifecycle events — those are book keeping.

**Verify:** rule tests for the event shape and cursor node; on a device, rename in the
buyer app and watch the seller's customer list update.

**Risk:** additive — the old path is still live.

## Phase 4 — Cut over

The payoff.

- Delete `syncDisplayNameToLinkedSellers` (`GitLiveProfileRepository.kt:941`) and the
  `linkedSellerIds` bookkeeping that exists only to serve it.
- Delete `enrichWithDisplayNames` (`SellerProfileViewModel.kt:194`) and its N+1
  `getBuyerDisplayName` read, plus `correctApprovedBuyerDisplayName` and
  `updateApprovedBuyerDisplayName`. The drained customer store is now the source of truth.
- Tighten the `buyer_access_status` write rule so a buyer can write **nothing** beyond
  their own PENDING request. The `newData.status === data.status` guard stops being
  load-bearing for identity data, which was the original point.
- Add the `.validate` field whitelist on `/orders`
  (`doc/buyer-profile-data-safety.md` finding 4) while the rules file is open.

**Verify:** full emulator suite; device pass on both flavors; confirm a rename still
propagates with the old path gone.

**Risk:** rules deploy. Same precaution as Phase 2.

## Out of scope

- `conversations` / `messages` — human chat. Wiped in Phase 2a as test data, but the
  feature is untouched.
- Seller→buyer push — see `doc/order-ready-notification-design.md`. Node naming here
  should not preclude a future merge, but it is not built now.
- The remaining `buyer-profile-data-safety.md` findings (buyer email embedded in every
  order; order history in the order DTO). Related but separate — except the `/orders`
  `.validate` whitelist, folded into Phase 4.
