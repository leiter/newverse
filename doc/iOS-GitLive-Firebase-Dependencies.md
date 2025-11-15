# iOS GitLive + Firebase Dependencies Explained

## The Question

**"We use GitLive, so do we still need Firebase dependencies?"**

## The Short Answer

**YES!** GitLive is a **wrapper** around the native Firebase SDKs, not a replacement.

## How It Works

```
┌─────────────────────────────────────┐
│   Your Kotlin Code                  │
│   (Cross-platform)                  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   GitLive Firebase SDK              │
│   dev.gitlive:firebase-*            │
│   (Kotlin Multiplatform wrapper)    │
└──────────────┬──────────────────────┘
               │
      ┌────────┴────────┐
      │                 │
┌─────▼──────┐   ┌─────▼──────┐
│  iOS SDK   │   │ Android SDK│
│ (CocoaPods)│   │  (Maven)   │
└────────────┘   └────────────┘
```

## What Each Layer Does

### Your Code (Shared Kotlin)
```kotlin
// This is what you write
val auth = Firebase.auth
auth.signInWithEmailAndPassword(email, password)
```

### GitLive Layer (KMP Wrapper)
- Provides **one API** that works on all platforms
- **Translates** your Kotlin calls to platform-specific calls
- Located in: `dev.gitlive:firebase-auth`, `firebase-database`, etc.

### Native Firebase SDKs (Platform-Specific)
- **Does the actual work** (networking, auth, database)
- iOS: Installed via CocoaPods
- Android: Installed via Gradle/Maven

## Dependencies Breakdown

### Shared Module (build.gradle.kts)

```kotlin
commonMain.dependencies {
    // GitLive - Your actual API
    implementation("dev.gitlive:firebase-auth:2.1.0")
    implementation("dev.gitlive:firebase-common:2.1.0")
    implementation("dev.gitlive:firebase-database:2.1.0")
    implementation("dev.gitlive:firebase-storage:2.1.0")
}
```

### iOS App (Podfile)

```ruby
# Native Firebase SDKs (required by GitLive)
pod 'FirebaseCore'      # Core - Required
pod 'FirebaseAuth'      # Auth - Required by dev.gitlive:firebase-auth
pod 'FirebaseDatabase'  # Database - Required by dev.gitlive:firebase-database
pod 'FirebaseStorage'   # Storage - Required by dev.gitlive:firebase-storage
pod 'GoogleSignIn'      # Google Auth - Required for Google sign-in
```

### Android App (build.gradle.kts)

```kotlin
dependencies {
    // Native Firebase SDKs (required by GitLive)
    implementation("com.google.firebase:firebase-auth-ktx:23.0.0")
    implementation("com.google.firebase:firebase-database-ktx:21.0.0")
    implementation("com.google.firebase:firebase-storage-ktx:21.0.0")
}
```

## What You Can Remove

### ❌ Cannot Remove

**ALL Firebase CocoaPods are required!**

- `FirebaseCore` - Required by everything
- `FirebaseAuth` - GitLive firebase-auth needs this
- `FirebaseDatabase` - GitLive firebase-database needs this
- `FirebaseStorage` - GitLive firebase-storage needs this
- `GoogleSignIn` - Your code uses GoogleAuthProvider

### ✅ Could Potentially Remove (if you stop using them)

**GoogleSignIn** - Only if you:
1. Remove GoogleAuthProvider from GitLiveAuthRepository
2. Don't use Google sign-in at all
3. Only use email/password auth

But currently you ARE using it, so **keep it**.

## Why GitLive Then?

Good question! Here's why GitLive is valuable:

### Without GitLive (Old Approach)
```kotlin
// androidMain
val auth = FirebaseAuth.getInstance()
auth.signInWithEmailAndPassword(email, password)

// iosMain
val auth = FIRAuth.auth()
auth.signInWithEmail(email, password)

// Different APIs for each platform! 😫
```

### With GitLive (Current Approach)
```kotlin
// commonMain - ONE codebase for all platforms!
val auth = Firebase.auth
auth.signInWithEmailAndPassword(email, password)

// Same API everywhere! 😊
```

## Initialization Flow

### iOS Startup Sequence

1. **SwiftUI** launches (`NewverseApp.swift`)
2. **Koin** initializes (`KoinInitializer.kt`)
3. **GitLive** detects Firebase is available
4. **Native Firebase** initializes via CocoaPods
5. **Your app** uses GitLive API
6. **GitLive** delegates to native Firebase

### Required Files

- ✅ `GoogleService-Info.plist` (Firebase config)
- ✅ CocoaPods installed (`pod install`)
- ✅ Native Firebase SDKs (via Podfile)
- ✅ GitLive SDKs (via build.gradle.kts)

## Example: What Happens When You Sign In

```kotlin
// 1. Your code (commonMain)
Firebase.auth.signInWithEmailAndPassword(email, password)
       ↓
// 2. GitLive SDK (KMP wrapper)
// Detects we're on iOS, calls iOS implementation
       ↓
// 3. Native Firebase iOS SDK (CocoaPods)
// FIRAuth.signInWithEmail() - actual network call
       ↓
// 4. Firebase servers
// Authentication happens
       ↓
// 5. Response flows back up
// Native → GitLive → Your code
```

## Comparison with Android

| Component | iOS | Android |
|-----------|-----|---------|
| GitLive SDK | ✅ Same | ✅ Same |
| Native Firebase | CocoaPods | Gradle/Maven |
| Config File | GoogleService-Info.plist | google-services.json |
| Package Manager | pod install | Gradle sync |

## Memory Footprint

**Question**: "Doesn't this add extra code?"

**Answer**: Minimal overhead

- GitLive wrapper: ~200KB
- Native Firebase: ~3-5MB (needed anyway)
- **Total extra cost**: ~200KB for cross-platform capability

**Worth it?** Absolutely! You get:
- One codebase instead of two
- Shared business logic
- Easier maintenance
- Consistent behavior

## Troubleshooting

### Error: "No Firebase App '[DEFAULT]' has been created"

**Cause**: Native Firebase not initialized

**Solution**:
1. Check `GoogleService-Info.plist` exists
2. Check it's in Xcode project
3. Make sure CocoaPods installed (`pod install`)

### Error: "Module 'FirebaseAuth' not found"

**Cause**: CocoaPods not installed

**Solution**:
```bash
cd iosApp
pod install
open iosApp.xcworkspace  # NOT .xcodeproj!
```

### Error: "Could not find dev.gitlive:firebase-auth"

**Cause**: GitLive dependency missing from build.gradle.kts

**Solution**: Already configured in your shared module ✅

## Best Practices

### ✅ Do

- Keep both GitLive and native Firebase dependencies
- Use GitLive API in shared code
- Initialize native Firebase in platform code
- Update both when upgrading

### ❌ Don't

- Remove native Firebase dependencies
- Mix GitLive and native APIs in shared code
- Forget to update `GoogleService-Info.plist`
- Skip `pod install` after changing Podfile

## Version Compatibility

Current setup:

| Package | Version | Notes |
|---------|---------|-------|
| GitLive Firebase | 2.1.0 | KMP wrapper |
| Firebase iOS SDK | Latest | Via CocoaPods |
| Firebase Android SDK | 23.x / 21.x | Via Gradle |

GitLive handles version compatibility between wrapper and native SDKs.

## Summary

**You NEED both:**

1. **GitLive** (`dev.gitlive:firebase-*`) - Kotlin API
2. **Native Firebase** (CocoaPods on iOS) - Actual implementation

GitLive doesn't replace Firebase - it makes it cross-platform!

Think of it like:
- **GitLive** = Universal remote control (works for any TV)
- **Native Firebase** = The actual TV

You need both the remote AND the TV to watch! 📺

## References

- [GitLive Firebase SDK](https://github.com/GitLiveApp/firebase-kotlin-sdk)
- [Firebase iOS Setup](https://firebase.google.com/docs/ios/setup)
- [CocoaPods Guide](https://guides.cocoapods.org/)
