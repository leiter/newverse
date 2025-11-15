# Firebase Authentication - Quick Reference Guide

## Key Files

| Component | File | Lines | Status |
|-----------|------|-------|--------|
| Interface | `domain/repository/AuthRepository.kt` | 79 | Active |
| Implementation | `androidMain/data/repository/FirebaseAuthRepository.kt` | 428 | Active |
| Testing | `commonMain/data/repository/InMemoryAuthRepository.kt` | 235 | Active |
| Extensions | `androidMain/data/firebase/FirebaseExtensions.kt` | 259 | Active |
| State Mgmt | `commonMain/ui/state/UnifiedAppViewModel.kt` | ~400 | Active |
| DI (Android) | `androidMain/di/AndroidDomainModule.kt` | 34 | Active |
| DI (App) | `commonMain/di/AppModule.kt` | 31 | Active |

## Authentication Methods

```kotlin
// Email/Password
signInWithEmail(email, password) -> Result<String>
signUpWithEmail(email, password) -> Result<String>

// Social (Google & Twitter)
signInWithGoogle(idToken) -> Result<String>
signInWithTwitter(token, secret) -> Result<String>

// Guest
signInAnonymously() -> Result<String>
isAnonymous() -> Boolean
linkAnonymousAccount(email, password) -> Result<String>

// Session Management
checkPersistedAuth() -> Result<String?>        // Startup
observeAuthState() -> Flow<String?>            // Real-time
getCurrentUserId() -> String?
signOut() -> Result<Unit>
deleteAccount() -> Result<Unit>

// Account Management
sendPasswordResetEmail(email) -> Result<Unit>
updateEmail(newEmail) -> Result<Unit>
updatePassword(newPassword) -> Result<Unit>
reauthenticate(email, password) -> Result<Unit>
resendEmailVerification() -> Result<Unit>
isEmailVerified() -> Boolean
getCurrentUserEmail() -> String?
```

## Dependencies

**Active:**
- `com.google.firebase:firebase-auth-ktx:23.0.0`
- `com.google.firebase:firebase-database-ktx:21.0.0`
- `com.google.firebase:firebase-storage-ktx:21.0.0`
- `com.google.android.gms:play-services-auth:21.2.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3`

**Planned (GitLive Multiplatform):**
- `dev.gitlive:firebase-auth:2.1.0`
- `dev.gitlive:firebase-database:2.1.0`
- `dev.gitlive:firebase-storage:2.1.0`

## Error Codes Handled

| Code | Message |
|------|---------|
| `INVALID_EMAIL` | Invalid email format |
| `USER_NOT_FOUND` | No account with this email |
| `WRONG_PASSWORD` | Incorrect password |
| `EMAIL_EXISTS` | Account already exists |
| `WEAK_PASSWORD` | Password too weak (min 6 chars) |
| `USER_DISABLED` | Account disabled by admin |
| `REQUIRES_RECENT_LOGIN` | Re-authentication needed |
| `TOO_MANY_REQUESTS` | Rate limited |
| `NETWORK_ERROR` | No connection |
| `ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL` | Provider mismatch |

## App State Structure

```kotlin
sealed class UserState {
    object Guest : UserState()
    data class LoggedIn(
        val id: String,        // Firebase UID
        val email: String,     // From profile
        val name: String,      // From profile
        val role: UserRole     // CUSTOMER, SELLER, etc
    ) : UserState()
}
```

## Initialization Flow

```
1. App Start
   └─> UnifiedAppViewModel.init()
       ├─ checkPersistedAuth() - Check for existing session
       ├─ observeAuthState() - Listen to auth changes
       └─ loadMainScreenArticles() - Load content after auth ready

2. User Logs In
   └─> signInWithEmail/Google/Twitter()
       └─> observeAuthState() emits userId
           └─> ViewModel updates state to LoggedIn
               └─> UI updates

3. User Logs Out
   └─> signOut()
       └─> observeAuthState() emits null
           └─> ViewModel updates state to Guest
               └─> UI updates
```

## Testing

**Test Users in InMemoryAuthRepository:**
```
Buyer:  test@buyer.com / password123 (ID: buyer_001)
Seller: test@seller.com / password123 (ID: seller_001)
```

**To Enable/Disable Auto-Login:**
```kotlin
// In InMemoryAuthRepository.init():
persistedUserId = "buyer_001"  // Enable auto-login
// persistedUserId = null     // Disable auto-login
```

## DI Configuration

```kotlin
// Android provides:
single<AuthRepository> { FirebaseAuthRepository() }

// Used by ViewModel:
viewModel { UnifiedAppViewModel(get(), get(), get(), get(), get()) }
```

## Firebase Configuration

**Enable Providers:**
1. Email/Password - Firebase Console
2. Google - Register SHA-1, configure OAuth
3. Twitter - Add API credentials, configure callback

**Security Rules:**
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
    }
  }
}
```

## Migration Status

**Current:** Google Firebase on Android only
**Target:** GitLive for multiplatform (Android + iOS)
**Status:** Previous migration incomplete - files backed up

**Backup Files:**
- `firebase/Database.kt.backup`
- `firebase/FirebaseExtensions.kt.backup`
- `firebase/model/ArticleDto.kt.backup`
- `firebase/model/OrderDto.kt.backup`
- `repository/FirebaseAuthRepository.kt.backup`

## Key Integration Points

1. **AuthRepository** - All auth operations
2. **UnifiedAppViewModel** - State management
3. **FirebaseArticleRepository** - Uses auth UID
4. **FirebaseOrderRepository** - Uses auth UID
5. **FirebaseProfileRepository** - Stores user profile
6. **LoginScreen** - UI for auth actions

## Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| User state not updating | Ensure `observeAuthState()` is called in ViewModel init |
| Login button disabled | Check that `isLoading` is set to false after login |
| Error not displaying | Verify error state is collected in UI |
| Session not persisting | Check `checkPersistedAuth()` is called on startup |
| Google Sign-In fails | Verify SHA-1 fingerprint registered in Firebase |
| Twitter auth fails | Check API credentials in Firebase Console |

## Performance Notes

- `checkPersistedAuth()` called once on startup
- `observeAuthState()` listener added in init, removed in destroy
- Token refresh automatic on `checkPersistedAuth()`
- Real-time updates via Flow (cold, collected on demand)

## Security Features

✅ Password validation (min 6 chars)
✅ Email format validation
✅ Email verification support
✅ Reauthentication for sensitive ops
✅ Token refresh on session check
✅ Firebase security rules
⚠️ Firebase App Check not enabled (recommended for prod)
⚠️ 2FA not implemented (recommended for prod)

## Related Repositories

- **ArticleRepository** - Uses auth UID for data access
- **OrderRepository** - Uses auth UID for order queries
- **ProfileRepository** - Stores user profile data
- **BasketRepository** - Uses auth UID for shopping state

