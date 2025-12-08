# Seller Product Creation

**Status:** Core functionality implemented

## Architecture

```
UI (CreateProductScreen)
  ↓
ViewModel (CreateProductViewModel)
  ↓
Repositories (ArticleRepository, StorageRepository)
  ↓
Firebase (Realtime Database, Storage)
```

## Key Components

### StorageRepository
- `uploadProductImage(imageData: ByteArray)` - Returns image URL
- Firebase or GitLive implementation (feature flag: `useGitLiveStorage`)

### ImagePicker (Platform-specific)
- **Android:** Gallery + Camera via Activity Result API
- **iOS:** Stub (needs implementation)

### Categories/Units
- `ProductCategory` enum: 13 predefined (Obst, Gemüse, etc.)
- `ProductUnit` enum: 12 types (kg, g, Stück, etc.)
- Countable vs measurable distinction

## Form Fields

1. Product Name* (required)
2. Search Terms* (comma-separated)
3. Price* (> 0)
4. Unit* (dropdown)
5. Weight per Piece* (required for countable units)
6. Category* (dropdown)
7. Detail Info (optional)
8. Availability (toggle)
9. Product Image* (required)

## Setup Required

### Android ImagePicker

1. Add FileProvider to AndroidManifest.xml:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriReadPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

2. Create `res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="images" path="." />
</paths>
```

3. Provide ImagePicker via CompositionLocal:
```kotlin
val imagePicker = remember { ImagePicker(this) }
CompositionLocalProvider(LocalImagePicker provides imagePicker) {
    NavGraph(...)
}
```

## Files

**Created:**
- `StorageRepository.kt`, `FirebaseStorageRepository.kt`
- `ImagePicker.kt` (expect/actual)
- `ProductCategory.kt`, `ProductUnit.kt`

**Modified:**
- `CreateProductViewModel.kt` - Form state, validation
- `CreateProductScreen.kt` - Full form UI
- `AppModule.kt`, `AndroidDomainModule.kt` - DI

## Not Yet Implemented

- Edit existing product
- Delete product
- iOS ImagePicker
- Barcode scanner
- Bulk import (BNN parser exists)
