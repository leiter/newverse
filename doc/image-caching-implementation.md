# Image Caching Implementation (Multiplatform)

## Overview

Image caching has been implemented in the **shared module** using Coil3's multiplatform capabilities. This provides optimized image loading and caching for product images across all platforms (Android, iOS).

## Architecture

### Multiplatform Design (expect/actual pattern)

**Common Interface** (`shared/src/commonMain/kotlin/com/together/newverse/util/ImageLoaderConfig.kt`):
```kotlin
expect fun createImageLoader(context: PlatformContext): ImageLoader
expect fun initializeImageLoader(context: PlatformContext)
```

**Android Implementation** (`shared/src/androidMain/kotlin/com/together/newverse/util/ImageLoaderConfig.android.kt`):
- Memory cache: 25% of available RAM
- Disk cache: 100MB at `cacheDir/image_cache`
- Crossfade animations enabled
- Debug logging (automatically enabled in debug builds)

**iOS Implementation** (`shared/src/iosMain/kotlin/com/together/newverse/util/ImageLoaderConfig.ios.kt`):
- Memory cache: 25% of available RAM
- Disk cache: 100MB at NSCachesDirectory
- Crossfade animations enabled
- Ready for future iOS deployment

## Usage

### Initialization

The ImageLoader is initialized once during app startup in `MainActivity.onCreate()`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize image caching (from shared module)
    initializeImageLoader(this)

    // ... rest of initialization
}
```

### Image Loading

Images are loaded using Coil's `AsyncImage` composable (already implemented throughout the app):

```kotlin
AsyncImage(
    model = imageUrl,
    contentDescription = "Product image",
    modifier = Modifier.size(80.dp)
)
```

## Benefits

1. **Cross-platform**: Single configuration for Android and iOS
2. **Automatic caching**: Images cached on first load
3. **Offline support**: Cached images available without network
4. **Performance**:
   - Memory cache for instant access
   - Disk cache for persistent storage
5. **Bandwidth savings**: Reduced Firebase Storage requests
6. **Smooth UX**: Crossfade animations for better visual experience

## Cache Management

### Memory Cache
- Automatically evicts least-recently-used images when memory pressure occurs
- Size: 25% of available RAM (dynamic)

### Disk Cache
- Maximum size: 100MB
- Location (Android): `{app_cache_dir}/image_cache`
- Location (iOS): `{NSCachesDirectory}/image_cache`
- Automatically cleaned by OS when storage is low

## Files Modified

### Created Files
- `shared/src/commonMain/kotlin/com/together/newverse/util/ImageLoaderConfig.kt`
- `shared/src/androidMain/kotlin/com/together/newverse/util/ImageLoaderConfig.android.kt`
- `shared/src/iosMain/kotlin/com/together/newverse/util/ImageLoaderConfig.ios.kt`

### Modified Files
- `androidApp/src/main/kotlin/com/together/newverse/android/MainActivity.kt` (simplified to use shared config)
- `androidApp/build.gradle.kts` (removed duplicate Coil dependencies)

## Dependencies

All image loading dependencies are in the **shared module** (`shared/build.gradle.kts`):

```kotlin
commonMain.dependencies {
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
}

androidMain.dependencies {
    implementation("io.ktor:ktor-client-okhttp:3.0.1")
}

iosMain.dependencies {
    implementation("io.ktor:ktor-client-darwin:3.0.1")
}
```

## Testing

Both Buy and Sell flavors built successfully with shared caching:
- `androidApp-buy-debug.apk`: 18MB
- `androidApp-sell-debug.apk`: 18MB

## Future Enhancements

1. **Cache size configuration**: Allow users to configure cache size in settings
2. **Cache clearing**: Add UI option to clear image cache
3. **Preloading**: Preload commonly accessed images
4. **Analytics**: Track cache hit/miss rates
5. **iOS deployment**: Test on actual iOS devices when ready
