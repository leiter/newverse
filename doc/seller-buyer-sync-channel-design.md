# Seller ↔ Buyer Sync Channel Design

**Date:** 2026-09-28
**Status:** Design — not implemented
**Related:** `doc/buyer-profile-data-safety.md`, `firebase/database.rules.json`

## Goal

Give the buyer a way to *notify* a seller that their own data changed, without either
side gaining read access into the other's profile. The seller stays the admin of the
channel: they alone read it, they alone prune it, and they decide what to do with what
arrives.

Concretely: when a buyer renames themselves, the seller's customer list should reflect
the new name — without the seller ever being able to read `buyer_profile`, and without
the buyer being able to write into the seller's access-control records.

### Non-goals

- **Not chat.** The `conversations` / `messages` nodes stay as they are; that is
  human-to-human messaging and a separate concern.
- **Not order mutation.** Orders keep the buyer snapshot taken at order time. An
  invoice must not change retroactively. This channel updates the seller's *current*
  view of a customer, not their historical records.
- **Not a permission grant.** Nothing here widens what either party can read.

## Why the current path is wrong

Today a rename reaches the seller through `syncDisplayNameToLinkedSellers`
(`GitLiveProfileRepository.kt:941`), which writes into
`buyer_access_status/{sellerId}/{buyerUUID}/displayName`. The method's own comment
states the reason: that is *"the only buyer-writable node a seller can read."* It is a
workaround, not a design.

Two problems follow:

1. **Identity data rides on the permission record.** `buyer_access_status` exists to
   answer "may this buyer order from this seller." It now also carries the buyer's
   name. The only thing keeping a buyer write from touching access state is the guard
   `newData.child('status').val() === data.child('status').val()` in the rules. That
   guard is load-bearing for security while being incidental to the feature it
   enables. One careless rule edit and a buyer can self-approve.

2. **The seller has to poll.** `SellerProfileViewModel.enrichWithDisplayNames`
   (`:194`) re-reads `getBuyerDisplayName` for *every* buyer on *every* load, then
   writes corrections back via `correctApprovedBuyerDisplayName`. Its comment explains
   why it cannot be lazy: a rename lands in `buyer_access_status` invisibly, so there
   is no signal to react to. This is an N+1 read on every customer-list load,
   compensating for a missing notification.

## The mechanism already exists

`seller_events` is already precisely this channel, and its rules are already correct:

```
seller_events/{sellerId}/{eventId}
  .read   seller only            (auth.uid === $sellerId)
  .write  create-only, authored   (!data.exists()
                                  && newData.child('firebaseUserId').val() === auth.uid
                                  && knownClientIds[auth.uid] exists)
```

Append-only for the buyer, readable only by the seller, and the writer is bound to
their own auth uid so a buyer cannot forge an event as someone else. Rule behaviour is
already covered in `firebase/tests/profile-orders-events.test.js`: an impostor write
fails, a buyer cannot read the log, a buyer cannot modify or remove their own event
after writing, and the seller can read.

**It is half-wired.** `SellerEventRepository` is registered in Koin on *both* flavors,
but `observeEvents` has **zero call sites in sellMain** — the seller never reads the
log. There is exactly one producer: account-deletion / logout lifecycle events at
`BuyAppViewModelAuth.kt:795`. The drop box is built and nobody collects the mail.

The existing `SellerEventType` values are all terminal account-lifecycle facts
(`GUEST_DATA_DELETED`, `ACCOUNT_DELETED`, `ACCOUNT_LINKED`,
`ACCOUNT_DELETION_INCOMPLETE`). Generalizing means admitting *non-terminal* change
events into the same log.

The `SellerEvent` doc comment already justifies denormalising buyer name and UUID into
the record "because the buyer profile is usually deleted in the same operation." That
rationale extends cleanly: for change events the seller cannot read `buyer_profile`
*at all*, so the event must carry the payload.

## Blocker: write eligibility is not guaranteed

The write rule gates on `knownClientIds[auth.uid]`. That set is populated from only
two call sites:

- `GitLiveInvitationRepository.kt:152` — invitation acceptance
- `BuyAppViewModelSeller.kt:199` — `performConnection`, wrapped in a try/catch that
  logs `"addKnownClient failed (non-fatal)"` and continues

So a buyer who obtained access through the `access_requests` → `approvedBuyerIds` path
may never enter `knownClientIds`, and a buyer whose `addKnownClient` call failed is
silently ineligible forever. Either way they cannot write events, and the failure is
invisible: the write is rejected by rules, the buyer sees nothing, the seller's name
stays stale.

**There is also a key mismatch.** `knownClientIds` is keyed by **auth.uid**, while
`approvedBuyerIds` and `buyer_access_status` are keyed by **buyerUUID**. Two identity
namespaces for one buyer. `SellerEvent` already carries both (`firebaseUserId` and
`buyerUUID`), so the drain can bridge them — but the rule's gate and the seller's
customer store are keyed differently, and that has to be explicit.

This must be resolved before the channel can be relied on. Options:

| Option | Effect | Cost |
|---|---|---|
| **A.** Widen the write rule to also admit `buyer_access_status[$sellerId][*].authUID === auth.uid` with status APPROVED | Any approved buyer can write, regardless of `knownClientIds` | Rule reads across a buyerUUID-keyed node by value, not key — needs a rule-level lookup that RTDB cannot express without knowing the UUID. Likely needs the buyerUUID passed in the event and the rule indexing on it. |
| **B.** Make `addKnownClient` authoritative and non-optional on every access-grant path | One eligibility set, simple rule stays as-is | Must backfill existing buyers; must make the currently-non-fatal call fatal or retried |
| **C.** Gate on `approvedBuyerIds[buyerUUID]` instead, and have the event path key by buyerUUID | Matches the customer store's key | Rule must verify the writer owns that buyerUUID, which means reading `buyer_access_status[...][buyerUUID].authUID === auth.uid` — expressible, since the event path can carry buyerUUID as the key |

**Superseded — see "Identity model" below.** All three options are workarounds for a
deeper problem: `buyerUUID` is doing two unrelated jobs at once. Fixing that removes
this blocker instead of routing around it.

## Identity model: buyerUUID vs firebaseId

Investigated 2026-09-28. **Conclusion: `buyerUUID` should become a throwaway invite
token, and every durable record should key on the Firebase auth uid.**

### What buyerUUID is actually for

It has one legitimate reason to exist. `SellerProfileViewModel.generateBuyerLink()`
(`:119`) mints a random UUID, embeds it in
`https://cutthecrap.link/connect?seller=…&token=…`, and immediately pre-approves it:

```kotlin
val uuid = Uuid.random().toString()
// ...
profileRepository.approveAccessRequestWithTracking(sellerId, uuid, QR_LINK_PLACEHOLDER)
```

At that moment **the buyer has no Firebase identity** — possibly no buyer exists yet, as
the seller can print QR codes in advance for a market stall. You cannot pre-approve an
auth uid you do not know. So `buyerUUID` is a **bearer claim ticket**: a capability that
whoever presents it may redeem.

That justifies a token. It does not justify using that token as the permanent customer
key, which is what happens today.

### The pre-approval flow cannot work

`approveAccessRequestWithTracking` writes `{status: APPROVED, buyerUUID, displayName}`
and **no `authUID`**. The `buyer_access_status` rules then lock the buyer out of their
own record:

- **read** requires `data.child('authUID').val() === auth.uid` → the field is null, so
  the buyer cannot read it
- **write** on an existing record requires `data.child('authUID').val() === auth.uid` →
  the buyer must *already* be the owner in order to *become* the owner

A chicken-and-egg deadlock. Verified against the emulator with a throwaway probe
reproducing the exact pre-approval shape — all four claim paths are denied
(read, write `authUID`, write `displayName`, overwrite as PENDING), while the
`access_requests` half succeeds. Every existing test in `access.test.js` seeds
`authUID` as already present, which is why the gap was never caught.

The observable consequence of a buyer scanning a seller's QR code:

1. `getAccessStatus` read is denied; the catch block returns `AccessStatus.NONE`
2. so `connectWithToken` takes the `submitAccessRequest` branch, not the APPROVED branch
3. `submitAccessRequest` writes `access_requests` (succeeds), then `buyer_access_status`
   (denied) and throws — the buyer sees *"Zugangsanfrage fehlgeschlagen"*
4. the seller sees a pending request and approves it — writing `authUID`-less data again
5. the buyer **still** cannot read their status, and never will under that token

The token is poisoned on first use: because the record now exists without an `authUID`,
no buyer can ever claim it. The only working path is the manual one, where the buyer
mints their own UUID and creates the record themselves (`!data.exists()` + PENDING).

This likely explains the still-open "walk-in market device check" item — the flow has
probably never been exercised end-to-end on a device.

### Why auth uid is the right durable key

The comment at the top of `firebase/tests/access.test.js` already states the conclusion:

> *A buyer is identified to the seller by buyerUUID, but that value lives in the buyer's
> own profile and can be set to anything. Authorisation must key on the auth uid instead.*

The rules already obey this — they authorize on `authUID` — but the **path key** is
still `buyerUUID`. That mismatch is the direct cause of the awkward
`data.child('authUID').val() === auth.uid` indirection in every clause, and of the
deadlock above.

The `orders` subtree already does it the proposed way. `saveBuyerProfile` forces
`id = userId`, `orderToMap` writes that as `buyerId`, and the rule is a single clause:

```
".validate": "auth.uid === $sellerId || newData.child('buyerId').val() === auth.uid"
```

No indirection, no second namespace, no deadlock. That is the target shape.

Note also that `buyerUUID` provides **no pseudonymity benefit**: `BuyerUUIDStorage`
holds one value reused across all sellers (with `linkedSellerIds` fanning out from it),
so it is just as globally identifying as the auth uid.

### Proposed split

Separate the two jobs:

```
invite_tokens/{sellerId}/{token}
  { createdAt, expiresAt, redeemedBy: null }
  .read   unredeemed, or redeemed by you
  .write  seller: anything
          buyer: redeem once — !data.child('redeemedBy').exists()
                 && newData.child('redeemedBy').val() === auth.uid
```

The redeem clause is expressible *precisely because* the record is unclaimed — which is
what breaks the chicken-and-egg. On redemption, `buyer_access_status/{sellerId}/{authUid}`
is written, keyed by auth uid, and the token is dead and can be deleted.

Then `buyer_access_status`, `approvedBuyerIds`, `knownClientIds` and `seller_events` all
key on auth uid. **The eligibility blocker disappears**: it becomes
`approvedBuyerIds[auth.uid]`, with no A/B/C choice and no key bridging in the drain.

### Issues arising

1. **Migration.** Existing records are keyed by buyerUUID. For each
   `buyer_access_status/{sellerId}/{uuid}` that *has* an `authUID`, rewrite it under that
   uid (and the matching `approvedBuyerIds` / `blockedClientIds` entry). Records
   *without* an `authUID` are unredeemable by construction — drop them. Buyers cannot
   rekey their own records, so this needs the seller app or an admin script.
2. **Printed QR codes.** Any token already handed out is in the old namespace. Since the
   flow never worked, none can have been successfully redeemed — recommend invalidating
   rather than building a compatibility path.
3. **Anonymous uid churn.** A reinstall yields a new anonymous uid. `BuyerUUIDStorage`
   does not survive an uninstall either, so this is not a regression. Account *linking*
   preserves the uid, so guest→permanent upgrades are safe.
4. **Delete-then-recreate.** Today local storage keeps the old `buyerUUID`, so a
   re-registering buyer silently re-attaches to the deleted customer record even though
   `ACCOUNT_DELETED` already fired. Keying on uid is strictly more correct here.
5. **`BuyerProfile.buyerUUID` becomes vestigial.** Drop it from the profile; hold a
   pending token in local storage only, cleared on redemption.
6. **Orders need no migration** — already keyed on auth uid (see above).

### Revised step 1

Step 1 of the rollout below is replaced by: split the token from the identity, rekey the
access nodes onto auth uid, and land the pre-approval claim tests (which currently fail)
as the red-to-green proof. The `knownClientIds` eligibility question dissolves once this
lands.

## Design

### Event as change notification

Extend `SellerEventType` with non-terminal change events:

```kotlin
enum class SellerEventType {
    // existing lifecycle events
    GUEST_DATA_DELETED,
    ACCOUNT_DELETED,
    ACCOUNT_LINKED,
    ACCOUNT_DELETION_INCOMPLETE,

    // new: buyer-authored change notifications
    PROFILE_CHANGED,
}
```

`PROFILE_CHANGED` carries only the fields that changed, so the seller applies a patch
rather than reconciling a whole profile:

```kotlin
data class SellerEvent(
    // ... existing fields ...
    /** Field name → new value, for PROFILE_CHANGED. Empty for lifecycle events. */
    val changedFields: Map<String, String> = emptyMap(),
)
```

Start with `displayName` as the only accepted key. Adding `telephoneNumber` or address
later is then a one-line change on both ends, and the seller can ignore keys it does
not understand — forward compatibility for free.

Deliberately **not** included: email. Per `doc/buyer-profile-data-safety.md` finding 1,
the seller already receives more buyer PII than they need. This channel should not
widen that; it exists to keep the seller's *existing* view accurate.

### Seller-side drain

A `SellerEventDrainer` (sellMain) observes the log, applies each unprocessed event to
the seller-owned customer store, then advances a cursor:

```
1. read cursor            seller_event_cursor/{sellerId}/lastTimestamp
2. observe events         seller_events/{sellerId}  where timestamp > cursor
3. for each, oldest-first:
     PROFILE_CHANGED   → patch approvedBuyerIds[buyerUUID] (and blocked list if present)
     lifecycle events  → existing handling / surface in seller UI
4. advance cursor to the newest applied timestamp
```

Ordering is by `timestamp`, applied oldest-first, so a rename followed by a second
rename lands in the right order. The drain must be **idempotent**: applying the same
`PROFILE_CHANGED` twice is a no-op, which makes a lost cursor write harmless (worst
case: re-apply).

Once the drain is live, `enrichWithDisplayNames` and its per-buyer `getBuyerDisplayName`
reads can be deleted — the customer list becomes the source of truth because the drain
keeps it current.

### Cursor placement — do not use `seller_profile`

The cursor needs a seller-owned node. It must **not** go in `seller_profile`, because
that node's `$field` rule grants read to *any* authenticated user for every field
except the three client lists:

```
"$field": { ".read": "auth != null && $field !== 'knownClientIds' && ... }
```

A cursor timestamp is not sensitive, but this is worth stating plainly: anything added
to `seller_profile` is world-readable to signed-in users unless explicitly excluded.
Use a dedicated node instead:

```
seller_event_cursor/{sellerId}
  .read   auth != null && auth.uid === $sellerId
  .write  auth != null && auth.uid === $sellerId
```

### Retention

RTDB has no server-side TTL. The log grows until pruned, and only the seller can
prune (buyers are blocked from delete by `!data.exists()` on write). After advancing
the cursor, the drain should delete applied `PROFILE_CHANGED` events — they are pure
deltas with no archival value once applied. Lifecycle events are book keeping and
should be retained.

## Rollout

Ordered so nothing breaks mid-flight, and so the old path stays alive until the new
one is proven:

1. **Fix eligibility** (blocker above). Backfill `knownClientIds` / align on
   `approvedBuyerIds`, and make the access-grant path's registration non-silent.
   Add rule tests for the newly eligible buyer shape.
2. **Extend the model** — `PROFILE_CHANGED`, `changedFields`. Serialization on both
   ends. No behaviour change yet.
3. **Rules** — add `seller_event_cursor`; adjust the `seller_events` write rule per
   the chosen eligibility option; extend `.validate` to accept `changedFields` and
   keep rejecting unknown top-level fields. Land rule tests in
   `firebase/tests/profile-orders-events.test.js` **before** deploying.
4. **Write side** — buyer emits `PROFILE_CHANGED` on rename, *in addition to* the
   existing `syncDisplayNameToLinkedSellers` write. Both paths run; nothing regresses.
5. **Read side** — implement the drain in sellMain, wire it into the seller session.
   Verify the customer list updates on a real device with a real rename.
6. **Cut over** — delete `displayName` from `buyer_access_status` writes, delete
   `syncDisplayNameToLinkedSellers`, delete `enrichWithDisplayNames` and
   `correctApprovedBuyerDisplayName`. Tighten the `buyer_access_status` write rule so
   buyers can no longer write any field there beyond the PENDING request itself.

Step 6 is the payoff: `buyer_access_status` goes back to doing only access control,
and the `status`-equality guard stops being load-bearing for identity data.

## Open questions

1. **Eligibility option** — A, B, or C above? C is recommended but the rule needs
   drafting against the emulator before committing.
2. **Drain trigger** — on seller app foreground only, or a live listener for the whole
   session? A live listener means a rename appears while the seller watches; it also
   means a long-lived connection per seller.
3. **Failure surfacing** — if a buyer's event write is rejected, should the buyer see
   anything? Currently the equivalent failure is silent. A silent failure here means a
   stale name, which is tolerable; a loud one is confusing ("your seller could not be
   notified"). Leaning silent, with a retry on next app start.
4. **Does the seller ever need to push to the buyer?** This design is one-way
   (buyer → seller). A seller-authored channel (e.g. "your order is ready") is the
   subject of `doc/order-ready-notification-design.md` and should stay separate — but
   if the two converge later, the node naming should not preclude it.
