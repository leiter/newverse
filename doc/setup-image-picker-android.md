# Setup ImagePicker for Android App

## Overview
The seller product creation feature requires an ImagePicker to select/capture product images. This guide shows how to set it up using CompositionLocal.

---

## Step 1: Provide ImagePicker in Your App

Find where you call `NavGraph` (likely in `MainActivity.kt` or `AndroidApp.kt` in the `/androidApp` module).

### Update Your Main Composable:

```kotlin
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.together.newverse.util.ImagePicker
import com.together.newverse.util.LocalImagePicker

@Composable
fun App() {
    val context = LocalContext.current as ComponentActivity
    val imagePicker = remember { ImagePicker(context) }

    // Provide ImagePicker to the entire app
    CompositionLocalProvider(LocalImagePicker provides imagePicker) {
        // Your existing app content
        NavGraph(
            navController = navController,
            appState = appState,
            onAction = onAction
        )
    }
}
```

---

## Step 2: Add FileProvider Configuration

### A. Update AndroidManifest.xml

Add this inside the `<application>` tag in `/androidApp/src/sell/AndroidManifest.xml`:

```xml
<application>
    <!-- Existing content -->

    <!-- FileProvider for camera capture -->
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriReadPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
</application>
```

### B. Create file_paths.xml

Create file: `/androidApp/src/main/res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="images" path="." />
    <files-path name="images" path="." />
</paths>
```

If the `xml` directory doesn't exist, create it: `/androidApp/src/main/res/xml/`

---

## Step 3: Ensure Camera Permission (Optional)

If using camera capture, add to AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

---

## How It Works

1. **CompositionLocal Pattern**: `LocalImagePicker` makes ImagePicker available anywhere in the Compose tree
2. **Platform-Specific**: ImagePicker is created with Android's ComponentActivity
3. **CreateProductScreen** retrieves it automatically with `LocalImagePicker.current`

---

## Example: Full MainActivity

```kotlin
package com.together.newverse.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.together.newverse.ui.navigation.NavGraph
import com.together.newverse.ui.theme.NewverseTheme
import com.together.newverse.util.ImagePicker
import com.together.newverse.util.LocalImagePicker
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KoinContext {
                NewverseTheme {
                    val navController = rememberNavController()
                    val viewModel = koinViewModel<UnifiedAppViewModel>()
                    val appState by viewModel.state.collectAsState()

                    // Create ImagePicker with this activity
                    val imagePicker = remember { ImagePicker(this) }

                    // Provide ImagePicker to entire app
                    CompositionLocalProvider(LocalImagePicker provides imagePicker) {
                        NavGraph(
                            navController = navController,
                            appState = appState,
                            onAction = viewModel::handleAction
                        )
                    }
                }
            }
        }
    }
}
```

---

## Testing

1. Build and run the sell flavor
2. Navigate to "Produkt anlegen" (Create Product)
3. Tap the image picker icons
4. Select from gallery or take photo
5. Fill in product details
6. Save product

---

## Troubleshooting

### "ImagePicker not provided" Error
- Make sure `CompositionLocalProvider(LocalImagePicker provides imagePicker)` wraps your `NavGraph`
- Verify ImagePicker is created with ComponentActivity: `ImagePicker(this)` or `ImagePicker(context as ComponentActivity)`

### Camera Not Working
- Check FileProvider configuration in AndroidManifest.xml
- Verify file_paths.xml exists and is referenced correctly
- Check camera permission is granted

### Gallery Not Working
- Should work without additional permissions on Android 10+
- For older Android, may need READ_EXTERNAL_STORAGE permission

---

## Files Modified

### Created:
- `/shared/src/commonMain/kotlin/.../util/ImagePickerProvider.kt`

### Need to Modify:
- `/androidApp/src/main/kotlin/.../MainActivity.kt` (or wherever you setup the app)
- `/androidApp/src/sell/AndroidManifest.xml`
- `/androidApp/src/main/res/xml/file_paths.xml` (create new)

---

## Next Steps

After setup:
1. Test image selection
2. Test camera capture
3. Verify image upload to Firebase Storage
4. Check product appears in Overview screen
5. Verify product data in Firebase Realtime Database
