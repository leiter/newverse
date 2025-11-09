# MainScreenModern Flow Connection

## Overview
This document describes how MainScreenModern was connected to the Flow-based article repository to display real-time article data from Firebase.

## Implementation

### 1. Created MainScreenViewModel

**File**: `/shared/src/commonMain/kotlin/com/together/newverse/ui/MainScreenViewModel.kt`

The ViewModel manages:
- Loading articles from repository using Flow
- Handling article events (ADDED, CHANGED, REMOVED)
- Managing selected article and quantity
- Cart functionality
- Error handling

#### State Management
```kotlin
data class MainScreenState(
    val isLoading: Boolean = true,
    val articles: List<Article> = emptyList(),
    val selectedArticle: Article? = null,
    val selectedQuantity: Int = 0,
    val cartItemCount: Int = 0,
    val error: String? = null
)
```

#### Actions
```kotlin
sealed interface MainScreenAction {
    data class SelectArticle(val article: Article) : MainScreenAction
    data class UpdateQuantity(val quantity: Int) : MainScreenAction
    data object AddToCart : MainScreenAction
    data object Refresh : MainScreenAction
}
```

#### Flow Collection
```kotlin
articleRepository.getArticles(sellerId)
    .catch { e -> /* handle error */ }
    .collect { article ->
        when (article.mode) {
            MODE_ADDED -> currentArticles.add(article)
            MODE_CHANGED -> /* update article */
            MODE_REMOVED -> /* remove article */
        }
    }
```

### 2. Updated MainScreenModern

**File**: `/shared/src/commonMain/kotlin/com/together/newverse/ui/MainScreenModern.kt`

Changed from:
```kotlin
@Composable
fun MainScreenModern(...) {
    // Hard-coded sample data
    val products = remember { listOf(...) }
    var selectedProduct by remember { mutableStateOf(...) }
    ...
}
```

To:
```kotlin
@Composable
fun MainScreenModern(
    viewModel: MainScreenViewModel = koinViewModel(),
    ...
) {
    val state by viewModel.state.collectAsState()

    MainScreenModernContent(
        state = state,
        onAction = viewModel::onAction,
        ...
    )
}
```

### 3. Updated UI to React to State

The UI now:
- Shows loading indicator while articles are being loaded
- Displays error messages if loading fails
- Auto-selects the first article when data loads
- Updates in real-time when articles change in Firebase

```kotlin
// Show loading state
if (state.isLoading && products.isEmpty()) {
    item {
        Text("Loading articles...")
    }
}

// Show error state
if (state.error != null) {
    item {
        Text(state.error, color = MaterialTheme.colorScheme.error)
    }
}

// Display articles
items(products.chunked(2)) { productPair ->
    productPair.forEach { product ->
        ModernProductCard(
            product = product,
            onClick = { onAction(MainScreenAction.SelectArticle(product)) }
        )
    }
}
```

### 4. Registered in Dependency Injection

**File**: `/shared/src/commonMain/kotlin/com/together/newverse/di/AppModule.kt`

```kotlin
val appModule = module {
    // Main Screen ViewModel
    viewModel { MainScreenViewModel(get()) }
    ...
}
```

## Data Flow Diagram

```
Firebase Database (articles/{sellerId}/{articleId})
    ↓
FirebaseArticleRepository.getArticles()
    ↓ (emits Article with mode flag)
MainScreenViewModel.loadArticles()
    ↓ (collects and updates state)
MainScreenState
    ↓ (observed via StateFlow)
MainScreenModern UI
    ↓ (recomposes with new data)
User sees updated articles
```

## Event Handling

### Article Added
```
Firebase: New article created
    ↓
callbackFlow emits: Article(mode=MODE_ADDED)
    ↓
ViewModel: currentArticles.add(article)
    ↓
UI: New card appears in grid
```

### Article Changed
```
Firebase: Article updated
    ↓
callbackFlow emits: Article(mode=MODE_CHANGED)
    ↓
ViewModel: Updates article at index
    ↓
UI: Card content updates
```

### Article Removed
```
Firebase: Article deleted
    ↓
callbackFlow emits: Article(mode=MODE_REMOVED)
    ↓
ViewModel: Removes article from list
    ↓
UI: Card disappears from grid
```

## Features

### Real-time Updates
- Articles automatically appear/update/disappear as they change in Firebase
- No manual refresh needed
- Multiple users see changes simultaneously

### Loading States
- Shows "Loading articles..." on initial load
- Maintains articles list during background updates
- Graceful error handling with retry capability

### Auto-selection
- First article is automatically selected for the hero card
- Selection persists across updates unless removed
- Quantity resets when selecting different article

### Error Handling
- Network errors displayed to user
- Failed loads show error message with option to retry
- Doesn't crash on Firebase errors

## Testing with Mock Data

The MockArticleRepository simulates the event pattern:
```kotlin
override fun getArticles(sellerId: String): Flow<Article> = flow {
    delay(500) // Simulate network delay
    PreviewData.sampleArticles.forEach { article ->
        emit(article.copy(mode = MODE_ADDED))
        delay(50) // Small delay between emissions
    }
}
```

This allows testing:
- Loading state displays correctly
- Articles appear one by one
- UI handles incremental updates
- Error states work properly

## Usage

### For Buyer (Customer)
```kotlin
// Shows articles from a specific seller
// sellerId would come from selected market/seller
viewModel.loadArticles(sellerId = "seller123")
```

### For Seller
```kotlin
// Shows seller's own articles
// Empty sellerId = current authenticated user
viewModel.loadArticles(sellerId = "")
```

## Future Enhancements

1. **Category Filtering**: Filter articles by category in ViewModel
2. **Search**: Add search functionality to ViewModel
3. **Sorting**: Sort articles by price, name, etc.
4. **Pagination**: Load articles in chunks for large lists
5. **Offline Support**: Cache articles locally with Room
6. **Pull to Refresh**: Add swipe-to-refresh gesture

## Build Status
✅ Successfully builds
✅ ViewModel properly injected
✅ Flow collection working
✅ Real-time updates functional
✅ Mock data displays correctly