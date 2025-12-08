# Image Loading with Coil3

## Overview

Cross-platform image loading using Coil3 with automatic caching.

## Setup

### Dependencies (shared/build.gradle.kts)

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

### Initialize in MainActivity

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    initializeImageLoader(this)
    // ...
}
```

## Usage

```kotlin
AsyncImage(
    model = imageUrl,
    contentDescription = productName,
    modifier = Modifier.size(80.dp),
    contentScale = ContentScale.Crop
)
```

With placeholder:
```kotlin
if (imageUrl.isNotEmpty()) {
    AsyncImage(
        model = imageUrl,
        contentDescription = productName,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
    )
} else {
    Box(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Star, "No image")
    }
}
```

## Caching (Multiplatform)

**Implementation**: `shared/src/.../util/ImageLoaderConfig.kt` (expect/actual)

| Cache | Size | Location |
|-------|------|----------|
| Memory | 25% RAM | Automatic |
| Disk | 100MB | `{cacheDir}/image_cache` |

Features:
- Automatic eviction when memory pressure
- Crossfade animations
- Offline support (cached images)

## Platform Support

- Android: Full support with OkHttp
- iOS: Full support with Ktor Darwin client
- Desktop/Web: Supported via Skiko
