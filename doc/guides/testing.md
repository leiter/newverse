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
| `commonTest` | Model, parser, repository, util, core state — anything flavor-agnostic. Also all the fakes. | 74 | 948 |
| `testBuy` | Tests that need buy-flavor `BuildKonfig` or `buyMain` types | 28 | 326 |
| `testSell` | Tests that need sell-flavor `BuildKonfig` or `sellMain` types | 15 | 315 |
| **Total** | | **117** | **1589** |

No single run executes all 1589: `commonTest` runs in both flavors, so
`testBuyDebugUnitTest` reports 1274 (948 + 326) and `testSellDebugUnitTest`
1263 (948 + 315). Both were green on 2026-11-09, with nothing skipped or
`@Ignore`d. The file counts include fakes and helpers, which carry no `@Test` of
their own.

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

## Compose UI tests

New on 2026-11-09, and so far one class: `testBuy/.../BrandActionRenderTest.kt`. These
run on the JVM with the ordinary unit tests — no device, no emulator — so
`./gradlew :shared:testBuyDebugUnitTest` runs them with everything else.

What makes that work is in `shared/build.gradle.kts`:

- `compose.uiTest` in `commonTest`,
- Robolectric, `ui-test-junit4-android`, `ui-test-manifest` and `activity-compose` in
  the `testBuy` source set,
- `android.testOptions.unitTests.isIncludeAndroidResources = true`, without which
  `stringResource` returns nothing and every text assertion fails for the wrong reason.

A test needs the runner, an SDK and a locale, then the rule:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class MyScreenTest {
    @get:Rule val composeRule = createComposeRule()
}
```

Pin the locale. German lives in `values/` (the default) and English in `values-en/`, so
asserting on an English string is also an assertion that the resource resolved rather
than fell back.

**`runComposeUiTest` does not work here** — it throws `NullPointerException`, because
the shared module has no JVM/desktop target and an Android unit test has no Compose
platform of its own without Robolectric. Use `createComposeRule()`.

**Prefer pinning the rule, not the pixels.** `BrandAction` keeps its decision in a pure
`brandSeat()` function that `BrandActionTest` covers with ordinary unit tests; the UI
test then only asserts what a pure function cannot — that a tap opens the menu rather
than navigating, that each row calls back with its own target, and that the rows are
drawn in the promised order. Order is asserted from `positionInRoot.y`, not from the
semantics tree's print order: a menu composed correctly and drawn wrongly still reads
wrong to the buyer. Note that `onRoot()` finds **two** roots once a `DropdownMenu` is
open, since the popup is a root of its own.

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
