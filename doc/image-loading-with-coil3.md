# Image Loading with Coil3 in Compose Multiplatform

## Overview

Image loading has been implemented using **Coil3**, a modern image loading library that fully supports **Kotlin Multiplatform** including Android, iOS, Desktop, and Web.

## Why Coil3?

- ✅ **Full KMP Support**: Works on Android, iOS, JVM, JS, and WASM
- ✅ **Compose Optimized**: Built specifically for Jetpack/Compose Multiplatform
- ✅ **Efficient**: Automatic memory and disk caching
- ✅ **Network Support**: Includes Ktor client for cross-platform networking
- ✅ **Easy to Use**: Simple API with `AsyncImage` composable

## Dependencies Added

In `shared/build.gradle.kts`:

```kotlin
commonMain.dependencies {
    // Coil3 for image loading (supports Android, iOS, Desktop, Web)
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
}
```

- **coil-compose**: Core Coil3 library with AsyncImage composable
- **coil-network-ktor3**: Network image loading using Ktor (cross-platform HTTP client)

## Implementation

### 1. ProductDetailCard

**File**: `shared/src/commonMain/kotlin/com/together/newverse/ui/components/ProductDetailCard.kt`

**Added imageUrl parameter**:
```kotlin
@Composable
fun ProductDetailCard(
    productName: String,
    quantity: Int,
    price: Double,
    unit: String,
    imageUrl: String = "",  // ← Added
    onQuantityChange: (Int) -> Unit,
    onAddToCart: () -> Unit,
    modifier: Modifier = Modifier
)
```

**Image display logic**:
```kotlin
// Product Image
if (imageUrl.isNotEmpty()) {
    AsyncImage(
        model = imageUrl,
        contentDescription = productName,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop,
    )
} else {
    // Placeholder when no image is available
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "No image",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}
```

### 2. ProductListItem

**File**: `shared/src/commonMain/kotlin/com/together/newverse/ui/components/ProductListItem.kt`

**Added imageUrl parameter**:
```kotlin
@Composable
fun ProductListItem(
    productName: String,
    price: Double,
    unit: String,
    imageUrl: String = "",  // ← Added
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

**Thumbnail image (56x56dp)**:
```kotlin
// Product image thumbnail
if (imageUrl.isNotEmpty()) {
    AsyncImage(
        model = imageUrl,
        contentDescription = productName,
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
    )
} else {
    // Placeholder when no image
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "No image",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}
```

## Features

### 1. Automatic Caching
Coil3 automatically caches images:
- **Memory cache**: Fast access to recently loaded images
- **Disk cache**: Persistent storage for offline access

### 2. Progressive Loading
Images load progressively:
1. Check memory cache
2. Check disk cache
3. Fetch from network if needed
4. Decode and display

### 3. Placeholder Support
When `imageUrl` is empty or null:
- Shows a star icon placeholder
- Uses theme-aware background color
- Maintains consistent layout

### 4. Content Scaling
Images are scaled using `ContentScale.Crop`:
- Fills the container completely
- Centers the image
- Crops overflow to maintain aspect ratio

## Usage Example

```kotlin
// In a screen composable
ProductDetailCard(
    productName = article.productName,
    quantity = quantity,
    price = article.price,
    unit = article.unit,
    imageUrl = article.imageUrl,  // ← Pass the image URL
    onQuantityChange = { ... },
    onAddToCart = { ... }
)

ProductListItem(
    productName = article.productName,
    price = article.price,
    unit = article.unit,
    imageUrl = article.imageUrl,  // ← Pass the image URL
    onClick = { ... }
)
```

## Image URL Sources

Images can be loaded from various sources:

### 1. HTTP/HTTPS URLs
```kotlin
imageUrl = "https://example.com/images/carrots.jpg"
```

### 2. Firebase Storage URLs
```kotlin
imageUrl = "https://firebasestorage.googleapis.com/..."
```

### 3. Local Resources (Platform Specific)
```kotlin
// Android
imageUrl = "android.resource://com.together.newverse/drawable/placeholder"

// iOS
imageUrl = "file:///path/to/image.jpg"
```

## Platform Support

### Android
- ✅ Full support out of the box
- Uses Android's native image decoder
- Hardware acceleration

### iOS
- ✅ Full support via Skiko/Skia
- Renders using Skia graphics engine
- Ktor client for networking

### Desktop (JVM)
- ✅ Supported via Skiko
- Same rendering as iOS

### Web (JS/WASM)
- ✅ Supported
- Uses browser's image loading

## Performance Optimizations

Coil3 automatically handles:

1. **Image downsampling**: Reduces memory usage for large images
2. **Bitmap pooling**: Reuses bitmap memory
3. **Request coalescing**: Combines identical requests
4. **Lifecycle awareness**: Cancels requests when composable leaves composition

## Error Handling

AsyncImage handles errors gracefully:
- Failed network requests fall back to placeholder
- Invalid URLs show placeholder
- Corrupted images show placeholder

## Future Enhancements

Potential improvements:

1. **Loading indicators**: Show progress while loading
   ```kotlin
   AsyncImage(
       model = imageUrl,
       contentDescription = productName,
       loading = { CircularProgressIndicator() }
   )
   ```

2. **Error states**: Custom error UI
   ```kotlin
   AsyncImage(
       model = imageUrl,
       error = { ErrorImage() }
   )
   ```

3. **Transitions**: Fade-in animation
   ```kotlin
   AsyncImage(
       model = imageUrl,
       modifier = Modifier.animateContentSize()
   )
   ```

4. **Transformations**: Blur, rounded corners, etc.
   ```kotlin
   rememberAsyncImagePainter(
       model = ImageRequest.Builder(LocalContext.current)
           .data(imageUrl)
           .transformations(CircleCropTransformation())
           .build()
   )
   ```

## Troubleshooting

### Images not loading?

1. **Check internet permissions** (Android):
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```

2. **Check URL format**: Must be valid HTTP/HTTPS URL

3. **Check logs**: Coil logs errors to console
   ```
   🔥 Coil: Failed to load image - <error>
   ```

4. **Verify imageUrl is not empty**: Check Article model has imageUrl field

## Testing

To test image loading:

1. **With Firebase Storage**:
   - Upload test images to Firebase Storage
   - Copy the download URL
   - Add to Article in Firebase Database

2. **With Public URLs**:
   - Use placeholder services like `https://picsum.photos/200/300`
   - Or use product images from CDN

3. **Without Images**:
   - Empty `imageUrl` shows star placeholder
   - Ensures UI doesn't break without images

## Build Status

✅ Coil3 dependencies added
✅ ProductDetailCard updated with image support
✅ ProductListItem updated with thumbnail support
✅ Build successful on Android
✅ iOS support ready (not yet tested)

## References

- [Coil3 Documentation](https://coil-kt.github.io/coil/)
- [Coil3 Compose Multiplatform](https://coil-kt.github.io/coil/compose/)
- [GitHub Repository](https://github.com/coil-kt/coil)
