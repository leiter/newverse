# Google Sign-In Guide

## Overview

Google Sign-In is fully integrated using GitLive Firebase SDK (cross-platform).

## Quick Setup

### 1. Get Web Client ID from Firebase
1. [Firebase Console](https://console.firebase.google.com/) → Project Settings → General
2. Find "Web app" or add one
3. Copy the **Web Client ID** (format: `123456789012-xxx.apps.googleusercontent.com`)

### 2. Enable Google Sign-In in Firebase
1. Authentication → Sign-in method → Google → Enable
2. Select support email → Save

### 3. Add SHA-1 Fingerprint (Android)
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```
Add fingerprint: Project Settings → Your apps → Android → Add fingerprint

### 4. Update MainActivity
In `androidApp/src/main/kotlin/.../MainActivity.kt`, set your Web Client ID:
```kotlin
private val webClientId = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
```

### 5. Build and Test
```bash
./gradlew :androidApp:installBuyDebug
```

## Implementation

The app uses GitLive Firebase SDK:
- **Repository**: `GitLiveAuthRepository.kt` (commonMain)
- **Helper**: `GoogleSignInHelper.kt` (androidMain)
- **UI**: LoginScreen triggers `UnifiedUserAction.LoginWithGoogle`

**Flow:**
1. User clicks button → Triggers `LoginWithGoogle` action
2. ViewModel sets `triggerGoogleSignIn = true`
3. MainActivity launches Google Sign-In intent
4. User selects account → Returns ID token
5. `authRepository.signInWithGoogle(idToken)` authenticates with Firebase
6. User signed in

## Troubleshooting

### Error Code 10 (Developer Error)
- Web Client ID incorrect or SHA-1 not configured
- Verify Web Client ID matches Firebase Console
- Add SHA-1 fingerprint and download new `google-services.json`

### Error Code 12501 (Cancelled)
- User cancelled or Google Play Services issue
- Update Google Play Services if needed

### Monitor Logs
```bash
adb logcat | grep -E "GitLiveAuthRepository|GoogleSignIn"
```

**Expected logs:**
```
GitLiveAuthRepository.signInWithGoogle: Authenticating with Google
GitLiveAuthRepository.signInWithGoogle: Success - userId=abc123
```

## Debug Commands

```bash
# Check Play Services
adb shell pm list packages | grep google.android.gms

# Clear app data
adb shell pm clear com.together.buy

# Check SHA-1
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
```

## Security Notes

- Never commit Web Client ID to public repos
- Use different Client IDs for debug/release builds
- Enable Firebase App Check for production
