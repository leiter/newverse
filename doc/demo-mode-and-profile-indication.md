# Demo Mode & Profile Data — Indication Proposal

**Status:** Implemented 2026-10-08 (items 1-5; item 6 deliberately left out)
**Scope:** Buy flavor only (`shared/src/buyMain`)
**Goal:** Tell the buyer two different things in two different ways —
*"something here is yours to finish"* (incomplete profile data) and
*"this account is not live yet"* (demo mode) — without either one occupying the
product feed.

---

## Why the two are entangled

They look like one message today because the code ties them together, in both
directions:

1. **Demo mode *is* the access status.**
   `BuyAppState.kt:96` — `isDemoMode = isAccessStatusLoaded && accessStatus != APPROVED`.
   There is no separate demo flag to show. `AccessStatus` is
   `NONE, PENDING, APPROVED, BLOCKED`, and anything but `APPROVED` is demo mode.
   The `access_status_approved` string already reads "Produktionsmodus aktiv",
   so the Access card on the profile page is *already* a mode indicator.

2. **Complete profile data is the precondition for leaving demo mode.**
   - `BuyAppViewModelAccess.kt:115` — `requestAccess()` calls
     `checkProfileCompleteness()` first and returns without sending anything if
     fields are missing, raising `showProfileIncompleteDialog` (all missing) or a
     missing-fields snackbar (some missing).
   - `BuyAppViewModelAccess.kt:229` — `connectWithToken()` parks the seller's
     token in `pendingConnectToken` and raises the same dialog. Even a buyer the
     seller has already authorised by QR code stays in demo mode until their own
     profile is filled in.

   Required fields (`MissingProfileData`, `BuyAppViewModelAccess.kt:20`):
   display name, default pickup time, and — unless `isSelfPickup` — street and
   house number.

So profile data is not merely *nicer to have* in demo mode; it is the gate. Any
rule that hides the requirement while in demo mode hides it in precisely the
state where it blocks the buyer.

---

## Current state of the UI

| Where | What | Problem |
|-------|------|---------|
| Home screen, above the feed (`MainScreenModern.kt:173`) | Permanent `DemoModeBanner`, text swaps to a profile-completion prompt when `isProfileIncomplete` | Spends feed space on every launch; conflates the two messages into one strip; one line can only say one of them |
| `CommonNavGraph.kt:32` | `isProfileIncomplete = isDemoMode && (displayName blank ‖ pickup time blank ‖ (!selfPickup && (street ‖ houseNumber blank)))` | Second, hand-rolled copy of the `checkProfileCompleteness()` rule — the two can drift |
| Profile page (`CustomerProfileScreenModern.kt:461`) | `DemoModeCard` — one line, "Einkaufsdemo" or "Produktivmodus" | Says strictly less than the `AccessStatusCard` directly below it |
| Profile page | `AccessStatusCard` — status text, buyer id, "Zugang anfragen", QR scan | Never mentions that a complete profile is required before that button can succeed |
| Bottom navigation | Basket count badge only | No signal that anything needs attention on the profile tab |
| Basket / checkout | Nothing | The order silently becomes `OrderStatus.DEMO_ORDER` (`BuyAppViewModelBasket.kt:688`); the buyer is never told |
| Buy order history | Nothing | Demo and real orders look identical; `order_status_demo` is rendered only in the **seller** app (`sell/OrderDetailScreen.kt:466`) |
| — | `demo_limit_reached` string exists in both locales | Never referenced from Kotlin; the 2-write Firebase demo budget (`BuyerSellerConfig.kt:43`) is invisible to the buyer |

---

## Proposed model

One rule decides which instrument is used:

> **A badge means a task the buyer can finish. A status line means a condition
> they are in.**

| Message | Kind | Instrument |
|---------|------|-----------|
| Profile data missing | Task — entirely in the buyer's hands, has a definite end | Dot badge on the profile tab + prompt at the top of the profile page |
| Demo mode / access status | Condition — resolution needs the seller | Status, in the Access card on the profile page |
| This order is a demo order | Consequence — appears at one moment | Inline notice in the basket, chip in order history |

Demo mode gets **no badge**: in `PENDING` the buyer can do nothing but wait, so a
dot they cannot clear becomes noise and teaches them to ignore dots — including
the profile one on the same tab.

---

## Changes

### 1. Profile tab badge — profile data only

A dot `Badge` (no number: it marks a state, not a count) on the profile item in
`BuyerBottomNavigationBar` and `BuyerNavigationRail`
(`BuyerBottomBar.kt`), shown when the profile is incomplete **and** the buyer
has reached for something that needs it:

- `accessStatus == APPROVED` — production; the data is genuinely required, and
  real orders carry it to the seller.
- `pendingConnectToken != null` (`BuyAppState.kt:90`) — they scanned the
  seller's QR code and their own profile is the only thing still in the way.
- *(optional, see open questions)* an access request was already attempted and
  refused.

A buyer who is just browsing in demo mode and has asked for nothing is **not**
badged. This is the part of today's behaviour that reads as nagging: the banner
appears on first launch, before the buyer has any idea what it is for.

New a11y string for the badge, e.g. `a11y_profile_incomplete_badge`
("Profil unvollständig" / "Profile incomplete").

### 2. Profile page — the prompt moves here

Where the home banner's profile-completion variant used to be: a notice at the
top of the profile page naming the missing fields, whose action scrolls to the
personal-info card. `checkProfileCompleteness()` already produces exactly that
list for `buildMissingFieldsMessage()`.

### 3. Single source of truth for completeness

Expose the completeness result on `BuyAppState` (e.g.
`val missingProfileData: MissingProfileData` or a `profileIncomplete: Boolean`
derived property) so the badge, the profile prompt and the `requestAccess()` /
`connectWithToken()` gates all read one rule. This deletes the duplicated
expression in `CommonNavGraph.kt:32` and keeps the UI from ever disagreeing with
the gate that rejects the request. `MissingProfileData` is currently `internal`
to `ui.state.buy` and would move or widen.

### 4. Demo mode folds into the Access card

- **Delete `DemoModeCard`** (`CustomerProfileScreenModern.kt:461` and `:1108`).
  It duplicates the card below it.
- Give `AccessStatusCard` a headline that names the mode — "Einkaufsdemo" for
  anything but `APPROVED`, "Produktionsmodus" for `APPROVED` — above the
  existing status line.
- **State the precondition in the card**, directly above "Zugang anfragen":
  while the profile is incomplete, a line listing what is still missing. The
  buyer learns the requirement while considering the button instead of being
  refused after tapping it, and the dialog at
  `BuyAppViewModelAccess.kt:118` becomes a backstop rather than the primary
  channel.

Net effect: the profile page gets **shorter**, and demo mode has exactly one home.

### 5. Demo consequence at the point of consequence — the basket

One line above the checkout button while `isDemoMode`, with a text link to the
profile access card. Wording must stay accurate about the routing in
`BuyAppViewModelBasket.kt:693-700`: the first two demo orders *are* written to
Firebase `demo_orders/` for seller observability, the third migrates them to
local storage, and later ones are local-only. So the honest line is
"Demo-Bestellung — unverbindlich", not "wird nicht übermittelt".

Pair it with a "Demo" chip on demo orders in the buy order history, so past
demo orders stay distinguishable from real ones. `order_status_demo` already
exists in both locales.

### 6. Optional: always-on marker in the top app bar

If demo mode should be visible on every screen without costing feed space, a
small "Demo" assist chip next to the title in `AppScaffold.kt`, tappable →
profile access card. Zero vertical cost. Deliberately listed last: items 4 and 5
may well be enough, and the top bar already does custom layout work on the
basket route.

### Explicitly not doing

- No demo element of any kind on the home screen.
- No demo badge in the bottom navigation.
- No new nagging at app start: every indicator above is either tied to a buyer
  action or confined to the profile page.

---

## Strings

**Add:** profile-incomplete badge a11y label; Access-card mode headlines
(demo / production); Access-card precondition line; basket demo notice + its
action label.
**Remove:** `demo_mode_banner_message`, `demo_mode_banner_action`,
`demo_banner_profile_incomplete`, `demo_banner_complete_profile` (home banner),
`mode_demo` / `mode_production` (`DemoModeCard`), unless reused by the new
Access-card headline.
**Already present, newly used:** `order_status_demo` in the buy flavor.
**Still unused afterwards:** `demo_limit_reached` — surfacing the 2-write demo
budget is a separate question, not part of this proposal.

---

## Decisions taken at implementation

The open questions were settled as follows; each is cheap to revisit.

1. **"Attempted and refused" does not badge the tab.** `showProfileAttentionBadge`
   uses only `accessStatus == APPROVED` and `pendingConnectToken != null`, so no
   new flag was added to `BuyAppState`.
2. **The profile prompt is an inline notice at the top of the page.** Its button
   starts editing and scrolls to the personal-info card, which is anchored with
   its own `BringIntoViewRequester`.
3. **The top-bar "Demo" chip (item 6) is out.** Items 4 and 5 carry the mode.
4. **`BLOCKED` and `PENDING` get no request link.** The basket notice takes
   `canRequestAccess = accessStatus == NONE`, and the Access card's precondition
   line is shown under the same condition; `BLOCKED` remains the card's
   error-colored status and nothing else.
