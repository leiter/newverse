# Testing Guide

## Test Credentials

```
Buyer: test@buyer.com / password123
Seller: test@seller.com / password123
```

## Run Tests

```bash
# All instrumentation tests
./gradlew connectedAndroidTest

# Specific test class
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.together.newverse.android.authentication.LoginCases
```

## Test Infrastructure

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
