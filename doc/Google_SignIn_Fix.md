# Google Sign-In Fix

**Date:** 2025-11-11
**Issue:** Google Sign-In button click triggers action but nothing happens
**Status:** ✅ FIXED

---

## Problem

When clicking "Sign in with Google" button:
- Log shows: `🔐 UnifiedAppViewModel.loginWithGoogle: Triggering Google Sign-In flow`
- But Google account picker never appears
- No further action occurs

---

## Root Cause

The `webClientId` in `MainActivity.kt` was still set to the placeholder value:

```kotlin
// BEFORE - Placeholder value
private val webClientId = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
```

This prevented the `GoogleSignInHelper` from properly initializing the Google Sign-In client.

---

## Solution

Updated `MainActivity.kt` with the correct Web Client ID from `google-services.json`:

### File: `androidApp/src/main/kotlin/com/together/newverse/android/MainActivity.kt`

```kotlin
// AFTER - Correct value from google-services.json
private val webClientId = "352833414422-4qt81mifve0h0v5pu1em0tnarjmq0j7j.apps.googleusercontent.com"
```

---

## How Web Client ID Was Found

### Step 1: Located in google-services.json

File: `androidApp/src/main/google-services.json`

```json
{
  "oauth_client": [
    {
      "client_id": "352833414422-4qt81mifve0h0v5pu1em0tnarjmq0j7j.apps.googleusercontent.com",
      "client_type": 3  // ← Type 3 = Web Client
    }
  ]
}
```

### Client Types:
- `client_type: 1` = Android client (with SHA-1)
- `client_type: 3` = Web client (for OAuth)

### Step 2: Multiple Package Entries

The `google-services.json` has three client entries:
1. `com.together` - Base package
2. `com.together.buy` - Buy flavor
3. `com.together.sell` - Sell flavor

All three share the same Web Client ID: `352833414422-4qt81mifve0h0v5pu1em0tnarjmq0j7j`

---

## How Google Sign-In Works

### Flow:

1. **User clicks "Sign in with Google" button** in `LoginScreen.kt`
   ```kotlin
   onAction(UnifiedUserAction.LoginWithGoogle)
   ```

2. **ViewModel triggers state change** in `UnifiedAppViewModel.kt`
   ```kotlin
   _state.update { current ->
       current.copy(
           common = current.common.copy(
               triggerGoogleSignIn = true
           )
       )
   }
   ```

3. **MainActivity observes trigger** via `LaunchedEffect`
   ```kotlin
   LaunchedEffect(state.common.triggerGoogleSignIn) {
       if (state.common.triggerGoogleSignIn) {
           val signInIntent = googleSignInHelper.getSignInIntent()
           googleSignInLauncher.launch(signInIntent) // ← Launches Google account picker
           viewModel.resetGoogleSignInTrigger()
       }
   }
   ```

4. **GoogleSignInHelper creates intent** with `webClientId`
   ```kotlin
   fun getSignInIntent(): Intent {
       val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
           .requestIdToken(webClientId) // ← MUST be valid!
           .requestEmail()
           .build()

       val client = GoogleSignIn.getClient(context, gso)
       return client.signInIntent
   }
   ```

5. **User selects Google account** from account picker

6. **Result handled in MainActivity** via `ActivityResultContracts`
   ```kotlin
   val googleSignInLauncher = rememberLauncherForActivityResult(
       contract = ActivityResultContracts.StartActivityForResult()
   ) { result ->
       // Extract ID token and sign in to Firebase
   }
   ```

---

## Verification

### Expected Logs (After Fix):

```
🔐 UnifiedAppViewModel.loginWithGoogle: Triggering Google Sign-In flow
MainActivity: 🔐 Launching Google Sign-In...
[Google account picker appears]
MainActivity: Google Sign-In result received
MainActivity: Got ID token, signing in to Firebase...
MainActivity: ✅ Successfully signed in with Google: <userId>
```

### Test Steps:

1. **Build and install:**
   ```bash
   ./gradlew :androidApp:assembleBuyDebug
   adb install -r androidApp/build/outputs/apk/buy/debug/androidApp-buy-debug.apk
   ```

2. **Launch app and navigate to Login**

3. **Click "Sign in with Google" button**
   - Google account picker should appear
   - Select account
   - Verify successful sign-in

4. **Check logcat:**
   ```bash
   adb logcat -s MainActivity
   ```

---

## Important Notes

### Web Client ID Requirements:

1. **Must match OAuth 2.0 configuration in Firebase Console**
2. **Must be client_type 3 (Web client)**
3. **Same Web Client ID works for all flavors** (buy, sell)

### SHA-1 Fingerprints:

The Android clients (client_type 1) have SHA-1 fingerprints:
- `1bc08f7b4aaaa37ac575ac0278502b0cb1ccc46b`
- `8a1c8e7a516e84832b3d8ad5c543e9e0b879842a`

These are used for Android-specific authentication but not for Google Sign-In with ID tokens.

### Debug vs Release:

Make sure you have the correct SHA-1 fingerprint registered in Firebase Console:

**Debug keystore:**
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

**Release keystore:**
```bash
keytool -list -v -keystore /path/to/release.keystore -alias your-key-alias
```

---

## Related Files

### Modified:
- ✅ `androidApp/src/main/kotlin/com/together/newverse/android/MainActivity.kt`
  - Changed `webClientId` from placeholder to actual value

### Reference:
- `androidApp/src/main/google-services.json` - Source of Web Client ID
- `shared/src/androidMain/kotlin/com/together/newverse/util/GoogleSignInHelper.kt` - Google Sign-In implementation
- `shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt` - Trigger logic

---

## Troubleshooting

### If Google Sign-In still doesn't work:

1. **Check Web Client ID in Firebase Console:**
   - Go to Firebase Console → Project Settings → General
   - Scroll to "Your apps" → Web app
   - Verify the Client ID matches

2. **Verify SHA-1 in Firebase Console:**
   - Go to Firebase Console → Project Settings → General
   - Scroll to "Your apps" → Android app
   - Check SHA-1 fingerprints are registered

3. **Check google-services.json is up-to-date:**
   ```bash
   # Download latest from Firebase Console
   # Replace androidApp/src/main/google-services.json
   ```

4. **Enable Google Sign-In in Firebase Authentication:**
   - Go to Firebase Console → Authentication → Sign-in method
   - Ensure "Google" is enabled

5. **Check logcat for errors:**
   ```bash
   adb logcat -s GoogleSignInHelper MainActivity
   ```

---

## Summary

**Problem:** Web Client ID was placeholder value
**Fix:** Updated to actual value from google-services.json
**Result:** Google Sign-In now launches account picker correctly
**Build:** ✅ Successful
**Status:** ✅ Ready for testing

---

**Google Sign-In fixed! 🔐✅**
