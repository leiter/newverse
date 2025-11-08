# Cross-Platform Icon Setup

## Overview
Configured app icons with both cross-platform shared resources and platform-specific adaptive icon support for Android.

## Architecture

### Shared Resources (Compose Multiplatform)
Location: `shared/src/commonMain/composeResources/drawable/app_icon.png`

This high-resolution icon (xxxhdpi) can be used in Compose UI across all platforms:
```kotlin
import newverse.shared.generated.resources.Res
import org.jetbrains.compose.resources.painterResource

Image(
    painter = painterResource(Res.drawable.app_icon),
    contentDescription = "App Icon"
)
```

### Android Platform-Specific (Launcher Icons)
Location: `androidApp/src/main/res/`

Android launcher icons require specific structure:
```
res/
├── mipmap-mdpi/ic_launcher.png (1484 bytes)
├── mipmap-hdpi/ic_launcher.png (2385 bytes)
├── mipmap-xhdpi/ic_launcher.png (3335 bytes)
├── mipmap-xxhdpi/ic_launcher.png (5775 bytes)
├── mipmap-xxxhdpi/ic_launcher.png (7177 bytes)
├── mipmap-anydpi-v26/ic_launcher.xml (adaptive)
└── drawable/
    ├── ic_launcher_background.xml
    └── ic_launcher_foreground.xml
```

## Icon Details

### Visual Design
- **Theme:** Vegetables/produce (carrot or root vegetable)
- **Background Color:** Orange (#FA9C4D)
- **Style:** Flat design with plant symbol
- **Shape:** Rounded square

### Technical Specifications
- **Format:** PNG (raster) + XML (adaptive)
- **Densities:** mdpi (160) to xxxhdpi (640 dpi)
- **Adaptive Icon:** Yes (Android 8.0+)
- **Round Icon:** Yes (both regular and round variants)

## Adaptive Icon Configuration

### Background Layer
**File:** `res/drawable/ic_launcher_background.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FA9C4D"
        android:pathData="M0,0h108v108h-108z"/>
</vector>
```
- Solid orange color fill
- 108dp canvas (standard adaptive icon size)

### Foreground Layer
**File:** `res/drawable/ic_launcher_foreground.xml`
```xml
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <bitmap
            android:gravity="center"
            android:src="@mipmap/ic_vegi" />
    </item>
</layer-list>
```
- References the actual icon PNG
- Centered gravity for proper alignment

### Adaptive Icon Definitions
**Files:**
- `res/mipmap-anydpi-v26/ic_launcher.xml`
- `res/mipmap-anydpi-v26/ic_launcher_round.xml`

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

## Manifest Configuration

**File:** `androidApp/src/main/AndroidManifest.xml`
```xml
<application
    android:name=".NewverseApp"
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:label="@string/app_name"
    ...>
```

## Build Verification

✅ **All icon files packaged in APK**
✅ **Adaptive icons properly configured**
✅ **All density variants included**
✅ **Build successful: 12MB APK**
✅ **Icon displaying correctly in launcher** (Verified on Pixel 7a, Android 16)

### APK Contents Verified
```
res/drawable/ic_launcher_background.xml       568 bytes
res/drawable/ic_launcher_foreground.xml        448 bytes
res/mipmap-anydpi-v26/ic_launcher.xml          448 bytes
res/mipmap-anydpi-v26/ic_launcher_round.xml    448 bytes
res/mipmap-hdpi-v4/ic_launcher.png            2385 bytes
res/mipmap-mdpi-v4/ic_launcher.png            1484 bytes
res/mipmap-xhdpi-v4/ic_launcher.png           3335 bytes
res/mipmap-xxhdpi-v4/ic_launcher.png          5775 bytes
res/mipmap-xxxhdpi-v4/ic_launcher.png         7177 bytes
+ round variants for each density
```

## Installation & Testing

### Complete Reinstall (Recommended)
Use the enhanced reinstall script that aggressively clears all launcher caches:
```bash
cd /home/mandroid/Videos/newverse
./reinstall-buy-debug.sh
```

This script will:
- Show your device Android version (adaptive icons need Android 8.0+)
- Uninstall the old app completely
- Clear cache for multiple launcher variants (Pixel, Samsung, MIUI, etc.)
- Clean build the APK
- Install the new version
- Force-stop launchers to refresh
- Verify icon configuration in APK

### Manual Steps
```bash
# 1. Uninstall old app completely
adb uninstall com.together.newverse.buy

# 2. Clear launcher cache (force icon refresh)
adb shell pm clear com.android.launcher3
# or for Pixel Launcher
adb shell pm clear com.google.android.apps.nexuslauncher

# 3. Clean build
cd /home/mandroid/Videos/newverse
./gradlew clean

# 4. Build fresh APK
./gradlew :androidApp:assembleBuyDebug

# 5. Install
./gradlew :androidApp:installBuyDebug

# 6. If needed, restart device
adb reboot
```

## Expected Results

After installation, you should see:

1. **App Drawer:** Orange icon with vegetable symbol
2. **Home Screen:** Same icon (if added to home screen)
3. **Settings > Apps:** Icon visible in app list
4. **Adaptive Icon:**
   - On Android 8.0+, icon adapts to device theme
   - Background: Solid orange
   - Foreground: Vegetable symbol (can be animated by launcher)

## Platform Support

### ✅ Android
- Launcher icon: Full support (mdpi to xxxhdpi)
- Adaptive icon: Android 8.0+ (API 26+)
- Round icon: Supported on compatible devices
- Status: **Fully configured**

### 🔄 iOS (Future)
- Shared resource available in: `shared/composeResources/drawable/app_icon.png`
- iOS-specific assets needed in: `iosApp/Assets.xcassets/AppIcon.appiconset/`
- Status: **Pending iOS implementation**

### 🔄 Desktop (Future)
- Can use shared resource from Compose
- Status: **Pending desktop implementation**

## Troubleshooting

### Icon Not Updating

**Symptom:** Old icon still shows after reinstall

**Solutions:**
1. Clear launcher cache (see installation steps above)
2. Restart the device/emulator
3. Some launchers aggressively cache icons - try a different launcher
4. Check if multiple instances of the app are installed with different package names

### Icon Appears Distorted

**Symptom:** Icon looks stretched or pixelated

**Cause:** Launcher using wrong density variant

**Solution:** Icons are properly provided for all densities. Reinstall should fix this.

### Adaptive Icon Not Working

**Symptom:** Icon doesn't adapt to launcher theme

**Check:**
1. Device is running Android 8.0+ (API 26+)
2. Launcher supports adaptive icons (most modern launchers do)
3. Adaptive icon XMLs are in `mipmap-anydpi-v26/` folder

## Source Files

- **Original icons:** `/home/mandroid/Videos/universe/app/src/buy/res/`
- **Shared resource:** `shared/src/commonMain/composeResources/drawable/app_icon.png`
- **Android launcher icons:** `androidApp/src/main/res/mipmap-*/`
- **Adaptive icon resources:** `androidApp/src/main/res/drawable/ic_launcher_*`

## Maintenance

### Updating the Icon

To update the app icon:

1. **Create new icon** (xxxhdpi size recommended: 192x192px or higher)

2. **Update shared resource:**
   ```bash
   cp new_icon.png shared/src/commonMain/composeResources/drawable/app_icon.png
   ```

3. **Generate all density variants** (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)

4. **Update Android launcher icons:**
   ```bash
   cp new_icon_mdpi.png androidApp/src/main/res/mipmap-mdpi/ic_launcher.png
   cp new_icon_hdpi.png androidApp/src/main/res/mipmap-hdpi/ic_launcher.png
   # ... repeat for all densities
   ```

5. **Update background color** in `ic_launcher_background.xml` if needed

6. **Clean build:**
   ```bash
   ./gradlew clean :androidApp:assembleBuyDebug
   ```

### Adding iOS Icons

When ready to add iOS support:

1. Use the shared resource as source: `shared/composeResources/drawable/app_icon.png`
2. Generate iOS icon sizes using Xcode or icon generator tool
3. Add to `iosApp/Assets.xcassets/AppIcon.appiconset/`

## Benefits of This Setup

1. ✅ **Cross-platform ready:** Shared resource available for all platforms
2. ✅ **Android best practices:** Proper adaptive icon implementation
3. ✅ **All densities covered:** Looks good on all devices
4. ✅ **Standard naming:** Uses Android's expected `ic_launcher` convention
5. ✅ **Future-proof:** Easy to extend to iOS and Desktop
6. ✅ **Maintainable:** Clear structure with documentation
