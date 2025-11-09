# Firebase Article List Implementation

## Overview
This document describes the implementation of Firebase Realtime Database integration for loading article lists in the newverse KMP project, based on the pattern used in the universe project.

## Implementation Summary

### 1. Database Helper (`Database.kt`)
**Location:** `shared/src/androidMain/kotlin/com/together/newverse/data/firebase/Database.kt`

Centralized Firebase Database access with the following features:
- Provides typed references to Firebase paths (articles, orders, profiles, etc.)
- Manages database persistence configuration
- Handles user authentication for scoped references
- Mirrors the structure from the universe project

Key methods:
- `articles()` - Get articles for current authenticated user
- `providerArticles(providerId)` - Get articles for a specific seller
- `initialize()` - Setup database persistence

### 2. Firebase Extensions (`FirebaseExtensions.kt`)
**Location:** `shared/src/androidMain/kotlin/com/together/newverse/data/firebase/FirebaseExtensions.kt`

Modern Kotlin Coroutines and Flow-based extensions to replace RxJava:

**Conversion from Universe to Newverse:**
- RxJava `Observable` → Kotlin `Flow`
- RxJava `Single` → Kotlin `suspend fun`
- Firebase callbacks → Flow/suspend functions

Key extensions:
- `observeChildEventsAs<T>()` - Real-time child event monitoring (ADDED, CHANGED, REMOVED, MOVED)
- `observeListAs<T>()` - Observe full list updates
- `getSingleValue()` - One-time read as suspend function
- `getListAs<T>()` - Get list as suspend function with type mapping

### 3. Article DTO (`ArticleDto.kt`)
**Location:** `shared/src/androidMain/kotlin/com/together/newverse/data/firebase/model/ArticleDto.kt`

Firebase data transfer object that:
- Matches Firebase Realtime Database structure
- Preserves field name compatibility (e.g., `weighPerPiece` typo from original)
- Provides bidirectional conversion to/from domain model
- Separates Firebase concerns from domain layer

### 4. Firebase Article Repository (`FirebaseArticleRepository.kt`)
**Location:** `shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseArticleRepository.kt`

Implementation of `ArticleRepository` interface with Firebase backend:

**Key Features:**
- **Real-time updates:** Uses `observeChildEventsAs()` to build incremental list updates
- **Event handling:** Processes ADDED, CHANGED, REMOVED, MOVED events
- **One-time reads:** `getArticles()` and `getArticle()` for snapshot data
- **CRUD operations:** Full support for create, read, update, delete
- **Automatic persistence:** Initializes Firebase offline persistence

**Observable Pattern:**
```kotlin
observeArticles(sellerId) // Returns Flow<List<Article>>
  → observeChildEventsAs<ArticleDto>()
  → scan() to build up list incrementally
  → map DTO to domain model
```

### 5. Dependency Injection Update (`AndroidDomainModule.kt`)
**Location:** `shared/src/androidMain/kotlin/com/together/newverse/di/AndroidDomainModule.kt`

Updated to use Firebase implementation:
```kotlin
single<ArticleRepository> { FirebaseArticleRepository() }
```

## Key Differences from Universe Project

### Technology Stack
| Universe (Old) | Newverse (New) |
|---------------|----------------|
| RxJava Observable/Single | Kotlin Flow/suspend |
| Android-only | Kotlin Multiplatform |
| Tightly coupled | Clean Architecture (Domain/Data separation) |
| Direct Firebase access | Repository pattern with DTOs |

### Architecture Benefits
1. **Platform Independence:** Core logic in `commonMain`, Firebase in `androidMain`
2. **Clean Architecture:** Domain models separate from Firebase DTOs
3. **Modern Coroutines:** Flow-based instead of RxJava
4. **Testability:** Repository interface allows easy mocking
5. **Type Safety:** Kotlin's type system and null safety

## Usage Example

### In ViewModel (Sell Side - Overview)
```kotlin
class OverviewViewModel(
    private val articleRepository: ArticleRepository
) : ViewModel() {

    val articles = articleRepository
        .observeArticles("") // Empty string = current user
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
```

### In ViewModel (Buy Side - Products)
```kotlin
class ProductsViewModel(
    private val articleRepository: ArticleRepository
) : ViewModel() {

    val articles = articleRepository
        .observeArticles(sellerId) // Observe specific seller's articles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
```

## Firebase Database Structure

```
firebase-db/
├── articles/
│   └── {sellerId}/
│       └── {articleId}/
│           ├── productId: String
│           ├── productName: String
│           ├── available: Boolean
│           ├── unit: String
│           ├── price: Double
│           ├── weighPerPiece: Double
│           ├── imageUrl: String
│           ├── category: String
│           ├── searchTerms: String
│           └── detailInfo: String
├── seller_profile/
├── orders/
└── buyer_profile/
```

## Implementation Patterns

### Real-time Updates (Incremental)
Uses `ChildEventListener` pattern for efficient incremental updates:
- Only transmits changes (not full list each time)
- Reduces bandwidth and improves performance
- Maintains local state with `scan()` operator

### One-time Reads (Snapshot)
Uses `ValueEventListener` with single read:
- Efficient for data that doesn't need real-time updates
- Simpler error handling
- Lower Firebase read quota usage

## Error Handling

All operations return `Result<T>`:
```kotlin
when (val result = articleRepository.getArticles(sellerId)) {
    is Result.Success -> // Handle success
    is Result.Failure -> // Handle error
}
```

## Testing

The architecture supports multiple testing strategies:
1. **Mock Repository:** Use `MockArticleRepository` for UI testing
2. **Firebase Emulator:** Test real Firebase integration locally
3. **Unit Tests:** Test DTOs, extensions, and business logic

## Migration Notes

To migrate other repositories (Orders, Profiles):
1. Create DTO in `data/firebase/model/`
2. Implement repository in `data/repository/`
3. Update `AndroidDomainModule.kt`
4. Follow same Flow/suspend pattern

## Dependencies

Required Firebase dependencies (already in `shared/build.gradle.kts`):
```kotlin
androidMain.dependencies {
    implementation("com.google.firebase:firebase-auth-ktx:23.0.0")
    implementation("com.google.firebase:firebase-database-ktx:21.0.0")
    implementation("com.google.firebase:firebase-storage-ktx:21.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
```

## Next Steps

1. **Orders Repository:** Migrate order loading functionality
2. **Profile Repository:** Migrate seller/buyer profile functionality
3. **Storage Integration:** Implement image upload/download
4. **Offline Support:** Enhance offline capabilities with database keepSynced
5. **Error Recovery:** Add retry logic and connection monitoring
