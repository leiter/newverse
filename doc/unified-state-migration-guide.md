# Unified State Management Migration Guide

## Overview

The new unified state management system reduces code duplication and provides a single source of truth for the entire application. This guide explains how to migrate from individual screen states to the unified system.

## Architecture

### Before (Multiple ViewModels & States)
```
ProductsViewModel → ProductsScreenState
BasketViewModel → BasketScreenState
ProfileViewModel → ProfileScreenState
```

### After (Single ViewModel & Unified State)
```
UnifiedAppViewModel → UnifiedAppState
  ├── CommonState (shared across screens)
  ├── ScreenStates (using generic patterns)
  └── FeatureStates (feature-specific)
```

## Key Components

### 1. UnifiedAppState
The root state containing all app state in a single tree:
- **CommonState**: User, basket, navigation, UI, connection
- **ScreenStates**: Screen-specific states using generic patterns
- **FeatureStates**: Feature toggles and configurations
- **AppMetaState**: App metadata and configuration

### 2. Generic State Patterns

#### ListingState<T>
Used for screens displaying lists (products, orders, customers):
```kotlin
typealias ProductsScreenState = ListingState<Article>
typealias OrdersScreenState = ListingState<Order>
```

#### FormState<T>
Used for screens with forms (profile, checkout, product creation):
```kotlin
typealias ProfileFormState = FormState<UserProfile>
typealias CheckoutFormState = FormState<CheckoutData>
```

#### DetailState<T>
Used for detail views (product detail, order detail):
```kotlin
typealias ProductDetailScreenState = DetailState<Article>
typealias OrderDetailScreenState = DetailState<Order>
```

### 3. Actions
All state changes through dispatched actions:
```kotlin
sealed interface AppAction

// Examples:
ProductAction.LoadProducts
BasketAction.AddToBasket(product, quantity)
NavigationAction.NavigateTo(route)
UiAction.ShowSnackbar(message, type)
```

## Migration Steps

### Step 1: Update Screen to Use UnifiedAppViewModel

**Before:**
```kotlin
@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    // ...
}
```

**After:**
```kotlin
@Composable
fun UnifiedProductsScreen(
    viewModel: UnifiedAppViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val productsState = StateSelectors.selectProducts(state)
    val basketState = StateSelectors.selectBasket(state)
    // ...
}
```

### Step 2: Convert Screen Actions to App Actions

**Before:**
```kotlin
viewModel.onAction(ProductsAction.AddToBasket(article))
```

**After:**
```kotlin
viewModel.dispatch(Actions.addToBasket(article))
```

### Step 3: Use State Selectors

Use selectors to get specific parts of state:
```kotlin
val user = StateSelectors.selectUser(state)
val basket = StateSelectors.selectBasket(state)
val products = StateSelectors.selectProducts(state)
```

## Benefits

### 1. Reduced Duplication
- Common patterns (loading, error, pagination) defined once
- Shared state (user, basket) accessible everywhere
- Consistent error handling

### 2. Better Type Safety
- Generic states maintain strong typing
- Actions are type-safe
- State selectors prevent typos

### 3. Easier Testing
- Single state tree easy to mock
- Actions can be tested independently
- Time-travel debugging possible

### 4. Performance
- State selectors enable efficient recomposition
- Only affected components re-render
- Centralized state updates

## Migration Strategy

### Phase 1: Parallel Implementation ✅
- Create UnifiedAppState structure
- Implement UnifiedAppViewModel
- Keep existing ViewModels for backward compatibility

### Phase 2: Gradual Migration (Current)
1. Start with simple screens (Products, Basket)
2. Move to complex screens (Profile, Checkout)
3. Migrate custom screens last

### Phase 3: Cleanup
- Remove legacy ViewModels
- Delete old state classes
- Update all imports

## Example Migration: Products Screen

### 1. State Structure
The old `ProductsScreenState` becomes `ListingState<Article>`:
```kotlin
// Old
data class ProductsScreenState(
    val isLoading: Boolean,
    val articles: List<Article>,
    val error: String?
)

// New (using generic)
typealias ProductsScreenState = ListingState<Article>
// Includes: isLoading, error, items, selectedItem, filter, sort, pagination, search
```

### 2. ViewModel Usage
```kotlin
// Old
class ProductsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProductsScreenState())
    // ... handle actions locally
}

// New
class UnifiedAppViewModel : ViewModel() {
    private val _state = MutableStateFlow(UnifiedAppState())
    // ... handle all app actions centrally
}
```

### 3. Screen Implementation
```kotlin
@Composable
fun UnifiedProductsScreen(
    viewModel: UnifiedAppViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val productsState = StateSelectors.selectProducts(state)

    ProductsContent(
        state = productsState,
        onAction = { action ->
            when (action) {
                is ProductsScreenAction.AddToBasket -> {
                    viewModel.dispatch(Actions.addToBasket(action.product))
                }
                // ... other actions
            }
        }
    )
}
```

## Common Patterns

### Loading States
```kotlin
// Check if any screen is loading
val isAnyLoading = state.isAnyScreenLoading

// Check specific screen
val isProductsLoading = state.screens.products.isLoading
```

### Error Handling
```kotlin
// Show error dialog
viewModel.dispatch(Actions.showError("Something went wrong"))

// Check for errors
state.screens.products.error?.let { error ->
    // Handle error
}
```

### Navigation
```kotlin
// Navigate to route
viewModel.dispatch(Actions.navigateTo(NavRoutes.Products))

// Go back
viewModel.dispatch(Actions.back())
```

### Basket Management
```kotlin
// Add to basket
viewModel.dispatch(Actions.addToBasket(product, quantity = 2.0))

// Get basket count
val itemCount = state.basketItemCount
```

## Testing

### Unit Tests
```kotlin
@Test
fun `adding product to basket updates count`() {
    val viewModel = UnifiedAppViewModel(...)

    viewModel.dispatch(Actions.addToBasket(testProduct))

    val state = viewModel.state.value
    assertEquals(1, state.common.basket.itemCount)
}
```

### UI Tests
```kotlin
@Test
fun `products screen shows loading state`() {
    val testState = UnifiedAppState(
        screens = ScreenStates(
            products = ListingState(isLoading = true)
        )
    )

    composeTestRule.setContent {
        ProductsContent(
            state = testState.screens.products,
            onAction = {}
        )
    }

    composeTestRule.onNodeWithTag("loading").assertExists()
}
```

## Troubleshooting

### Issue: State not updating
**Solution**: Ensure you're using `update` function with immutable updates:
```kotlin
_state.update { current ->
    current.copy(
        screens = current.screens.copy(
            products = current.screens.products.copy(
                isLoading = true
            )
        )
    )
}
```

### Issue: Too many recompositions
**Solution**: Use state selectors to subscribe only to needed state:
```kotlin
// Bad - subscribes to entire state
val state by viewModel.state.collectAsState()

// Good - subscribes only to products
val productsState = remember(state) {
    StateSelectors.selectProducts(state)
}
```

### Issue: Complex nested updates
**Solution**: Create helper functions for common updates:
```kotlin
fun UnifiedAppState.updateProductsLoading(isLoading: Boolean) =
    copy(
        screens = screens.copy(
            products = screens.products.copy(isLoading = isLoading)
        )
    )
```

## Next Steps

1. **Migrate remaining screens** to use UnifiedAppViewModel
2. **Add middleware** for logging, analytics, persistence
3. **Implement state persistence** for offline mode
4. **Add time-travel debugging** for development
5. **Remove legacy ViewModels** once migration complete

## Resources

- **State Files**: `/shared/src/commonMain/kotlin/com/together/newverse/ui/state/`
- **Example Screen**: `UnifiedProductsScreen.kt`
- **Actions**: `AppActions.kt`
- **ViewModel**: `UnifiedAppViewModel.kt`