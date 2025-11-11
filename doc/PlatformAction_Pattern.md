# Platform Action Pattern

**Date:** 2025-11-11
**Purpose:** Clean callback architecture for platform-specific actions
**Status:** ✅ IMPLEMENTED

---

## Overview

Instead of having multiple callback parameters for each platform-specific action, we now use a single `onPlatformAction` callback with a sealed interface.

---

## Design

### Sealed Interface for Actions

**File:** `shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/AppScaffold.kt`

```kotlin
/**
 * Platform-specific actions that need to be handled by the platform layer
 */
sealed interface PlatformAction {
    data object GoogleSignIn : PlatformAction
    data object TwitterSignIn : PlatformAction
    // Future actions can be added here:
    // data class ShareContent(val text: String, val url: String?) : PlatformAction
    // data class OpenCamera : PlatformAction
    // data class RequestPermission(val permission: String) : PlatformAction
}
```

---

## Implementation

### Shared Module - AppScaffold

```kotlin
@Composable
fun AppScaffold(
    onPlatformAction: (PlatformAction) -> Unit = {}
) {
    val viewModel = koinViewModel<UnifiedAppViewModel>()
    val appState by viewModel.state.collectAsState()

    // Observe Google Sign-In trigger
    LaunchedEffect(appState.common.triggerGoogleSignIn) {
        if (appState.common.triggerGoogleSignIn) {
            onPlatformAction(PlatformAction.GoogleSignIn)
            viewModel.resetGoogleSignInTrigger()
        }
    }

    // Observe Twitter Sign-In trigger
    LaunchedEffect(appState.common.triggerTwitterSignIn) {
        if (appState.common.triggerTwitterSignIn) {
            onPlatformAction(PlatformAction.TwitterSignIn)
            viewModel.resetTwitterSignInTrigger()
        }
    }

    // ... rest of scaffold
}
```

### Android Platform - MainActivity

```kotlin
@Composable
private fun AppScaffoldWithGoogleSignIn() {
    val context = LocalContext.current
    val googleSignInHelper = GoogleSignInHelper(context, webClientId)

    // Register activity result launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Handle Google Sign-In result...
    }

    // Handle platform actions
    AppScaffold(
        onPlatformAction = { action ->
            when (action) {
                is PlatformAction.GoogleSignIn -> {
                    val signInIntent = googleSignInHelper.getSignInIntent()
                    googleSignInLauncher.launch(signInIntent)
                }
                is PlatformAction.TwitterSignIn -> {
                    // Handle Twitter sign-in
                }
            }
        }
    )
}
```

---

## Benefits

### ✅ Clean API

**Before (Multiple Callbacks):**
```kotlin
AppScaffold(
    onGoogleSignInRequested = { /* ... */ },
    onTwitterSignInRequested = { /* ... */ },
    onFacebookSignInRequested = { /* ... */ },
    onCameraRequested = { /* ... */ },
    // Gets messy quickly!
)
```

**After (Single Callback):**
```kotlin
AppScaffold(
    onPlatformAction = { action ->
        when (action) {
            is PlatformAction.GoogleSignIn -> { /* ... */ }
            is PlatformAction.TwitterSignIn -> { /* ... */ }
            is PlatformAction.FacebookSignIn -> { /* ... */ }
            is PlatformAction.Camera -> { /* ... */ }
        }
    }
)
```

### ✅ Type-Safe

The sealed interface ensures:
- All actions are handled at compile time
- Exhaustive `when` expressions
- No stringly-typed action names

### ✅ Extensible

Adding new platform actions is easy:

```kotlin
sealed interface PlatformAction {
    data object GoogleSignIn : PlatformAction
    data object TwitterSignIn : PlatformAction

    // New actions:
    data class ShareContent(
        val text: String,
        val url: String? = null
    ) : PlatformAction

    data class OpenCamera(
        val outputUri: String
    ) : PlatformAction

    data class RequestPermission(
        val permission: String
    ) : PlatformAction
}
```

### ✅ Testable

Easy to test by passing a lambda that captures actions:

```kotlin
@Test
fun testGoogleSignInAction() {
    var capturedAction: PlatformAction? = null

    composeTestRule.setContent {
        AppScaffold(
            onPlatformAction = { action ->
                capturedAction = action
            }
        )
    }

    // Trigger sign-in...

    assertThat(capturedAction).isInstanceOf<PlatformAction.GoogleSignIn>()
}
```

---

## Pattern Explanation

### Flow

1. **User action** in UI (e.g., click "Sign in with Google")
2. **ViewModel** updates state trigger
3. **AppScaffold** observes state change via `LaunchedEffect`
4. **AppScaffold** calls `onPlatformAction(PlatformAction.GoogleSignIn)`
5. **Platform layer** (MainActivity) handles the action
6. **Platform APIs** are invoked (ActivityResultLauncher, etc.)

### Architecture Layers

```
┌─────────────────────────────────────────┐
│  UI Layer (LoginScreen)                 │
│  - User clicks button                   │
│  - Calls onAction(UserAction)           │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│  ViewModel Layer                        │
│  - Handles UserAction                   │
│  - Updates state.triggerGoogleSignIn    │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│  Shared Scaffold (AppScaffold)          │
│  - Observes state changes               │
│  - Calls onPlatformAction()             │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│  Platform Layer (MainActivity)          │
│  - Receives PlatformAction              │
│  - Uses platform-specific APIs          │
│  - (ActivityResultLauncher, etc.)       │
└─────────────────────────────────────────┘
```

---

## Future Extensions

### Adding New Actions

**Step 1:** Add to sealed interface
```kotlin
sealed interface PlatformAction {
    // ... existing actions

    data class PickImage(
        val allowMultiple: Boolean = false
    ) : PlatformAction
}
```

**Step 2:** Trigger from ViewModel
```kotlin
// Add state trigger
data class CommonState(
    // ... existing state
    val triggerImagePicker: ImagePickerConfig? = null
)

// Add action
sealed interface UnifiedAppAction {
    // ... existing actions
    data class PickImage(val config: ImagePickerConfig) : UnifiedAppAction
}

// Handle in ViewModel
fun dispatch(action: UnifiedAppAction) {
    when (action) {
        is UnifiedAppAction.PickImage -> {
            _state.update { current ->
                current.copy(
                    common = current.common.copy(
                        triggerImagePicker = action.config
                    )
                )
            }
        }
    }
}
```

**Step 3:** Observe in AppScaffold
```kotlin
LaunchedEffect(appState.common.triggerImagePicker) {
    appState.common.triggerImagePicker?.let { config ->
        onPlatformAction(PlatformAction.PickImage(config.allowMultiple))
        viewModel.resetImagePickerTrigger()
    }
}
```

**Step 4:** Handle in Platform
```kotlin
AppScaffold(
    onPlatformAction = { action ->
        when (action) {
            is PlatformAction.PickImage -> {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(
                        mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }
            // ... other actions
        }
    }
)
```

---

## Comparison with Alternatives

### Alternative 1: Expect/Actual

```kotlin
// ❌ Not flexible, requires recompilation for changes
expect fun launchGoogleSignIn(context: Context)

actual fun launchGoogleSignIn(context: Context) {
    // Android implementation
}
```

**Downsides:**
- Requires recompilation of shared module
- Can't pass composable-scoped dependencies
- Harder to test

### Alternative 2: Multiple Callbacks

```kotlin
// ❌ Gets messy with many actions
AppScaffold(
    onGoogleSignIn = { },
    onTwitterSignIn = { },
    onCamera = { },
    onShare = { },
    // ... 20 more parameters
)
```

**Downsides:**
- Parameter explosion
- Hard to maintain
- Unclear which callbacks are required

### Alternative 3: PlatformAction (Current) ✅

```kotlin
// ✅ Clean, extensible, type-safe
AppScaffold(
    onPlatformAction = { action ->
        when (action) { /* handle */ }
    }
)
```

**Benefits:**
- Single parameter
- Type-safe sealed interface
- Easy to extend
- Clear separation of concerns

---

## Testing

### Mock Platform Actions

```kotlin
@Test
fun testPlatformActions() {
    val actions = mutableListOf<PlatformAction>()

    composeTestRule.setContent {
        AppScaffold(
            onPlatformAction = { action ->
                actions.add(action)
            }
        )
    }

    // Perform UI actions that should trigger platform actions
    composeTestRule.onNodeWithText("Sign in with Google").performClick()

    // Verify
    assertThat(actions).hasSize(1)
    assertThat(actions[0]).isInstanceOf<PlatformAction.GoogleSignIn>()
}
```

---

## Summary

**Pattern:** Single callback with sealed interface for platform-specific actions

**Benefits:**
- ✅ Clean API
- ✅ Type-safe
- ✅ Extensible
- ✅ Testable
- ✅ Maintainable

**Usage:**
```kotlin
// Shared module
AppScaffold(onPlatformAction = { action -> ... })

// Platform module
when (action) {
    is PlatformAction.GoogleSignIn -> { /* Android-specific code */ }
    is PlatformAction.TwitterSignIn -> { /* Android-specific code */ }
}
```

**Status:** ✅ Implemented and ready to use!
