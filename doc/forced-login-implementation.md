# Forced Login (Seller Flavor)

**Status:** Complete

## Overview

Sellers must authenticate before accessing app features - no guest access.

## How It Works

```
App Start → Splash → Check Auth
  ↓
[SELL flavor + No session] → requiresLogin = true → ForcedLoginScreen
[SELL flavor + Has session] → requiresLogin = false → Main UI
```

## Implementation

### State Flag

```kotlin
data class CommonState(
    val requiresLogin: Boolean = false  // Set based on flavor + auth state
)
```

### Auth Check (UnifiedAppViewModel)

```kotlin
// In checkAuthenticationStatus()
if (BuildFlavor.isSeller && userId == null) {
    requiresLogin = true
} else if (BuildFlavor.allowsGuestAccess) {
    signInAsGuest()
}
```

### UI Conditional (AppScaffold)

```kotlin
when {
    isInitializing -> SplashScreen()
    requiresLogin -> ForcedLoginScreen(onLogin = viewModel::login)
    else -> MainAppUI()
}
```

## Key Files

- `BuildFlavor.kt` - Flavor detection (`isSeller`, `allowsGuestAccess`)
- `ForcedLoginScreen.kt` - Login UI (Material 3, email/password + Google)
- `UnifiedAppViewModel.kt` - Auth flow logic
- `AppScaffold.kt` - Conditional rendering

## Testing

1. **Test forced login**: Clear app data → Should show login
2. **Test auto-login**: Login once, close/reopen → Skip login
3. **Test logout**: Logout → Immediate return to login screen
