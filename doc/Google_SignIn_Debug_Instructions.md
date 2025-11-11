# Google Sign-In Debug Instructions

**Issue:** Account picker not appearing when clicking "Sign in with Google"
**Date:** 2025-11-11

---

## Quick Test

Run this command to install the app and monitor logs:

```bash
./test-google-signin.sh
```

Then:
1. Open the app on your device
2. Navigate to Login screen
3. Click "Sign in with Google" button
4. Watch the terminal for log output

---

## Manual Testing Steps

### Step 1: Install APK
```bash
./gradlew :androidApp:assembleBuyDebug
adb install -r androidApp/build/outputs/apk/buy/debug/androidApp-buy-debug.apk
```

### Step 2: Clear Logcat
```bash
adb logcat -c
```

### Step 3: Monitor Logs
In a terminal window, run:
```bash
adb logcat | grep -E "MainActivity|GoogleSignInHelper|triggerGoogleSignIn"
```

### Step 4: Test the Flow
1. Open the app
2. Navigate to Login screen (open drawer → click Login)
3. Click "Sign in with Google" button
4. Watch the terminal logs

---

## Expected Log Sequence

If everything is working, you should see:

```
🔐 UnifiedAppViewModel.loginWithGoogle: Triggering Google Sign-In flow
🔍 LaunchedEffect triggered, triggerGoogleSignIn=true
🔐 GoogleSignInHelper: Initializing with webClientId: 352833414422-4qt81mifve0h0v5pu1em0tnarjmq0j7j.apps.googleusercontent.com
🔐 GoogleSignInHelper: Creating GoogleSignInClient...
🔐 GoogleSignInHelper: Initialization complete
🔐 Launching Google Sign-In...
🔐 Web Client ID: 352833414422-4qt81mifve0h0v5pu1em0tnarjmq0j7j.apps.googleusercontent.com
🔐 GoogleSignInHelper.getSignInIntent(): Getting sign-in intent...
🔐 GoogleSignInHelper.getSignInIntent(): Intent created: <intent details>
🔐 Launcher.launch() called
🔐 Trigger reset
```

Then the Google account picker should appear on screen.

---

## Debugging Checklist

### If LaunchedEffect never triggers:

**Problem:** State change not propagating

**Check:**
1. Is the button actually calling `onAction(UnifiedUserAction.LoginWithGoogle)`?
2. Is the ViewModel receiving the action?
3. Is the state being updated?

**Add this to LoginScreen temporarily:**
```kotlin
// In LoginScreen.kt, add before the button
LaunchedEffect(Unit) {
    println("🔍 LoginScreen composing")
}

// In the Google button onClick
onClick = {
    println("🔍 Google button clicked!")
    onAction(UnifiedUserAction.LoginWithGoogle)
}
```

### If LaunchedEffect triggers but `triggerGoogleSignIn` is false:

**Problem:** ViewModel not updating state correctly

**Check:**
```kotlin
// In UnifiedAppViewModel.loginWithGoogle()
println("🔐 Before state update: ${_state.value.common.triggerGoogleSignIn}")
_state.update { current ->
    current.copy(
        common = current.common.copy(
            triggerGoogleSignIn = true
        )
    )
}
println("🔐 After state update: ${_state.value.common.triggerGoogleSignIn}")
```

### If intent is created but launcher doesn't launch:

**Problem:** ActivityResultLauncher issue

**Check:**
1. Is the launcher registered before the first composition?
2. Is there an exception being thrown?
3. Check full logcat for errors:
```bash
adb logcat
```

### If you see error code 10:

**Error:** Developer configuration error

**Solutions:**
1. **Check SHA-1 fingerprint is registered in Firebase:**
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
       -storepass android -keypass android | grep SHA1
   ```

2. **Add SHA-1 to Firebase Console:**
   - Go to Firebase Console
   - Project Settings → General
   - Your apps → Android app
   - Add fingerprint

3. **Download updated google-services.json**

4. **Rebuild:**
   ```bash
   ./gradlew clean
   ./gradlew :androidApp:assembleBuyDebug
   ```

### If you see error code 12501:

**Error:** Sign-in cancelled

**This means:**
- The picker appeared but was cancelled by user
- OR there's a configuration mismatch

---

## Common Issues

### Issue 1: Google Play Services Not Available

**Symptoms:**
- Error in logs about Play Services
- Account picker doesn't appear

**Solution:**
```bash
# Check if Play Services is installed
adb shell pm list packages | grep google.android.gms

# Update Play Services on emulator or device
```

### Issue 2: Web Client ID Mismatch

**Symptoms:**
- Error code 10
- "Developer error" message

**Solution:**
1. Verify Web Client ID in `MainActivity.kt` matches `google-services.json`
2. Check Firebase Console → Project Settings → Web Client ID

**Current Web Client ID:**
```
352833414422-4qt81mifve0h0v5pu1em0tnarjmq0j7j.apps.googleusercontent.com
```

### Issue 3: Package Name Mismatch

**Symptoms:**
- Picker appears but sign-in fails
- "Package not registered" error

**Solution:**
1. Check `applicationId` in `build.gradle.kts`:
   ```kotlin
   defaultConfig {
       applicationId = "com.together"  // Or com.together.buy
   }
   ```

2. Verify in Firebase Console → Project Settings → Package name matches

### Issue 4: No Accounts on Device

**Symptoms:**
- Picker appears but shows "No accounts"

**Solution:**
```bash
# Check accounts on device
adb shell dumpsys account | grep "Account {"

# Add Google account to device:
# Settings → Accounts → Add Account → Google
```

---

## Advanced Debugging

### Enable Verbose Logging

Add to `MainActivity.kt`:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Enable debug logging
    if (BuildConfig.DEBUG) {
        Log.d("MainActivity", "onCreate called")
        Log.d("MainActivity", "Package: ${packageName}")
        Log.d("MainActivity", "Web Client ID: $webClientId")
    }
}
```

### Check Google Sign-In Status

Add to `MainActivity.kt`:
```kotlin
val account = GoogleSignIn.getLastSignedInAccount(context)
Log.d("MainActivity", "Last signed in account: $account")
```

### Inspect Intent Details

Add to `GoogleSignInHelper.kt`:
```kotlin
fun getSignInIntent(): Intent {
    val intent = googleSignInClient.signInIntent
    Log.d("GoogleSignInHelper", "Intent action: ${intent.action}")
    Log.d("GoogleSignInHelper", "Intent component: ${intent.component}")
    Log.d("GoogleSignInHelper", "Intent extras: ${intent.extras}")
    return intent
}
```

---

## Testing on Different Devices

### Emulator:
- Make sure Play Services is installed
- Add a Google account
- May need to update Play Services

### Physical Device:
- Should work out of the box
- Make sure SHA-1 fingerprint is registered
- Check device has Google account added

---

## Verification Commands

```bash
# Check if app is installed
adb shell pm list packages | grep com.together

# Check app info
adb shell dumpsys package com.together.buy

# Check Google Play Services version
adb shell dumpsys package com.google.android.gms | grep versionName

# Clear app data (reset state)
adb shell pm clear com.together.buy

# Force stop app
adb shell am force-stop com.together.buy

# Launch app
adb shell am start -n com.together.buy/.MainActivity
```

---

## Quick Fixes

### Reset Everything:
```bash
# Clean build
./gradlew clean

# Uninstall app
adb uninstall com.together.buy

# Rebuild
./gradlew :androidApp:assembleBuyDebug

# Reinstall
adb install -r androidApp/build/outputs/apk/buy/debug/androidApp-buy-debug.apk

# Clear logcat and test
adb logcat -c
adb logcat | grep -E "MainActivity|GoogleSignIn"
```

### Update SHA-1:
```bash
# Get SHA-1
keytool -list -v -keystore ~/.android/debug.keystore \
    -alias androiddebugkey -storepass android -keypass android

# Copy SHA-1 fingerprint
# Add to Firebase Console
# Download new google-services.json
# Rebuild app
```

---

## Success Indicators

You'll know it's working when:

1. ✅ Logs show all the 🔐 emoji messages
2. ✅ Google account picker appears
3. ✅ You can select an account
4. ✅ Sign-in completes without errors
5. ✅ User is logged in (check user state)

---

## Report Findings

After testing, please report:

1. **What logs did you see?** (copy/paste the output)
2. **Did the account picker appear?**
3. **Any error messages?**
4. **Which device/emulator?**
5. **Is Google Play Services installed?**

This will help identify the exact issue!
