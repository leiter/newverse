# ViewModel Architecture - Cross-Platform Shared ViewModels

This document explains how ViewModels work in our Kotlin Multiplatform project and how they're shared across Android and iOS.

## Overview

We use **androidx.lifecycle:lifecycle-viewmodel:2.8.0** which provides full Kotlin Multiplatform support. This means:

✅ ViewModels are defined once in `commonMain`
✅ ViewModels work on both Android and iOS
✅ State management using StateFlow is cross-platform
✅ Koin provides ViewModels to Compose screens on all platforms

## Architecture Pattern

We follow the **MVVM (Model-View-ViewModel)** pattern with unidirectional data flow:

```
┌─────────────┐
│   Screen    │  ← Composable UI (View)
│  (Compose)  │
└──────┬──────┘
       │ observes StateFlow
       │ calls functions
       ▼
┌─────────────┐
│  ViewModel  │  ← Business Logic + State
└──────┬──────┘
       │ calls
       ▼
┌─────────────┐
│ Repository  │  ← Data Layer
└─────────────┘
```

## ViewModel Structure

### Example: ProductsViewModel

```kotlin
class ProductsViewModel : ViewModel() {

    // Private mutable state (only ViewModel can modify)
    private val _uiState = MutableStateFlow<ProductsUiState>(ProductsUiState.Loading)

    // Public immutable state (Screens observe this)
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            // Business logic here
            _uiState.value = ProductsUiState.Success(articles)
        }
    }

    // Public functions for UI interactions
    fun addToBasket(article: Article) {
        // Handle user action
    }

    fun refresh() {
        loadProducts()
    }
}

// UI State sealed interface
sealed interface ProductsUiState {
    data object Loading : ProductsUiState
    data class Success(val articles: List<Article>) : ProductsUiState
    data class Error(val message: String) : ProductsUiState
}
```

### Key Components:

1. **StateFlow** - Reactive state container that works cross-platform
   - `MutableStateFlow` for internal state
   - `StateFlow` for exposed state (read-only)

2. **viewModelScope** - Coroutine scope tied to ViewModel lifecycle
   - Automatically cancelled when ViewModel is cleared
   - Works on both Android and iOS

3. **Sealed Interface** - Type-safe UI states
   - Loading - Show loading indicator
   - Success - Show data
   - Error - Show error message

## Using ViewModels in Compose Screens

### Basic Usage

```kotlin
@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel = koinViewModel()  // Koin provides the ViewModel
) {
    // Collect state as Compose State
    val uiState by viewModel.uiState.collectAsState()

    // Render UI based on state
    when (val state = uiState) {
        is ProductsUiState.Loading -> CircularProgressIndicator()
        is ProductsUiState.Success -> ProductList(state.articles)
        is ProductsUiState.Error -> ErrorView(state.message)
    }
}
```

### Key Points:

- `koinViewModel()` - Koin provides the ViewModel (works cross-platform)
- `collectAsState()` - Converts StateFlow to Compose State
- `by` delegate - Automatically recomposes when state changes
- Pattern matching with `when` - Handle all UI states

## Dependency Injection with Koin

ViewModels are registered in `AppModule.kt`:

```kotlin
val appModule = module {
    // Register ViewModels
    viewModel { ProductsViewModel() }
    viewModel { BasketViewModel() }
    viewModel { OrdersViewModel() }

    // With dependencies:
    viewModel { ProductsViewModel(get()) }  // get() injects repository
}
```

## Cross-Platform Lifecycle

### Android
- ViewModel survives configuration changes (rotation, etc.)
- Cleared when Activity/Fragment is destroyed
- State is preserved automatically

### iOS
- ViewModel lifecycle tied to SwiftUI view lifecycle
- Works with SwiftUI's `@StateObject` or `@ObservedObject`
- State preserved during navigation

## State Management Best Practices

### 1. Use StateFlow for all UI state

```kotlin
// ✅ Good - StateFlow
private val _items = MutableStateFlow<List<Item>>(emptyList())
val items: StateFlow<List<Item>> = _items.asStateFlow()

// ❌ Bad - LiveData (Android-only)
private val _items = MutableLiveData<List<Item>>()
```

### 2. Use sealed interfaces for UI states

```kotlin
// ✅ Good - Explicit states
sealed interface UiState {
    data object Loading : UiState
    data class Success(val data: Data) : UiState
    data class Error(val message: String) : UiState
}

// ❌ Bad - Multiple booleans
var isLoading: Boolean
var error: String?
var data: Data?
```

### 3. Keep ViewModels platform-agnostic

```kotlin
// ✅ Good - Pure Kotlin/Multiplatform
class ProductsViewModel : ViewModel() {
    fun loadProducts() {
        viewModelScope.launch {
            // Use multiplatform libraries
        }
    }
}

// ❌ Bad - Platform-specific imports
import android.content.Context  // Android-only!
```

### 4. Use viewModelScope for coroutines

```kotlin
// ✅ Good - Auto-cancelled
fun loadData() {
    viewModelScope.launch {
        // Coroutine work
    }
}

// ❌ Bad - Manual scope management
private val scope = CoroutineScope(Dispatchers.Main)
fun loadData() {
    scope.launch { }  // Need to cancel manually!
}
```

## Current ViewModels

### Buy (Customer) Features
- **ProductsViewModel** - Browse and search products
- **BasketViewModel** - Shopping basket management
- **CustomerProfileViewModel** - Customer profile settings

### Sell (Vendor) Features
- **OverviewViewModel** - Product and order statistics
- **OrdersViewModel** - Order management
- **CreateProductViewModel** - Product creation
- **SellerProfileViewModel** - Seller profile settings

### Common Features
- **LoginViewModel** - Authentication

## Example: Complete ProductsScreen Integration

```kotlin
// ViewModel
class ProductsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ProductsUiState>(ProductsUiState.Loading)
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _uiState.value = ProductsUiState.Loading
            try {
                val products = productRepository.getProducts()
                _uiState.value = ProductsUiState.Success(products)
            } catch (e: Exception) {
                _uiState.value = ProductsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addToBasket(article: Article) {
        // Add to basket logic
    }
}

// UI State
sealed interface ProductsUiState {
    data object Loading : ProductsUiState
    data class Success(val articles: List<Article>) : ProductsUiState
    data class Error(val message: String) : ProductsUiState
}

// Screen
@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is ProductsUiState.Loading -> {
            LoadingIndicator()
        }
        is ProductsUiState.Success -> {
            ProductList(
                articles = state.articles,
                onArticleClick = { article ->
                    viewModel.addToBasket(article)
                }
            )
        }
        is ProductsUiState.Error -> {
            ErrorView(
                message = state.message,
                onRetry = { viewModel.loadProducts() }
            )
        }
    }
}
```

## Testing ViewModels

Since ViewModels are in `commonMain`, they can be tested with common tests:

```kotlin
class ProductsViewModelTest {
    @Test
    fun `loadProducts updates state to Success`() = runTest {
        val viewModel = ProductsViewModel()

        viewModel.uiState.test {
            // Assert initial loading state
            assertEquals(ProductsUiState.Loading, awaitItem())

            // Assert success state
            val success = awaitItem() as ProductsUiState.Success
            assertTrue(success.articles.isNotEmpty())
        }
    }
}
```

## Migration from Universe Project

When migrating ViewModels from the Universe project:

1. **Remove LiveData** → Use StateFlow
2. **Remove Android Context** → Use expect/actual for platform-specific needs
3. **Replace Hilt** → Use Koin
4. **Move to commonMain** → Ensure no platform-specific imports

## Resources

- [Lifecycle ViewModel Documentation](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [StateFlow Guide](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/)
- [Koin Documentation](https://insert-koin.io/)
