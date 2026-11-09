# What the screenshots were holding

> **Folded into the roadmap on 2026-11-09.** Section A now lives in `doc/plans/TODO.md`
> — under "High Priority: Account deletion reaches no orders", "Medium Priority:
> Recovered from the screenshots, 2026-11-08", and appended to the existing Bookkeeping,
> BNN, Tablet and Housekeeping sections. **Track the work there.** This file is kept as
> the dated snapshot: it holds the reasoning, the per-screenshot verdicts, the ten
> `ActionKind.MENU` points in full, and section C's record of what was dropped and why —
> none of which belongs in a roadmap.

**Written:** 2026-11-08
**Source:** the 30 terminal screenshots in `~/Bilder/Bildschirmfotos/newverse/`
(2026-05-04 → 2026-11-06), read in full and checked against the code and `doc/`.
**Purpose:** so the screenshots can be deleted. Everything below is either already
tracked somewhere (with the pointer given) or is new and needs a decision.

Each screenshot is a frozen half of a conversation: a finding list, a design
question, or a "want me to do this?" that was never answered. Verdicts below are
from reading today's code, not from the screenshot's own claim.

---

## A. New, and worth noting

### A1. `clearUserData` reads a path that no longer exists — account deletion cancels nothing

**Severity: high.** `GitLiveProfileRepository.kt:424` loads each of the buyer's
orders from `seller_profile/{sellerId}/orders/{date}/{orderId}`. Orders live at
`/orders/{sellerId}/{yyyyMMdd}/{orderId}` (`GitLiveOrderRepository.kt:62`,
`ORDERS_ROOT`), and this is the **only** site in the Kotlin still reaching for the
old shape. So "Konto löschen" walks `placedOrderIds`, finds nothing at any of
them, records `"Order X not found"` into `errors`, and deletes the profile and the
auth account anyway — leaving the seller holding live future orders for a buyer
who no longer exists. The 2026-08-21 screenshot flagged two *design* caveats here
and did not notice that the lookup itself is broken.

Related, from the same screenshot and still true:

- **Only the connected seller's orders are cancelled.** `clearUserData(sellerId, …)`
  takes one seller, but `placedOrderIds` is a flat `Map<date, orderId>` with no
  seller dimension. A buyer who ever ordered from a second seller leaves those
  orders standing. (Moot while there is one seller; not moot in the data model.)
- **A cancellation failure does not stop the deletion.** Errors are collected into
  `CleanUpResult` and the profile and auth account go anyway. `ACCOUNT_DELETED`
  carries `cancelledOrderCount`, so it is at least visible in the log, but nothing
  reacts to it.
- **Guest logout cancels nothing at all.** `confirmGuestLogout()`
  (`BuyAppViewModelAuth.kt:387`) deletes the profile and never calls
  `clearUserData`. The dialog says so out loud ("Bestellungen bleiben erhalten"),
  so this is deliberate — but it is the opposite asymmetry from "Konto löschen"
  and worth deciding rather than inheriting.

Apple token revocation itself, the headline of that screenshot, **is done**
(`AppleTokenRevoker`, commit `2ac3a57`).

### A2. The cart badge's shake ignores the OS reduced-motion setting

`BuyerBottomBar.kt:375-395` runs a four-stage ~450ms rotation on every basket-count
change, unconditionally. Compose Multiplatform has no common reduce-motion flag, so
this needs an `expect/actual` reading `Settings.Global.ANIMATOR_DURATION_SCALE` on
Android and `UIAccessibility.isReduceMotionEnabled` on iOS — nothing of the kind
exists in the tree today. Low severity (one icon, purely decorative, skipping it
costs nothing), but it is the one accessibility defect from 2026-10-15 that was
never fixed: the plural string and the announcement order both were.

### A3. The Abrechnung's gaps as a bookkeeping product

From 2026-09-21. The export works; what it is *not* was never written down:

- **Only confirmed sales are booked.** An order picked up but never confirmed is
  missing from the books and nothing in the app points it out. **A list of
  "unbooked pickups" is the obvious next feature** — this was called the biggest
  practical risk and does not exist in the code (`grep` finds the phrase only in a
  test name).
- **Only revenue.** Purchases (Terra's invoices) are not in the export; the
  purchase price is used only to show gross profit.
- **No cash book.** Payment method is deliberately not recorded, so a separate cash
  book is still needed and the export cannot replace it.
- **No customer receipts**, and no tax number or company details in the export.
- **Receipt numbers are unique but not consecutive** (`V-P23oEOE…`).
- **Retention is unbacked.** Sales cannot be deleted from the app, by design — they
  must be kept 10 years — but anyone with owner access to the Firebase project
  still can, and there is no backup.

The tax-advisor questions from the same conversation **are** in
`doc/plans/TODO.md:304`.

### A4. BNN: producer names resolve for ~10 of 52 supplier codes

From 2026-09-20. The gastro catalogue covers Terra's dry/dairy assortment; the
produce codes (`SCH`, `RHO`, `AHR`, `BOA`, `ZAN` and the lower-case regional ones)
are in no file we hold, so those products get origin and certification but no
"Erzeuger:" line. **The action is to ask Terra for the produce supplier list** —
not a code change. Also: "Berlin/Brandenburg" in `BnnCodeTables.REGIONAL_DESCRIPTION`
is our wording, not Terra's, whose legend says "von der Lausitz bis zur Ostsee"
with depots in Berlin, Rostock and Leipzig.

### A5. `ActionKind.MENU` — the design note that was asked for and never written

From 2026-11-06, the last screenshot. The question was whether the top-bar contact
menu should become a published `ActionKind.MENU` row so a seller can place contacts
on any screen, with About keeping the full list. The answer was yes-but-narrow, with
ten things to settle first; the user said **"write it up as a design note first"**
and the session ended. `doc/design/` has no such file, and
`doc/guides/seller-contact-design.md` is the as-built 2026-10-19 doc, not this.

The ten points, so they are not lost: (1) kind comes from the slot, so this needs
new `TOPBAR_HOME_MENU` / `TOPBAR_PROFILE_MENU` locations rather than a new kind
alone; (2) the menu must name a *source* (a selector over the contact directory),
not hold copies of rows, or it goes stale the moment the seller edits a number;
(3) a source-based menu keeps `resolveFor(contextualMarketId)`'s market union for
free, and a copying one silently loses it — the failure most likely to go unnoticed
until a buyer rings the wrong stall; (4) entries must not become free-form, or
`tel:`/`mailto:` ends up in a stored value and the rules forbid a colon in
`OPEN_URL` precisely to stop that; (5) `ActionAudience.stored(null)` returns
`EVERYONE`, which is the wrong default here — default this kind to `APPROVED_ONLY`
or keep the hard `canContactSeller` gate as a floor; (6) hide the row when the
resolved list is empty; (7) `HIDE_ON_ACTION` is ambiguous for a menu — pin `MENU`
to `UNTIL_CANCELLED` and don't offer the picker; (8) there is no icon field, which
is where "generic menu" starts growing; (9) every existing install silently loses
the contact button until the seller publishes a row — seed one at deploy or accept
it explicitly; (10) keep contacts on the Einkaufsdemo access card either way, since
that buyer cannot order yet and most needs to ask. `firebase/tests/seller-ui-actions.test.js`
needs the new kind and locations too.

### A6. Tablet work the 2026-10-26 audit did not carry over

`doc/plans/TODO.md:356` has the width caps, Messages two-pane, the missing
previews, the un-written conventions guide and the iOS status bar. These five, from
2026-07-23, are not in it:

- **A reusable `TwoPaneLayout(list, detail, paneRatio)`.** Three panes each
  re-implement "list-or-detail by width" by hand; a shared one in the adaptive
  package would consolidate that and make the fourth conversion (Messages) a few
  lines.
- **Spike `androidx.compose.material3.adaptive`.** `NavigationSuiteScaffold` would
  replace the hand-rolled rail switch and `ListDetailPaneScaffold` gives real
  back-handling, predictive-back and pane restoration that the manual selection
  state doesn't. Hand-rolling was the right call to ship (KMP/JS artifact risk);
  the question is whether that risk is still real. **Spike and decide, not a given.**
- **Tablet portrait (Medium, 600–839dp) has had no visual pass** — everything was
  checked in landscape, and Medium is the class most likely to look wrong
  (2-column grids slightly too tight, forms that feel lost).
- **Multi-window / foldables.** The phone portrait lock uses `requestedOrientation`,
  which interacts oddly with split-screen and freeform. Layout keys off measured
  width so content should adapt — worth *confirming* the lock doesn't fight
  resizable mode on newer large-screen Android.
- **Roborazzi screenshot tests** for the converted screens — offered as "what makes
  everything else safe to change", never set up.

Small UX polish from the same list: auto-select the first item in a detail pane on
wide screens instead of a placeholder; remember the list scroll position when
switching panes; cap the ProductDetail description column so text doesn't run to
900px.

The **web** item from that screenshot (check the buy screens resized in a browser)
is dead — web is not maintained, see `doc/plans/TODO.md` and `CLAUDE.md`.

### A7. Seller ↔ buyer sync channel: the ordinary work that is left

`doc/design/seller-buyer-sync-channel-design.md` says it plainly and
`doc/plans/TODO.md` does not mention it at all: the eligibility blocker is
**resolved**, and what remains is a `PROFILE_CHANGED` event type, a producer at the
rename site, and a drain on the seller side. `observeEvents` still has **no
callers** — the whole seller-side read of the channel is unbuilt. Two cautions from
the 2026-09-28 session that belong with it: don't put the drain cursor in
`seller_profile` (that node's `$field` rule grants read to any authenticated user
for every field except the three client lists, so anything added there is
world-readable to signed-in users — use a separate `seller_event_cursor`), and
retention is on us, since RTDB has no server-side TTL and the append-only guard
stops buyers deleting their own events.

### A8. Buyer-profile data safety: two findings open, and the table is stale

`doc/reports/buyer-profile-data-safety.md`'s summary table still lists all four as
Open; its own banner and the code disagree. **#3 (PII in logcat) is fixed** — the
`Log` refactor landed and the policy is settled (PII is fine in `Log.d`, out of
`Log.w`/`Log.e`). **#4 (no order schema validation)** looks done —
`firebase/database.rules.json` now has 117 `validate` clauses; worth confirming the
order nodes specifically are among them. Genuinely open:

- **#1 — the buyer's email is embedded in every order.** The seller does not need
  it to fulfil a pickup; messaging already exists as the contact route. Named in
  the report's own banner as the main one.
- **#2 — `BuyerProfileDto.fromDomain()` carries `placedOrderIds` and
  `favouriteProductIds` into `OrderDto`**, so the seller sees the buyer's whole
  order history and favourites.

Either way the table needs a pass so it stops contradicting the banner above it.

### A9. Verification debt — things built but never watched run

- **The stock refill notification has never been seen on a device** (2026-10-20).
  It fires at 07:00 the morning after an edit deadline, so it needs a wait or a
  device clock change. 19 unit tests cover its rules; none of them is the same as
  watching it arrive.
- **The iOS side of Apple account deletion is uncompiled and unverified.**
  `ContentView.swift` and `AppleSignInHelper.swift` were reviewed but never built;
  `pod install` has never run on the Mac. The three Mac checks from 2026-08-21 are
  still outstanding: that `import FirebaseAuth` resolves transitively through the
  shared pod (12.13.0); that `AppleRevokeBridge.shared.setHandler { onSuccess, onError in … }`
  matches the generated Objective-C header, whose Kotlin function-type params
  sometimes surface with different labels; and that deleting an Apple-backed
  account end to end makes the token disappear from **Settings → Apple ID → Sign in
  with Apple** — which is the check an App Store reviewer performs.

### A10. Small open items not in TODO

- **Re-authentication before deletion.** `delete()` demands a recent login and auth
  sessions here are long-lived, so `deleteAuthAccountOrSignOut` hits its
  retry-then-record path often — logging an orphan rather than completing a
  deletion. Prompting for a fresh sign-in on `requires-recent-login` would close
  that path properly instead of recording it. (`requiresRecentLogin` appears
  nowhere in the Kotlin outside a comment.)
- **The `AuthProvider` profile badge heuristic is still wrong.** `NavGraph.kt:95`
  derives the provider by sniffing the email for `@gmail.com`, so a Google user
  with a non-gmail address shows as "E-Mail". Cosmetic, and deliberately left when
  `getProviderIds()` was added for the revocation path — but the sniff is still
  there.
- **`AboutScreenModern`'s `ContactChannelUi.kt` has no accessibility semantics at
  all** (`doc/guides/accessibility.md`'s own banner says so), and that guide's
  coverage map is explicitly out of date for everything added since 2026-10-20.

---

## B. Already tracked — pointer only

| From | Item | Where it lives |
|---|---|---|
| 2026-09-23 | The whole buyer-app open-task sweep: bugs A1/A2/B1/B2 fixed, C1/C2/C3/E1/E2/E3/G1/G2/G3 open; push notifications; pull-to-refresh; one error display; promo codes; Twitter sign-in; versionCode; iOS App Store; web; failing tests; `ForcedLoginScreen` password toggle; `DEFAULT_SELLER_ID` | `doc/plans/TODO.md` — the user said "add them to the TODO" and that is where they are |
| 2026-09-21 | Tax-advisor questions; BNN availability flag, weight per piece, re-import duplicates, 45 % markup as a setting; CSV trailing spaces; the 7 failing unit tests | `doc/plans/TODO.md:304`, `:316`, `:453`, `:455` |
| 2026-10-19 | Market pricing: 6 steps, only closures done, step 1 (Market as an entity) is the blocker | `doc/guides/market-pricing-and-access-design.md`; the per-market pickup question is `doc/plans/TODO.md:101` |
| 2026-10-08 | Default seller id per build (Fix 1) — **done**; `loadMainScreenArticles` clears `isLoading` only inside `.collect`, so an empty catalogue hangs — **still true** at `BuyAppViewModelMainScreen.kt:511` | tracked as "Buyer article loading hangs on an empty catalogue", `doc/plans/TODO.md:447`; the iOS plist/two-app registration is `doc/plans/build-identity-and-firebase-wiring.md` |
| 2026-08-31, 2026-09-01 | The entire iOS accessibility programme — basket cards, radio-option semantics, `ProductDetailScreen`, `CategoryChips`, the dialog cluster, `OrderHistoryScreen` | **all done**; see the coverage map in `doc/guides/accessibility.md` |
| 2026-10-15 | Nav-bar a11y: plural string and announcement order | **both fixed**; the third (reduced motion) is A2 above |
| 2026-11-05 | Hero / top-bar scroll model — the open question picker | **decided and built**, commit `d27ce73` ("Let the hero card move the top bar instead of the product list") |

---

## C. Dropped — do not re-file

- **2026-05-04, the nine remaining buy-app review findings** (`#3`–`#8`, `#11`–`#13`:
  redundant `it.id` check in `addToCart`, fire-and-forget `updateOrderStatus`,
  `showMergeDialog` read from global state, `firstOrNull { it.canEdit() }` assuming
  order, `updateDemoOrder` swallowing exceptions, a redundant `containsValue`
  precheck, top-level date helpers, `println` logging, O(N²) change detection).
  Six months stale against a file that has gone from ~900 to 2831 lines. `#11` and
  `#12` are done (`basketScreenFormatDateKey` is top-level; the `Log` refactor
  landed in `d659337`). The rest no longer sit at the quoted lines and are
  micro-cleanups of the kind `/simplify` finds in a minute. Re-run a review on the
  current file if the itch returns; don't carry the 2026-05 list forward.
- **2026-05-05, the four CocoaPods screenshots.** Workstation setup for a Mac that
  wasn't at hand: `brew install cocoapods`, the `/opt/homebrew` ownership fix,
  `pod install`, and why an `.xcworkspace` cannot be generated on Ubuntu. That last
  point is the only durable one and it is in `doc/guides/ios-setup.md`'s territory
  already.
- **2026-05-05 08-01**, a file listing of the Firebase layer. No task in it.
- **Web**: resizing the buy screens in a browser window (2026-07-23). The platform
  is not maintained.
