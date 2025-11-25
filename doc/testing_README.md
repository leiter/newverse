# Buyer User Interaction Stories - Quick Reference

## Files in This Directory

### Story Files
1. **BuyerStory1_BrowseAndAddToCart.kt** - Basic browsing and cart operations
2. **BuyerStory2_ModifyQuantitiesAndFavorites.kt** - Quantity management and favorites
3. **BuyerStory3_CheckoutNewOrder.kt** - Complete checkout flow
4. **BuyerStory4_EditExistingOrder.kt** - Edit existing orders
5. **BuyerStory5_CompleteBuyerJourney.kt** - End-to-end realistic journey

### Runner
- **BuyerStoriesRunner.kt** - Orchestrates story execution

## Quick Start

### Run a Single Story

```kotlin
val runner = BuyerStoriesRunner(
    buyAppViewModel,
    basketViewModel,
    articleRepository,
    basketRepository,
    orderRepository,
    profileRepository,
    sellerId = "your-seller-id"
)

// In a coroutine scope:
launch {
    runner.runStory(BuyerStory.STORY_5_COMPLETE_JOURNEY)
}
```

### Run All Stories

```kotlin
launch {
    runner.runAllStories()
}
```

## Story Summary

| Story | Focus | Phases | Duration |
|-------|-------|--------|----------|
| Story 1 | Browse & Add | 4 | ~15s |
| Story 2 | Modify & Favorites | 5 | ~20s |
| Story 3 | Checkout | 8 | ~25s |
| Story 4 | Edit Order | 9 | ~30s |
| Story 5 | Complete Journey | 14 | ~45s |

## Key Features

- ✅ Extensive state logging
- ✅ Prediction validation
- ✅ Repository verification
- ✅ Generous timing delays
- ✅ Realistic user behavior
- ✅ Edge case handling

## Debug Build Only

These stories are located in the `debug` source set and are **NOT** included in release builds.

## Full Documentation

See `/doc/BuyerStories.md` for complete documentation including:
- Detailed phase descriptions
- Prediction specifications
- Integration examples
- Logging strategy
- Troubleshooting guide
- Extension instructions

## Console Output Format

Stories produce formatted console output:

```
================================================================================
STORY N: STORY TITLE
================================================================================

[PHASE 1] Phase Description
--------------------------------------------------------------------------------
[ACTION] User: Does something
   Details about the action

[STATE UPDATE] State changed
   Property: value

[PREDICTION CHECK] Expected behavior
   Expected: value
   Actual: value
   Match: true/false

================================================================================
STORY N COMPLETED
================================================================================
```

## Dependencies Required

```kotlin
// Koin injection or constructor parameters:
- BuyAppViewModel
- BasketViewModel
- ArticleRepository
- BasketRepository
- OrderRepository
- ProfileRepository
- sellerId: String
```

## Common Use Cases

### Debugging State Issues
Run **Story 5** (Complete Journey) - covers all major flows

### Testing Checkout
Run **Story 3** (Checkout) - focused checkout validation

### Testing Order Editing
Run **Story 4** (Edit Order) - covers edit deadline and modifications

### Testing Cart Operations
Run **Story 1** (Browse & Add) + **Story 2** (Modify) - comprehensive cart testing

### Regression Testing
Run **All Stories** - complete app flow validation
