# State Hoisting in Kotlin Multiplatform Compose

## What is State Hoisting?

State hoisting is a pattern where you move state up to a composable's caller to make the composable **stateless** and **reusable**.

## The Golden Rule

**❌ DON'T pass ViewModels to child composables**
**✅ DO pass state and callbacks instead**

## Why State Hoisting?

### Problems with passing ViewModels:
```kotlin
// ❌ BAD - Tightly coupled to ViewModel
@Composable
fun ProductCard(viewModel: ProductsViewModel) {
    val product by viewModel.selectedProduct.collectAsState()
    // This component can ONLY work with ProductsViewModel
    // Can't preview, can't reuse, hard to test
}
```

### Benefits of State Hoisting:
```kotlin
// ✅ GOOD - Pure, reusable component
@Composable
fun ProductCard(
    product: Article,
    onAddToBasket: (Article) -> Unit
) {
    // Works with any Article, any callback
    // Easy to preview, reuse, and test
}
```

## Pattern 1: Action-Based State + Single Callback (Recommended)

This is the **most scalable pattern** for complex screens with multiple user actions. Instead of passing many individual callbacks, you define a sealed interface for all possible actions and pass a single callback.

### Define Actions

```kotlin
sealed interface ProductsAction {
    data class AddToBasket(val article: Article) : ProductsAction
    data object Refresh : ProductsAction
}
```

### Define Screen State

```kotlin
data class ProductsScreenState(
    val isLoading: Boolean = false,
    val articles: List<Article> = emptyList(),
    val error: String? = null
)
```

### ViewModel Implementation

```kotlin
class ProductsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProductsScreenState(isLoading = true))
    val state: StateFlow<ProductsScreenState> = _state.asStateFlow()

    fun onAction(action: ProductsAction) {
        when (action) {
            is ProductsAction.AddToBasket -> addToBasket(action.article)
            ProductsAction.Refresh -> refresh()
        }
    }

    private fun addToBasket(article: Article) { /* implementation */ }
    private fun refresh() { /* implementation */ }
}
```

### Screen Level (Stateful - has ViewModel)

```kotlin
@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    ProductsContent(
        state = state,
        onAction = viewModel::onAction
    )
}
```

### Content Level (Stateless - no ViewModel)

```kotlin
@Composable
fun ProductsContent(
    state: ProductsScreenState,
    onAction: (ProductsAction) -> Unit
) {
    when {
        state.isLoading -> LoadingView()
        state.error != null -> ErrorView(
            message = state.error,
            onRetry = { onAction(ProductsAction.Refresh) }
        )
        else -> ProductList(
            articles = state.articles,
            onProductClick = { article ->
                onAction(ProductsAction.AddToBasket(article))
            }
        )
    }
}
```

### Benefits of Action Pattern:
- ✅ **Single callback parameter** instead of multiple `onXyz` parameters
- ✅ **Type-safe actions** - compiler ensures all actions are handled
- ✅ **Easier to test** - can verify which actions are dispatched
- ✅ **Scales better** - adding new actions doesn't change signatures
- ✅ **Clear intent** - actions clearly express user intent
- ✅ **MVI-style** - follows Model-View-Intent architecture

## Pattern 2: State + Multiple Callbacks

For simpler screens with just a few actions, multiple callbacks can be clearer:

### Screen Level (Stateful - has ViewModel)

```kotlin
@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Screen observes ViewModel and passes data down
    ProductsContent(
        uiState = uiState,
        onProductClick = { viewModel.addToBasket(it) },
        onRefresh = { viewModel.refresh() }
    )
}
```

### Content Level (Stateless - no ViewModel)

```kotlin
@Composable
fun ProductsContent(
    uiState: ProductsUiState,
    onProductClick: (Article) -> Unit,
    onRefresh: () -> Unit
) {
    when (uiState) {
        is Loading -> LoadingView()
        is Success -> ProductList(
            articles = uiState.articles,
            onProductClick = onProductClick
        )
        is Error -> ErrorView(
            message = uiState.message,
            onRetry = onRefresh
        )
    }
}
```

### Component Level (Stateless - pure UI)

```kotlin
@Composable
fun ProductList(
    articles: List<Article>,
    onProductClick: (Article) -> Unit
) {
    LazyColumn {
        items(articles) { article ->
            ProductCard(
                article = article,
                onClick = { onProductClick(article) }
            )
        }
    }
}

@Composable
fun ProductCard(
    article: Article,
    onClick: () -> Unit
) {
    Card(onClick = onClick) {
        Text(article.productName)
        Text("${article.price}€")
    }
}
```

## Pattern 3: State Objects

For complex state, group related values:

```kotlin
// Define a state data class
data class BasketScreenState(
    val items: List<OrderedProduct> = emptyList(),
    val total: Double = 0.0,
    val isCheckingOut: Boolean = false
)

// Screen with ViewModel
@Composable
fun BasketScreen(
    viewModel: BasketViewModel = koinViewModel()
) {
    val items by viewModel.basketItems.collectAsState()
    val total by viewModel.totalAmount.collectAsState()

    val state = BasketScreenState(
        items = items,
        total = total
    )

    BasketContent(
        state = state,
        onRemoveItem = { viewModel.removeItem(it) },
        onCheckout = { viewModel.checkout() }
    )
}

// Content without ViewModel
@Composable
fun BasketContent(
    state: BasketScreenState,
    onRemoveItem: (String) -> Unit,
    onCheckout: () -> Unit
) {
    Column {
        BasketItemList(
            items = state.items,
            onRemoveItem = onRemoveItem
        )
        TotalSection(total = state.total)
        CheckoutButton(
            enabled = state.items.isNotEmpty(),
            onClick = onCheckout
        )
    }
}
```

## Pattern 4: remember + derivedStateOf for Local UI State

For UI-only state that doesn't need to survive process death:

```kotlin
@Composable
fun SearchBar(
    onSearch: (String) -> Unit
) {
    // Local UI state - doesn't need ViewModel
    var query by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    Row {
        TextField(
            value = query,
            onValueChange = { query = it }
        )
        Button(onClick = { onSearch(query) }) {
            Text("Search")
        }
    }
}
```

## Pattern 5: CompositionLocal (Avoid Prop Drilling)

For deeply nested trees, use CompositionLocal instead of passing through many layers:

```kotlin
// Define CompositionLocal
val LocalBasketActions = compositionLocalOf<BasketActions> {
    error("No BasketActions provided")
}

data class BasketActions(
    val onAddItem: (Article) -> Unit,
    val onRemoveItem: (String) -> Unit
)

// Provide at top level
@Composable
fun ProductsScreen(viewModel: ProductsViewModel = koinViewModel()) {
    val basketActions = remember {
        BasketActions(
            onAddItem = { viewModel.addToBasket(it) },
            onRemoveItem = { viewModel.removeItem(it) }
        )
    }

    CompositionLocalProvider(LocalBasketActions provides basketActions) {
        ProductsContent()
    }
}

// Access deep in the tree (no prop drilling)
@Composable
fun ProductCard(article: Article) {
    val basketActions = LocalBasketActions.current

    Card(onClick = { basketActions.onAddItem(article) }) {
        // UI
    }
}
```

## Complete Example: Action Pattern in Real Implementation

This shows the actual implementation used in the Newverse project:

### ProductsScreen with Action Pattern

```kotlin
// Actions
sealed interface ProductsAction {
    data class AddToBasket(val article: Article) : ProductsAction
    data object Refresh : ProductsAction
}

// State
data class ProductsScreenState(
    val isLoading: Boolean = false,
    val articles: List<Article> = emptyList(),
    val error: String? = null
)

// ViewModel
class ProductsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProductsScreenState(isLoading = true))
    val state: StateFlow<ProductsScreenState> = _state.asStateFlow()

    init {
        loadProducts()
    }

    fun onAction(action: ProductsAction) {
        when (action) {
            is ProductsAction.AddToBasket -> addToBasket(action.article)
            ProductsAction.Refresh -> refresh()
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val articles = repository.getProducts()
                _state.value = _state.value.copy(
                    isLoading = false,
                    articles = articles
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    private fun addToBasket(article: Article) { /* implementation */ }
    private fun refresh() { loadProducts() }
}

// Screen (Stateful)
@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    ProductsContent(
        state = state,
        onAction = viewModel::onAction
    )
}

// Content (Stateless)
@Composable
fun ProductsContent(
    state: ProductsScreenState,
    onAction: (ProductsAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Browse Products", style = MaterialTheme.typography.headlineMedium)

        when {
            state.isLoading -> CircularProgressIndicator()
            state.error != null -> {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { onAction(ProductsAction.Refresh) }) {
                    Text("Retry")
                }
            }
            else -> {
                LazyColumn {
                    items(state.articles) { article ->
                        ProductListItem(
                            article = article,
                            onClick = { onAction(ProductsAction.AddToBasket(article)) }
                        )
                    }
                }
            }
        }
    }
}

// Preview (No ViewModel needed!)
@Preview
@Composable
fun ProductsContentPreview() {
    ProductsContent(
        state = ProductsScreenState(
            isLoading = false,
            articles = PreviewData.sampleArticles
        ),
        onAction = {}
    )
}
```

### BasketScreen with Action Pattern

```kotlin
// Actions
sealed interface BasketAction {
    data class RemoveItem(val productId: String) : BasketAction
    data class UpdateQuantity(val productId: String, val quantity: Double) : BasketAction
    data object Checkout : BasketAction
}

// State
data class BasketScreenState(
    val items: List<OrderedProduct> = emptyList(),
    val total: Double = 0.0,
    val isCheckingOut: Boolean = false
)

// ViewModel
class BasketViewModel : ViewModel() {
    private val _state = MutableStateFlow(BasketScreenState())
    val state: StateFlow<BasketScreenState> = _state.asStateFlow()

    fun onAction(action: BasketAction) {
        when (action) {
            is BasketAction.RemoveItem -> removeItem(action.productId)
            is BasketAction.UpdateQuantity -> updateQuantity(action.productId, action.quantity)
            BasketAction.Checkout -> checkout()
        }
    }

    private fun removeItem(productId: String) {
        _state.value = _state.value.copy(
            items = _state.value.items.filter { it.productId != productId }
        )
        calculateTotal()
    }

    private fun updateQuantity(productId: String, quantity: Double) {
        val items = _state.value.items.map {
            if (it.productId == productId) it.copy(amountCount = quantity)
            else it
        }
        _state.value = _state.value.copy(items = items)
        calculateTotal()
    }

    private fun calculateTotal() {
        val total = _state.value.items.sumOf { it.price * it.amountCount }
        _state.value = _state.value.copy(total = total)
    }

    private fun checkout() {
        _state.value = _state.value.copy(isCheckingOut = true)
        // TODO: Process checkout
    }
}

// Screen (Stateful)
@Composable
fun BasketScreen(
    viewModel: BasketViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    BasketContent(state = state, onAction = viewModel::onAction)
}

// Content (Stateless)
@Composable
fun BasketContent(
    state: BasketScreenState,
    onAction: (BasketAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Shopping Basket", style = MaterialTheme.typography.headlineMedium)

        if (state.items.isEmpty()) {
            Text("Your basket is empty")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.items) { item ->
                    BasketItem(
                        item = item,
                        onRemove = { onAction(BasketAction.RemoveItem(item.productId)) },
                        onQuantityChange = { qty ->
                            onAction(BasketAction.UpdateQuantity(item.productId, qty))
                        }
                    )
                }
            }
        }

        Text("Total: ${state.total.formatPrice()}€")
        Button(
            onClick = { onAction(BasketAction.Checkout) },
            enabled = state.items.isNotEmpty() && !state.isCheckingOut
        ) {
            Text(if (state.isCheckingOut) "Processing..." else "Checkout")
        }
    }
}
```

## Complete Example: Multiple Callbacks Pattern

### Step 1: Separate Screen (Stateful) from Content (Stateless)

```kotlin
// ProductsScreen.kt
@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    ProductsContent(
        uiState = uiState,
        onProductClick = viewModel::addToBasket,
        onRefresh = viewModel::refresh
    )
}

@Composable
fun ProductsContent(
    uiState: ProductsUiState,
    onProductClick: (Article) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Browse Products",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (uiState) {
            is ProductsUiState.Loading -> LoadingState()
            is ProductsUiState.Success -> ProductsSuccessState(
                articles = uiState.articles,
                onProductClick = onProductClick
            )
            is ProductsUiState.Error -> ErrorState(
                message = uiState.message,
                onRetry = onRefresh
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProductsSuccessState(
    articles: List<Article>,
    onProductClick: (Article) -> Unit
) {
    if (articles.isEmpty()) {
        EmptyState()
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(articles) { article ->
                ProductListItem(
                    productName = article.productName,
                    price = article.price,
                    unit = article.unit,
                    onClick = { onProductClick(article) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No products available",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
```

## Preview Benefits

With state hoisting, previews are simple:

```kotlin
// In androidMain/kotlin/.../preview/Previews.kt

@Preview(showBackground = true)
@Composable
fun ProductsContentPreview() {
    NewverseTheme {
        ProductsContent(
            uiState = ProductsUiState.Success(
                articles = PreviewData.sampleArticles
            ),
            onProductClick = {},
            onRefresh = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProductsLoadingPreview() {
    NewverseTheme {
        ProductsContent(
            uiState = ProductsUiState.Loading,
            onProductClick = {},
            onRefresh = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProductsErrorPreview() {
    NewverseTheme {
        ProductsContent(
            uiState = ProductsUiState.Error("Failed to load products"),
            onProductClick = {},
            onRefresh = {}
        )
    }
}
```

## Testing Benefits

Stateless composables are easier to test:

```kotlin
@Test
fun productsList_displays_all_articles() {
    composeTestRule.setContent {
        ProductsContent(
            uiState = ProductsUiState.Success(testArticles),
            onProductClick = {},
            onRefresh = {}
        )
    }

    testArticles.forEach { article ->
        composeTestRule.onNodeWithText(article.productName).assertExists()
    }
}
```

## Architecture Layers

```
┌──────────────────────────────────────┐
│  Screen (Stateful)                   │  ← Has ViewModel
│  - ProductsScreen                    │  ← Observes StateFlow
│  - koinViewModel()                   │  ← Handles events
└───────────┬──────────────────────────┘
            │ passes state + callbacks
            ▼
┌──────────────────────────────────────┐
│  Content (Stateless)                 │  ← No ViewModel
│  - ProductsContent                   │  ← Receives state
│  - Pure functions of props           │  ← Calls callbacks
└───────────┬──────────────────────────┘
            │ passes data + callbacks
            ▼
┌──────────────────────────────────────┐
│  Components (Stateless)              │  ← Pure UI
│  - ProductList, ProductCard          │  ← 100% reusable
│  - No business logic                 │  ← Easy to preview
└──────────────────────────────────────┘
```

## Best Practices

### ✅ DO

1. **Hoist state to the lowest common ancestor**
   - If two siblings need to share state, lift it to their parent

2. **Pass specific data, not entire objects**
   ```kotlin
   // ✅ Good - only what's needed
   ProductCard(
       name = product.productName,
       price = product.price,
       onClick = { }
   )
   ```

3. **Use function references**
   ```kotlin
   // ✅ Good - function reference
   ProductsContent(
       onRefresh = viewModel::refresh
   )
   ```

4. **Keep callbacks immutable with remember**
   ```kotlin
   val onProductClick = remember {
       { article: Article -> viewModel.addToBasket(article) }
   }
   ```

### ❌ DON'T

1. **Don't pass ViewModels to child composables**
   ```kotlin
   // ❌ Bad
   ProductCard(viewModel = viewModel)
   ```

2. **Don't create ViewModels in non-root composables**
   ```kotlin
   // ❌ Bad
   @Composable
   fun ProductCard() {
       val viewModel = koinViewModel() // Wrong!
   }
   ```

3. **Don't use global state when local state works**
   ```kotlin
   // ❌ Bad - ViewModel for local UI state
   class ExpandableCardViewModel : ViewModel() {
       val isExpanded = MutableStateFlow(false)
   }

   // ✅ Good - local state
   @Composable
   fun ExpandableCard() {
       var isExpanded by remember { mutableStateOf(false) }
   }
   ```

## When to Use What

| Scenario | Solution |
|----------|----------|
| **Complex screen with 3+ user actions** | **Action pattern (Pattern 1)** |
| **Simple screen with 1-2 actions** | **Multiple callbacks (Pattern 2)** |
| Business state (survives process death) | ViewModel + StateFlow |
| UI-only state (doesn't survive) | remember + mutableStateOf (Pattern 4) |
| Shared state between screens | ViewModel in shared parent |
| Deep nesting (avoiding prop drilling) | CompositionLocal (Pattern 5) |
| Form state with many fields | State object (Pattern 3) |
| Single value with callback | Individual parameters |

## Cross-Platform Considerations

All these patterns work identically on Android and iOS:

- ✅ State hoisting works everywhere
- ✅ remember works everywhere
- ✅ CompositionLocal works everywhere
- ✅ Callbacks work everywhere

The only difference:
- Android: `koinViewModel()` in Compose
- iOS: ViewModel provided to SwiftUI view

## Summary

**State hoisting hierarchy:**
1. **Screen** - Has ViewModel, observes state, handles events
2. **Content** - Stateless, pure function of props
3. **Components** - Stateless, reusable, easy to preview

**Golden rule:**
> ViewModels at the top, pure composables below!
