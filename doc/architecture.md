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
