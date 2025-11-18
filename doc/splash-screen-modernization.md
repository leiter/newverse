# Splash Screen Modernization

## Current Implementation (Legacy Pattern)

The app currently uses the **legacy splash screen pattern**:

```kotlin
// AndroidManifest.xml
<activity android:theme="@style/AppTheme.Splash">

// MainActivity.onCreate()
setTheme(R.style.AppTheme)  // Switches theme
```

This works, but **`setTheme()` is necessary** to remove the splash background.

## Modern Alternative: SplashScreen API (Android 12+)

### Benefits
- No `setTheme()` call needed
- Smoother transition
- Backward compatible to API 21
- Follows Android 12+ guidelines
- Better animations

### Implementation

**1. Add dependency** (`androidApp/build.gradle.kts`):
```kotlin
dependencies {
    implementation("androidx.core:core-splashscreen:1.0.1")
}
```

**2. Update themes.xml**:
```xml
<style name="AppTheme" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/orange</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/banner</item>
    <item name="postSplashScreenTheme">@style/AppTheme.Main</item>
</style>

<style name="AppTheme.Main" parent="android:Theme.Material.Light.NoActionBar">
    <!-- Regular app theme -->
</style>
```

**3. Update MainActivity**:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // Install splash screen (MUST be before super.onCreate())
    installSplashScreen()

    super.onCreate(savedInstanceState)

    // NO setTheme() call needed!

    setupImageLoader()
    imagePicker = ImagePicker(this)
    enableEdgeToEdge()
    setContent { /* ... */ }
}
```

**4. Update AndroidManifest**:
```xml
<activity
    android:name=".MainActivity"
    android:theme="@style/AppTheme"  <!-- Just AppTheme, no .Splash -->
    android:exported="true">
```

### Migration Steps

If you want to modernize:

1. Add SplashScreen library dependency
2. Update themes.xml with SplashScreen styles
3. Remove `setTheme()` call from MainActivity
4. Add `installSplashScreen()` before `super.onCreate()`
5. Update AndroidManifest theme reference
6. Test on Android 12+ and older devices

## Recommendation

**Current implementation is fine** for now, but consider migrating to SplashScreen API when you have time. The modern API:
- Eliminates the need for `setTheme()`
- Provides better UX on Android 12+
- Is backward compatible
- Is the recommended approach by Google

## References

- [Android Splash Screen Guide](https://developer.android.com/develop/ui/views/launch/splash-screen)
- [SplashScreen Library](https://developer.android.com/reference/kotlin/androidx/core/splashscreen/SplashScreen)
