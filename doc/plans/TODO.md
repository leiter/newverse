# Newverse Feature Status

**Last Updated:** 2026-11-09

*Note: This file replaces the previous TODO.md. The TODO.md file in the root directory is now obsolete and should be deleted.*

This document outlines the current implementation status of core features and a roadmap for future development.

---

## Implemented Features

### Core Infrastructure
- ✅ Kotlin Multiplatform setup with Android & iOS targets
- ✅ Compose Multiplatform for shared UI
- ✅ Koin for dependency injection
- ✅ MVVM architecture with refactored ViewModels
- ✅ Clean Architecture principles
- ✅ Complete Material3 theme system (light/dark themes) — on the Bodenschätze flyer palette since 2026-10-26, see `doc/guides/theme.md`
- ✅ Type-safe, flavor-aware navigation system with modal drawer

### iOS Platform Features
- ✅ Image Picker (photo library and camera)
- ✅ Google Sign-In
- ✅ Document Picker
- ✅ Native Sharing
- ✅ QR Code Scanning

### Buyer Features (Buy App)
- ✅ User authentication (Google, Apple, Email, Anonymous)
- ✅ Product browsing and real-time search
- ✅ Product detail view
- ✅ Shopping basket and checkout flow
- ✅ Order editing with deadlines
- ✅ Merge conflict resolution for existing orders
- ✅ Order history
- ✅ Favorites persistence
- ✅ Password reset flow
- ✅ Change password while signed in (both flavors)

### Seller Features (Sell App)
- ✅ User authentication
- ✅ Product and order overview with revenue calculation
- ✅ Market management (CRUD operations for market locations)
- ✅ Customer management (approve, block, unblock buyers)
- ✅ QR code / deep link based invitations for buyers
- ✅ Seller-only article data (purchase price, markup, sourcing) in `/seller_articles`, hidden from buyers
- ✅ BNN price list import with purchase price, VAT rate and sourcing codes
- ✅ Pickup confirmation: book what was handed over as a sale, cancel a booking, mark "nicht abgeholt"
- ✅ Abrechnung from booked sales by calendar week / month
- ✅ CSV export of a week or month for the tax advisor (share sheet on Android and iOS)
- ✅ Walk-in market sales from the Abrechnung (catalog price prefilled and editable), listed with the period's bookings and cancellable
- ✅ Buyer edits keep the stored order's message, market and seller's "hidden" flag (fixed 2026-09-21)

---

## Roadmap & Future Work

The following features are planned but not yet implemented, or are only partially complete.

### High Priority: User Experience
- **The keyboard covered the bottom of every form**
  - **Status:** Fixed 2026-11-04 on Android, and **checked on two phones on 2026-11-09**
    — Pixel 2 (Android 11 / API 30) and Pixel 7a (Android 17), both on the buy release
    build, versionCode 40. The check found one defect, fixed below. **Still not checked
    on iOS at all.**
  - **Issue:** The app draws edge to edge (`enableEdgeToEdge` in all three activities, and
    — until 2026-11-09 — no `windowSoftInputMode` anywhere), so the window is *not*
    resized when the keyboard opens. Nothing in the app consumed `WindowInsets.ime` — `imePadding` appeared in no
    file. Almost every screen with a field already had a scroll container, but a scroll
    container does not help: the layout still ended behind the keyboard, so there was no
    scroll range to bring the lower fields up. Anything in the bottom half of a form —
    the password field on login, the lower rows of the buyer and seller profiles, the
    message composer in both conversation screens, the delivery address in the checkout
    dialog — was unreachable while typing.
  - **Fix:** `Modifier.keyboardPadding()`, an expect/actual in `KeyboardUtils.kt` beside
    the existing `KeyboardManager`, applied at the one place each surface passes through:
    the `Scaffold` content row of both flavors' `AppScaffold.kt`, plus the three screens
    that render outside it (`LoginScreen`, `RegisterScreen`, `ForcedLoginScreen`). The
    scaffolds pair it with `consumeWindowInsets(innerPadding)` so the bottom bar's height
    is not charged twice and the content gives up exactly `max(bottom bar, keyboard)`.
    Five input dialogs that had no scroll of their own got one, matching what
    `ChangePasswordDialog`, `EmailLinkingDialog`, `MarketEditDialog` and
    `PickupEditorDialog` already did: the two password-reset dialogs,
    `CheckoutConfirmDialog`, and `StockScreen`'s booking and shortfall dialogs.
  - **Why it is a no-op on iOS:** `ComposeUIViewController` runs with the default
    `OnFocusBehavior.FocusableAboveKeyboard`, which lifts the focused view for the whole
    scene, dialogs included — padding as well would move a focused field twice. The
    documented alternative is `OnFocusBehavior.DoNothing` plus explicit insets, which
    would also mean padding every dialog by hand, since an iOS dialog is drawn inside the
    same Compose scene rather than in a window of its own. Left as it was.
  - **Found on the device, and fixed (2026-11-09): the first focus padded by both.**
    On the buyer's profile, tapping *edit* and then focusing a field — Anzeigename —
    left the content padded by the bottom bar's height **plus** the keyboard's, instead
    of the larger of the two. Closing the keyboard and opening it again gave the correct
    padding, and it stayed correct from then on.
    - **The arithmetic named the fault.** The overshoot was exactly
      `paddingValues.bottom` — M3's `NavigationBar` height plus the navigation-bar inset
      it applies itself — which means that on the first pass `keyboardPadding()` was not
      seeing the `consumeWindowInsets(paddingValues)` consumption, and the content got
      `bar + ime` rather than `max(bar, ime)`.
    - **Cause: no `windowSoftInputMode` was set anywhere.** Not in any of the five source
      manifests, not in the merged manifest, not in `AppTheme` or `AppTheme.Splash`. The
      window was therefore `adjustUnspecified`, which the framework resolves on the
      *first* keyboard show and then keeps — which is the shape of the bug exactly: wrong
      once, right every time after.
    - **Fix:** `android:windowSoftInputMode="adjustResize"` on `BuyMainActivity` and
      `SellMainActivity`. It is the documented pairing for `enableEdgeToEdge()`: the flag
      does not make the window resize (`decorFitsSystemWindows=false` still prevents
      that, so everything written above about insets still holds) — it makes the IME
      inset **dispatched consistently**, from the first show.
    - **Verified on both phones**, which matters because they sit on either side of the
      API 35 line where edge-to-edge became mandatory and soft-input resolution changed:
      Pixel 2 / Android 11 and Pixel 7a / Android 17, both correct after the change.
    - **The flag has no reference in Kotlin**, so it looks unused and is the kind of line
      that gets deleted in a tidy-up. `KeyboardUtils.kt` says what depends on it, in both
      the `expect` and the Android `actual`. Keep those notes with it.
  - **Tasks:**
    - Check the five screenshot-run fixes on an iOS simulator. On Android they now stand
      on a device. Watch for the bottom bar: it stays put behind the keyboard by design,
      and on Home the basket stands in for it as a top-bar action for as long as it is
      covered (`isKeyboardHidingBottomBar`, false on iOS because the scene pans there).
    - **Unverified, raised 2026-11-09:** whether the stand-in basket is still the right
      behaviour now the flag is explicit. It is driven by `isKeyboardHidingBottomBar()`,
      which asks whether `WindowInsets.ime` is non-zero. The reasoning says the window is
      still not resized, so the bar is still covered and the stand-in is still a
      stand-in — but nobody has looked at Home with a quantity field focused since the
      change. If the bar turns out to be visible above the keyboard, the stand-in is a
      second copy rather than a replacement, which its own comment says it must never be.
    - On iOS, confirm a long form (`CustomerProfileScreenModern`) and a dialog with a
      field still reach their lower fields; if not, switch `MainViewController` to
      `OnFocusBehavior.DoNothing` and make `keyboardPadding()` real there — at which
      point every dialog with a field needs it too.
    - `androidApp/.../ui/NotificationSettingsScreen.kt` has no callers. It was left
      untouched; delete it or wire it up.
- **Pickup confirmation at checkout, one slot per market**
  - **Status:** Decided 2026-10-16, not implemented. Reasoning in
    `doc/guides/ordering-business-rules.md` §2; what it does to the slot picker in
    `doc/guides/market-pricing-and-access-design.md` §4 ("One slot per market, confirmed at
    checkout").
  - **Issue:** The buyer is offered five dates per market and the pickup date is then
    chosen in a dialog, which promises a catalogue and a price the seller cannot commit
    to that far ahead — `OrderedProduct` snapshots both at order time. The decision is to
    offer only the next open slot per market and to make the step a confirmation at the
    moment of commitment.
  - **Tasks:**
    - ✅ **Done 2026-10-19** — offer one slot per eligible market instead of five dates
      each: `PickupSlots.nextPerMarket`, called from `BuyAppViewModelBasket.kt:1157`.
      `next(count)` is unchanged for other callers. Covered by five cases in
      `PickupSlotTest` and by the two `BuyAppViewModelSlotTest` cases, which now assert
      one slot per market.
    - ✅ **Done 2026-10-20** — the checkout path shows `CheckoutConfirmDialog` before
      the order is placed. `Checkout` validates and opens it; `ConfirmCheckout` writes.
      Seven cases in `BuyAppViewModelCheckoutConfirmTest`.
    - ✅ **Done 2026-10-20** — the edit deadline and the time remaining are in it, with
      `formatTimeUntilDeadline` and `getDeadlineWarningLevel` colouring the remaining
      time by urgency. Both were unused until this.
    - ✅ **Done 2026-10-20** — one line for demo buyers, plus the market named when the
      seller runs more than one.
    - State that a reorder is priced today (`basketScreenReorder` already re-reads each
      article, so this is wording, not logic). **Still open.**
    - No confirmation in edit mode; show the deadline inline on the basket instead.
    - ✅ **Settled 2026-10-20** — `availableSlots: List<PickupSlot>` carries the N rows,
      `availablePickupDates` is kept in step for callers that read dates alone, and
      `showCheckoutConfirm` is the confirmed flag.
    - Announce the deadline to screen readers, not only colour it by
      `DeadlineWarningLevel`.
    - Strings in `values/` and `values-en/`, positional specifiers (`%1$s`) — see
      `substituteFormatArgs` in `util/FormatUtils.kt`.
    - Render `MarketClosure.reason` for a closed day instead of omitting the day
      (market design doc, open question 7 — settled).

- **Pickup date and deadline are computed in the device timezone**
  - **Status:** Decided 2026-10-16, not implemented. Rule rewritten in
    `doc/guides/ordering-business-rules.md` §7.3, which used to prescribe the device zone.
  - **Issue:** This is the cause of manual-test-plan **E2 (S1 critical)** below. A
    market happens on its own calendar, but `OrderDateUtils` (14 uses of
    `TimeZone.currentSystemDefault()`), `PickupSlot.kt` and `GitLiveOrderRepository`
    derive the pickup day, the deadline and the `yyyyMMdd` path segment from the
    device's zone (`PickupSlot.kt:41`/`:83`/`:110`,
    `GitLiveOrderRepository.kt:533`/`:881`), while `BasketScreen.kt:1138`/`:1175` and
    `DateFormattingUtils.kt:16` are already Berlin-pinned (that split is E1).
    Re-verified 2026-10-20: 42 non-test uses of `currentSystemDefault()` against three
    Berlin pins, and the pins are all on the display side — the inverse of the rule.
    Table in `doc/guides/ordering-business-rules.md` §7.3.
  - **Tasks:**
    - Pin pickup, deadline and date-key arithmetic to the seller's zone; leave display
      in the device zone.
    - Check what existing orders hold before changing how keys are derived — a buyer
      whose key was written in another zone is already orphaned.
    - Then re-run manual-test-plan E1 and E2 on a device.

- **One order per pickup date: the rule and the code disagree**
  - **Status:** Open decision, raised 2026-10-16. Recorded in
    `doc/guides/ordering-business-rules.md` §7.5.
  - **Issue:** §7.4/§7.5 say a date that already has an order cannot get a second
    basket — load the order and edit it. The app builds a second basket and merges at
    checkout (`BuyAppViewModelBasket.kt:695`, plus the history path at
    `BuyAppViewModelHistory.kt:109`). With one orderable slot this becomes the normal
    case rather than an edge, so it has to be settled either way.
  - **Tasks:**
    - Decide: route into edit mode and delete both merge paths, or amend §7.4/§7.5 and
      keep merging. Recommendation is the former; it changes behaviour buyers have today.

- **Push Notifications**
  - **Status:** Not Implemented — options written up in `doc/design/order-ready-notification-design.md` (in-app banner first, then FCM + Cloud Function, and a configurable WorkManager reminder engine)
  - **Tasks:**
    - Set up Firebase Cloud Messaging (FCM) for Android and iOS.
    - Implement notifications for order status changes (buyer) and new orders (seller).

- **Pull-to-Refresh**
  - **Status:** Not Implemented
  - **Tasks:**
    - Add pull-to-refresh functionality to the Order History screen.
    - Add pull-to-refresh to the main Products list screen.

- **Error Handling Standardization**
  - **Status:** Partially Implemented
  - **Issue:** Error display is inconsistent across different screens.
  - **Tasks:**
    - Create a unified error component (e.g., a full-screen error message with a retry button).
    - Standardize all ViewModels to use a single error state pattern (e.g., `AsyncState.Error` or a global dialog).

### High Priority: Suspected Bugs (Buy App)
- **Manual test plan still unrun**
  - **Status:** Open — suspected from reading the code, not yet observed; no commit since 2026-09-10 addresses them
  - **Issue:** `doc/reports/manual-test-plan.md` lists the steps for each. Buyer-side items:
    - A1 (critical): ~~`formatPrice()` shows most prices one cent too low.~~ **Fixed** (`eafaf1e`) — rounds via `Money.toCents()` instead of truncating a Double.
    - E2 (critical, needs confirmation): a time-zone change can orphan a placed order.
    - A2: ~~kg amounts render 1 g short (`1,2 kg` → `1,199 kg`).~~ **Fixed 2026-09-25** — `OrderedProduct.getFormattedAmount()` rounded a Double then re-derived the fraction by subtraction, which reintroduced binary floating-point noise; now rounds directly into integer grams/cents, same pattern as `Money.toCents()`. Covered by `OrderedProductTest.kt`.
    - B1: ~~the pickup date picker only ever offers 2 dates.~~ **Fixed 2026-10-04** (`ea5d84b`) — `OrderDateUtils.getAvailablePickupDates()` built up as many dates as `count` asked for and then returned `dates.take(2)`; it now returns them all. Its one caller already asked for 5. **Superseded 2026-10-16:** the caller should ask for one slot per market, not five dates — see the pickup-confirmation entry above.
    - B2: ~~an order reads as "pickup passed" for all of pickup day.~~ **Fixed 2026-10-04** (`ea5d84b`) — `OrderDateUtils.getOrderWindowStatus()` compared `now` against `pickupDate`, which is always midnight at the *start* of pickup day, so the order read as passed from 00:00 on. It now compares against the end of the pickup day, resolved through the local date so the cutoff holds across a DST boundary.
    - C1: re-adding an item corrupts its displayed amount/unit.
    - C2: a quantity edit can set a negative piece count.
    - C3 (needs confirmation): basket items with a blank product id merge together.
    - E1: the basket renders dates in Berlin time, the rest of the app in device time.
    - E3 (needs confirmation): a failed profile write leaves the order in Firebase but not in the buyer's list.
    - G1: 46 strings untranslated — German text in the English UI.
    - G2/G3: hardcoded English error messages in the German app.
  - **Tasks:**
    - Run the remaining steps on a device, mark each verdict, then fix the confirmed ones — E2 next.
- **Found while retaking the App Store screenshots**
  - **Status:** Open — observed 2026-11-04 on the iPhone 17 Pro Max and iPad Pro 13" simulators, Debug-Buy, as a fresh guest against the live backend; not yet traced in the code
  - **Issue:**
    - ~~**Wrong weekday in the basket.**~~ **Fixed 2026-11-04.** The pickup picker offered "Freitag, 06.11.2026" (Ökomarkt im Hansaviertel); once picked, the basket's Abholdatum card read "Donnerstag, 06.11.2026". `PickupDateSelector` and `ReorderDateOption` in `BasketScreen.kt` printed `day_thursday` as a literal next to the real date — the last two sites the `DayOfWeek.displayName()` helper was written for and missed. Both now derive the weekday from the same instant and zone as the date itself.
    - ~~**List and basket disagree on price.**~~ **Fixed 2026-11-04.** The basket was the side that was right. The catalogue rendered `Article.price` raw — the seller's base price, no market conditions — while the basket snapshotted `BuyAppState.priceOf`, which applies them: 2,30 € was the base, 2,48 € the Ökomarkt price. Choosing the other market's slot repriced the basket to *its* (unadjusted) price, which is why both then read 2,30 € and the bug looked like it had healed. `MainScreenState` now carries the active `pricing` and exposes `priceOf`, filled in one place by the derived `BuyAppState.mainScreenState`, and all four catalogue surfaces — product card, hero card, product detail, favourites — take the resolved price instead of the article's. `correctArticleData` had the same defect in a path that sets what gets charged: bringing an order back from history repriced it to the base and left `priceComponents` describing a total they no longer summed to; it now goes through `pricedWith(resolvePrice(...))`. `CataloguePricingTest` pins the shelf against the basket, including that resolving an already-resolved price would charge the surcharge twice.
    - ~~**Pickup time shown but not saved.**~~ **Fixed 2026-11-04.** A new guest's profile showed "Abholzeit 15:45 Uhr" while `defaultPickUpTime` stayed empty, so `missingProfileData()` kept reporting Abholzeit missing and `requestAccess()` stayed blocked against a field that looked filled in. The "15:45" was invented in `CustomerProfileViewModel` for display only and is gone: an unset time now reads `--:--` with "Zum Festlegen tippen", the row opens the editor, and the editor no longer styles a blank field as an error. The screen and the gate now say the same thing, and the seller only ever gets a time the buyer chose. **The concept behind it is still open — see "Pickup time is not part of an order" below.**
    - ~~**English in the German pickup picker.**~~ **Fixed 2026-11-04.** `OrderDateUtils.formatTimeUntilDeadline` builds that sentence out of hardcoded English, and a plain util cannot read Compose resources. The arithmetic stays in the util; the wording moved to a composable, `timeUntilDeadlineText` (`ui/components/DurationText.kt`), over eight new `duration_*` strings in both locales. The same leak was in the checkout confirmation dialog, which the screenshot run had not reached — fixed too. The util is now documented as logs-and-tests only.
    - ~~**Placeholder logo on the login screen.**~~ **Fixed 2026-11-04.** The same 🌿 leftover was on all three auth screens, not just login: `LoginScreen` and `RegisterScreen` in a `primary` card, `ForcedLoginScreen` in a 120dp circle. All three now draw `logo_mark.png` through one shared `AppLogoMark` composable, tinted from the scheme the way the splash does, so they cannot drift apart again. `ForcedLoginScreen` keeps its entry animation, now on the mark itself. The orphaned `app_leaf_icon` string is gone from both locales.
  - **Tasks:**
    - ~~Fix the weekday first — it tells a buyer the wrong day to come.~~ Done.
    - ~~The three easier ones: English remaining-time text, the placeholder logo, the unsaved pickup-time default.~~ Done 2026-11-04.
    - ~~The price mismatch: find which side ignores the chosen market.~~ Done 2026-11-04. **All five bugs from this block are fixed.** None of the five has been seen on a device since; the block closes when they have.
    - Follow-up this opened: the buyer is now shown an adjusted price with no explanation of it. `PriceBreakdown` exists and is used only in the seller's `PriceConditionEditor`, whose comment calls it "the same PriceBreakdown the buyer is meant to see" — the buy app has never shown it, on the shelf, the detail screen or in the basket. `ResolvedPrice` reaches all four catalogue surfaces now, so the data is in place wherever it is decided to go.

- **Pickup time is not part of an order**
  - **Status:** Open — design, not a bug report. Traced 2026-11-04 while fixing the unsaved-default bug above; nothing here is started.
  - **Issue:** `BuyerProfile.defaultPickUpTime` is the only place a pickup time is ever
    stored, and it is a *current preference*, not a snapshot. Everything else about an
    order — `fulfilment`, `pricingRevision`, every price and its breakdown — is
    snapshotted onto the order precisely so it cannot change under the seller. The time
    the buyer says they will turn up is the exception.
    - **The seller cannot see it at all.** `orderToMap` writes `buyerId`, `buyerName`
      and `buyerEmail` and nothing else from the profile, and the seller cannot read
      `/buyer_profile`. So the one field the buy app makes a buyer fill in before they
      may order never reaches the person waiting at the stall.
    - **`Order` has no time field, and the seller's screens pretend it does.**
      `Order.pickUpDate` is a single epoch-millis and the slots come from
      `calculateNextPickupDate`, which ends in `.atTime(0, 0)`. So
      `Order.getFormattedPickupDateAndTime()` — "Abholung DD.MM.YYYY um HH:MM Uhr" —
      renders **"um 00:00 Uhr"** on `OrdersScreen` (card and its accessibility text)
      and on `OrderDetailScreen`. Live today, in the seller app.
    - **No per-order override.** A buyer who will come late next Thursday can only
      change their standing default, which is the wrong scope and silently re-describes
      nothing (no order holds the value). The ask — prefill from the profile, snapshot
      onto the order, editable as long as `canEdit()` says the order is — is the
      snapshot pattern the codebase already uses everywhere else.
    - **Changing the default must not rewrite placed orders**, and editing the time on
      an order must go through the same deadline gate as editing its articles. Neither
      rule can be broken today only because there is no field to break.
    - **The window is global, not the market's.** `ProfileValidation.MIN_HOUR = 7` /
      `MAX_HOUR = 18` is hardcoded ("Gültig von 7:00 - 18:00 Uhr"), while
      `Market.begin` / `Market.end` hold the stall's real hours. A buyer can save 07:30
      for a market that opens at 10:00. Once the time belongs to an order, the chosen
      market's own window is the bound that matters.
    - **Delivery.** For a `Fulfilment.DELIVERY` order "Abholzeit" is a delivery time,
      and the profile's `isSelfPickup` is a separate flag from `Order.fulfilment`. The
      naming needs settling before the field spreads further.
    - **Dead DTO carrying the opposite story.** `OrderDto`/`BuyerProfileDto` do
      serialise the time (as `defaultTime`, "to match universe project") and have no
      callers outside their own file and tests — the live path is
      `GitLiveOrderRepository`'s hand-written maps. Reading `OrderDto` suggests the time
      is already stored per order. It is not. Delete it or wire it, but do not leave
      both stories in the tree.
  - **Tasks:**
    - Decide the field: a time on `Order` (prefilled from the profile, snapshotted at
      placement) versus a timestamp that carries its own time of day. A field is
      probably right — `pickUpDate`'s midnight is load-bearing for date keys
      (`yyyyMMdd` paths), deadlines and `BookingPeriod`.
    - Then: write it in `orderToMap`, read it back, show it on both seller screens
      instead of the midnight-derived "um 00:00 Uhr", and make it editable under
      `canEdit()`.
    - Bound the picker by the chosen market's `begin`/`end` instead of 7–18.
    - Settle the delivery wording, and resolve `OrderDto`.

### High Priority: R8 deleted enum constants, silently

- **Status:** Fixed 2026-11-09 in `androidApp/proguard-rules.pro`. Verified in both
  flavors' mapping files and in the buy DEX itself. **Not yet run on a device** — the
  fixed minified build has been built, not installed.
- **Symptom:** On a **minified** buy release the top-bar brand mark went straight to
  About for an approved customer, as if the seller had published no contacts. The same
  build unminified was fine. Nothing crashed and nothing was logged.
- **Cause:** Almost every enum in this codebase is written to the database as
  `.name` and read back as `entries.firstOrNull { it.name == raw }`. R8 cannot see the
  individual constants referenced anywhere, so it deletes them. Measured on the
  2026-11-09 buy release, from `mapping.txt` and confirmed against `classes.dex`:

  | Enum | Declared | Survived R8 |
  |---|---|---|
  | `ContactChannel` | 9 | **2** — only `PHONE` and `LOCATION`, the two named in code |
  | `AccessStatus` | 4 | **3** — `PENDING` gone |
  | `ArticleScarcity` | 4 | **2** |

  `fromStored("EMAIL")` then returns null, `parseContacts` drops the row, the directory
  is empty, and the mark correctly falls back to About. Every step behaved as written.
- **Fix:** `-keepclassmembers enum com.together.newverse.** { *; }`. Scoped to our own
  package, so libraries shrink as before. Cost: **16 KB** on the buy APK
  (5,521,379 → 5,537,765 bytes). After it, all nine `ContactChannel` constants are back
  in the DEX, and both flavors keep every constant of every enum.
- **Why this was invisible, which is the part worth remembering:** the failure has no
  error path. `fromStored` returns null, the caller falls back to a default, and
  `observeSellerContacts`'s `.catch` turns an unreadable node into an empty list on
  purpose. The one diagnostic — `Log.w` — is doubly suppressed in a release build:
  `Log.minLevel` is `ERROR` unless `isDebugBuild`, **and** `-assumenosideeffects` strips
  `android.util.Log.w` at the R8 stage anyway. So a minified build cannot report this
  class of fault at all.
- **What it means for what has been tested:** every device check so far used the
  unminified APK the install scripts build by default, so **no finding about app
  behaviour to date says anything about the minified artifact.** The one that goes to
  Play is the minified one.
- **Tasks:**
  - Install the fixed minified build and re-check contacts, the access card (a
    `PENDING` buyer read as `NONE` before this) and the scarcity bands.
  - Consider a build-time guard: compare each `com.together.newverse` enum's constant
    count in `mapping.txt` against the source and fail the build on a mismatch. Unit
    tests cannot catch this — they run unminified — so the mapping file is the only
    place the truth is visible.
  - Consider whether `fromStored` should map with an explicit `when` instead of
    iterating `entries`. That references each constant individually and so cannot be
    shrunk away, making the keep rule a belt rather than the only thing holding it up.

### High Priority: Release

> **Before any release, work `doc/plans/pre-release-checklist.md`.** It gates the items below
> behind the rules deploy to `bodenschaetze-a988e`: `main` now needs six undeployed
> rules commits, and a release build without them is broadly broken. The release
> project also holds data that has never been inventoried.
- **Android: versionCode**
  - **Status:** Fixed 2026-09-23 — each flavor has its own counter in `androidApp/version.properties` (`VERSION_CODE_BUY`, `VERSION_CODE_SELL`), read by Gradle and bumped per flavor by the fastlane lanes
  - **Tasks:**
    - Check the sell counter against Play before the next sell upload: it restarts at 23, the value last pinned in the build file (8d9c40b).
    - After each upload by hand in the Play Console, tag it: `fastlane tag_release flavor:buy` (or `flavor:sell`, optional `track:internal`) from `androidApp/`. No Android build has a tag yet. Needs fastlane installed first (see below).
- **Android: Play Store deployment via fastlane**
  - **Status:** Not set up — uploads are done by hand in the Play Console
  - **Issue:** The `beta_*` / `deploy_*` lanes cannot run:
    - `androidApp/fastlane/Appfile` still has the placeholder `json_key_file("path/to/google-play-service-account.json")`; no service-account key exists.
    - fastlane is not installed on the dev machine (no `fastlane` / `bundle`; `androidApp/Gemfile` was never installed, no `Gemfile.lock`) — this also blocks `tag_release`.
    - The key file is not in `.gitignore` (the repo is public).
    - `fastlane/metadata/android` only holds the buyer listing text, and `deploy_sell` does not skip metadata, so it would publish the buyer text on the sell listing.
  - **Tasks:**
    - Create the service account and grant it "Release manager" in the Play Console (steps in `doc/guides/google-play-api-setup.md`).
    - Store the key outside git, add its path to `.gitignore`, and point `json_key_file` at it.
    - `bundle install` in `androidApp/`, then check the key with `fastlane run validate_play_store_json_key`.
    - Add sell listing metadata, or set `skip_upload_metadata` / `skip_upload_images` / `skip_upload_screenshots` in `deploy_sell`.
    - Try `beta_buy` to the internal track first.
- **iOS App Store submission**
  - **Status:** Not submittable (see `doc/reports/apple-store-release-status.md`)
  - **Tasks:**
    - Fix the `CFBundleVersion` / `CURRENT_PROJECT_VERSION` build-number wiring.
    - ~~Capture screenshots.~~ **Retaken 2026-11-04** in the new design (`d2db7dd`), iPhone 6.9" and iPad 13". Uploaded the same day to version 1.0.1 (Prepare for Submission), screenshots only — no metadata, no build.
    - Replace the generic "Newverse" store metadata and add the two `subtitle.txt` files.
    - Run the app on a device — so far it has only been compiled. Known code issues: iOS `NetworkConnectivity` always reports online; `HeroProductCard` uses a fixed width.

### High Priority: Bookkeeping & Tax (Sell App)
- **Questions for the tax advisor**
  - **Status:** Open — not code decisions
  - **Tasks:**
    - Regular VAT or small business (Kleinunternehmer, §19 UStG)? The export assumes regular VAT; under §19 its VAT columns would be wrong.
    - Are the app's records sufficient under GoBD / KassenSichV? Sales can only be added in the app (enforced by the database rules), but the Firebase project owner can still change data in the console, and there is no certified security module (TSE).
    - Is 19 % right for Süßkartoffel? Terra's price list marks it 19 %; the app follows the list.

- **Walk-in market sales: device check**
  - **Status:** Implemented, not yet tried on a device
  - **Tasks:**
    - Record a market sale with a changed price, cancel one, and export the month (the CSV shows "Marktverkauf").

- **A list of unbooked pickups**
  - **Status:** Open — named on 2026-09-21 as the biggest practical risk in the feature, never built. `grep` finds "unbooked" only in a test name.
  - **Issue:** Only *confirmed* sales are booked. An order that was picked up but never confirmed is simply missing from the books, and nothing in the app points at it — the seller has no way to notice the gap, which is the part that makes it a risk rather than an inconvenience.
  - **Tasks:**
    - A screen or section listing orders whose pickup date has passed with neither a booking nor a "nicht abgeholt".

- **What the Abrechnung deliberately is not**
  - **Status:** Open — written down here on 2026-11-09 so it is a decision rather than a surprise at the first tax return. None of it is a bug.
  - **Issue:**
    - **Revenue only.** Purchases (Terra's invoices) are not exported; the purchase price is used only to show gross profit.
    - **No cash book.** Payment method is deliberately not recorded, so a separate cash book is still required and the export cannot replace it.
    - **No customer receipts**, and no tax number or company details in the export.
    - **Receipt numbers are unique but not consecutive** (e.g. `V-P23oEOE…`). Worth asking the tax advisor whether that is acceptable.
    - **Retention is unbacked.** Sales cannot be deleted from the app, by design — they must be kept 10 years — but anyone with owner access to the Firebase project still can, and there is no backup of the data.
  - **Tasks:**
    - Decide which of these the app should close and which stay the accountant's job.
    - A backup of `/sales` is the one with no design question in it.

### Medium Priority: BNN Import (Sell App)
- **Producer names resolve for only ~10 of 52 supplier codes**
  - **Status:** Open — found 2026-09-20. **The action is a question to Terra, not a code change.**
  - **Issue:** The gastro catalogue covers Terra's dry and dairy assortment. The produce codes (`SCH`, `RHO`, `AHR`, `BOA`, `ZAN`, and the lower-case regional ones) appear in no file we hold, so those products import with origin and certification but no "Erzeuger:" line.
  - **Tasks:**
    - Ask Terra for the produce supplier list.
    - Separately: "Berlin/Brandenburg" in `BnnCodeTables.REGIONAL_DESCRIPTION` is our wording, not Terra's. Their legend reads "von der Lausitz bis zur Ostsee", with depots in Berlin, Rostock and Leipzig. Decide whether to widen it.
- **Availability flag**
  - **Status:** Unverified
  - **Issue:** Only rows with change flag `A` (field 1) are imported as available. In Terra's list 30 rows are `X` and 22 `N` — including articles that are in the offer (e.g. Apfel Topaz). The meaning of the flags needs checking against the BNN spec.
- **Weight per piece**
  - **Status:** Broken
  - **Issue:** The parser reads fields 67/68, which are empty in every row of Terra's list, so "Gewicht pro Stück" must be entered by hand for every piece-counted product before it can be saved.
- **Re-import creates duplicates**
  - **Status:** Open
  - **Issue:** Every import creates new articles. Importing the weekly price list again duplicates the catalog; articles should be matched by BNN number and updated.
- **Import markup**
  - **Status:** Fixed default (45 %, `ProductCatalogConfig.importMarkupFactor`)
  - **Tasks:**
    - Decide whether the markup should be a seller profile setting or chosen in the import preview.

### Medium Priority: The catalogue on a subdomain
- **Status:** Design only, written 2026-11-09 — `doc/design/public-catalog-subdomain.md`. Nothing built, no rule changed, no hosting configured.
- **Why it is cheaper than it sounds:** the seller/buyer article split already put exactly the shop-window fields in `/articles` and everything confidential in `/seller_articles`, so there is no data modelling to do; and `imageUrl` already holds a public Firebase Storage URL, so images need no migration at all.
- **Decided:** a **periodic snapshot** exported with admin credentials to static JSON, not a live public read. That leaves `/articles` at `auth != null` — no rules change — and keeps an uncapped RTDB bandwidth surface from existing.
- **Tasks:**
  - **Decide the scope first; everything waits on it.** Read-only shop window ≈ 1–1.5 days. A data feed with no page ≈ half a day. Ordering in the browser, or moving the catalogue off Firebase, are weeks each.
  - Decide the refresh trigger (cron, a step in the seller's publish, or a manual button), the host (Firebase Hosting — `firebase.json` has no `hosting` block — or GitHub Pages, which has no `CNAME` today), and the subdomain name.
  - Decide whether `scarcity` ships publicly: it is in `publicFields` and is derived from stock, so on an open page it broadcasts the stall's stock position. Drop it at export rather than at render if the answer is no.
  - Put the public price-display question (Grundpreis, VAT statement, Impressum on the page) on the tax-advisor list above.
  - **Do not resurrect `webApp` for this.** It does not compile, web is unmaintained, and a static page costs less than fixing the target.

### Medium Priority: Business Features
- **Promo Codes (Buy App)**
  - **Status:** Stubbed
  - **Description:** The UI action and state holders exist, but the logic is not implemented.
  - **Tasks:**
    - Design and implement a data model for promo codes.
    - Add validation logic in the `BuyAppViewModel`.
    - Apply discounts to the order total during checkout.

### Medium Priority: Web Buyer App
- **Blocking gaps**
  - **Status:** Open — last assessed 2026-05-25 (`doc/archive/web-implementation-audit.md`), may be out of date
  - **Tasks:**
    - Fill in the Firebase config placeholders in `webApp/src/jsMain/resources/index.html`.
    - Test login → order → message end to end.
    - Check the Firebase Storage CORS rules for image loading.
- **Polish**
  - **Tasks:**
    - Replace the non-working "Scan QR Code" button with a code input, or remove it.
    - Tell users on the login screen that Google/Apple sign-in is not available on web.
    - Test the layout on desktop and tablet widths.
- **Later**
  - **Tasks:**
    - Google OAuth web flow, FCM with a service worker, offline caching, keyboard handling on mobile web.

### Medium Priority: Tablet Layout

Audited 2026-10-26 against the buy flavor. Milestones 1 and 2 are in `main`; ten
buyer screens adapt, the phone portrait lock holds, and the rail switches at
Expanded. The conventions themselves are written down nowhere — see the fourth
item below.

- **Four buy-reachable screens have no adaptation at all**
  - **Status:** Open — found 2026-10-26; none of them uses the adaptive toolkit
  - **Issue:** They stretch to the full width of a tablet. `CustomerProfileScreenModern`
    is the one that matters: it is a bottom-bar tab, so it is one tap away, and it is
    the same single-`Column` `fillMaxSize().verticalScroll()` form shape as the Tier 1
    forms that each took a one-line `.constrainedContentWidth()`. It looks simply
    missed — the Tier 1 list was Login, Register, AddBuyerContact, and Profile was
    not on it.
  - **Tasks:**
    - `CustomerProfileScreenModern` (buy Profile tab) — `.constrainedContentWidth()`.
    - `AboutScreenModern` (commonMain, so it fixes both flavors) — same.
    - `MessagesScreen` — width cap; see the two-pane item below.
    - `ConversationDetailScreen` — same.

- **Messages two-pane**
  - **Status:** Deferred at the user's request
  - **Issue:** Milestone 2 (two-pane list-detail) left out Messages ↔ ConversationDetail.
    *Corrected 2026-10-26:* this entry used to say those screens "only have width
    caps". They have none — both are bare `fillMaxSize()`. The gap is larger than
    it read, and the width caps are the first item above.

- **No preview above 411dp**
  - **Status:** Open — found 2026-10-26
  - **Issue:** All four device-comparison previews in `BuyPreviews.kt` are Compact
    (375/411). Nothing renders at Medium (600–839) or Expanded (840+), so a tablet
    regression cannot be seen in Android Studio and only shows up on a device.
  - **Tasks:**
    - Add a `@WidthClassPreview` multipreview next to `@LightDarkPreview`
      (`androidApp/src/debug/.../preview/ThemePreviews.kt`), rendering Compact,
      Medium and Expanded.
    - Put it on the screens that branch on width: Home, Basket, ProductDetail.

- **Five pieces the 2026-10-26 audit did not carry over**
  - **Status:** Open — raised 2026-07-23 when Milestone 2 shipped, recovered from the screenshots on 2026-11-08
  - **Tasks:**
    - **A reusable `TwoPaneLayout(list, detail, paneRatio)`** in the adaptive package. Three panes each re-implement "list-or-detail by width" by hand; consolidating them makes the fourth conversion (Messages, above) a few lines.
    - **Spike `androidx.compose.material3.adaptive`.** `NavigationSuiteScaffold` would replace the hand-rolled rail switch and `ListDetailPaneScaffold` brings real back-handling, predictive-back and pane restoration that the manual selection state does not have. Hand-rolling was the right call to ship, to dodge the CMP/JS artifact-versioning risk; the question is whether that risk is still real. **Spike and decide — not a given.**
    - **Tablet portrait (Medium, 600–839dp) has had no visual pass.** Everything was checked in landscape. Medium is the width class most likely to look wrong: 2-column grids slightly too tight, forms that feel lost.
    - **Multi-window / foldables.** The phone portrait lock uses `requestedOrientation`, which interacts oddly with split-screen and freeform. Layout keys off *measured* width, so content should adapt — but confirm the lock does not fight resizable mode on newer large-screen Android.
    - **Screenshot tests (Roborazzi)** for the converted screens. This was offered in 2026-07 as "what makes everything else safe to change" and never set up. Partly overtaken on 2026-11-09: `shared` now has working Compose UI tests on Robolectric (`doc/guides/testing.md`), so the remaining question is image diffing rather than infrastructure.
    - Small UX polish, all three still open: auto-select the first item in a detail pane on wide screens instead of showing a placeholder; remember the list scroll position when switching panes; cap the `ProductDetail` description column so text does not run to 900px.
  - **Dropped:** checking the buy screens resized in a browser window. Web is not maintained.

- **The adaptive conventions are not in `doc/`**
  - **Status:** Open — found 2026-10-26
  - **Issue:** Width-class-never-orientation, the phone portrait lock in
    `BuyMainActivity`/`SellMainActivity`, the `AdaptiveDefaults` values
    (`FormMaxWidth` 480, `ListItemMinWidth` 300, `ListMaxWidth` 640,
    `FeedMaxWidth` 960) and the tier plan exist only in the code and in session
    memory. `navigation.md` mentions the rail and `accessibility.md` mentions
    `BasketTwoPane`, but there is no guide, so the next person has no way to know
    the rules before breaking one.
  - **Tasks:**
    - Write `doc/guides/adaptive-layout.md` and index it in `doc/README.md`.

- **iOS status bar is unstyled, and unreadable on the brown top bar**
  - **Status:** Open — found 2026-10-26, not verified on a device (no Mac available)
  - **Issue:** Nothing in `iosApp` sets a status bar style: no
    `UIViewControllerBasedStatusBarAppearance`, no `preferredStatusBarStyle`
    override, no `UIStatusBarStyle` in `Info.plist`. So it is `.default`, which is
    **dark** content in light mode, and Compose draws the brown `#5A3E2B` top bar
    edge-to-edge underneath it: **2.16:1**, against the 3:1 a control needs. Light
    content would be 9.73:1. Dark mode is fine by accident. This predates the
    palette work — it is the iOS mirror of the Android bug fixed in `cfe5693`.
    iPhone has no navigation bar (the home indicator adapts itself), so only the
    status bar is affected.
  - **Note:** `SystemBarIcons` has an iOS `actual`, but it is a deliberate no-op —
    the status bar style is a `UIViewController` property, not something a
    Compose side effect can set. The `expect/actual` existing does **not** mean
    iOS is covered.
  - **Tasks:**
    - Decide between the cheap fix (`UIViewControllerBasedStatusBarAppearance=false`
      plus `UIStatusBarStyle=UIStatusBarStyleLightContent` in `Info.plist` — right
      on every screen inside the scaffold, wrong on Splash and the login screens,
      which have no brown bar) and the correct one (a hosting controller overriding
      `preferredStatusBarStyle` with `setNeedsStatusBarAppearanceUpdate()`, matching
      the per-screen behaviour Android now has).
    - Verify on a device before shipping either.

### High Priority: Account deletion reaches no orders

- **`clearUserData` reads a path that no longer exists**
  - **Status:** Open — found 2026-11-08 by reading the code; not yet observed on a device, but the path mismatch is not in doubt.
  - **Issue:** `GitLiveProfileRepository.kt:424` loads each of the buyer's orders from
    `seller_profile/{sellerId}/orders/{date}/{orderId}`. Orders live at
    `/orders/{sellerId}/{yyyyMMdd}/{orderId}` (`GitLiveOrderRepository.kt:62`, `ORDERS_ROOT`),
    and this is the **only** site in the Kotlin still reaching for the old shape. So
    "Konto löschen" walks `placedOrderIds`, finds nothing at any of them, records
    `"Order X not found"` into `errors`, and deletes the profile and the auth account
    anyway — leaving the seller holding live future orders for a buyer who no longer
    exists. The buyer believes their orders were cancelled; the dialog says so.
  - **Tasks:**
    - Point it at `ORDERS_ROOT`, and decide whether `demo_orders/` needs the same walk.
    - Add a test with an order actually present, which is what would have caught this: the existing path returns "not found" for every input, so nothing failed.
    - Then re-check the three design caveats below against a run that finds orders.

- **Three things account deletion decides by accident**
  - **Status:** Open — raised 2026-08-21, re-confirmed 2026-11-08. Apple token revocation, the headline of that session, **is done** (`AppleTokenRevoker`, `2ac3a57`).
  - **Issue:**
    - **Only the connected seller's orders are cancelled.** `clearUserData(sellerId, …)` takes one seller, but `placedOrderIds` is a flat `Map<date, orderId>` with no seller dimension. A buyer who ever ordered from a second seller leaves those orders standing. Moot while there is one seller; not moot in the data model.
    - **A cancellation failure does not stop the deletion.** Errors are collected into `CleanUpResult` and the profile and auth account go anyway. `ACCOUNT_DELETED` carries `cancelledOrderCount`, so it is visible in the log, but nothing reacts to it.
    - **Guest logout cancels nothing at all.** `confirmGuestLogout()` (`BuyAppViewModelAuth.kt:387`) deletes the profile and never calls `clearUserData`. The dialog says so explicitly ("Bestellungen bleiben erhalten"), so it is deliberate — but it is the opposite asymmetry from "Konto löschen" and is worth deciding rather than inheriting.
  - **Tasks:**
    - Re-authentication before deletion. `delete()` demands a recent login and sessions here are long-lived, so `deleteAuthAccountOrSignOut` hits its retry-then-record path often — logging an orphan rather than completing a deletion. Prompting for a fresh sign-in on `requires-recent-login` would close that path instead of recording it. (`requiresRecentLogin` appears nowhere in the Kotlin outside a comment.)

### Medium Priority: Recovered from the screenshots, 2026-11-08

Assembled from 30 terminal screenshots spanning 2026-05-04 → 2026-11-06 and checked
against the code. Full reasoning, including what was dropped as stale and what turned
out to be already done, is in `doc/reports/screenshot-backlog-2026-11-08.md`.

- **Seller ↔ buyer sync channel: the ordinary work that is left**
  - **Status:** Open — the eligibility blocker is **resolved** (re-verified 2026-10-21); what remains is not a decision.
  - **Issue:** `observeEvents` has **no callers** — the whole seller-side read of the channel is unbuilt. Design in `doc/design/seller-buyer-sync-channel-design.md`.
  - **Tasks:**
    - A `PROFILE_CHANGED` event type, a producer at the rename site, and a drain on the seller side.
    - **Do not put the drain cursor in `seller_profile`** — that node's `$field` rule grants read to any authenticated user for every field except the three client lists, so anything added there is world-readable to signed-in users. Use a separate `seller_event_cursor` node.
    - Retention is on us: RTDB has no server-side TTL and the append-only guard stops buyers deleting their own events, so the drain should prune what it has applied.

- **Buyer-profile data safety: two findings open, and the table contradicts itself**
  - **Status:** Partly fixed. `doc/reports/buyer-profile-data-safety.md`'s summary table still lists all four as Open; its own banner and the code disagree.
  - **Issue:**
    - **#1 — the buyer's email is embedded in every order.** The seller does not need it to fulfil a pickup, and messaging already exists as the contact route. Named in the report's own banner as the main one.
    - **#2 — `BuyerProfileDto.fromDomain()` carries `placedOrderIds` and `favouriteProductIds` into `OrderDto`**, so the seller sees the buyer's whole order history and favourites.
    - **#3 (PII in logcat) is fixed** — the `Log` refactor landed and the policy is settled: PII is fine in `Log.d`, out of `Log.w`/`Log.e`.
    - **#4 (no order schema validation)** looks done — `database.rules.json` now holds 117 `validate` clauses. Confirm the order nodes are among them.
  - **Tasks:**
    - Fix #1 and #2; refresh that report's summary table so it stops disagreeing with its own banner.

- **Verification debt — built, never watched run**
  - **Status:** Open
  - **Tasks:**
    - **The stock refill notification has never been seen on a device** (noted 2026-10-20). It fires at 07:00 the morning after an edit deadline, so observing it needs a wait or a device clock change. 19 unit tests cover its rules; none of them is the same as watching it arrive.
    - **The iOS half of Apple account deletion is uncompiled.** `ContentView.swift` and `AppleSignInHelper.swift` were reviewed but never built, and `pod install` has never run on the Mac. Three checks, all needing macOS: that `import FirebaseAuth` resolves transitively through the shared pod (12.13.0); that `AppleRevokeBridge.shared.setHandler { onSuccess, onError in … }` matches the generated Objective-C header, whose Kotlin function-type params sometimes surface with different labels; and that deleting an Apple-backed account end to end makes the token disappear from **Settings → Apple ID → Sign in with Apple**, which is the check an App Store reviewer performs.

- **Accessibility: the one nav-bar defect never fixed**
  - **Status:** Open — found 2026-10-15. The other two from that session (the unpluralised `%d Artikel`, and the badge announcing the count before the name) **are fixed**.
  - **Issue:** `BuyerBottomBar.kt:375-395` runs a four-stage ~450ms rotation on the cart badge on every basket-count change, unconditionally. Users who turned animations off at the OS level (vestibular disorders) still get it.
  - **Tasks:**
    - An `expect/actual` reading `Settings.Global.ANIMATOR_DURATION_SCALE` on Android and `UIAccessibility.isReduceMotionEnabled` on iOS — Compose Multiplatform has no common reduce-motion flag, and nothing of the kind exists in the tree today. Fits the `kmp-boundaries` pattern. Low severity: one icon, purely decorative, skipping it costs nothing.
    - While there: `ContactChannelUi.kt` still has no accessibility semantics at all, and `doc/guides/accessibility.md`'s coverage map is explicitly stale for anything added since 2026-10-20.

- **`ActionKind.MENU` — the design note that was asked for and never written**
  - **Status:** Parked deliberately on 2026-11-09, in favour of the narrow version that shipped (the brand mark holding the contacts — see below). Reopen only when a **second** menu source actually exists; until then this is a generalisation with one instance.
  - **Issue:** The idea is letting a seller place contact menus on arbitrary screens as published data. Ten things would need settling first; they are written out in full in `doc/reports/screenshot-backlog-2026-11-08.md` § A5. The load-bearing ones: the kind comes from the *slot*, so it needs new `TOPBAR_*_MENU` locations rather than a new kind alone; the menu must name a **source** rather than hold copies of rows, or it goes stale the moment the seller edits a number and silently loses `resolveFor`'s market union; `ActionAudience.stored(null)` returns `EVERYONE`, which is the wrong default for a phone number; and there is no icon field, which is where "generic menu" starts growing.
  - **Tasks:**
    - If it is ever built: `firebase/tests/seller-ui-actions.test.js` needs the new kind and locations too.

- **The brand mark now holds the contacts — not checked on a device or on iOS**
  - **Status:** Implemented 2026-11-09. Both flavors build; 1274 buy + 1263 sell unit tests green, including 9 rule tests and 7 Compose UI tests. **Nothing has been run on a phone, and nothing on iOS.**
  - **Issue:** The top-bar contact shortcut (a person glyph) and the brand mark were two adjacent icons meaning one thing. They are now one seat on Home **and Profile**: no contacts → the mark goes straight to About, exactly as before; contacts → a menu of the override, the channels, then About last. `BrandAction.kt`, and `doc/guides/seller-ui-actions.md` § "The mark also holds the contact channels".
  - **Tasks:**
    - Check it on a phone as an unapproved buyer and as an approved one, on Home and on Profile.
    - **Check it on iOS.** `DropdownMenu` anchoring in a CMP top bar is exactly the kind of thing that looks right on Android and wrong on iOS.
    - **Two overflow menus can sit side by side.** With 2+ published rows the seller's actions collapse into a `MoreVert` menu, which now sits immediately beside the logo menu. Pre-existing in shape — the person icon had the same neighbour — but both being menus is new. Look at it with two published rows before deciding whether it needs anything.
    - A screen-reader user gets no signal that the mark opens a menu rather than navigating. That matches the `MoreVert` beside it, so improving it is a pass over both, not a patch on one.

### Low Priority: Housekeeping
- **Unused build-logic convention plugin**
  - **Status:** Open decision
  - **Issue:** `build-logic` defines `newverse.android.application` (`AndroidApplicationConventionPlugin`), but `androidApp` applies its plugins directly and never uses it. The copy has drifted: compileSdk 35 / targetSdk 37 against the app's 36 / 36, and it still reads the old single `VERSION_CODE` key instead of `VERSION_CODE_BUY` / `VERSION_CODE_SELL`.
  - **Tasks:**
    - Decide: delete the plugin, or bring it in line and switch `androidApp` over to it.
- **Password visibility toggle**
  - **Status:** Open
  - **Issue:** `ForcedLoginScreen.kt` has no show/hide icon on the password field (TODO in the code).
- **The `AuthProvider` profile badge heuristic is wrong**
  - **Status:** Open — cosmetic, deliberately left when `getProviderIds()` was added for the Apple revocation path
  - **Issue:** `NavGraph.kt:95` derives the provider by sniffing the email address for `@gmail.com`, so a Google user with a non-gmail address shows as "E-Mail". The correct source (`auth.currentUser.providerData`) is already read elsewhere.
- **`a11y_home_brand_about` is now a misnomer**
  - **Status:** Open — naming debt only, created 2026-11-09
  - **Issue:** The string is read on Profile too, since the brand seat is on both screens.
- **CustomerDetail top bar title**
  - **Status:** Fixed 2026-09-25 — `SellerTopBar.getRouteTitle()` had no case for `NavRoutes.Sell.CustomerDetail`, so it fell back to the generic "Verkäufer" title; now shows the existing `customer_detail_title` string ("Kundendetails" / "Customer details").
- **Orphaned `bottomnav_new` string**
  - **Status:** Fixed 2026-09-25 — removed from `strings.xml` (German); it was left over after the "Neu" bottom-nav tab was replaced by the plus button on Overview (07d5ed6) and was never present in the English strings file.
- **Default seller id**
  - **Status:** Fixed 2026-10-08 — the constant no longer hardcodes the development seller. `DEFAULT_SELLER_ID` and `DefaultSellerConfig.sellerId` now resolve through `defaultSellerId`, an expect/actual set per build type (`buildConfigField` in `shared/build.gradle.kts` on Android, `Platform.isDebugBinary` on iOS), so it follows the Firebase project the build talks to. Before this, a release build read `/articles/cPkcZSiF3LMXjWoqW6AqpA9paoO2`, which does not exist in `bodenschaetze-a988e`, and showed an empty catalogue; it also wiped any seller connection on every launch, because `BuyerSellerConfig` clears a stored id that differs from `demoSellerId`. See `doc/plans/build-identity-and-firebase-wiring.md`.
  - **Still open:** `getFirstSellerId()` returns the build default rather than the *connected* seller. Correct while there is one seller per project, wrong the moment there are two.
- **Buyer article loading hangs on an empty catalogue**
  - **Status:** Open — a fix was written on 2026-10-08 and reverted on request; the approach needs deciding first.
  - **Issue:** `loadMainScreenArticles` and `loadProducts` clear `isLoading` only inside `.collect` (and `.catch`). `observeArticles` turns each Firebase snapshot into per-article ADDED/CHANGED/REMOVED events, so a seller with no articles produces no emission and throws nothing — neither branch runs and the spinner stays up forever. Any genuinely empty catalogue hangs the buy app. This is what made the wrong-seller bug above present as "stuck loading" rather than "no products".
  - **Constraint:** the buyer should **not** keep a live listener on article changes the way it does now. A one-shot read on screen entry, with an explicit refresh, is the preferred direction — decide this before writing code, because it also removes the diffing the ViewModels currently carry.
  - **Reverted attempt:** added `ArticleRepository.observeArticleSnapshots(): Flow<List<Article>>` emitting the whole list per snapshot (empty list included) and switched both buy loaders to it. It worked and was covered by two regression tests, but it kept the buyer on a live `valueEvents` listener, which is the part to change. Patch kept at `tmp/wip/fix2-article-snapshots.patch` (gitignored, ephemeral) for reference only.
  - **Note:** the sell app does want incremental events — `SellerProfileViewModel` and the overview path rely on the mode flags — so whatever the buyer moves to, `observeArticles` stays for sell.
- **CSV export: trailing spaces in article names**
  - **Status:** Fixed 2026-09-25 (cosmetic) — names now trimmed at both write points: `CreateProductViewModel` (manual create/edit) and `BnnParser` (import, the actual source of "Teesieb "). Existing DB records keep their trailing space until re-saved or re-imported.
- **Known failing unit tests**
  - **Status:** ~~Open~~ **Resolved 2026-10-19** (`71fa5a5`, "Assert what the app does, not what it used to") — both suites green: buy 0 failures, sell 872 tests / 0. The three entries below are kept for the reasoning, in particular the `require` decision in `OrderDateUtils`.
  - **Was:** 6 distinct as of 2026-10-04 (buy 491 tests / 5 failed, sell 604 / 2; `OrderDateUtilsTest` is in `commonTest` and so runs, and fails, in both)
  - **Issue:**
    - `OrderDateUtilsTest` (1): `calculateEditDeadline throws for non-pickup day` expects a `require` that is commented out in `OrderDateUtils.kt`. Needs a decision, not a fix: restoring it makes the function throw for any stored pickup date that is not a Thursday, and `Order.getEditDeadline()` passes whatever the record holds. Either delete the test or decide the `require` belongs there.
    - `BuyAppViewModelTest` (4): navigation and initial auth state.
    - `SellAppViewModelTest` (1): initial state is `Loading`, test expects `Guest`.
  - **Fixed 2026-10-04:** `getAvailablePickupDates` count (B1 above), and `AbrechnungViewModelTest > cancelling a walk-in sale books its reversal` — the fixture pinned `today` but left `AbrechnungViewModel`'s `now` on the system clock, so the reversal was stamped with the real date and fell outside the September 2026 period under test once that month was over. It was green when written and broke on 2026-10-01 with nothing committed.

### Medium Priority: Demo Order Containment
Implemented on 2026-10-15: `Order.origin` (`DEMO` / `TRANSITIONED` / `PRODUCTION`), the
atomic demo→production move on approval, and `origin` validation in the rules. Full design
and reasoning in `doc/guides/demo-order-provenance.md`. What was deliberately left open:

- **The demo write budget is not a control**
  - **Status:** Open
  - **Issue:** `DEMO_FIREBASE_WRITE_LIMIT = 2` is a local counter, reset by clearing app
    data or reinstalling. The rules cap nothing: any signed-in (typically anonymous) user
    may write unlimited orders under `demo_orders/{anySellerId}/{anyDate}/{anyOrderId}`.
  - **Tasks:** Enable **App Check** (highest leverage, protects every node); optionally
    constrain `$date` to a plausible window and cap article count in the rules. A real
    per-buyer cap needs a Cloud Function — RTDB rules cannot count.
- **`orders/` is not closed to unapproved buyers**
  - **Status:** Open
  - **Issue:** The write rule gates on `status !== 'BLOCKED'`, and an unapproved buyer is
    `NONE`. The path split is a traffic boundary and a convention, not a write-permission
    boundary.
  - **Tasks:** Decide whether to require `APPROVED`; check first that no legitimate
    pre-approval write depends on today's behaviour.
- **Does `demo_orders/` earn its place in Firebase?**
  - **Status:** Open decision
  - **Issue:** The two Firebase demo writes exist "for seller observability", but nothing
    in the seller app displays them — `observeSellerOrders` reads only `orders/`, and the
    only reference is the `clearOldDemoOrders` button. The benefit is currently zero.
  - **Tasks:** Either make demo orders local-only (deletes the node, its rules, the budget
    and the abuse question), or build the seller-side "prospective buyers" view that
    justifies them. The current middle state is the one that is not defensible.
- **Dead defensive `origin.isDemo` checks in the seller app**
  - **Status:** Open, cosmetic
  - **Issue:** `AppScaffold`, `OverviewViewModel`, `AbrechnungViewModel` and
    `PickupViewModel` all guard on `origin.isDemo`, which can no longer fire — the seller
    reads only `orders/`, where the rules reject `DEMO`. Kept as cheap guards; delete if
    the item above settles on local-only demo orders.

### Fixed 2026-10-15: Backup restore silently destroyed the signed-in user
- **Status:** Fixed — `android:allowBackup="false"` plus
  `androidApp/src/main/res/xml/data_extraction_rules.xml` in the main manifest.
- **Symptom:** The buy app showed the login screen on *every* cold start and came back as a
  brand-new guest each time, losing the seller approval and the order history with it.
  It looked like a crash; it was not. Across the whole episode there were zero `am_crash`
  and zero `am_anr` events — every process death was an installer kill or a recents swipe.
- **Cause:** FirebaseAuth persists the signed-in user in `shared_prefs`, encrypted with a
  device keystore key that is deliberately never backed up. A fresh install triggers
  Android's restore-at-install, whose first step is literally
  `BackupManagerService: Clearing app data preparatory to full restore` — the live data
  directory is wiped (`keystore2: clearNamespace(APP, nspace=…)` takes the key with it) and
  an **old encrypted auth blob** is written back in its place. Firebase then cannot decrypt
  it, reports `currentUser = null` at every launch, and the app falls back to
  `signInAnonymously`, minting a new uid each time. The device had accumulated seven
  abandoned `newverse_user_<uid>.xml` files this way.
- **Why `allowBackup="false"` is not enough on its own:** on API 31+ it stops cloud backup
  but not device-to-device transfer, which is exactly the new-phone case. Hence the
  extraction rules, which exclude both.
- **Why the exclusion is total rather than targeted at the Firebase files:** backup rules
  support no wildcards, and the auth file name embeds a base64 of the Firebase app id,
  which differs per flavor and per Firebase project. A hardcoded name would quietly stop
  matching the next time `google-services.json` changes. Nothing of value is lost — profile,
  orders, favourites and access status live in Firebase keyed by the account; the reminder
  settings, connected seller id and demo orders are a local cache that rebuilds itself.
- **This was a production bug, not only a dev annoyance.** A real buyer restoring to a new
  phone hit the same path: encrypted blob restored, keystore key absent, silently signed out
  with a new identity.
- **Verified on a Pixel 7a:** uninstall + install of the fixed APK logs `restoreAtInstall`
  but then only `initiateOneRestore packageName=@pm@` and `No more packages` — no
  `RestoreDescription{com.together.buy : STREAM}`, no "Clearing app data preparatory to full
  restore". Guest sign-in then survived two cold starts with the same uid.
- **Recovery for a device already in the broken state:** `adb shell pm clear <package>`.
  A plain clear with no restore in the mix leaves a clean slate; a reinstall does not,
  because that is what triggers the restore.
- **Note:** this also explains an earlier mis-reading — auto-login (documented in
  `doc/archive/forced-login-implementation.md:59` and `doc/reports/feature-report.md:305`) appeared to be
  missing. It was not; the broken auth store was forcing a fresh guest account each launch.
- **Still gated for release:** the behaviour above was proven on a *debug* buy build only.
  Device-to-device transfer — the scenario the change exists for — is untested, as is the
  release build on a device. See `doc/plans/pre-release-checklist.md` → Gate 2 → *Backup and auth
  persistence — buy flavor*.

### Low Priority & On Hold
- **Twitter Sign-In**
  - **Status:** Stubbed
  - **Description:** The UI button exists and is wired up to the ViewModel, but the native platform implementation is missing on both Android and iOS.
  - **Priority:** Low - needs evaluation of whether it's required for the target audience.
