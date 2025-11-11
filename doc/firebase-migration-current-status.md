# Firebase Migration - Current Status & Next Steps

## ⚠️ Build Status: BROKEN

The migration from Google Firebase to GitLive Firebase is partially complete but has broken the build. **The androidMain Firebase files have been temporarily backed up.**

## Files Backed Up (Renamed to .backup)

These files were moved to prevent redeclaration errors:
```
shared/src/androidMain/kotlin/com/together/newverse/data/
├── firebase/
│   ├── Database.kt.backup
│   ├── FirebaseExtensions.kt.backup
│   └── model/
│       ├── ArticleDto.kt.backup
│       └── OrderDto.kt.backup
└── repository/
    └── FirebaseAuthRepository.kt.backup
```

## Current Build Errors

1. **FirebaseExtensions.kt** (commonMain) has Flow extension method issues
2. **AndroidMain Repositories** are broken because they depended on the backed-up extensions
3. **Type mismatches** between Google Firebase and GitLive Firebase SDKs

## Recommended Rollback Steps

To restore the working state:

```bash
cd /home/mandroid/Videos/newverse/shared/src/androidMain/kotlin/com/together/newverse/data

# Restore backed up files
mv firebase/Database.kt.backup firebase/Database.kt
mv firebase/FirebaseExtensions.kt.backup firebase/FirebaseExtensions.kt
mv firebase/model/ArticleDto.kt.backup firebase/model/ArticleDto.kt
mv firebase/model/OrderDto.kt.backup firebase/model/OrderDto.kt
mv repository/FirebaseAuthRepository.kt.backup repository/FirebaseAuthRepository.kt

# Remove commonMain Firebase files (incomplete implementation)
rm ../../../commonMain/kotlin/com/together/newverse/data/firebase/Database.kt
rm ../../../commonMain/kotlin/com/together/newverse/data/firebase/FirebaseExtensions.kt
rm ../../../commonMain/kotlin/com/together/newverse/data/firebase/model/ArticleDto.kt
rm ../../../commonMain/kotlin/com/together/newverse/data/firebase/model/OrderDto.kt
rm ../../../commonMain/kotlin/com/together/newverse/data/repository/FirebaseAuthRepository.kt

# Remove commonDomainModule
rm ../../../commonMain/kotlin/com/together/newverse/di/CommonDomainModule.kt
```

Then in `androidApp/src/main/kotlin/com/together/newverse/android/NewverseApp.kt`:
```kotlin
// Remove commonDomainModule import
modules(appModule, androidDomainModule)  // Remove commonDomainModule
```

And in `androidMain/di/AndroidDomainModule.kt`:
```kotlin
// Restore Auth and Basket repository bindings
single<AuthRepository> { FirebaseAuthRepository() }
single<BasketRepository> { InMemoryBasketRepository() }
```

## Alternative: Fix Forward

If you want to continue the migration, these issues need to be fixed:

### 1. Fix FirebaseExtensions.kt (commonMain)

The property access syntax needs to be fixed:
```kotlin
// WRONG:
fun DatabaseReference.observeChildEvents(): Flow<ChildEvent> = this.childEvents

// RIGHT:
fun DatabaseReference.observeChildEvents(): Flow<ChildEvent> = childEvents
```

### 2. Add Missing Imports

Need to import Flow extensions:
```kotlin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
```

### 3. Migrate Remaining Repositories

The Article, Order, and Profile repositories in androidMain need to be migrated to use GitLive SDK or kept as platform-specific implementations.

## What Was Accomplished

✅ Added GitLive Firebase dependencies
✅ Created commonMain Database wrapper structure
✅ Created commonMain DTOs with @Serializable
✅ Created partial FirebaseAuthRepository
❌ Build is currently broken
❌ Migration incomplete

## Recommendation

**ROLLBACK** to the previous working state using the steps above, then plan a more incremental approach:

1. Keep Google Firebase SDK for Android
2. Add GitLive Firebase SDK only for iOS
3. Use expect/actual pattern to share interfaces
4. Migrate one repository at a time with proper testing

This approach would allow Android to continue working while iOS support is being added.