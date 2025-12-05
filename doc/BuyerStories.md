# Buyer User Interaction Stories

## Overview

This document describes the buyer user interaction story scenarios created for testing and validating buyer flows in the Newverse app. These stories simulate realistic user behavior patterns and log extensive state changes and UI interactions.

**Location**: `androidApp/src/debug/kotlin/com/together/newverse/stories/`

**Purpose**:
- Simulate realistic buyer journeys through the app
- Validate state management and business logic
- Track predictions vs actual behavior
- Provide reproducible test scenarios
- Enable logging-based debugging

## Story Files

### 1. BuyerStory1_BrowseAndAddToCart.kt

**User Journey**: Basic article browsing and cart management

**Phases**:
1. **App Launch & Article Loading** - Load articles with MODE flags, skeleton screens
2. **Browse and Select Articles** - User taps through different article cards
3. **Add Items to Cart** - User adds multiple items with different quantities
4. **Final State Verification** - Verify cart state, badge counts, repository sync

**Key Predictions**:
- Articles load with real-time MODE flags (ADDED, CHANGED, REMOVED)
- First available article auto-selected as hero card
- Quantity input validates based on unit type (kg vs stk)
- Cart badge reflects accurate item count
- Each add-to-cart updates basket state
- ViewModel and Repository states stay in sync

**Logging Focus**:
- Article MODE flags during streaming
- State before/after each action
- Cart badge updates
- Basket repository verification

---

### 2. BuyerStory2_ModifyQuantitiesAndFavorites.kt

**User Journey**: Quantity management and favorites

**Phases**:
1. **Initial Setup** - Load articles and profile
2. **Add Item and Modify Quantity** - Change quantities multiple times (1.0 → 2.5 → 5.0 → 3.5 → 0.5)
3. **Remove Item by Setting Quantity to 0** - Test removal flow
4. **Manage Favorites** - Toggle favorites on multiple articles
5. **Final Verification** - Verify persistence to profile

**Key Predictions**:
- Quantity changes update basket in real-time
- Weight-based (kg) allows decimals, pieces (stk) are integers
- Favorite toggle updates UI immediately
- Favorite article IDs saved to buyer profile
- Setting quantity to 0 removes item from basket
- Cart badge reflects accurate count after modifications

**Logging Focus**:
- Quantity changes sequence
- Item total price calculations
- Favorite toggle state changes
- Profile repository persistence

---

### 3. BuyerStory3_CheckoutNewOrder.kt

**User Journey**: Complete checkout flow

**Phases**:
1. **Add Items to Cart** - Add 3 items with different quantities
2. **Navigate to Basket Screen** - Simulate screen transition
3. **Load Available Pickup Dates** - Verify Thursday-only dates
4. **Select Pickup Date** - Choose a date from available options
5. **Review Before Checkout** - Display order summary
6. **Submit Order** - Execute checkout
7. **Verify Order Creation** - Check order ID in profile
8. **Final Verification** - Confirm basket cleared

**Key Predictions**:
- Available pickup dates are Thursdays only
- At least 5 upcoming Thursday options available
- Total matches sum of all item prices
- Order receives unique ID after checkout
- Basket clears after successful checkout
- Order has status SUBMITTED or DRAFT
- Created order contains all basket items
- Order appears in buyer profile's placedOrderIds

**Logging Focus**:
- Pickup date validation (all Thursdays)
- Order review details
- Checkout success/failure
- Order ID assignment
- Basket clearing verification
- Profile update confirmation

---

### 4. BuyerStory4_EditExistingOrder.kt

**User Journey**: Edit existing order within deadline

**Phases**:
1. **Setup - Create Initial Order** - Create order with 2 items for editing
2. **Load Existing Order** - Navigate with orderId and date parameters
3. **Check Edit Deadline** - Verify 3-day rule (Tuesday 23:59 before Thursday)
4. **Modify Order - Change Quantity** - Increase/decrease item quantity
5. **Modify Order - Add New Item** - Add additional item to order
6. **Modify Order - Remove Item** - Remove item from order
7. **Update Order** - Save modifications
8. **Verify Persistence** - Reload order to confirm changes saved
9. **Final Verification** - Confirm all modifications

**Key Predictions**:
- Order items populate basket on load
- Edit deadline checked (3 days before pickup = Tuesday 23:59)
- `hasChanges` flag tracks modifications
- Adding items marks order as modified
- Removing items marks order as modified
- Changing quantities marks order as modified
- Update button saves changes to existing order
- After update, `originalOrderItems` reflects new state
- Update rejected if past deadline

**Logging Focus**:
- Order loading details
- Edit deadline calculation
- Change detection (hasChanges flag)
- Each modification type (add, remove, quantity change)
- Update success/failure
- Persistence verification via reload

---

### 5. BuyerStory5_CompleteBuyerJourney.kt

**User Journey**: Realistic end-to-end buyer journey

**Phases**:
1. **App Launch & Initial Browse** - User opens app, sees articles load
2. **Browse and Explore Articles** - User scrolls through 5 articles
3. **Mark Favorites** - User favorites 2 articles for later
4. **Select and Add First Item** - User adds first item to cart
5. **Continue Shopping** - User adds 3 more items
6. **Change Mind - Remove Item** - User removes one item
7. **Review Cart Before Checkout** - User reviews cart contents
8. **Navigate to Checkout** - User goes to basket screen
9. **Select Pickup Date** - User chooses date from calendar
10. **Complete Checkout** - User places order
11. **Continue Browsing After Checkout** - User realizes they forgot something
12. **Add Additional Items** - User adds more items to new cart
13. **View Order History** - User checks their orders
14. **Edit Recent Order** - User loads and modifies recent order

**Key Predictions**:
- Seamless navigation between screens
- State persistence across screen changes
- Real-time updates reflected in UI
- Cart badge always accurate
- Favorites persist across sessions
- Order appears in history after creation
- Can edit order within deadline
- All repositories stay in sync

**Logging Focus**:
- Elapsed time tracking (MM:SS format)
- Every user action with context
- State persistence across navigation
- Repository synchronization
- Journey statistics summary
- Complete interaction audit trail

---

## Runner Class: BuyerStoriesRunner.kt

**Purpose**: Orchestrate execution of buyer story scenarios

**Key Features**:
- Run individual stories
- Run all stories in sequence
- Run custom story sequences
- Parallel execution support (with warnings)
- Formatted console output with headers/footers

**Usage Examples**:

```kotlin
// 1. Individual Story
val runner = BuyerStoriesRunner(
    buyAppViewModel, basketViewModel,
    articleRepository, basketRepository, orderRepository, profileRepository,
    sellerId = "seller-id"
)
runner.runStory(BuyerStory.STORY_5_COMPLETE_JOURNEY)

// 2. All Stories
runner.runAllStories()

// 3. Custom Sequence
runner.runStories(listOf(
    BuyerStory.STORY_1_BROWSE_AND_ADD,
    BuyerStory.STORY_3_CHECKOUT
))
```

**Integration Example** (Debug Build):

```kotlin
@Composable
fun DebugPanel(viewModel: BuyAppViewModel, basketViewModel: BasketViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val runner = remember {
        BuyerStoriesRunner(
            buyAppViewModel = viewModel,
            basketViewModel = basketViewModel,
            articleRepository = get(), // Koin
            basketRepository = get(),
            orderRepository = get(),
            profileRepository = get(),
            sellerId = "seller-id-from-config"
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Debug User Stories", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    runner.runStory(BuyerStory.STORY_5_COMPLETE_JOURNEY)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run Complete Journey (Story 5)")
        }

        Button(
            onClick = {
                scope.launch {
                    runner.runStory(BuyerStory.STORY_3_CHECKOUT)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run Checkout Flow (Story 3)")
        }

        Button(
            onClick = {
                scope.launch {
                    runner.runAllStories()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run All Stories")
        }
    }
}
```

---

## Logging Strategy

All stories use **intensive logging** to track:

### State Changes
- ViewModel state before/after each action
- Repository state verification
- Item counts, totals, quantities
- Loading states
- Error states

### UI Interactions
- Button taps
- Text input
- Navigation events
- Selection changes
- Favorites toggles

### Predictions vs Reality
Each story includes **PREDICTION CHECK** sections:
```
[PREDICTION CHECK] Articles should be loaded with MODE flags
   Expected: isLoading=false, articles.size > 0, selectedArticle != null
   Actual: isLoading=false, articles.size=42, selectedArticle=Tomaten
   Match: ✓
```

### Timing
- Generous delays (`delay()`) after actions to allow processing
- Typical delays:
  - 500ms: Quick UI updates
  - 1000-1500ms: Repository operations
  - 2000-3000ms: Network operations
  - 4000ms: Complex operations (checkout, order updates)

---

## Story Execution Flow

### Typical Story Pattern

```kotlin
// 1. Setup Phase
println("[PHASE 1] Setup")
// Load necessary data
viewModel.dispatch(LoadArticles)
delay(2000) // Wait for data

// 2. Action Phase
println("[ACTION] User taps button")
viewModel.dispatch(SomeAction)
delay(1000) // Wait for processing

// 3. State Verification
val state = viewModel.state.value
println("[STATE UPDATE]")
println("   Property: ${state.property}")

// 4. Prediction Check
println("[PREDICTION CHECK] Expected behavior")
println("   Expected: value")
println("   Actual: ${state.property}")
println("   Match: ${state.property == expectedValue}")

// 5. Repository Verification
val repoData = repository.getData()
println("[REPOSITORY VERIFICATION]")
println("   Match: ${repoData == state.data}")
```

---

## Interpreting Story Logs

### Success Indicators
- `✓` - Prediction matched actual behavior
- `Match: true` - Values matched expectations
- `Success: true` - Operation completed successfully

### Warning Indicators
- `⚠️  WARNING:` - Potential issue but story can continue
- `Match: false` - Unexpected behavior
- `Error: <message>` - Operation failed

### State Sections
- `[PHASE N]` - Major story phase
- `[ACTION]` - User interaction
- `[STATE UPDATE]` - ViewModel state changed
- `[PREDICTION CHECK]` - Verify expected behavior
- `[REPOSITORY VERIFICATION]` - Check data persistence
- `[FINAL VERIFICATION]` - End-of-story summary

---

## Running Stories in Production Builds

**IMPORTANT**: These stories are in the `debug` source set and are **NOT** included in release builds.

To make stories available in other builds:
1. Move files from `androidApp/src/debug/` to `androidApp/src/main/`
2. Add conditional compilation checks
3. Use feature flags to enable/disable

---

## Extending Stories

### Creating a New Story

1. **Create Story File**: `BuyerStory6_YourStory.kt`

```kotlin
suspend fun runBuyerStory6_YourStory(
    viewModel: BuyAppViewModel,
    // ... other dependencies
    sellerId: String
) {
    println("\n" + "=".repeat(80))
    println("STORY 6: YOUR STORY TITLE")
    println("=".repeat(80))

    // Implement phases with logging

    println("\n" + "=".repeat(80))
    println("STORY 6 COMPLETED")
    println("=".repeat(80))
}
```

2. **Add to Runner Enum**:

```kotlin
enum class BuyerStory {
    // ... existing stories
    STORY_6_YOUR_STORY(
        "Story 6: Your Story",
        "Description of your story"
    )
}
```

3. **Add to Runner Switch**:

```kotlin
when (story) {
    // ... existing cases
    BuyerStory.STORY_6_YOUR_STORY -> {
        runBuyerStory6_YourStory(/* params */)
    }
}
```

---

## Best Practices

### Logging
- Use consistent formatting (see existing stories)
- Log before and after state changes
- Include prediction checks for key behaviors
- Add generous delays between actions

### Story Design
- Keep stories focused on specific user journeys
- Include setup and teardown phases
- Verify against multiple data sources (ViewModel + Repository)
- Handle edge cases (empty lists, no data, etc.)

### Predictions
- Document expected behavior clearly
- Compare expected vs actual
- Mark mismatches prominently
- Summarize all predictions at end

### Timing
- Use generous delays (2-4s for network operations)
- Allow time for Firebase real-time updates
- Account for state propagation delays
- Log wait periods clearly

---

## Troubleshooting

### Story Hangs or Times Out
- Increase `delay()` durations
- Check for deadlocks in ViewModels
- Verify repository operations complete
- Check network connectivity

### State Mismatches
- Verify ViewModel action dispatching
- Check repository observation flows
- Ensure state updates propagate
- Review action handling logic

### Missing Data
- Check seller ID is correct
- Verify Firebase data exists
- Check authentication state
- Review repository queries

### Order Creation Fails
- Verify buyer profile exists
- Check pickup date is valid (Thursday)
- Ensure basket not empty
- Check edit deadline not passed

---

## Related Documentation

- **Codebase Exploration**: See `Current_Back_Reference` tag for reference implementation
- **ViewModels**: `/shared/src/buyMain/kotlin/.../ui/state/`
- **Repositories**: `/shared/src/commonMain/kotlin/.../domain/repository/`
- **Models**: `/shared/src/commonMain/kotlin/.../domain/model/`
- **Navigation**: `/shared/src/buyMain/kotlin/.../ui/navigation/NavGraph.kt`

---

## Summary

These buyer stories provide:
- ✅ Reproducible test scenarios
- ✅ Extensive logging for debugging
- ✅ Prediction verification
- ✅ Realistic user behavior simulation
- ✅ State management validation
- ✅ Repository synchronization checks
- ✅ End-to-end flow testing

Use them to validate buyer flows, debug issues, and ensure state management correctness across the app.
