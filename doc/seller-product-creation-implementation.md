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
3. Price* (selling price, > 0 — auto-filled by pricing calculation)
4. Acquire Price (Einkaufspreis, optional — purchase/cost price)
5. Markup Factor (Aufschlag, default 1.0 — multiplier on acquire price)
6. Tax Rate (MwSt., dropdown: 0% / 7% / 19%, configurable via `ProductCatalogConfig`)
7. Unit* (dropdown)
8. Weight per Piece* (required for countable units)
9. Category* (dropdown)
10. Detail Info (optional)
11. Availability (toggle)
12. Product Image* (required)

## Pricing Calculation

Selling price is derived automatically:

```
sellPrice = acquirePrice × markupFactor × (1 + taxRate)
```

Bidirectional: editing the sell price directly recalculates `markupFactor`:

```
markupFactor = sellPrice / (acquirePrice × (1 + taxRate))
```

All three fields (`acquirePrice`, `markupFactor`, `taxRate`) are persisted to Firebase via `ArticleDto`.

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

## BNN Import

The BNN parser (`BnnParser.kt`) populates `acquirePrice` from field position 35 (Einkaufspreis). Position 37 is the selling price. Both are parsed with German decimal format (comma → dot).

## Not Yet Implemented

- iOS ImagePicker
- Barcode scanner
