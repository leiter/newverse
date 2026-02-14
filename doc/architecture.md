# Architecture Guide

## Overview

MVVM pattern with unidirectional data flow. ViewModels in `commonMain` work on both Android and iOS.

```
Screen (Composable) → observes → ViewModel → calls → Repository → Firebase
```

## State Hoisting Pattern

**Golden Rule:** Don't pass ViewModels to child composables. Pass state + callbacks instead.

### Screen Level (Stateful)

```kotlin
@Composable
fun ProductsScreen(viewModel: ProductsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    ProductsContent(state = state, onAction = viewModel::onAction)
}
```

### Content Level (Stateless)

```kotlin
@Composable
fun ProductsContent(
    state: ProductsScreenState,
    onAction: (ProductsAction) -> Unit
) {
    when {
        state.isLoading -> CircularProgressIndicator()
        state.error != null -> ErrorView(onRetry = { onAction(ProductsAction.Refresh) })
        else -> ProductList(articles = state.articles, onAction = onAction)
    }
}
```

## Action Pattern (Recommended)

```kotlin
sealed interface ProductsAction {
    data class AddToBasket(val article: Article) : ProductsAction
    data object Refresh : ProductsAction
}

class ProductsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProductsScreenState())
    val state: StateFlow<ProductsScreenState> = _state.asStateFlow()

    fun onAction(action: ProductsAction) {
        when (action) {
            is ProductsAction.AddToBasket -> addToBasket(action.article)
            ProductsAction.Refresh -> refresh()
        }
    }
}
```

## UI State

```kotlin
data class ProductsScreenState(
    val isLoading: Boolean = false,
    val articles: List<Article> = emptyList(),
    val error: String? = null
)

// Or sealed interface for distinct states
sealed interface UiState {
    data object Loading : UiState
    data class Success(val data: Data) : UiState
    data class Error(val message: String) : UiState
}
```

## Koin DI

```kotlin
// AppModule.kt
val appModule = module {
    viewModel { ProductsViewModel(get()) }
    single<ArticleRepository> { FirebaseArticleRepository() }
}
```

## Best Practices

| Do | Don't |
|----|-------|
| Pass state + callbacks | Pass ViewModels to children |
| Use StateFlow | Use LiveData (Android-only) |
| Use sealed interfaces for UI state | Use multiple boolean flags |
| Use viewModelScope | Create manual CoroutineScope |
| Keep ViewModels platform-agnostic | Import android.* in commonMain |

## Architecture Layers

```
┌──────────────────────┐
│  Screen (Stateful)   │  ← Has ViewModel, observes StateFlow
└──────────┬───────────┘
           │ passes state + callbacks
           ▼
┌──────────────────────┐
│  Content (Stateless) │  ← No ViewModel, pure function
└──────────┬───────────┘
           │ passes data + callbacks
           ▼
┌──────────────────────┐
│  Components (Pure)   │  ← Reusable, easy to preview
└──────────────────────┘
```

## Local UI State

For UI-only state that doesn't need ViewModel:

```kotlin
@Composable
fun SearchBar(onSearch: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    TextField(value = query, onValueChange = { query = it })
    Button(onClick = { onSearch(query) }) { Text("Search") }
}
```

## Core State Abstractions

Located in `shared/src/commonMain/.../ui/state/core/`:

### AsyncState<T>

Generic loading/success/error pattern:

```kotlin
sealed interface AsyncState<out T> {
    data object Initial : AsyncState<Nothing>
    data object Loading : AsyncState<Nothing>
    data class Success<T>(val data: T) : AsyncState<T>
    data class Error(val message: String, val retryable: Boolean = true) : AsyncState<Nothing>
}

// Usage in ViewModel
val ordersState: StateFlow<AsyncState<List<Order>>> = ...

// Usage in Composable
AsyncStateContent(
    state = ordersState,
    onRetry = { viewModel.loadOrders() },
    loadingContent = { CircularProgressIndicator() }
) { orders ->
    OrderList(orders = orders)
}
```

### AuthAwareState<T>

For auth-dependent data flows:

```kotlin
sealed interface AuthAwareState<out T> {
    data object AwaitingAuth : AuthAwareState<Nothing>
    data object AuthRequired : AuthAwareState<Nothing>
    data class Authenticated<T>(val userId: String, val data: AsyncState<T>) : AuthAwareState<T>
}

// Usage with AuthFlowCoordinator
val profileFlow: StateFlow<AuthAwareState<BuyerProfile>> =
    authFlowCoordinator.whenAuthenticated { userId ->
        profileRepository.observeProfile()
    }.stateIn(viewModelScope, SharingStarted.Lazily, AuthAwareState.AwaitingAuth)
```

### FormState<T>

For form management with validation:

```kotlin
data class FormState<T>(
    val data: T,
    val isSubmitting: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val isDirty: Boolean = false
)

// Usage
val formState = formStateOf(ProductFormData())
    .updateField { it.copy(name = "New Product") }
    .validate { data ->
        if (data.name.isBlank()) mapOf("name" to "Required") else emptyMap()
    }
```

### Converters

Bridge legacy ScreenState to AsyncState:

```kotlin
// Convert ListingState to AsyncState
val asyncState = orderHistoryState.toAsyncState()

// Generic converter with data extractor
val profileAsync = customerProfileState.toAsyncState { it.profile }
```

## BaseAppViewModel

Both BuyAppViewModel and SellAppViewModel extend BaseAppViewModel which provides:

- `authCoordinator` - AuthFlowCoordinator for auth-aware flows
- `isAuthenticated()` - Check current auth state
- `initializeAuthCoordinator()` - Setup auth observation

```kotlin
abstract class BaseAppViewModel<S : Any, A : Any>(
    protected val authRepository: AuthRepository
) : ViewModel() {
    protected val authCoordinator = AuthFlowCoordinator(authRepository)

    abstract fun dispatch(action: A)
}
