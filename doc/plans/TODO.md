# Newverse Feature Status

**Last Updated:** 2026-10-15

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
    - Capture screenshots.
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

### Medium Priority: BNN Import (Sell App)
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

### Low Priority: Housekeeping
- **Unused build-logic convention plugin**
  - **Status:** Open decision
  - **Issue:** `build-logic` defines `newverse.android.application` (`AndroidApplicationConventionPlugin`), but `androidApp` applies its plugins directly and never uses it. The copy has drifted: compileSdk 35 / targetSdk 37 against the app's 36 / 36, and it still reads the old single `VERSION_CODE` key instead of `VERSION_CODE_BUY` / `VERSION_CODE_SELL`.
  - **Tasks:**
    - Decide: delete the plugin, or bring it in line and switch `androidApp` over to it.
- **Password visibility toggle**
  - **Status:** Open
  - **Issue:** `ForcedLoginScreen.kt` has no show/hide icon on the password field (TODO in the code).
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
