# Google Sign-In Setup Guide

## Current Status

✅ **Code Implemented**: Google Sign-In flow is fully integrated
❌ **Configuration Needed**: Web Client ID must be configured

## Quick Setup

### 1. Get Your Web Client ID from Firebase

1. Open [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Go to **Project Settings** (gear icon) → **General** tab
4. Scroll down to **"Your apps"** section
5. Find **"Web app"** or click "Add app" → "Web" if you don't have one
6. Copy the **Web Client ID**

It will look like:
```
123456789012-abcdefghijklmnopqrstuvwxyz123456.apps.googleusercontent.com
```

### 2. Enable Google Sign-In in Firebase

1. In Firebase Console, go to **Authentication** → **Sign-in method**
2. Click on **Google** provider
3. Click **Enable**
4. Select a support email
5. Click **Save**

### 3. Add SHA-1 Fingerprint (for Android)

Get your debug SHA-1:
```bash
cd /home/mandroid/Videos/newverse/androidApp
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Look for the line starting with `SHA1:` and copy the fingerprint.

Then in Firebase Console:
1. Go to **Project Settings** → **General**
2. Scroll to **"Your apps"** → Android app
3. Click **"Add fingerprint"**
4. Paste your SHA-1 fingerprint
5. Click **Save**

### 4. Update MainActivity with Your Web Client ID

**File**: `androidApp/src/main/kotlin/com/together/newverse/android/MainActivity.kt`

**Line 32** - Replace:
```kotlin
private val webClientId = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
```

With your actual Web Client ID:
```kotlin
private val webClientId = "123456789012-abcdefghijklmnopqrstuvwxyz123456.apps.googleusercontent.com"
```

### 5. Rebuild and Test

```bash
./gradlew :androidApp:installBuyDebug
```

Then:
1. Open the app
2. Navigate to Login screen
3. Click **"Sign in with Google"** button
4. Select your Google account
5. Verify successful sign-in

## Testing

### Check Logs

Clear and monitor logs:
```bash
adb logcat -c
adb logcat | grep -E "MainActivity|🔐|GoogleSignIn"
```

### Expected Log Output

**When button is clicked**:
```
🔐 UnifiedAppViewModel.loginWithGoogle: Triggering Google Sign-In flow
MainActivity: 🔐 Launching Google Sign-In...
```

**After selecting account**:
```
MainActivity: Google Sign-In result received
MainActivity: Got ID token, signing in to Firebase...
🔐 FirebaseAuthRepository.signInWithGoogle: Starting Google sign in...
🔐 FirebaseAuthRepository.signInWithGoogle: SUCCESS - userId=abc123, email=user@gmail.com
MainActivity: ✅ Successfully signed in with Google: abc123
```

### Common Issues

#### Issue 1: "Developer error" (Error code 10)
**Cause**: Web Client ID is incorrect or SHA-1 not configured
**Solution**:
- Verify Web Client ID is correct
- Add SHA-1 fingerprint to Firebase Console
- Rebuild app

#### Issue 2: "Sign-in cancelled" (Error code 12501)
**Cause**: User cancelled or Google Play Services issue
**Solution**: Try again or update Google Play Services

#### Issue 3: No logs appearing
**Cause**: Web Client ID not set
**Solution**: Update MainActivity.kt with your actual Web Client ID

## Current Configuration

**File**: `androidApp/src/main/kotlin/com/together/newverse/android/MainActivity.kt`

The Google Sign-In flow:
1. User clicks button → Triggers `UnifiedUserAction.LoginWithGoogle`
2. ViewModel sets `triggerGoogleSignIn = true`
3. MainActivity observes trigger via `LaunchedEffect`
4. Launches Google Sign-In intent
5. Receives ID token from Google
6. Calls `authRepository.signInWithGoogle(idToken)`
7. Firebase authenticates and returns user ID
8. User is signed in ✅

## Next Steps

Once Google Sign-In is working:
1. ✅ Test with multiple Google accounts
2. ✅ Test sign-out and sign-in again
3. ✅ Test error handling (cancel sign-in)
4. 🔲 Configure Twitter authentication (similar process)
5. 🔲 Add account linking feature
6. 🔲 Enable production SHA-1 for release builds

## Security Notes

⚠️ **Never commit your Web Client ID to public repositories**
⚠️ **Use different Client IDs for debug and release builds**
⚠️ **Enable App Check for production apps**
⚠️ **Monitor Firebase Authentication logs for suspicious activity**

## Summary

To enable Google Sign-In:
1. Get Web Client ID from Firebase Console
2. Enable Google provider in Firebase Authentication
3. Add SHA-1 fingerprint
4. Update `MainActivity.kt` line 32 with your Web Client ID
5. Rebuild and test

That's it! Your users can now sign in with Google.
