# Social Authentication Integration (Google & Twitter)

## Overview

The app now supports Google Sign-In and Twitter authentication in addition to email/password authentication. This provides users with convenient one-tap sign-in options using their existing social media accounts.

## Features Implemented

✅ **Google Sign-In** - One-tap sign-in with Google account
✅ **Twitter Sign-In** - OAuth authentication with Twitter/X account
✅ **Firebase Authentication** - Backend integration with Firebase Auth
✅ **UI Integration** - Sign-in buttons on LoginScreen
✅ **Error Handling** - Comprehensive error messages for auth failures

## Architecture

### 1. AuthRepository Interface

**File**: `shared/src/commonMain/kotlin/com/together/newverse/domain/repository/AuthRepository.kt`

New methods added:
```kotlin
/**
 * Sign in with Google
 * @param idToken Google ID token from Google Sign-In
 * @return User ID or error
 */
suspend fun signInWithGoogle(idToken: String): Result<String>

/**
 * Sign in with Twitter
 * @param token Twitter OAuth token
 * @param secret Twitter OAuth secret
 * @return User ID or error
 */
suspend fun signInWithTwitter(token: String, secret: String): Result<String>
```

### 2. Firebase Implementation

**File**: `shared/src/androidMain/kotlin/com/together/newverse/data/repository/FirebaseAuthRepository.kt`

#### Google Sign-In Implementation

```kotlin
override suspend fun signInWithGoogle(idToken: String): Result<String> {
    return try {
        println("🔐 FirebaseAuthRepository.signInWithGoogle: Starting Google sign in...")

        // Create Google credential with the ID token
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        // Sign in with the credential
        val authResult = auth.signInWithCredential(credential).await()
        val user = authResult.user

        if (user != null) {
            println("🔐 FirebaseAuthRepository.signInWithGoogle: SUCCESS - userId=${user.uid}, email=${user.email}")
            Result.success(user.uid)
        } else {
            Result.failure(Exception("Google sign in failed: User is null"))
        }
    } catch (e: Exception) {
        // Error handling with user-friendly messages
        val errorMessage = when {
            e.message?.contains("ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL") == true ->
                "An account already exists with the same email but different sign-in credentials"
            e.message?.contains("INVALID_CREDENTIAL") == true ->
                "Invalid Google credentials"
            // ... more error cases
            else -> e.message ?: "Google sign in failed"
        }
        Result.failure(Exception(errorMessage))
    }
}
```

#### Twitter Sign-In Implementation

```kotlin
override suspend fun signInWithTwitter(token: String, secret: String): Result<String> {
    return try {
        println("🔐 FirebaseAuthRepository.signInWithTwitter: Starting Twitter sign in...")

        // Create Twitter credential with the OAuth token and secret
        val credential = TwitterAuthProvider.getCredential(token, secret)

        // Sign in with the credential
        val authResult = auth.signInWithCredential(credential).await()
        val user = authResult.user

        if (user != null) {
            println("🔐 FirebaseAuthRepository.signInWithTwitter: SUCCESS - userId=${user.uid}, displayName=${user.displayName}")
            Result.success(user.uid)
        } else {
            Result.failure(Exception("Twitter sign in failed: User is null"))
        }
    } catch (e: Exception) {
        // Error handling
        val errorMessage = // ... similar to Google
        Result.failure(Exception(errorMessage))
    }
}
```

### 3. Google Sign-In Helper

**File**: `shared/src/androidMain/kotlin/com/together/newverse/util/GoogleSignInHelper.kt`

Utility class to simplify Google Sign-In integration:

```kotlin
class GoogleSignInHelper(
    private val context: Context,
    private val webClientId: String
) {
    /**
     * Get the sign-in intent to launch with ActivityResultLauncher
     */
    fun getSignInIntent(): Intent

    /**
     * Handle the sign-in result from the activity result
     * @param data Intent data from the activity result
     * @return Google ID token on success, null on failure
     */
    fun handleSignInResult(data: Intent?): Result<String>

    /**
     * Sign out from Google
     */
    fun signOut()

    /**
     * Revoke access (disconnect from Google)
     */
    fun revokeAccess()

    /**
     * Check if user is already signed in with Google
     */
    fun isSignedIn(): Boolean
}
```

### 4. UI Integration

**File**: `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/common/LoginScreen.kt`

Added buttons after the email/password section:

```kotlin
// Google Sign-In Button
OutlinedButton(
    onClick = {
        onAction(UnifiedUserAction.LoginWithGoogle)
    },
    modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
    enabled = !authState.isLoading,
    colors = ButtonDefaults.outlinedButtonColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
) {
    Text(
        text = "G",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(end = 12.dp)
    )
    Text("Sign in with Google", style = MaterialTheme.typography.labelLarge)
}

// Twitter Sign-In Button
OutlinedButton(
    onClick = {
        onAction(UnifiedUserAction.LoginWithTwitter)
    },
    // ... similar structure
) {
    Text(
        text = "𝕏",  // Twitter/X logo
        // ...
    )
    Text("Sign in with Twitter", style = MaterialTheme.typography.labelLarge)
}
```

### 5. Action Handling

**File**: `shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppActions.kt`

Added new actions:
```kotlin
sealed interface UnifiedUserAction : UnifiedAppAction {
    data class Login(val email: String, val password: String) : UnifiedUserAction
    data object LoginWithGoogle : UnifiedUserAction  // NEW
    data object LoginWithTwitter : UnifiedUserAction  // NEW
    data object Logout : UnifiedUserAction
    data class Register(val email: String, val password: String, val name: String) : UnifiedUserAction
    data class UpdateProfile(val profile: UserProfile) : UnifiedUserAction
}
```

**File**: `shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppViewModel.kt`

Handler implementation:
```kotlin
private fun handleUserAction(action: UnifiedUserAction) {
    when (action) {
        is UnifiedUserAction.Login -> login(action.email, action.password)
        is UnifiedUserAction.LoginWithGoogle -> loginWithGoogle()
        is UnifiedUserAction.LoginWithTwitter -> loginWithTwitter()
        is UnifiedUserAction.Logout -> logout()
        is UnifiedUserAction.Register -> register(action.email, action.password, action.name)
        is UnifiedUserAction.UpdateProfile -> updateProfile(action.profile)
    }
}
```

## Dependencies

### Gradle Dependencies

**File**: `shared/build.gradle.kts`

```kotlin
androidMain.dependencies {
    // Firebase
    implementation("com.google.firebase:firebase-auth-ktx:23.0.0")
    implementation("com.google.firebase:firebase-database-ktx:21.0.0")
    implementation("com.google.firebase:firebase-storage-ktx:21.0.0")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Coroutines Play Services
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
```

## Firebase Configuration

### Enable Authentication Providers

1. **Google Sign-In**:
   - Go to Firebase Console → Authentication → Sign-in method
   - Enable "Google" provider
   - Configure OAuth consent screen
   - Add your app's SHA-1 fingerprint
   - Download updated `google-services.json`

2. **Twitter Sign-In**:
   - Go to Firebase Console → Authentication → Sign-in method
   - Enable "Twitter" provider
   - Create Twitter Developer account
   - Create Twitter App and get API Key and API Secret
   - Add callback URL: `https://[PROJECT-ID].firebaseapp.com/__/auth/handler`
   - Enter API Key and Secret in Firebase Console

### Get Web Client ID for Google Sign-In

The Web Client ID is needed for `GoogleSignInHelper`:

1. Open Firebase Console → Project Settings
2. Go to "General" tab
3. Scroll to "Your apps" section
4. Find "Web client" under OAuth 2.0 Client IDs
5. Copy the Client ID

**Usage in code**:
```kotlin
val googleSignInHelper = GoogleSignInHelper(
    context = applicationContext,
    webClientId = "YOUR_WEB_CLIENT_ID_HERE.apps.googleusercontent.com"
)
```

## Implementation Flow

### Google Sign-In Flow

```
┌─────────────────────────────────────────────────┐
│ 1. User clicks "Sign in with Google" button    │
│    LoginScreen → UnifiedUserAction.LoginWithGoogle │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 2. Activity launches Google Sign-In Intent      │
│    Uses GoogleSignInHelper.getSignInIntent()    │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 3. User selects Google account                  │
│    Google Sign-In UI handles account selection  │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 4. Get ID Token from result                     │
│    GoogleSignInHelper.handleSignInResult(data)  │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 5. Sign in to Firebase with ID Token           │
│    authRepository.signInWithGoogle(idToken)     │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 6. Firebase authenticates and returns user     │
│    authResult.user.uid → Success!              │
└─────────────────────────────────────────────────┘
```

### Twitter Sign-In Flow

```
┌─────────────────────────────────────────────────┐
│ 1. User clicks "Sign in with Twitter" button   │
│    LoginScreen → UnifiedUserAction.LoginWithTwitter│
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 2. Activity launches Twitter OAuth flow         │
│    Uses Firebase TwitterAuthProvider            │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 3. User authorizes app on Twitter              │
│    Twitter OAuth UI handles authorization       │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 4. Get OAuth token and secret                  │
│    Twitter callback provides credentials        │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 5. Sign in to Firebase with credentials        │
│    authRepository.signInWithTwitter(token, secret)│
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 6. Firebase authenticates and returns user     │
│    authResult.user.uid → Success!              │
└─────────────────────────────────────────────────┘
```

## Error Handling

### Common Errors

1. **Account exists with different credential**
   - User already has an account with the same email but different provider
   - Solution: Link accounts or ask user to sign in with original provider

2. **Invalid credentials**
   - ID token or OAuth credentials are invalid
   - Solution: Retry sign-in process

3. **User disabled**
   - Account has been disabled in Firebase Console
   - Solution: Contact support or re-enable account

4. **Network error**
   - No internet connection
   - Solution: Check connectivity and retry

### Error Messages

All errors are logged with emoji prefixes for easy filtering:

```bash
# Successful sign-in
🔐 FirebaseAuthRepository.signInWithGoogle: SUCCESS - userId=abc123, email=user@example.com

# Failed sign-in
❌ FirebaseAuthRepository.signInWithGoogle: EXCEPTION - Invalid credentials
```

## Testing

### Test Google Sign-In

1. Build and install app
2. Navigate to Login screen
3. Click "Sign in with Google" button
4. Select Google account
5. Verify successful sign-in and navigation to home

### Test Twitter Sign-In

1. Build and install app
2. Navigate to Login screen
3. Click "Sign in with Twitter" button
4. Authorize app on Twitter
5. Verify successful sign-in and navigation to home

### Check Logs

```bash
# Filter authentication logs
adb logcat | grep "🔐"

# Check for errors
adb logcat | grep "❌.*Firebase"

# Complete auth flow
adb logcat | grep -E "🔐|FirebaseAuth|GoogleSignIn|Twitter"
```

## Security Considerations

1. **Never commit credentials**: Don't include Web Client ID or API keys in version control
2. **Use environment variables**: Store sensitive data in local.properties or environment variables
3. **Verify SHA-1**: Ensure your app's SHA-1 is registered in Firebase Console
4. **Enable App Check**: Add Firebase App Check for additional security
5. **Monitor authentication**: Check Firebase Console for unusual sign-in activity

## Future Enhancements

- [ ] Add Apple Sign-In for iOS users
- [ ] Add Facebook authentication
- [ ] Implement account linking (merge multiple sign-in methods)
- [ ] Add biometric authentication
- [ ] Support sign-in with phone number
- [ ] Add multi-factor authentication (MFA)

## Summary

✅ **Implemented**: Google and Twitter sign-in fully integrated
✅ **UI Updated**: Sign-in buttons added to LoginScreen
✅ **Backend**: Firebase Authentication configured
✅ **Error Handling**: Comprehensive error messages
✅ **Build Status**: Successfully compiled and deployed

Users can now sign in using:
- Email/Password
- Google Account (one-tap)
- Twitter/X Account (OAuth)
- Guest/Anonymous mode
