# iOS Back Navigation

## Overview

iOS uses swipe gestures instead of hardware back button. Navigation Compose handles this automatically.

## How It Works

| Platform | Primary Back | Secondary |
|----------|-------------|-----------|
| Android | Hardware/software button | TopAppBar arrow |
| iOS | Swipe from left edge | TopAppBar arrow |

## Implementation

Uses **Navigation Compose 2.8.0-alpha10** with multiplatform support:

```kotlin
val navController = rememberNavController()

// TopAppBar back button (works on both platforms)
IconButton(onClick = { navController.navigateUp() }) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
}
```

The `ComposeUIViewController` automatically:
- Integrates with iOS navigation
- Handles swipe gestures
- Provides predictive back gesture (iOS 13+)

## Best Practices

**DO:**
- Use `navController.navigateUp()` or `popBackStack()`
- Show back arrow in TopAppBar for detail screens
- Let the library handle gestures

**DON'T:**
- Use `BackHandler` composable (Android-specific)
- Implement custom swipe handlers
- Disable iOS swipe gesture

## Testing

- Swipe from left edge → navigates back
- Partial swipe and release → cancels navigation
- Back arrow tap → instant navigation
