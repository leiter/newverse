# App Startup Authentication Flow

## Overview

Authentication is checked during startup to determine Guest or LoggedIn mode before loading data.

## Flow

```
App Launch → AppScaffold
    ↓
isInitializing = true → Show SplashScreen
    ↓
ViewModel.checkAuthenticationStatus()
    ↓
GitLiveAuthRepository.checkPersistedAuth()
    ↓
Firebase.auth.currentUser check
    ↓
Has Session? → Yes → Restore User, load user data
            → No  → Guest mode (BUY) or Force Login (SELL)
    ↓
isInitializing = false → Show Main UI
```

## Key Components

### 1. GitLiveAuthRepository

```kotlin
override suspend fun checkPersistedAuth(): Result<String?> {
    val currentUser = Firebase.auth.currentUser
    return if (currentUser != null) {
        currentUser.getIdToken(true)  // Refresh token
        Result.success(currentUser.uid)
    } else {
        Result.success(null)
    }
}
```

### 2. AppScaffold

Conditionally renders based on state:
- `isInitializing = true` → SplashScreen
- `requiresLogin = true` → ForcedLoginScreen (seller)
- Otherwise → Main UI

### 3. ViewModel Init

```kotlin
init {
    observeAuthState()
    initializeApp()
}

private fun initializeApp() {
    checkAuthenticationStatus()
    // Wait for auth state
    loadProducts()
}
```

## App State

```kotlin
data class AppMetaState(
    val isInitializing: Boolean = false,
    val isInitialized: Boolean = false
)

data class CommonState(
    val user: UserState = UserState.Guest,
    val requiresLogin: Boolean = false  // For seller flavor
)
```

## Build Flavors

| Flavor | No Session Behavior |
|--------|---------------------|
| BUY | Auto sign-in as guest |
| SELL | Force login (no guest access) |

## Testing Scenarios

1. **Returning user**: Firebase restores session → Skip login
2. **New user (BUY)**: No session → Guest mode
3. **New user (SELL)**: No session → Forced login screen
4. **Invalid session**: Token validation fails → Treated as new user
