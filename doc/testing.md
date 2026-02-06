# Testing Guide

## Test Credentials

```
Buyer: test@buyer.com / password123
Seller: test@seller.com / password123
```

## Run Tests

```bash
# Unit tests (shared module)
./gradlew :shared:testBuyDebugUnitTest
./gradlew :shared:testSellDebugUnitTest

# All unit tests
./gradlew :shared:test

# Specific test class
./gradlew :shared:testBuyDebugUnitTest --tests "*.BuyAppViewModelTest"
./gradlew :shared:testSellDebugUnitTest --tests "*.SellAppViewModelTest"

# All instrumentation tests (Android)
./gradlew connectedAndroidTest

# Specific instrumentation test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.together.newverse.android.authentication.LoginCases
```

## Test Coverage Summary

| Category | Tests | Description |
|----------|-------|-------------|
| **Buyer ViewModel** | 136 | BuyAppViewModel + 7 extension modules |
| **Seller ViewModels** | 98 | SellAppViewModel + screen ViewModels |
| **Total Unit Tests** | 234 | All shared module tests |

## Unit Test Infrastructure

### Fake Repositories

Located in `shared/src/commonTest/kotlin/.../test/`:

| Repository | Purpose |
|------------|---------|
| `FakeAuthRepository` | Auth state, sign-in/out, social auth |
| `FakeProfileRepository` | Buyer/seller profiles |
| `FakeArticleRepository` | Product/article data |
| `FakeOrderRepository` | Order management |
| `FakeBasketRepository` | Shopping basket operations |
| `FakeStorageRepository` | Image storage |

### Test Utilities

- **MainDispatcherRule** - Sets up test dispatcher for coroutines
- **TestData** - Sample articles, orders, profiles for tests

### Test Structure

```
shared/src/commonTest/kotlin/com/together/newverse/
├── test/                           # Test infrastructure
│   ├── Fake*Repository.kt          # Repository fakes
│   ├── MainDispatcherRule.kt       # Dispatcher setup
│   └── TestData.kt                 # Sample data
├── ui/state/
│   ├── BuyAppViewModelTest.kt      # Core buyer tests (23)
│   ├── SellAppViewModelTest.kt     # Core seller tests (30)
│   └── buy/                        # Buyer extension tests (113)
│       ├── BuyAppViewModelAuthTest.kt
│       ├── BuyAppViewModelBasketTest.kt
│       ├── BuyAppViewModelInitializationTest.kt
│       ├── BuyAppViewModelMainScreenTest.kt
│       ├── BuyAppViewModelNavigationTest.kt
│       ├── BuyAppViewModelProfileTest.kt
│       └── BuyAppViewModelUiTest.kt
└── ui/screens/sell/                # Seller screen tests (68)
    ├── CreateProductViewModelTest.kt
    ├── OrdersViewModelTest.kt
    ├── OverviewViewModelTest.kt
    └── SellerProfileViewModelTest.kt
```

## Instrumentation Test Infrastructure

### Espresso Testing

- **TestContainerActivity**: Debug-only activity for auth testing
- **IdleMessenger**: IdlingResource for async operations
- **BaseTest**: Base class with login/logout helpers

**Files:**
- `androidApp/src/debug/.../TestContainerActivity.kt`
- `androidApp/src/androidTest/.../utils/BaseTest.kt`

### Buyer Stories (Debug Mode)

Location: `androidApp/src/debug/.../stories/`

| Story | Focus | Duration |
|-------|-------|----------|
| Story 1 | Browse & Add to Cart | ~15s |
| Story 2 | Modify Quantities & Favorites | ~20s |
| Story 3 | Checkout Flow | ~25s |
| Story 4 | Edit Existing Order | ~30s |
| Story 5 | Complete Buyer Journey | ~45s |

**Run Stories:**
```kotlin
val runner = BuyerStoriesRunner(
    buyAppViewModel, basketViewModel,
    articleRepository, basketRepository, orderRepository, profileRepository,
    sellerId = "your-seller-id"
)

// Run single story
runner.runStory(BuyerStory.STORY_5_COMPLETE_JOURNEY)

// Run all stories
runner.runAllStories()
```

**Note:** Stories are in `debug` source set - NOT included in release builds.

## Key Test Scenarios

### Authentication
- Create account, login, delete
- Google Sign-In
- Auto-login on app restart
- Forced login (seller flavor)

### Buyer Flow
- Browse products, add to cart
- Modify quantities
- Checkout with pickup date
- Edit existing order within deadline

### Expected Behaviors
- Pickup dates: Thursdays only
- Edit deadline: Tuesday 23:59 before pickup
- Cart badge: Reflects accurate count
- Order status: DRAFT → PLACED → LOCKED → COMPLETED
