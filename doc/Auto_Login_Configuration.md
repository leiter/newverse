# Auto-Login Configuration

**Date:** 2025-11-11
**Status:** ⚠️ AUTO-LOGIN CURRENTLY DISABLED FOR TESTING
**Purpose:** Enable manual testing of Google Sign-In

---

## Current State: Auto-Login DISABLED

Auto-login and automatic guest sign-in have been **temporarily disabled** to allow manual testing of the Google Sign-In functionality.

---

## What Was Changed

### File: `UnifiedAppViewModel.kt`

**Location:** `shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt`

### Changes Made:

#### 1. **Disabled Auto Guest Sign-In** (Lines 283-337)

**Before:**
```kotlin
private suspend fun checkAuthenticationStatus() {
    authRepository.checkPersistedAuth().fold(
        onSuccess = { userId ->
            if (userId != null) {
                // Restore session
            } else {
                // Auto sign-in as guest
                signInAsGuest()  // ✅ ENABLED
            }
        },
        onFailure = { error ->
            // Auto sign-in as guest on error
            signInAsGuest()  // ✅ ENABLED
        }
    )
}
```

**After:**
```kotlin
private suspend fun checkAuthenticationStatus() {
    authRepository.checkPersistedAuth().fold(
        onSuccess = { userId ->
            if (userId != null) {
                // Restore session
            } else {
                // Wait for manual login
                // DISABLED: signInAsGuest()  // ❌ DISABLED
            }
        },
        onFailure = { error ->
            // Wait for manual login
            // DISABLED: signInAsGuest()  // ❌ DISABLED
        }
    )
}
```

#### 2. **Modified App Initialization** (Lines 200-302)

**Before:**
```kotlin
private fun initializeApp() {
    // Check auth
    checkAuthenticationStatus()

    // Wait for auth to complete
    val userId = authRepository.observeAuthState()
        .filterNotNull()
        .first()  // Blocks until user is authenticated

    // Load products
    loadProducts()
}
```

**After:**
```kotlin
private fun initializeApp() {
    // Check auth
    checkAuthenticationStatus()

    // Skip waiting for auth - load immediately
    loadProducts()

    // Mark as initialized
    // (Original code commented out)
}
```

---

## Behavior Changes

### Before (Auto-Login Enabled):
1. App starts
2. Check for persisted auth session
3. If no session → **automatically sign in as guest**
4. Wait for Firebase auth to complete
5. Load products
6. Show home screen

### After (Auto-Login Disabled):
1. App starts
2. Check for persisted auth session
3. If no session → **wait for manual login**
4. Load products immediately (no auth required)
5. Show home screen
6. User must manually click "Sign in with Google" or "Sign In"

---

## Impact on User Experience

### With Auto-Login Disabled:

**✅ Benefits:**
- Can test Google Sign-In flow manually
- Can test email/password login without interference
- Can verify login UI displays correctly
- Can test auth state changes

**⚠️ Limitations:**
- User is not automatically signed in
- Basket and user-specific features won't work until logged in
- Profile data won't load automatically
- No anonymous/guest user session

---

## How to Test Google Sign-In

### Step 1: Build and Install
```bash
./gradlew :androidApp:assembleBuyDebug
adb install -r androidApp/build/outputs/apk/buy/debug/androidApp-buy-debug.apk
```

### Step 2: Launch App
- App will start without auto-login
- No Firebase authentication will occur automatically

### Step 3: Navigate to Login Screen
- Open drawer → Click "Login"
- Or if login screen appears automatically

### Step 4: Test Google Sign-In
- Click "Sign in with Google" button
- Google account picker should appear
- Select account
- Verify sign-in completes successfully
- Check user state updates correctly

### Step 5: Verify Auth State
- Check console logs for:
  ```
  🔐 UnifiedAppViewModel.loginWithGoogle: Triggering Google Sign-In flow
  App Startup: No persisted auth - waiting for manual login...
  ```

---

## How to Re-Enable Auto-Login

When you're done testing and want to restore automatic guest sign-in:

### Method 1: Uncomment the Code

#### In `checkAuthenticationStatus()` (Line 316, 329, 335):
```kotlin
// Change from:
// DISABLED: signInAsGuest()

// To:
signInAsGuest()
```

#### In `initializeApp()` (Lines 215-300):
```kotlin
// Delete the simplified version (lines 215-239)
// Uncomment the original code (lines 241-300)
```

### Method 2: Use Git to Revert

```bash
# Check the changes
git diff shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt

# Revert just this file
git checkout shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt

# Rebuild
./gradlew :androidApp:assembleBuyDebug
```

---

## Original Code (For Reference)

### Original `checkAuthenticationStatus()`:

```kotlin
private suspend fun checkAuthenticationStatus() {
    try {
        _state.update { current ->
            current.copy(
                meta = current.meta.copy(
                    initializationStep = "Checking authentication..."
                )
            )
        }

        authRepository.checkPersistedAuth().fold(
            onSuccess = { userId ->
                if (userId != null) {
                    println("App Startup: Restored auth session for user: $userId")
                    _state.update { current ->
                        current.copy(
                            meta = current.meta.copy(
                                initializationStep = "Loading user data..."
                            )
                        )
                    }
                } else {
                    println("App Startup: No persisted auth, signing in as guest...")
                    signInAsGuest()  // ← RE-ENABLE THIS
                }
            },
            onFailure = { error ->
                println("App Startup: Failed to check auth - ${error.message}, signing in as guest...")
                signInAsGuest()  // ← RE-ENABLE THIS
            }
        )
    } catch (e: Exception) {
        println("App Startup: Exception checking auth - ${e.message}, signing in as guest...")
        signInAsGuest()  // ← RE-ENABLE THIS
    }
}
```

### Original `initializeApp()` auth waiting:

```kotlin
// Step 2: Wait for auth state to be ready (non-null userId)
try {
    println("App Init: Waiting for authentication to complete...")
    val userId = authRepository.observeAuthState()
        .filterNotNull()
        .first()

    println("App Init: Authentication complete, user ID: $userId")

    // Step 3: Load data based on auth status
    val isAuthenticated = _state.value.common.user is UserState.LoggedIn

    if (isAuthenticated) {
        _state.update { current ->
            current.copy(
                meta = current.meta.copy(
                    initializationStep = "Loading user data..."
                )
            )
        }
        loadUserProfile()
    }

    // Load products
    _state.update { current ->
        current.copy(
            meta = current.meta.copy(
                initializationStep = "Loading products..."
            )
        )
    }
    loadProducts()

    // Mark complete
    _state.update { current ->
        current.copy(
            meta = current.meta.copy(
                isInitializing = false,
                isInitialized = true,
                initializationStep = ""
            )
        )
    }
} catch (e: Exception) {
    println("App Init: Error waiting for auth: ${e.message}")
}
```

---

## Testing Checklist

### Manual Login Testing:
- [ ] App starts without automatic authentication
- [ ] Login screen is accessible
- [ ] Google Sign-In button triggers account picker
- [ ] Selected Google account signs in successfully
- [ ] User state updates after successful login
- [ ] Products load after login
- [ ] Basket functionality works after login

### Email/Password Testing:
- [ ] Email/password login works
- [ ] Registration creates new account
- [ ] Error messages display correctly
- [ ] Success messages show after login

### Auth State Testing:
- [ ] User remains logged in after app restart (persisted session)
- [ ] Logout clears user state
- [ ] Basket clears after logout
- [ ] Login screen shows after logout

---

## Console Log Examples

### With Auto-Login Disabled (Current):
```
App Init: Set loading state to true
App Startup: No persisted auth - waiting for manual login...
App Init: Skipping auth wait for manual testing...
App Init: Initialization complete (manual testing mode)
```

### With Auto-Login Enabled (Original):
```
App Init: Set loading state to true
App Startup: No persisted auth, signing in as guest...
App Startup: Guest sign-in successful, user ID: abc123
App Init: Waiting for authentication to complete...
App Init: Authentication complete, user ID: abc123
App Init: Initialization complete
```

---

## Related Files

### Authentication Flow:
- **ViewModel:** `shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt`
- **Repository:** `shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseAuthRepository.kt`
- **Google Helper:** `shared/src/androidMain/kotlin/com/together/newverse/util/GoogleSignInHelper.kt`

### UI Screens:
- **Login:** `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/common/LoginScreen.kt`
- **Register:** `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/common/RegisterScreen.kt`

---

## Important Notes

### ⚠️ Remember to Re-Enable Auto-Login

This is a **temporary configuration** for testing purposes only. Don't forget to:

1. **Re-enable auto-login** before production deployment
2. **Uncomment the original code** in `UnifiedAppViewModel.kt`
3. **Test the full flow** after re-enabling
4. **Update this documentation** when changes are made

### 🔍 Why Auto-Login Exists

Auto-login (guest sign-in) is important for:
- **User Experience:** Users can browse products without signing up
- **Basket Functionality:** Anonymous users can add items to basket
- **Firebase Requirements:** Many Firebase operations require authentication
- **Smooth Onboarding:** Reduces friction for first-time users

---

## Quick Reference

| Feature | Auto-Login Enabled | Auto-Login Disabled |
|---------|-------------------|---------------------|
| App starts | Auto sign-in as guest | No authentication |
| Product browsing | ✅ Works | ✅ Works |
| Basket | ✅ Works (anonymous) | ❌ Requires manual login |
| User profile | ❌ Guest user | ❌ No user |
| Manual login | ✅ Can upgrade guest | ✅ Must login first |
| Testing Google Sign-In | ⚠️ Conflicts with guest | ✅ Clear testing |

---

## Summary

**Current Status:** Auto-login is **DISABLED** for manual Google Sign-In testing.

**To Test:**
1. Build and install app
2. Navigate to login screen
3. Click "Sign in with Google"
4. Verify sign-in works correctly

**To Restore:**
1. Uncomment `signInAsGuest()` calls (3 places)
2. Restore original `initializeApp()` code
3. Rebuild app

**File Modified:** `UnifiedAppViewModel.kt` (lines 283-337, 200-302)

---

**Auto-login configuration documented! 📝**
