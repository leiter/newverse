# Newverse Project Guide

## Overview

**Newverse** is a Kotlin Multiplatform (KMP) marketplace app with separate buyer and seller flavors. It enables customers to browse products, manage orders, and sellers to manage inventory and fulfill orders.

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.3.0 (K2 compiler) |
| UI | Compose Multiplatform 1.10.3 |
| Architecture | MVVM + Repository Pattern |
| DI | Koin 4.1.0 |
| Async | Coroutines 1.10.2, StateFlow |
| Backend | Firebase (GitLive SDK for cross-platform) |
| Images | Coil3 3.3.0 |
| Navigation | Navigation Compose 2.9.1 |

## Project Structure

```
newverse/
├── androidApp/          # Android app module
│   └── src/
│       ├── buy/         # Buy flavor resources
│       └── sell/        # Sell flavor resources
├── shared/              # KMP shared code
│   └── src/
│       ├── commonMain/  # Shared code (domain, data, ui, di)
│       ├── buyMain/     # Buy flavor screens & navigation
│       ├── sellMain/    # Sell flavor screens & navigation
│       ├── androidMain/ # Android implementations
│       └── iosMain/     # iOS implementations
└── doc/                 # Documentation
```

## Build Flavors

| Flavor | Package | Purpose |
|--------|---------|---------|
| buy | com.together.buy | Customer app - browse, order, checkout |
| sell | com.together.sell | Seller app - manage products & orders |

Base `applicationId` is `com.together`, plus a flavor suffix. A plain `:shared`
build resolves to a third **combined** configuration where both `IS_BUY_APP` and
`IS_SELL_APP` are `false` — see `doc/guides/build-flavors.md`.

```bash
./gradlew :androidApp:assembleBuyDebug
./gradlew :androidApp:assembleSellDebug
```

## Architecture Patterns

### MVVM + State Hoisting

```kotlin
// Screen (Stateful) - has ViewModel
@Composable
fun ProductsScreen(viewModel: ProductsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    ProductsContent(state = state, onAction = viewModel::onAction)
}

// Content (Stateless) - receives state + callbacks
@Composable
fun ProductsContent(state: State, onAction: (Action) -> Unit) { }
```

### Action Pattern

```kotlin
sealed interface ProductsAction {
    data class AddToBasket(val article: Article) : ProductsAction
    data object Refresh : ProductsAction
}
```

### Repository Pattern

- Interfaces in `domain/repository/`
- Implementations in `data/repository/`
- GitLive implementations for cross-platform Firebase

## Key Domain Models

| Model | Purpose |
|-------|---------|
| `Order` | Customer order with status, items, pickup date |
| `Product` / `Article` | Marketplace product |
| `OrderedProduct` | Item in basket/order with quantity |
| `BuyerProfile` | Customer profile with favorites, order history |
| `SellerProfile` | Vendor profile with markets |

## Order Business Rules

- **Pickup Day:** per market (`Market.dayOfWeek`). Thursday where a seller runs one
  market, which is every seller today — not a global rule.
- **Edit Deadline:** the same *distance* before pickup that Tuesday 23:59 is before
  Thursday, so a Monday market and a Saturday market get equal notice
  (`Market.scheduleConfig`).
- **Order Window:** the next open slot only — one slot per eligible market, and
  nothing beyond it. An order snapshots the catalogue and the price it was placed
  against, so a longer horizon promises what the seller cannot keep.
- **Order States:** DRAFT → PLACED → LOCKED → COMPLETED
- **Pricing:** per market, via `Market.priceConditions`. The charged price, its base
  and its breakdown are snapshotted onto each `OrderedProduct`, with the market's
  `pricingRevision` on the `Order`.

```kotlin
val slots = PickupSlots.nextPerMarket(markets, closures)  // one per market
val canEdit = order.canEdit()                             // checks the deadline
val resolved = state.priceOf(article)                     // gross + why
```

Details: `doc/guides/ordering-business-rules.md` and
`doc/guides/market-pricing-and-access-design.md`.

## Key Files

### ViewModels
- `shared/src/buyMain/.../BuyAppViewModel.kt`
- `shared/src/sellMain/.../SellAppViewModel.kt`
- `shared/src/commonMain/.../ui/screens/buy/BasketViewModel.kt`

### Repositories
- `shared/src/commonMain/.../domain/repository/` (interfaces)
- `shared/src/commonMain/.../data/repository/` (implementations)

### Navigation
- `shared/src/buyMain/.../navigation/BuyNavGraph.kt`
- `shared/src/sellMain/.../navigation/SellNavGraph.kt`

### DI
- `shared/src/buyMain/.../AppModule.kt`
- `shared/src/sellMain/.../AppModule.kt`

## Firebase Structure

Realtime Database, top-level node first, then owner:

```
/articles/{sellerId}/{articleId}          # Public product data, readable by every signed-in user
/seller_articles/{sellerId}/{articleId}   # Seller-only: purchase price, markup, sourcing
/orders/{sellerId}/{yyyyMMdd}/{orderId}   # Orders by pickup date
/demo_orders/{sellerId}/{yyyyMMdd}/{id}   # Orders from buyers the seller has not approved
/sales/{sellerId}/{yyyyMM}/{saleId}        # Booked sales (seller-only, add-only)
/sale_index/{sellerId}/{orderId}/{saleId}  # = yyyyMM: which sales belong to an order
/stock_movements/{sellerId}/{yyyyMM}/{id}  # Stock ledger, append-only (seller-only)
/stock/{sellerId}/{articleId}              # The level those movements add up to
/markets/{sellerId}/{marketId}            # Seller's markets, readable by every signed-in user
/seller_ui_actions/{sellerId}/{id}        # Buttons the seller publishes into the buy app
/seller_profile/{sellerId}                # Seller profile
/buyer_profile/{buyerId}                  # Buyer profile with order history
```

An order records where it came from in `Order.origin` — `DEMO`, `TRANSITIONED` or
`PRODUCTION`. The *path* is the access and traffic boundary (the seller subscribes to
`/orders` only, never `/demo_orders`); the flag is provenance. On the seller approving a
buyer, their demo orders move into `/orders` as `TRANSITIONED` in one multi-path update.
`doc/guides/demo-order-provenance.md` has the model and the open abuse-containment questions.

An article's two halves are always written and deleted together in one
multi-path update (`ArticleNodes`); the seller app reads them through
`SellerArticleRepository`, buyers through `ArticleRepository`. Every node and
its access rules: `firebase/database.rules.json` (tests in `firebase/tests`).

## Feature Flags

In `FeatureFlags.kt`:
- `authProvider` - FIREBASE, GITLIVE, AUTO
- `useGitLiveStorage` - Storage provider selection
- `gitLiveRolloutPercentage` - Gradual migration control

## Coding Conventions

### State Management
- Use `StateFlow` (not LiveData)
- Use sealed interfaces for UI states
- Keep ViewModels platform-agnostic

### Compose
- Hoist state to Screen level
- Pass callbacks, not ViewModels to children
- Use `koinViewModel()` for injection

### Naming
- Screens: `*Screen.kt` (stateful), `*Content` (stateless)
- ViewModels: `*ViewModel.kt`
- Actions: `*Action` sealed interface
- States: `*State` or `*UiState`

## Common Tasks

### Add New Screen

1. Create `NewScreen.kt` with ViewModel injection
2. Add route to `NavRoutes.kt`
3. Add to NavGraph (`BuyNavGraph.kt` or `SellNavGraph.kt`)
4. Register ViewModel in AppModule

### Add Repository

1. Define interface in `domain/repository/`
2. Create GitLive implementation in `data/repository/`
3. Register in Koin module

### Add Localized String

1. Add to `shared/src/commonMain/composeResources/values/strings.xml`
2. Add translation to `values-en/strings.xml`
3. Use: `stringResource(Res.string.my_string)`

## Documentation

`doc/` is organized by lifecycle. **Start at `doc/README.md`** — it indexes every
file with its status.

| Folder | Means |
|--------|-------|
| `doc/guides/` | How the app works now; verified against the code |
| `doc/design/` | Proposed, not built |
| `doc/plans/` | Work in flight |
| `doc/reports/` | Dated snapshots, accurate as of their date |
| `doc/archive/` | Superseded or done; kept as history |

Most-used:
- `doc/guides/architecture.md` - MVVM, state patterns, core state abstractions
- `doc/guides/navigation.md` - Routes, per-flavor nav graphs
- `doc/guides/auth-and-accounts.md` - Startup auth, guest accounts, linking, deletion
- `doc/guides/ordering-business-rules.md` - Order logic, per-market pickup, editing
- `doc/guides/stock-driven-availability-design.md` - Stock vs. demand, derived `available`, refill reminder
- `doc/guides/seller-ui-actions.md` - Seller-published top-bar buttons and the home-title announcement; the scheme is never stored
- `doc/guides/build-flavors.md` - applicationIds, BuildKonfig, the combined build
- `doc/guides/testing.md` - Source sets, fakes, how to run what
- `doc/plans/TODO.md` - Feature status and roadmap
- `doc/plans/pre-release-checklist.md` - What must happen before a release reaches a user

Note: `docs/` at the repo root is the GitHub Pages site (privacy/terms/support),
not developer documentation.

Guest access, for the avoidance of doubt: **the buy flavor does not sign anyone in
automatically.** A buyer with no session sees the login screen, and an anonymous
account is created only if they tap "Continue as Guest".

## Current Status

- Android: Functional for both flavors
- iOS: In progress with known issues
- Web: **Not maintained.** The `js(IR)` browser target and `webApp` module are still
  in the build, but nothing is kept working there and the target does not currently
  compile (three `jsMain` files call `Log.d(TAG)` without a `TAG`, left behind by the
  Log refactor in `d659337`). Do not treat a web break as a regression, and do not
  spend effort keeping `jsMain` compiling unless the platform is picked up again.
  Earlier assessment, now stale: `doc/archive/web-implementation-audit.md`.
- Localization: German (primary), English

## Debug Tips

```bash
# Filter logs
adb logcat -s "BasketViewModel" -s "GitLiveAuthRepository"

# Check auth state
adb logcat | grep -E "Auth|Firebase"
```
