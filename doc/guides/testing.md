# Testing Guide

**Status:** current — counts and layout verified on 2026-10-26
**Scope:** `shared/src/{commonTest,testBuy,testSell}`,
`androidApp/src/androidTest`, `firebase/tests`

## Test credentials

```
Buyer:  test@buyer.com  / password123
Seller: test@seller.com / password123
```

## Running tests

```bash
# Shared unit tests, per flavor
./gradlew :shared:testBuyDebugUnitTest
./gradlew :shared:testSellDebugUnitTest

# A single class
./gradlew :shared:testBuyDebugUnitTest --tests "*.BuyAppViewModelTest"

# Android instrumentation
./gradlew connectedAndroidTest
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.together.newverse.android.authentication.LoginCases

# Firebase security rules (needs the emulator)
cd firebase/tests && ./run-tests.sh
```

In a **git worktree**, symlink `tmp/` and `debug.keystore` in before any
`androidTest` build, or the build fails.

## Source sets

There are three Kotlin test source sets, and which one a test belongs in is decided
by what it needs rather than what it tests:

| Source set | What goes there | Files | `@Test` |
|------------|-----------------|-------|---------|
| `commonTest` | Model, parser, repository, util, core state — anything flavor-agnostic. Also all the fakes. | 69 | 854 |
| `testBuy` | Tests that need buy-flavor `BuildKonfig` or `buyMain` types | 14 | 183 |
| `testSell` | Tests that need sell-flavor `BuildKonfig` or `sellMain` types | 14 | 304 |
| **Total** | | **97** | **1341** |

No single run executes all 1341: `commonTest` runs in both flavors, so
`testBuyDebugUnitTest` reports 1037 (854 + 183) and `testSellDebugUnitTest`
1158 (854 + 304). Both were green on 2026-10-26, with nothing skipped or
`@Ignore`d.

A ViewModel test lands in `testBuy`/`testSell` because `BuyAppViewModel` and
`SellAppViewModel` live in the flavor source sets. Everything they depend on —
fakes, `TestData`, `MainDispatcherRule` — lives in `commonTest` and is visible to
both.

```
shared/src/commonTest/kotlin/com/together/newverse/
├── test/              # Fake*Repository, MainDispatcherRule, TestData
├── domain/model/      # Order, Market, Stock*, pricing
├── domain/reminder/
├── data/firebase/     # *Nodes multi-path writes
├── data/parser/       # BNN parser
├── data/repository/
├── ui/state/core/     # AsyncState, AuthAwareState, FormState
├── ui/components/
└── util/
```

## Fakes

Fake-first: a test uses a fake repository, not a mock framework. All thirteen live
in `commonTest/.../test/`:

`FakeArticleRepository`, `FakeAuthRepository`, `FakeBasketRepository`,
`FakeInvitationRepository`, `FakeMarketRepository`, `FakeOrderRepository`,
`FakeProfileRepository`, `FakeSaleRepository`, `FakeSellerArticleRepository`,
`FakeSellerConfig`, `FakeStockRepository`, `FakeStorageRepository`,
`FakeTextFileSharer`

When you add a repository interface, add its fake in the same change — otherwise
every ViewModel test that touches it is blocked.

## Instrumentation tests

`androidApp/src/androidTest/`, Espresso, with:

- `utils/BaseTest.kt` — login/logout helpers
- `utils/IdleMessenger.kt` — `IdlingResource` for async work
- `androidApp/src/debug/.../TestContainerActivity.kt` — debug-only host activity

Classes: `LoginCases`, `CatalogUploadTests`, `ProductDataTests`,
`OfferProductDataTests`, `LowPriceOfferProductDataTests`, `ImageUploadArticleTests`,
with their row data under `android/data/`.

`CatalogUploadTests` uploads price and image data idempotently — it can be re-run
against the same catalog without duplicating rows.

> The `BuyerStoriesRunner` / `BuyerStory` scripted walkthroughs described in earlier
> versions of this guide no longer exist. `androidApp/src/debug/` now holds only
> `TestContainerActivity` and Compose `Previews`.

## Firebase security rules tests

`firebase/tests/` is a JS suite against the emulator, one file per node group:
`access`, `articles`, `conversations`, `invite-tokens`, `market-eligibility`,
`markets`, `profile-orders-events`, `sales`, `stock`. Every rules change needs a
test here — the rules are the only thing standing between a buyer and another
buyer's data.

## Behaviours worth asserting

- **Pickup day is per market** (`Market.dayOfWeek`), not globally Thursday. A seller
  with one market happens to run Thursdays. See `guides/ordering-business-rules.md`.
- **Edit deadline** keeps its *distance* from the pickup day across markets, rather
  than pinning a weekday — `Market.scheduleConfig()`.
- **A buyer may order for the next open slot only** (decided 2026-10-16).
- Order status: `DRAFT → PLACED → LOCKED → COMPLETED`.
- Cart badge count matches the basket.
