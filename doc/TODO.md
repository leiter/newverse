# Newverse Feature Status

**Last Updated:** 2026-09-23

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
- ✅ Complete Material3 theme system (light/dark themes)
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
- **Push Notifications**
  - **Status:** Not Implemented — options written up in `doc/order-ready-notification-design.md` (in-app banner first, then FCM + Cloud Function, and a configurable WorkManager reminder engine)
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
  - **Issue:** `doc/manual-test-plan.md` lists the steps for each. Buyer-side items:
    - A1 (critical): `formatPrice()` shows most prices one cent too low.
    - E2 (critical, needs confirmation): a time-zone change can orphan a placed order.
    - A2: kg amounts render 1 g short (`1,2 kg` → `1,199 kg`).
    - B1: the pickup date picker only ever offers 2 dates.
    - B2: an order reads as "pickup passed" for all of pickup day.
    - C1: re-adding an item corrupts its displayed amount/unit.
    - C2: a quantity edit can set a negative piece count.
    - C3 (needs confirmation): basket items with a blank product id merge together.
    - E1: the basket renders dates in Berlin time, the rest of the app in device time.
    - E3 (needs confirmation): a failed profile write leaves the order in Firebase but not in the buyer's list.
    - G1: 46 strings untranslated — German text in the English UI.
    - G2/G3: hardcoded English error messages in the German app.
  - **Tasks:**
    - Run the steps on a device, mark each verdict, then fix the confirmed ones — A1 and E2 first.

### High Priority: Release (Buy App)
- **Android: versionCode**
  - **Status:** Fixed 2026-09-23 — each flavor has its own counter in `androidApp/version.properties` (`VERSION_CODE_BUY`, `VERSION_CODE_SELL`), read by Gradle and bumped per flavor by the fastlane lanes
  - **Tasks:**
    - Check the sell counter against Play before the next sell upload: it restarts at 23, the value last pinned in the build file (8d9c40b).
- **iOS App Store submission**
  - **Status:** Not submittable (see `doc/apple-store-release-status.md`)
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
  - **Status:** Open — last assessed 2026-05-25 (`doc/web-implementation-audit.md`), may be out of date
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
- **Messages two-pane**
  - **Status:** Deferred at the user's request
  - **Issue:** Milestone 2 (two-pane list-detail) left out Messages ↔ ConversationDetail; those screens only have width caps.

### Low Priority: Housekeeping
- **Unused build-logic convention plugin**
  - **Status:** Open decision
  - **Issue:** `build-logic` defines `newverse.android.application` (`AndroidApplicationConventionPlugin`), but `androidApp` applies its plugins directly and never uses it. The copy has drifted: compileSdk 35 / targetSdk 37 against the app's 36 / 36, and it still reads the old single `VERSION_CODE` key instead of `VERSION_CODE_BUY` / `VERSION_CODE_SELL`.
  - **Tasks:**
    - Decide: delete the plugin, or bring it in line and switch `androidApp` over to it.
- **Password visibility toggle**
  - **Status:** Open
  - **Issue:** `ForcedLoginScreen.kt` has no show/hide icon on the password field (TODO in the code).
- **Default seller id**
  - **Status:** Open
  - **Issue:** `GitLiveArticleRepository.getFirstSellerId()` still returns the deprecated `DEFAULT_SELLER_ID` instead of the configured or connected seller.
- **CSV export: trailing spaces in article names**
  - **Status:** Open (cosmetic)
  - **Issue:** Names are exported as entered, e.g. "Teesieb " with a trailing space. Trim names in the export or when saving the product.
- **Known failing unit tests**
  - **Status:** Open, unrelated to the bookkeeping work
  - **Issue:** `OrderDateUtilsTest` (2: `getAvailablePickupDates` count, `calculateEditDeadline` for a non-pickup day), `BuyAppViewModelTest` (4: navigation and initial auth state), `SellAppViewModelTest` (1: initial state is `Loading`, test expects `Guest`).

### Low Priority & On Hold
- **Twitter Sign-In**
  - **Status:** Stubbed
  - **Description:** The UI button exists and is wired up to the ViewModel, but the native platform implementation is missing on both Android and iOS.
  - **Priority:** Low - needs evaluation of whether it's required for the target audience.
