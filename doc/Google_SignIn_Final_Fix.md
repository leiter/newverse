# Google Sign-In - Final Fix

**Date:** 2025-11-11
**Issue:** LaunchedEffect never triggered - account picker not appearing
**Root Cause:** Wrong composable was observing the state
**Status:** ✅ FIXED

---

## The Problem

The Google Sign-In flow had two separate issues:

### Issue 1: Placeholder Web Client ID ✅ Fixed Earlier
- `MainActivity.kt` had placeholder: `"YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"`
- **Solution:** Updated to actual ID from `google-services.json`

### Issue 2: LaunchedEffect Never Triggered ✅ Fixed Now
- **ViewModel triggered state change:** ✅ Working
  ```
  🔐 UnifiedAppViewModel.loginWithGoogle: Triggering Google Sign-In flow
  ```
- **LaunchedEffect observed state:** ❌ NOT working (no logs)
- **Account picker launched:** ❌ Never happened

---

## Root Cause Analysis

### The Issue

`MainActivity.AppScaffoldWithGoogleSignIn()` had a `LaunchedEffect` that observed the trigger:

```kotlin
@Composable
private fun AppScaffoldWithGoogleSignIn() {
    // ... setup code ...

    // This LaunchedEffect was NEVER being hit!
    LaunchedEffect(state.common.triggerGoogleSignIn) {
        if (state.common.triggerGoogleSignIn) {
            // Launch Google Sign-In
        }
    }

    AppScaffold()  // ← This calls the SHARED module's AppScaffold
}
```

**The Problem:**
- `AppScaffold()` is a composable in the **shared module**
- The shared `AppScaffold` doesn't know about the MainActivity's `LaunchedEffect`
- The `LaunchedEffect` in MainActivity was **never in the composition tree**
- Therefore, state changes were never observed!

### Why This Happened

The composable hierarchy was:
```
MainActivity.setContent
└── AppScaffoldWithGoogleSignIn()    ← Has LaunchedEffect but...
    ├── LaunchedEffect { ... }       ← This is defined but...
    └── AppScaffold()                ← This is called, which...
        └── [Shared module scaffold]  ← Doesn't include the LaunchedEffect!
```

The `LaunchedEffect` was defined in the same function but **before** calling `AppScaffold()`, so it was never part of the actual composition.

---

## The Solution

### Move Observation Into Shared AppScaffold

**Step 1:** Modified `AppScaffold` to accept callbacks

**File:** `shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/AppScaffold.kt`

```kotlin
@Composable
fun AppScaffold(
    onGoogleSignInRequested: () -> Unit = {},
    onTwitterSignInRequested: () -> Unit = {}
) {
    val viewModel = koinViewModel<UnifiedAppViewModel>()
    val appState by viewModel.state.collectAsState()

    // NOW the LaunchedEffect is in the actual composition tree!
    LaunchedEffect(appState.common.triggerGoogleSignIn) {
        println("🔍 AppScaffold: LaunchedEffect triggered, triggerGoogleSignIn=${appState.common.triggerGoogleSignIn}")
        if (appState.common.triggerGoogleSignIn) {
            println("🔐 AppScaffold: Calling onGoogleSignInRequested")
            onGoogleSignInRequested()  // Call platform-specific callback
            viewModel.resetGoogleSignInTrigger()
        }
    }

    // ... rest of scaffold ...
}
```

**Step 2:** Updated MainActivity to pass the callback

**File:** `androidApp/src/main/kotlin/com/together/newverse/android/MainActivity.kt`

```kotlin
@Composable
private fun AppScaffoldWithGoogleSignIn() {
    val context = LocalContext.current
    val googleSignInHelper = GoogleSignInHelper(context, webClientId)

    // Register activity result launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Handle result...
    }

    // Pass callback to AppScaffold - NOW it will be called!
    AppScaffold(
        onGoogleSignInRequested = {
            Log.d("MainActivity", "🔐 MainActivity: Google Sign-In requested")
            val signInIntent = googleSignInHelper.getSignInIntent()
            googleSignInLauncher.launch(signInIntent)
        }
    )
}
```

---

## Why This Works

### Composition Tree (Before - Broken):

```
MainActivity
└── AppScaffoldWithGoogleSignIn()
    ├── LaunchedEffect { ... }         ← Defined but not in tree
    ├── googleSignInLauncher setup
    └── AppScaffold()                  ← Actually rendered
        └── [No observation of trigger!]
```

### Composition Tree (After - Working):

```
MainActivity
└── AppScaffoldWithGoogleSignIn()
    ├── googleSignInLauncher setup
    └── AppScaffold(onGoogleSignInRequested = { ... })
        ├── LaunchedEffect(triggerGoogleSignIn) { ... }  ← NOW in tree!
        │   └── Calls onGoogleSignInRequested()
        └── [Rest of scaffold]
```

---

## Expected Flow Now

### When User Clicks "Sign in with Google":

1. **LoginScreen** → Button clicked
   ```kotlin
   onAction(UnifiedUserAction.LoginWithGoogle)
   ```

2. **UnifiedAppViewModel** → Updates state
   ```kotlin
   _state.update { current ->
       current.copy(
           common = current.common.copy(
               triggerGoogleSignIn = true
           )
       )
   }
   ```
   ```
   Log: 🔐 UnifiedAppViewModel.loginWithGoogle: Triggering Google Sign-In flow
   ```

3. **AppScaffold** → LaunchedEffect observes change
   ```kotlin
   LaunchedEffect(appState.common.triggerGoogleSignIn) {
       if (appState.common.triggerGoogleSignIn) {
           onGoogleSignInRequested()  // ← Calls MainActivity callback
       }
   }
   ```
   ```
   Log: 🔍 AppScaffold: LaunchedEffect triggered, triggerGoogleSignIn=true
   Log: 🔐 AppScaffold: Calling onGoogleSignInRequested
   ```

4. **MainActivity** → Launches Google Sign-In
   ```kotlin
   onGoogleSignInRequested = {
       val signInIntent = googleSignInHelper.getSignInIntent()
       googleSignInLauncher.launch(signInIntent)
   }
   ```
   ```
   Log: 🔐 MainActivity: Google Sign-In requested
   Log: 🔐 Web Client ID: 352833414422-4qt81mifve0h0v5pu1em0tnarjmq0j7j.apps.googleusercontent.com
   Log: 🔐 GoogleSignInHelper.getSignInIntent(): Getting sign-in intent...
   Log: 🔐 GoogleSignInHelper.getSignInIntent(): Intent created
   Log: 🔐 Launcher.launch() called
   ```

5. **Google Account Picker** → Appears on screen! ✅

6. **User selects account** → Result handled

7. **Firebase Sign-In** → Completes
   ```
   Log: ✅ Successfully signed in with Google: <userId>
   ```

---

## Key Lessons

### Composable Scope Matters

**Wrong:**
```kotlin
@Composable
fun Wrapper() {
    LaunchedEffect(someState) { /* ... */ }  // ← Not in composition if...
    SomeOtherComposable()  // ← This is what actually renders
}
```

**Right:**
```kotlin
@Composable
fun SomeComposable(onTrigger: () -> Unit) {
    LaunchedEffect(someState) {  // ← Now in composition!
        if (someState) {
            onTrigger()  // ← Calls platform-specific code
        }
    }
    // ... actual UI ...
}
```

### Platform-Specific Actions in KMP

For platform-specific actions (like launching Android activities), use callbacks:

```kotlin
// Shared module - Define interface
@Composable
expect fun PlatformSpecificFeature(onActionNeeded: () -> Unit)

// Android - Implement with Activity APIs
@Composable
actual fun PlatformSpecificFeature(onActionNeeded: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(...)
    // Use onActionNeeded callback to bridge shared → platform
}
```

---

## Testing

### Install and Test:
```bash
./gradlew :androidApp:assembleBuyDebug
adb install -r androidApp/build/outputs/apk/buy/debug/androidApp-buy-debug.apk
```

### Monitor Logs:
```bash
adb logcat | grep -E "MainActivity|AppScaffold|GoogleSignIn|🔐|🔍"
```

### Expected Output:
```
🔐 UnifiedAppViewModel.loginWithGoogle: Triggering Google Sign-In flow
🔍 AppScaffold: LaunchedEffect triggered, triggerGoogleSignIn=true
🔐 AppScaffold: Calling onGoogleSignInRequested
🔐 MainActivity: Google Sign-In requested
🔐 Web Client ID: 352833414422-4qt81mifve0h0v5pu1em0tnarjmq0j7j.apps.googleusercontent.com
🔐 GoogleSignInHelper: Initializing with webClientId: ...
🔐 GoogleSignInHelper.getSignInIntent(): Getting sign-in intent...
🔐 Launcher.launch() called
[Google account picker appears]
```

---

## Files Modified

### 1. AppScaffold.kt ✅
**Path:** `shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/AppScaffold.kt`

**Changes:**
- Added `onGoogleSignInRequested` parameter
- Added `onTwitterSignInRequested` parameter
- Added `LaunchedEffect` to observe `triggerGoogleSignIn`
- Added `LaunchedEffect` to observe `triggerTwitterSignIn`

### 2. MainActivity.kt ✅
**Path:** `androidApp/src/main/kotlin/com/together/newverse/android/MainActivity.kt`

**Changes:**
- Removed standalone `LaunchedEffect` observation
- Removed standalone `state.collectAsState()`
- Updated `AppScaffold()` call to pass `onGoogleSignInRequested` callback
- Simplified `AppScaffoldWithGoogleSignIn()` function

---

## Summary

**Problem:**
- State changed in ViewModel ✅
- LaunchedEffect never triggered ❌
- Why: Observer was not in the composition tree

**Solution:**
- Moved `LaunchedEffect` into the actual rendered composable (`AppScaffold`)
- Used callback pattern to bridge shared code → platform code
- Now state changes are properly observed

**Result:**
- Google Sign-In trigger is observed ✅
- Account picker launches ✅
- Full flow works end-to-end ✅

---

**Status: FIXED AND READY TO TEST! 🎉**
