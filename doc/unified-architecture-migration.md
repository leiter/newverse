# Unified Architecture Migration

## Overview
Successfully migrated MainScreenViewModel into UnifiedAppViewModel to eliminate duplicate state management and complete the transition to a unified Redux-style architecture.

## Changes Made

### 1. State Management (UnifiedAppState.kt)
**Added MainScreenState to UnifiedAppState:**
```kotlin
data class MainScreenState(
    override val isLoading: Boolean = true,
    override val error: ErrorState? = null,
    val articles: List<Article> = emptyList(),
    val selectedArticle: Article? = null,
    val selectedQuantity: Double = 0.0,
    val cartItemCount: Int = 0,
    val basketItems: List<OrderedProduct> = emptyList(),
    val favouriteArticles: List<String> = emptyList()
) : ScreenState
```

Added to ScreenStates:
```kotlin
data class ScreenStates(
    // ... existing states
    val mainScreen: MainScreenState = MainScreenState()
)
```

### 2. Actions (UnifiedAppActions.kt)
**Added UnifiedMainScreenAction:**
```kotlin
sealed interface UnifiedMainScreenAction : UnifiedAppAction {
    data class SelectArticle(val article: Article) : UnifiedMainScreenAction
    data class UpdateQuantity(val quantity: Double) : UnifiedMainScreenAction
    data class UpdateQuantityText(val text: String) : UnifiedMainScreenAction
    data object AddToCart : UnifiedMainScreenAction
    data object RemoveFromBasket : UnifiedMainScreenAction
    data class ToggleFavourite(val articleId: String) : UnifiedMainScreenAction
    data object Refresh : UnifiedMainScreenAction
}
```

### 3. ViewModel (UnifiedAppViewModel.kt)
**Migrated all MainScreenViewModel logic:**
- Added `handleMainScreenAction()` to dispatch method
- Implemented all action handlers:
  - `selectMainScreenArticle()`
  - `updateMainScreenQuantity()`
  - `updateMainScreenQuantityFromText()`
  - `addMainScreenToCart()`
  - `removeMainScreenFromBasket()`
  - `toggleMainScreenFavourite()`
  - `refreshMainScreen()`
- Added `loadMainScreenArticles()` for loading articles
- Added `observeMainScreenBasket()` for basket updates
- Added `observeMainScreenBuyerProfile()` for favourite articles
- Initialized observers in init block

### 4. UI Component (MainScreenModern.kt)
**Updated to use unified state and actions:**
```kotlin
@Composable
fun MainScreenModern(
    state: com.together.newverse.ui.state.MainScreenState,
    onAction: (com.together.newverse.ui.state.UnifiedAppAction) -> Unit
)
```

Updated all action calls:
- `MainScreenAction.UpdateQuantity` → `UnifiedMainScreenAction.UpdateQuantity`
- `MainScreenAction.AddToCart` → `UnifiedMainScreenAction.AddToCart`
- `MainScreenAction.SelectArticle` → `UnifiedMainScreenAction.SelectArticle`
- etc.

### 5. Navigation (NavGraph.kt)
**Simplified Home route:**
```kotlin
composable(NavRoutes.Home.route) {
    MainScreenModern(
        state = appState.screens.mainScreen,
        onAction = onAction
    )
}
```

Removed:
- MainScreenViewModel import
- koinViewModel() call
- collectAsState() call
- Local ViewModel instance

### 6. Preview Data (PreviewData.kt)
**Updated mock data:**
```kotlin
val sampleMainScreenState = com.together.newverse.ui.state.MainScreenState(
    isLoading = false,
    error = null,
    articles = sampleArticles,
    // ... other fields
)
```

### 7. File Removal
**Deleted:** `MainScreenViewModel.kt` - No longer needed

## Benefits

### 1. Single Source of Truth
- All state now flows through UnifiedAppViewModel
- No conflicting state between multiple ViewModels
- Easier to debug and track state changes

### 2. Eliminated Duplication
**Before:**
- MainScreenViewModel: Managed articles, basket, favourites
- UnifiedAppViewModel: Also managed products, basket
- Both observed same repositories

**After:**
- UnifiedAppViewModel: Single manager for all state
- MainScreen state is part of unified state tree
- No duplicate repository observations

### 3. Consistent Architecture
- All screens now follow the same pattern
- UnifiedAppState → Screen Component → UnifiedAppAction
- Redux-style unidirectional data flow

### 4. Better Testability
- Single ViewModel to test
- Easier to mock state
- Predictable state updates

### 5. Improved Maintainability
- Changes to MainScreen logic happen in one place
- Clear separation of concerns
- Easier to add new features

## Data Flow

```
User Action → UI Component
    ↓
UnifiedMainScreenAction
    ↓
UnifiedAppViewModel.dispatch()
    ↓
handleMainScreenAction()
    ↓
Specific handler method
    ↓
Repository calls
    ↓
State update via _state.update{}
    ↓
UI Component recomposes with new state
```

## Migration Pattern for Other Screens

This migration establishes a pattern for other screens:

1. **Add state to UnifiedAppState:**
   ```kotlin
   data class ScreenStates(
       val screenName: ScreenState = ScreenState()
   )
   ```

2. **Add actions to UnifiedAppActions:**
   ```kotlin
   sealed interface UnifiedScreenAction : UnifiedAppAction {
       // Screen-specific actions
   }
   ```

3. **Add handler in UnifiedAppViewModel:**
   ```kotlin
   private fun handleScreenAction(action: UnifiedScreenAction) {
       when (action) {
           // Handle each action
       }
   }
   ```

4. **Update screen component:**
   ```kotlin
   @Composable
   fun Screen(
       state: ScreenState,
       onAction: (UnifiedAppAction) -> Unit
   )
   ```

5. **Update NavGraph:**
   ```kotlin
   composable(route) {
       Screen(
           state = appState.screens.screenName,
           onAction = onAction
       )
   }
   ```

## Next Steps

Consider migrating these screens following the same pattern:
- ProductsScreen
- OrdersScreen
- BasketScreen
- Any other screen with dedicated ViewModel

This will complete the unified architecture transition and eliminate all remaining state management duplication.
