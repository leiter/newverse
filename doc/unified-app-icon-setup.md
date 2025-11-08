# Unified App Icon Setup

## Overview
Configured the buyer app icon (`ic_vegi`) as the default icon for all flavors to ensure consistent app icon display across all variants.

## Changes Made

### 1. Copied Icons to Main Source Set
All `ic_vegi` icon resources have been copied to the main source set so they're available to all flavors by default:

```
androidApp/src/main/res/
├── mipmap-hdpi/ic_vegi.png
├── mipmap-mdpi/ic_vegi.png
├── mipmap-xhdpi/ic_vegi.png
├── mipmap-xxhdpi/ic_vegi.png
├── mipmap-xxxhdpi/ic_vegi.png
├── mipmap-anydpi-v26/ic_vegi.xml (adaptive icon)
└── drawable/ic_vegi_background.xml
```

### 2. Updated Main AndroidManifest
**File:** `androidApp/src/main/AndroidManifest.xml`

Added icon configuration to the main manifest:
```xml
<application
    android:name=".NewverseApp"
    android:allowBackup="true"
    android:icon="@mipmap/ic_vegi"
    android:roundIcon="@mipmap/ic_vegi"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@style/AppTheme">
```

### 3. Simplified Flavor Manifests

#### Buy Flavor Manifest
**File:** `androidApp/src/buy/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Buy flavor uses the default ic_vegi icon from main -->
</manifest>
```
- No icon override needed
- Inherits `ic_vegi` icon from main manifest

#### Sell Flavor Manifest
**File:** `androidApp/src/sell/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Sell flavor overrides with seller-specific icon -->
    <application
        android:icon="@mipmap/ic_vegi"
        android:roundIcon="@mipmap/ic_vegi"
        tools:replace="android:icon,android:roundIcon">
    </application>
</manifest>
```
- Also uses `ic_vegi` icon for consistency
- Can be changed to `ic_launcher` later if needed for seller differentiation

## Icon Details

### ic_vegi Icon
- **Theme:** Vegetables/produce themed
- **Style:** Colorful, professional customer-facing design
- **Format:** PNG for all densities + Adaptive Icon (XML)
- **Adaptive Icon Layers:**
  - Background: `@drawable/ic_vegi_background` (solid color)
  - Foreground: `@mipmap/ic_vegi` (icon graphic)

### Supported Densities
- ✅ mdpi (160dpi)
- ✅ hdpi (240dpi)
- ✅ xhdpi (320dpi)
- ✅ xxhdpi (480dpi)
- ✅ xxxhdpi (640dpi)
- ✅ anydpi-v26 (Adaptive icons for Android 8.0+)

## Build Verification

✅ **Buy Debug Build:** `BUILD SUCCESSFUL`
✅ **Sell Debug Build:** `BUILD SUCCESSFUL`

Both variants now use the buyer app icon (`ic_vegi`) consistently.

## APK Output
- **Buy APK:** `androidApp/build/outputs/apk/buy/debug/androidApp-buy-debug.apk` (12MB)
- **Sell APK:** `androidApp/build/outputs/apk/sell/debug/androidApp-sell-debug.apk`

## Benefits

1. **Consistency:** Same icon appears across all flavors
2. **Simplicity:** Single icon configuration in main source set
3. **Maintainability:** Easier to update icon in one place
4. **Reliability:** No integration issues with flavor-specific resources
5. **Future-proof:** Can still override per flavor if needed

## Future Customization

If you want to use different icons per flavor in the future:

### For Sell Flavor
Update `androidApp/src/sell/AndroidManifest.xml`:
```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    tools:replace="android:icon,android:roundIcon">
</application>
```

The sell-specific icons are still available in:
`androidApp/src/sell/res/mipmap-*/ic_launcher*.png`

## Testing

To verify the icon is correctly applied:

1. Build and install the app:
   ```bash
   ./gradlew :androidApp:installBuyDebug
   # or
   ./gradlew :androidApp:installSellDebug
   ```

2. Check the home screen - the `ic_vegi` icon should be displayed

3. Check app info in Settings - icon should match

## Source
Icon originally from: `/home/mandroid/Videos/universe/app/src/buy/res/`
