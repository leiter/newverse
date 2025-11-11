# Firebase Migration to GitLive SDK - Multiplatform Support

## Overview
Migration from Google Firebase SDK (Android-only) to GitLive Firebase SDK (Multiplatform) to support iOS, Android, and potentially other platforms.

## Migration Status

### ✅ Completed

#### 1. Dependencies Added (build.gradle.kts)
```kotlin
// GitLive Firebase - Multiplatform support
implementation("dev.gitlive:firebase-auth:2.1.0")
implementation("dev.gitlive:firebase-database:2.1.0")
implementation("dev.gitlive:firebase-storage:2.1.0")
implementation("dev.gitlive:firebase-common:2.1.0")
```

#### 2. Database Wrapper (commonMain)
- **Created:** `/shared/src/commonMain/kotlin/com/together/newverse/data/firebase/Database.kt`
- Migrated all database reference methods to use GitLive SDK
- Maintains same API surface for compatibility

#### 3. FirebaseAuthRepository (commonMain)
- **Created:** `/shared/src/commonMain/kotlin/com/together/newverse/data/repository/FirebaseAuthRepository.kt`
- Full authentication support:
  - Email/password authentication
  - Anonymous sign-in
  - Google Sign-In (via credential)
  - Twitter Sign-In (via credential)
  - Password reset
  - Account deletion
  - Real-time auth state observation

#### 4. Data Transfer Objects (commonMain)
- **Created:** `/shared/src/commonMain/kotlin/com/together/newverse/data/firebase/model/ArticleDto.kt`
- **Created:** `/shared/src/commonMain/kotlin/com/together/newverse/data/firebase/model/OrderDto.kt`
- Added `@Serializable` annotations for GitLive SDK
- Maintains exact same structure for Firebase compatibility

### 🚧 Remaining Work

#### 1. Firebase Extensions
Need to create commonMain version of Firebase extensions that work with GitLive SDK:
- Child event observations
- Value observations
- Type-safe data retrieval
- Flow-based real-time updates

#### 2. Repository Implementations
Need to migrate:
- **FirebaseArticleRepository** - Article CRUD operations
- **FirebaseOrderRepository** - Order management
- **FirebaseProfileRepository** - User profile management

#### 3. Dependency Injection
Update DI modules to use commonMain implementations instead of androidMain

#### 4. Platform-Specific Initialization
- Android: Keep existing google-services.json
- iOS: Add GoogleService-Info.plist
- Initialize Firebase SDK on each platform

## Key Differences: Google Firebase vs GitLive

### 1. Initialization
**Google Firebase (Android):**
```kotlin
Firebase.auth
FirebaseDatabase.getInstance()
```

**GitLive (Multiplatform):**
```kotlin
Firebase.auth
Firebase.database
```

### 2. Data Serialization
**Google Firebase:**
```kotlin
snapshot.getValue(T::class.java)
```

**GitLive:**
```kotlin
snapshot.value<T>() // with @Serializable
```

### 3. Authentication State
**Google Firebase:**
```kotlin
auth.addAuthStateListener(listener)
```

**GitLive:**
```kotlin
auth.authStateChanged.collect { user -> }
```

### 4. Async Operations
**Google Firebase:**
```kotlin
task.await() // with kotlinx-coroutines-play-services
```

**GitLive:**
```kotlin
// Direct suspend functions, no Task wrapper
```

## Implementation Guidelines

### For Repository Migration

1. **Keep Same Interface:** Maintain the existing repository interfaces to minimize changes

2. **Use Serialization:** Add `@Serializable` to all DTOs

3. **Handle Platform Differences:** Use expect/actual for platform-specific code if needed

4. **Error Handling:** Map GitLive exceptions to match existing error handling

### For Firebase Extensions

Create extension functions that provide similar API to current androidMain extensions:

```kotlin
// Example migration pattern
suspend inline fun <reified T : Any> DatabaseReference.valueAsFlow(): Flow<T?> {
    return valueEvents.map { snapshot ->
        snapshot.value<T?>()
    }
}
```

## Testing Strategy

1. **Unit Tests:** Create common tests that verify repository behavior
2. **Integration Tests:** Test actual Firebase operations on both platforms
3. **Migration Tests:** Compare results between old and new implementations

## Benefits of Migration

✅ **iOS Support:** Native iOS support without additional wrappers
✅ **Code Sharing:** Single codebase for all Firebase operations
✅ **Type Safety:** Better type safety with Kotlin serialization
✅ **Coroutines Native:** Built-in coroutines support
✅ **Maintenance:** Single implementation to maintain

## Risks and Mitigation

⚠️ **Risk:** Breaking existing functionality
**Mitigation:** Keep androidMain implementations during migration

⚠️ **Risk:** Performance differences
**Mitigation:** Profile and optimize critical paths

⚠️ **Risk:** API differences causing bugs
**Mitigation:** Comprehensive testing on both platforms

## Next Steps

1. Complete FirebaseExtensions migration
2. Migrate remaining repositories
3. Update DI configuration
4. Test on iOS simulator
5. Performance testing
6. Remove androidMain implementations once stable

## References

- [GitLive Firebase Documentation](https://github.com/GitLiveApp/firebase-kotlin-sdk)
- [Firebase Multiplatform Migration Guide](https://firebase.blog/posts/2023/10/kotlin-multiplatform-support)
- [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)