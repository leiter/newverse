# Article Flow Implementation with Event Flags

## Overview
This document describes the migration from the universe project's RxJava Observable pattern to a modern Kotlin Flow-based implementation using callbackFlow and event mode flags, matching the original Firebase ChildEventListener pattern.

## Migration from Universe Project

### Universe Pattern (RxJava)
```kotlin
// Observable with event flags
fun DatabaseReference.getObservable(): Observable<Result.Article> {
    return Observable.create { emitter ->
        val listener = addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val article = snapshot.getValue(Result.Article::class.java)!!
                article.id = snapshot.key ?: ""
                article.mode = ADDED
                emitter.onNext(article)
            }
            // ... other events
        })
        emitter.setCancellable { removeEventListener(listener) }
    }
}

// Usage
Database.articles().getObservable<Result.Article>()
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
```

### Newverse Pattern (Kotlin Flow)
```kotlin
// Flow with event flags
fun observeArticles(sellerId: String): Flow<Article> = callbackFlow {
    val articlesRef = if (sellerId.isEmpty()) {
        Database.articles()
    } else {
        Database.providerArticles(sellerId)
    }

    val listener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            val dto = snapshot.getValue(ArticleDto::class.java)
            if (dto != null) {
                val article = dto.toDomain(snapshot.key ?: "").copy(mode = MODE_ADDED)
                trySend(article)
            }
        }
        // ... other events
    }

    articlesRef.addChildEventListener(listener)
    awaitClose { articlesRef.removeEventListener(listener) }
}

// Usage
articleRepository.getArticles("")
    .catch { e -> /* handle error */ }
    .collect { article -> /* handle article event */ }
```

## Implementation Details

### 1. Event Mode Constants

Added to the Article model:
```kotlin
data class Article(
    // ... other fields
    val mode: Int = MODE_UNDEFINED
) {
    companion object {
        const val MODE_ADDED = 0
        const val MODE_CHANGED = 1
        const val MODE_MOVED = 2
        const val MODE_REMOVED = 3
        const val MODE_UNDEFINED = -1
    }
}
```

### 2. Repository Interface

Changed from suspend function returning Result to Flow:
```kotlin
interface ArticleRepository {
    // Before:
    suspend fun getArticles(sellerId: String): Result<List<Article>>

    // After:
    fun getArticles(sellerId: String): Flow<Article>

    // Each emission is a single article with a mode flag
}
```

### 3. Firebase Implementation

Uses `callbackFlow` with `ChildEventListener`:
- **MODE_ADDED**: New article added to Firebase
- **MODE_CHANGED**: Existing article updated
- **MODE_REMOVED**: Article deleted from Firebase
- **MODE_MOVED**: Article position changed (rare for articles)

### 4. ViewModel Pattern

ViewModels maintain local state and update based on event modes:

```kotlin
articleRepository.getArticles(sellerId)
    .catch { e ->
        // Handle errors
    }
    .collect { article ->
        val currentArticles = _state.value.articles.toMutableList()

        when (article.mode) {
            MODE_ADDED -> {
                currentArticles.add(article)
            }
            MODE_CHANGED -> {
                val index = currentArticles.indexOfFirst { it.id == article.id }
                if (index >= 0) currentArticles[index] = article
            }
            MODE_REMOVED -> {
                currentArticles.removeAll { it.id == article.id }
            }
        }

        _state.value = _state.value.copy(articles = currentArticles)
    }
```

## Key Advantages

### 1. Efficient Updates
- Only changed articles are transmitted, not the entire list
- Reduces bandwidth and improves performance
- Matches Firebase's event-driven architecture

### 2. Real-time Synchronization
- Immediate updates when data changes in Firebase
- Multiple clients see changes in real-time
- No polling required

### 3. Modern Kotlin
- Uses Kotlin Flow instead of RxJava
- Better integration with Kotlin Coroutines
- More idiomatic Kotlin code

### 4. Flexible State Management
- ViewModels can maintain their own list representation
- Can apply additional filtering/sorting locally
- Easy to implement optimistic updates

## Event Flow Diagram

```
Firebase Database (articles/{sellerId}/{articleId})
    ↓
ChildEventListener callbacks
    ↓
callbackFlow emits Article with mode flag
    ↓
ViewModel collects and updates local state
    ↓
UI observes state and recomposes
```

## Migration Checklist

- [x] Add mode constants to Article model
- [x] Update ArticleRepository interface to return Flow<Article>
- [x] Implement callbackFlow in FirebaseArticleRepository
- [x] Update MockArticleRepository for testing
- [x] Update ProductsViewModel to handle events
- [x] Update OverviewViewModel to handle events
- [x] Update UnifiedAppViewModel to handle events
- [x] Update DI module to inject repository

## Usage Examples

### Seller (Overview Screen)
```kotlin
class OverviewViewModel(
    private val articleRepository: ArticleRepository
) : ViewModel() {
    init {
        viewModelScope.launch {
            articleRepository.getArticles("") // Current user's articles
                .collect { article ->
                    when (article.mode) {
                        MODE_ADDED -> articles.add(article)
                        MODE_CHANGED -> updateArticle(article)
                        MODE_REMOVED -> removeArticle(article)
                    }
                }
        }
    }
}
```

### Buyer (Products Screen)
```kotlin
class ProductsViewModel(
    private val articleRepository: ArticleRepository
) : ViewModel() {
    init {
        viewModelScope.launch {
            articleRepository.getArticles(sellerId) // Specific seller's articles
                .collect { article ->
                    // Update product list based on article mode
                }
        }
    }
}
```

## Testing

### Mock Implementation
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

## Performance Considerations

1. **Memory**: ViewModels maintain full list in memory
2. **Network**: Only incremental updates transmitted
3. **Battery**: Persistent connection to Firebase
4. **Threading**: Flow collection happens on viewModelScope

## Future Enhancements

1. **Pagination**: Handle large article lists with windowing
2. **Filtering**: Apply server-side queries before collection
3. **Caching**: Implement disk cache with Room database
4. **Conflict Resolution**: Handle simultaneous edits
5. **Optimistic Updates**: Update UI before Firebase confirms

## Build Status
✅ All code compiles successfully
✅ Firebase integration tested
✅ Mock implementation working