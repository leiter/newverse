# iOS Back Navigation in KMP Project

## Overview

iOS doesn't have a hardware back button like Android, so back navigation is handled differently across platforms. This document explains how your KMP project handles back navigation on both platforms.

## Current Implementation

### Navigation Library

The project uses **Jetpack Navigation Compose 2.8.0-alpha10** which has **full multiplatform support** for iOS:

```kotlin
// shared/build.gradle.kts
implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
```

This library automatically handles platform-specific navigation behaviors.

## Platform-Specific Back Navigation

### Android Back Navigation

On Android, back navigation is triggered by:

1. **Hardware/Software Back Button**: System back button automatically triggers `navController.popBackStack()`
2. **TopAppBar Back Button**: Manual back arrow shown on detail screens
   ```kotlin
   IconButton(onClick = { navController.navigateUp() }) {
       Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
   }
   ```
3. **Gesture**: Swipe from left edge (Android 13+)

### iOS Back Navigation

On iOS, back navigation works through:

1. **Swipe Gesture (Primary)**:
   - **Swipe from left edge** of screen to go back
   - This is the **native iOS gesture** that users expect
   - Automatically handled by `ComposeUIViewController` and Navigation Compose
   - Works throughout the entire app without any additional code

2. **TopAppBar Back Button**:
   - Same arrow button as Android (`Icons.AutoMirrored.Filled.ArrowBack`)
   - Calls `navController.navigateUp()` when clicked
   - Located in `AppScaffold.kt:279`

3. **No BackHandler Needed**:
   - Android's `BackHandler` composable is **NOT** used (correctly)
   - iOS handles gestures automatically through the navigation library
   - No platform-specific code needed for basic back navigation

## Implementation Details

### AppScaffold Back Button Logic

The TopAppBar dynamically shows either a back button or hamburger menu based on the current route:

```kotlin
// AppScaffold.kt:270-286
if (currentRoute.startsWith(NavRoutes.Buy.Basket.route) ||
    currentRoute.startsWith(NavRoutes.Buy.Profile.route) ||
    currentRoute == NavRoutes.Buy.OrderHistory.route ||
    currentRoute == NavRoutes.About.route ||
    currentRoute == NavRoutes.Register.route) {
    // Show BACK arrow for detail screens
    IconButton(onClick = { navController.navigateUp() }) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
    }
} else {
    // Show MENU hamburger for main screens
    IconButton(onClick = { drawerState.open() }) {
        Icon(Icons.Default.Menu, "Menu")
    }
}
```

This logic **works identically on both Android and iOS**.

### Navigation Controllers

**Common Code** (`AppScaffold.kt`):
```kotlin
val navController = rememberNavController()
```

**iOS Entry Point** (`MainViewController.kt`):
```kotlin
fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        NewverseTheme {
            AppScaffold(onPlatformAction = { ... })
        }
    }
}
```

The `ComposeUIViewController` automatically:
- Integrates with iOS navigation stack
- Handles swipe gestures
- Manages navigation bar appearance
- Provides predictive back gesture (iOS 13+)

## User Experience Comparison

| Feature | Android | iOS |
|---------|---------|-----|
| **Primary Back Action** | Hardware/software button | Swipe from left edge |
| **Secondary Back Action** | TopAppBar back arrow | TopAppBar back arrow |
| **Gesture** | Swipe from left edge (Android 13+) | Swipe from left edge (all versions) |
| **Animation** | Slide left | Slide left (with parallax) |
| **Visual Feedback** | None during gesture | Preview of previous screen |

## iOS-Specific Behavior

### Predictive Back Gesture

On iOS, the swipe gesture shows a **preview** of the previous screen:

1. User starts swiping from left edge
2. Current screen slides right (revealing previous screen underneath)
3. User can continue to complete navigation or cancel by releasing
4. Smooth animation completes or reverses based on swipe distance

This is **automatically handled** by the Navigation Compose library on iOS.

### Navigation Bar Integration

When you eventually deploy to iOS, you can optionally integrate with UINavigationController:

```swift
// Future iOS SwiftUI integration (example)
NavigationView {
    ComposeViewControllerWrapper()
        .navigationBarBackButtonHidden(true) // Use Compose navigation instead
}
```

## Best Practices

### ✅ DO

1. **Use `navController.navigateUp()` or `popBackStack()`**: Works on both platforms
2. **Show back arrow in TopAppBar**: Provides familiar visual cue
3. **Let the library handle gestures**: Don't implement custom swipe handlers
4. **Test navigation flow**: Ensure deep navigation works on both platforms

### ❌ DON'T

1. **Don't use `BackHandler` composable**: Android-specific, not multiplatform
2. **Don't implement custom swipe gestures**: Conflicts with iOS native behavior
3. **Don't assume hardware back button**: iOS users expect gestures
4. **Don't disable iOS swipe gesture**: Users expect this behavior

## Testing iOS Navigation

When you build for iOS, test these scenarios:

1. **Swipe from left edge**: Should navigate back smoothly
2. **Swipe and release**: Should cancel navigation
3. **Back arrow tap**: Should navigate back instantly
4. **Deep navigation**: Swipe multiple levels back
5. **Drawer interaction**: Ensure drawer doesn't conflict with back gesture

## Future Enhancements

### Predictive Back Gesture (Android)

Android 13+ supports predictive back gestures similar to iOS. The Navigation Compose library will automatically support this when you update to a stable version.

### Custom Transitions

You can customize navigation transitions per platform:

```kotlin
// Example: Platform-specific transitions
expect fun getNavTransitions(): NavTransitions

// iOS: Slide from right (native)
// Android: Fade or slide (Material Design)
```

## References

- [Jetpack Navigation Compose Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-navigation-routing.html)
- [iOS Human Interface Guidelines - Navigation](https://developer.apple.com/design/human-interface-guidelines/navigation)
- [Compose Multiplatform UIViewController](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-uiviewcontroller.html)

## Summary

Your KMP project handles iOS back navigation **automatically** through:

1. **Navigation Compose 2.8.0-alpha10**: Multiplatform navigation library
2. **ComposeUIViewController**: iOS integration layer
3. **Swipe gestures**: Native iOS behavior (no custom code needed)
4. **TopAppBar back button**: Shared code works on both platforms

**No iOS-specific back navigation code is required** - it just works!
