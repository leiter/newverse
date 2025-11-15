# Firebase Authentication Implementation Overview

## Executive Summary

This document provides a comprehensive analysis of the Firebase authentication implementation in the Newverse application. The app currently uses **Google Firebase SDK** for Android with full multiplatform support in progress via **GitLive Firebase SDK**. Authentication is a critical component managing user sessions, profiles, and data access.

## Current Architecture

### 1. Authentication Layer Structure

```
Domain Layer (Interface)
└── AuthRepository (com.together.newverse.domain.repository.AuthRepository)

Data Layer (Implementations)
├── FirebaseAuthRepository (androidMain - Google Firebase)
├── InMemoryAuthRepository (commonMain - Testing/Development)
└── [Planned] GitLive-based AuthRepository (commonMain - Multiplatform)

UI Layer
└── UnifiedAppViewModel (State Management & Authentication Logic)
```

### 2. Current Tech Stack

**Active Firebase Dependencies (Android):**
- `com.google.firebase:firebase-auth-ktx:23.0.0` - Authentication
- `com.google.firebase:firebase-database-ktx:21.0.0` - Realtime Database
- `com.google.firebase:firebase-storage-ktx:21.0.0` - Storage
- `com.google.android.gms:play-services-auth:21.2.0` - Google Sign-In
- `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3` - Coroutines integration

**Planned GitLive Dependencies (Multiplatform):**
- `dev.gitlive:firebase-auth:2.1.0`
- `dev.gitlive:firebase-database:2.1.0`
- `dev.gitlive:firebase-storage:2.1.0`

## Authentication Methods Implemented

### 1. Email/Password Authentication
**Status:** Fully Implemented

**File:** `/shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseAuthRepository.kt`

**Methods:**
```kotlin
suspend fun signInWithEmail(email: String, password: String): Result<String>
suspend fun signUpWithEmail(email: String, password: String): Result<String>
suspend fun sendPasswordResetEmail(email: String): Result<Unit>
suspend fun updateEmail(newEmail: String): Result<Unit>
suspend fun updatePassword(newPassword: String): Result<Unit>
suspend fun reauthenticate(email: String, password: String): Result<Unit>
suspend fun resendEmailVerification(): Result<Unit>
```

**Key Features:**
- Input validation (email format, password length)
- Firebase-specific error handling with user-friendly messages
- Email verification support
- Password reset functionality
- Account reauthentication for sensitive operations

**Error Handling Examples:**
```
"Email and password cannot be empty"
"Invalid email format"
"Password must be at least 6 characters"
"An account already exists with this email"
"Incorrect password"
"Too many failed attempts. Please try again later"
"This account has been disabled"
```

### 2. Anonymous/Guest Authentication
**Status:** Fully Implemented

**File:** `/shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseAuthRepository.kt`

**Methods:**
```kotlin
suspend fun signInAnonymously(): Result<String>
suspend fun isAnonymous(): Boolean
suspend fun linkAnonymousAccount(email: String, password: String): Result<String>
```

**Purpose:**
- Allow users to browse and perform limited actions without creating an account
- Seamless upgrade to full account when user wants to persist data
- Used in the onboarding flow for guest mode

### 3. Google Sign-In
**Status:** Fully Implemented

**Files:**
- Implementation: `/shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseAuthRepository.kt`
- Helper: `/shared/src/androidMain/kotlin/com/together/newverse/util/GoogleSignInHelper.kt`
- UI Integration: `/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/common/LoginScreen.kt`

**Method:**
```kotlin
override suspend fun signInWithGoogle(idToken: String): Result<String>
```

**Architecture:**
1. `GoogleSignInHelper` handles the native Google Sign-In flow
2. Returns an ID token to the app
3. ID token is passed to Firebase Auth via credential
4. Firebase creates/retrieves user and returns UID

**Error Handling:**
```
"An account already exists with the same email but different sign-in credentials"
"Invalid Google credentials"
"This account has been disabled"
"Network error. Please check your connection"
```

### 4. Twitter Authentication
**Status:** Fully Implemented

**File:** `/shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseAuthRepository.kt`

**Method:**
```kotlin
override suspend fun signInWithTwitter(token: String, secret: String): Result<String>
```

**Architecture:**
1. Uses Firebase TwitterAuthProvider
2. Requires OAuth token and secret from Twitter
3. Creates credential and authenticates via Firebase
4. Returns user UID on success

**Dependencies:**
- Twitter Developer Account required
- API Key and Secret configured in Firebase Console
- Callback URL: `https://[PROJECT-ID].firebaseapp.com/__/auth/handler`

## Core Authentication Interface

**File:** `/shared/src/commonMain/kotlin/com/together/newverse/domain/repository/AuthRepository.kt`

```kotlin
interface AuthRepository {
    // Core authentication
    suspend fun checkPersistedAuth(): Result<String?>
    fun observeAuthState(): Flow<String?>
    suspend fun getCurrentUserId(): String?
    suspend fun signInWithEmail(email: String, password: String): Result<String>
    suspend fun signUpWithEmail(email: String, password: String): Result<String>
    suspend fun signOut(): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    
    // Social authentication
    suspend fun signInAnonymously(): Result<String>
    suspend fun isAnonymous(): Boolean
    suspend fun signInWithGoogle(idToken: String): Result<String>
    suspend fun signInWithTwitter(token: String, secret: String): Result<String>
}
```

**Key Methods:**
- `checkPersistedAuth()`: Checks for existing session on app startup
- `observeAuthState()`: Real-time Flow<String?> for auth state changes
- All methods return `Result<T>` for functional error handling

## Session Management

### Persistent Authentication
**File:** `/shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseAuthRepository.kt`

**Method:**
```kotlin
override suspend fun checkPersistedAuth(): Result<String?> {
    return try {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Refresh the token to ensure it's still valid
            currentUser.getIdToken(true).await()
            Result.success(currentUser.uid)
        } else {
            Result.success(null)
        }
    } catch (e: Exception) {
        Result.failure(Exception("Failed to check auth status: ${e.message}"))
    }
}
```

**Flow:**
1. Called on app startup
2. Checks Firebase.auth.currentUser
3. Refreshes ID token for validity
4. Returns UID or null
5. Enables seamless auto-login for returning users

### Real-time Auth State Observation
**Method:**
```kotlin
override fun observeAuthState(): Flow<String?> = callbackFlow {
    val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        trySend(user?.uid)
    }
    
    auth.addAuthStateListener(authStateListener)
    awaitClose {
        auth.removeAuthStateListener(authStateListener)
    }
}
```

**Used By:** UnifiedAppViewModel to update global app state whenever auth changes

## State Management Integration

### UnifiedAppViewModel
**File:** `/shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt`

**Auth State Observation (lines 63-89):**
```kotlin
private fun observeAuthState() {
    viewModelScope.launch {
        authRepository.observeAuthState().collect { userId ->
            _state.update { current ->
                current.copy(
                    common = current.common.copy(
                        user = if (userId != null) {
                            UserState.LoggedIn(
                                id = userId,
                                email = "",
                                name = "",
                                role = UserRole.CUSTOMER
                            )
                        } else {
                            UserState.Guest
                        }
                    )
                )
            }
        }
    }
}
```

**Auth Initialization (lines 43-61):**
```kotlin
init {
    initializeApp()           // Check persisted auth first
    observeAuthState()        // Listen for changes
    observeMainScreenBasket()
    observeMainScreenBuyerProfile()
    
    // Load articles after auth is ready
    viewModelScope.launch {
        authRepository.observeAuthState()
            .filterNotNull()
            .first()
        loadMainScreenArticles()
    }
}
```

**App State Structure:**
```kotlin
data class UnifiedAppState(
    val common: CommonState = CommonState(user = UserState.Guest),
    val meta: AppMetaState = AppMetaState(isInitializing = true),
    val screens: ScreensState = ScreensState()
)

sealed class UserState {
    object Guest : UserState()
    data class LoggedIn(
        val id: String,
        val email: String,
        val name: String,
        val role: UserRole
    ) : UserState()
}
```

## Testing Implementation

### InMemoryAuthRepository
**File:** `/shared/src/commonMain/kotlin/com/together/newverse/data/repository/InMemoryAuthRepository.kt`

**Purpose:** Development and testing without Firebase dependency

**Test Users:**
```
Email: test@buyer.com
Password: password123
User ID: buyer_001

Email: test@seller.com
Password: password123
User ID: seller_001
```

**Features:**
- In-memory user storage
- Simulated persistent storage
- 500ms artificial delay to simulate network
- Does NOT support social login (returns error)
- Used during development before Firebase integration

## Dependency Injection

### Android Configuration
**File:** `/shared/src/androidMain/kotlin/com/together/newverse/di/AndroidDomainModule.kt`

```kotlin
val androidDomainModule = module {
    single<AuthRepository> { FirebaseAuthRepository() }
    single<BasketRepository> { InMemoryBasketRepository() }
    single<ArticleRepository> { FirebaseArticleRepository() }
    single<OrderRepository> { FirebaseOrderRepository() }
    single<ProfileRepository> { FirebaseProfileRepository() }
}
```

### ViewModel Setup
**File:** `/shared/src/commonMain/kotlin/com/together/newverse/di/AppModule.kt`

```kotlin
val appModule = module {
    viewModel { UnifiedAppViewModel(get(), get(), get(), get(), get()) }
    // ... other viewmodels
}
```

**Injection Pattern:**
```kotlin
class UnifiedAppViewModel(
    private val articleRepository: ArticleRepository,
    private val orderRepository: OrderRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,      // Injected from DI
    private val basketRepository: BasketRepository
) : ViewModel()
```

## Firebase Configuration Files

**Locations:**
- `/androidApp/src/main/google-services.json` - Main configuration
- `/androidApp/src/debug/google-services.json` - Debug build config
- `/androidApp/src/release/google-services.json` - Release build config

**Android Build Configuration:**
- Plugin: `com.google.gms.google-services` in `androidApp/build.gradle.kts`
- Applies Firebase services to Android app

## Authentication Flow Diagram

```
App Startup
    ↓
UnifiedAppViewModel.init()
    ├─ checkPersistedAuth()
    │  └─ FirebaseAuth.currentUser? → UID or null
    │
    ├─ observeAuthState()
    │  └─ Auth listener → Flow<String?>
    │
    └─ loadMainScreenArticles() when auth ready
    
User Login (Email)
    ↓
UnifiedAppViewModel.login(email, password)
    ↓
authRepository.signInWithEmail(email, password)
    ↓
Firebase.auth.signInWithEmailAndPassword(email, password)
    ↓
FirebaseAuth creates/retrieves user
    ↓
authStateListener fires → observeAuthState() emits UID
    ↓
UnifiedAppViewModel updates state → UserState.LoggedIn
    ↓
UI receives new state and renders accordingly

User Login (Google)
    ↓
LoginScreen.onGoogleSignInClick()
    ↓
GoogleSignInHelper.getSignInIntent()
    ↓
User selects Google account
    ↓
GoogleSignInHelper.handleSignInResult() → ID Token
    ↓
authRepository.signInWithGoogle(idToken)
    ↓
Firebase.auth.signInWithCredential(GoogleAuthProvider.credential)
    ↓
[Rest same as email login]

User Logout
    ↓
UnifiedAppViewModel.logout()
    ↓
authRepository.signOut()
    ↓
Firebase.auth.signOut()
    ↓
authStateListener fires → observeAuthState() emits null
    ↓
UnifiedAppViewModel updates state → UserState.Guest
    ↓
UI navigates to login/home screen
```

## Error Handling Strategy

All authentication methods follow the pattern:

```kotlin
suspend fun methodName(...): Result<T> {
    return try {
        // Perform operation
        Result.success(value)
    } catch (e: Exception) {
        val errorMessage = when {
            e.message?.contains("ERROR_CODE") == true -> "User-friendly message"
            // ... more cases
            else -> e.message ?: "Generic error"
        }
        Result.failure(Exception(errorMessage))
    }
}
```

**Firebase Error Codes Handled:**
- `INVALID_EMAIL` - Email format validation
- `USER_DISABLED` - Account disabled by admin
- `USER_NOT_FOUND` - No account exists
- `WRONG_PASSWORD` - Incorrect credentials
- `EMAIL_EXISTS` - Duplicate registration
- `WEAK_PASSWORD` - Password strength
- `REQUIRES_RECENT_LOGIN` - Security re-auth needed
- `ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL` - Provider mismatch
- `TOO_MANY_REQUESTS` - Rate limiting
- `NETWORK_ERROR` - Connectivity issues

## Firebase Configuration Requirements

### Enable Authentication Providers

1. **Email/Password** ✅ Implemented
   - Firebase Console → Authentication → Sign-in method
   - Enable "Email/Password" provider

2. **Google Sign-In** ✅ Implemented
   - Enable "Google" provider in Firebase Console
   - Configure OAuth consent screen
   - Register app SHA-1 fingerprint
   - Download updated google-services.json

3. **Twitter Sign-In** ✅ Implemented
   - Enable "Twitter" provider in Firebase Console
   - Create Twitter Developer account
   - Create Twitter App (API Key & Secret)
   - Configure callback URL in Firebase
   - Enter credentials in Firebase Console

### Database Rules (Security)
The authentication state determines access:

```javascript
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    },
    "articles": {
      ".read": true,
      ".write": "auth !== null"
    },
    "orders": {
      "$orderId": {
        ".read": "root.child('orderOwner').child($orderId).val() === auth.uid",
        ".write": "root.child('orderOwner').child($orderId).val() === auth.uid"
      }
    }
  }
}
```

## Related Repositories

All repositories are integrated with authentication:

1. **FirebaseArticleRepository** - Articles (Google Firebase SDK)
2. **FirebaseOrderRepository** - Orders (Google Firebase SDK)
3. **FirebaseProfileRepository** - User profiles (Google Firebase SDK)

These use the authenticated user UID from authRepository.

## Known Issues and Migration Status

### Current Status
- **Build Status:** Working (Google Firebase on Android)
- **Platform Support:** Android only
- **Migration Target:** GitLive Firebase for multiplatform support

### Previous Migration Attempt
A migration to GitLive Firebase SDK was started but incomplete:
- Files backed up to `.backup` extension
- Build broken due to SDK conflicts
- Recommendation: Rollback and perform incremental migration
- Strategy: Keep Google Firebase for Android while adding GitLive for iOS

### Backup Files
Located in `/shared/src/androidMain/kotlin/com/together/newverse/data/`:
```
firebase/Database.kt.backup
firebase/FirebaseExtensions.kt.backup
firebase/model/ArticleDto.kt.backup
firebase/model/OrderDto.kt.backup
repository/FirebaseAuthRepository.kt.backup
```

## Security Considerations

### ✅ Implemented
- Password validation (minimum 6 characters)
- Email format validation
- Email verification support
- Reauthentication for sensitive operations
- Account deletion capability
- Token refresh on session check
- Firebase security rules

### ⚠️ Recommendations
- Enable Firebase App Check in production
- Configure Strong Password Policy
- Enable 2FA (Multi-Factor Authentication)
- Monitor suspicious login attempts
- Regularly audit access logs
- Implement account recovery flows
- Add rate limiting for API endpoints

## Next Steps for GitLive Migration

### Phase 1: Preparation
1. Document all Firebase operations
2. Create GitLive equivalents
3. Plan incremental migration strategy
4. Set up parallel testing

### Phase 2: Implementation
1. Migrate AuthRepository to commonMain with GitLive SDK
2. Create GitLive Firebase extensions
3. Migrate DTOs to use @Serializable
4. Update DI to use new implementations

### Phase 3: Repositories
1. Migrate ArticleRepository
2. Migrate OrderRepository
3. Migrate ProfileRepository
4. Test all CRUD operations

### Phase 4: iOS Support
1. Add iOS-specific Firebase initialization
2. Add GoogleService-Info.plist
3. Test on iOS simulator
4. Performance profiling

### Phase 5: Cleanup
1. Remove Google Firebase SDK from commonMain
2. Remove backup files
3. Remove platform-specific implementations
4. Documentation update

## Documentation References

Related documents in `/doc/`:
- `auth-repository-implementation.md` - Implementation guide
- `app-startup-authentication-flow.md` - Startup sequence
- `authentication-flow-and-article-loading.md` - Integration flow
- `social-authentication.md` - Google & Twitter setup
- `firebase-migration-to-gitlive.md` - Migration guide
- `firebase-gitlive-migration-summary.md` - Migration summary
- `firebase-migration-current-status.md` - Current status

## Code Statistics

**Authentication-Related Files:**
- `FirebaseAuthRepository.kt` - 428 lines (Android)
- `InMemoryAuthRepository.kt` - 235 lines (Common/Test)
- `FirebaseExtensions.kt` - 259 lines (Database utilities)
- `AuthRepository.kt` - 79 lines (Interface)
- `UnifiedAppViewModel.kt` - ~400 lines (partial, auth handling)

**Total LOC:** ~1,400 lines of authentication code

## Summary

The Newverse application has a **well-architected authentication system** using:
- **Google Firebase SDK** for production authentication on Android
- **InMemoryAuthRepository** for testing and development
- **Multiple authentication methods** (Email, Google, Twitter, Anonymous)
- **Clean separation of concerns** via repository pattern
- **Reactive state management** with Flow and StateFlow
- **Comprehensive error handling** with user-friendly messages
- **Proper dependency injection** using Koin

The system is **production-ready for Android** but requires **GitLive SDK migration** for full multiplatform support (iOS). The migration should be done incrementally to maintain stability.
