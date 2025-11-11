# Snackbar State Subscription Fix

## Date: 2025-11-11

## Issue
Snackbar state was not being subscribed to or displayed in the UI, even though the state was being updated correctly in the ViewModel.

## Root Cause
The `AppScaffold` had no `SnackbarHost` component to display snackbars. The snackbar state was being updated in `UnifiedAppState.common.ui.snackbar`, but there was no UI component observing and displaying this state.

## Architecture

### State Flow:
1. Action dispatched: `viewModel.dispatch(UnifiedUiAction.ShowSnackbar(message, type))`
2. ViewModel updates state: `_state.update { ... snackbar = SnackbarState(...) }`
3. **Missing**: UI observing and displaying the snackbar

## Implementation

### Changes Made:

1. **Added Imports** (AppScaffold.kt)
   ```kotlin
   import androidx.compose.material3.SnackbarHost
   import androidx.compose.material3.SnackbarHostState
   ```

2. **Created SnackbarHostState** (AppScaffold.kt:155)
   ```kotlin
   val snackbarHostState = remember { SnackbarHostState() }
   ```

3. **Added LaunchedEffect to Observe Snackbar State** (AppScaffold.kt:157-172)
   ```kotlin
   LaunchedEffect(appState.common.ui.snackbar) {
       appState.common.ui.snackbar?.let { snackbar ->
           snackbarHostState.showSnackbar(
               message = snackbar.message,
               actionLabel = snackbar.actionLabel,
               duration = when (snackbar.duration) {
                   SnackbarDuration.SHORT -> androidx.compose.material3.SnackbarDuration.Short
                   SnackbarDuration.LONG -> androidx.compose.material3.SnackbarDuration.Long
                   SnackbarDuration.INDEFINITE -> androidx.compose.material3.SnackbarDuration.Indefinite
               }
           )
           // Auto-hide snackbar after showing
           viewModel.dispatch(UnifiedUiAction.HideSnackbar)
       }
   }
   ```

4. **Added SnackbarHost to Scaffold** (AppScaffold.kt:247-249)
   ```kotlin
   Scaffold(
       // ... other parameters
       snackbarHost = {
           SnackbarHost(hostState = snackbarHostState)
       },
       topBar = {
           // ...
       }
   )
   ```

## How It Works

1. **State Update**: When `ShowSnackbar` action is dispatched, the ViewModel updates `appState.common.ui.snackbar`

2. **Observation**: The `LaunchedEffect` observes changes to `appState.common.ui.snackbar`

3. **Display**: When snackbar state becomes non-null, the LaunchedEffect shows it via `snackbarHostState.showSnackbar()`

4. **Cleanup**: After showing, the snackbar is automatically hidden by dispatching `HideSnackbar` action

5. **Rendering**: The `SnackbarHost` in the Scaffold renders the snackbar at the bottom of the screen

## Features Supported

- ✅ **Message Display**: Shows custom message text
- ✅ **Action Label**: Optional action button (e.g., "Retry", "Undo")
- ✅ **Duration Control**: SHORT, LONG, or INDEFINITE display duration
- ✅ **Type Support**: SUCCESS, ERROR, WARNING, INFO (state is tracked but visual styling can be enhanced)
- ✅ **Auto-dismiss**: Automatically hides after display

## Usage Example

```kotlin
// From any screen with access to the unified action dispatcher:
onAction(UnifiedUiAction.ShowSnackbar(
    message = "Item added to basket",
    type = SnackbarType.SUCCESS
))

// Or using the helper function:
onAction(showSnackbar("Profile updated successfully", SnackbarType.SUCCESS))
```

## Files Modified
- `shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/AppScaffold.kt`

## Build Status
✅ BUILD SUCCESSFUL

## Testing Recommendations

1. Test snackbar display when adding items to basket
2. Test error snackbars (e.g., failed login, network errors)
3. Test success snackbars (e.g., order placed, profile updated)
4. Test action button functionality if used
5. Verify snackbar appears at bottom of screen
6. Verify snackbar auto-dismisses after duration

## Future Enhancements

Consider adding visual styling based on `SnackbarType`:
- SUCCESS: Green background
- ERROR: Red background
- WARNING: Orange background
- INFO: Blue background

This could be done with a custom Snackbar composable or by using Material3's color system.
