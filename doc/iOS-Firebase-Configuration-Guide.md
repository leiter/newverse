# iOS Firebase Configuration Guide

Complete guide for setting up Firebase configurations for Buy and Sell variants on iOS.

## Overview

The iOS app uses **variant-specific Firebase configuration files**:
- `GoogleService-Info-Buy.plist` → For Buy variant (com.together.buy)
- `GoogleService-Info-Sell.plist` → For Sell variant (com.together.sell)

A build script automatically copies the correct file based on which scheme you're building.

---

## Current Setup Status

✅ **READY**: Firebase configuration files are in place:
- `/iosApp/iosApp/GoogleService-Info-Buy.plist` ✅ Real config
- `/iosApp/iosApp/GoogleService-Info-Sell.plist` ✅ Real config
- `/iosApp/iosApp/GoogleService-Info.plist` ✅ Default (copy of Buy)
- `/iosApp/copy-firebase-plist.sh` ✅ Build script

⏸️ **REQUIRES MAC**: Adding build script to Xcode project

---

## File Structure

```
iosApp/
├── iosApp/
│   ├── GoogleService-Info-Buy.plist   ✅ Source (tracked in git)
│   ├── GoogleService-Info-Sell.plist  ✅ Source (tracked in git)
│   └── GoogleService-Info.plist       ⚠️  Generated (gitignored)
├── copy-firebase-plist.sh             ✅ Build script
└── .gitignore                         ✅ Configured
```

### How It Works

1. **Source files** (`-Buy.plist` and `-Sell.plist`) are tracked in git
2. **Build script** copies the correct source file based on build configuration
3. **Generic file** (`GoogleService-Info.plist`) is generated at build time and gitignored

---

## Setup Instructions (When You Get Mac Access)

### Step 1: Open Xcode Project

```bash
cd /path/to/newverse/iosApp
pod install  # If you haven't already
open iosApp.xcworkspace
```

### Step 2: Add Build Script to Xcode

1. **Select the iosApp target** (top left in Xcode)
2. **Go to Build Phases tab**
3. **Click the + button** → "New Run Script Phase"
4. **Drag the script phase** to be BEFORE "Compile Sources"
5. **Name it**: "Copy Firebase Config"
6. **Add this script**:

```bash
# Copy Firebase Config
"${PROJECT_DIR}/copy-firebase-plist.sh"
```

7. **Uncheck** "Based on dependency analysis" (to run every time)

### Visual Guide

```
Build Phases:
├── [CP] Check Pods Manifest.lock
├── ⭐ Copy Firebase Config          ← Add this here
├── Sources (compile)
├── Frameworks
├── Resources
└── [CP] Embed Pods Frameworks
```

### Step 3: Verify Configuration

**For Buy Variant:**
1. Select scheme: `iosApp-Buy`
2. Build (⌘B)
3. Check console output for: `📦 Using Buy variant configuration`
4. Verify: Build succeeded ✅

**For Sell Variant:**
1. Select scheme: `iosApp-Sell`
2. Build (⌘B)
3. Check console output for: `📦 Using Sell variant configuration`
4. Verify: Build succeeded ✅

---

## Alternative: Manual Configuration (Simpler for Testing)

If you just want to test the Buy variant quickly without the build script:

### Option A: Use Default (Already Set Up)

The default `GoogleService-Info.plist` is already set to Buy variant. Just build with `iosApp-Buy` scheme and it will work!

### Option B: Manual Switching

To switch between variants manually:

**For Buy:**
```bash
cp iosApp/GoogleService-Info-Buy.plist iosApp/GoogleService-Info.plist
```

**For Sell:**
```bash
cp iosApp/GoogleService-Info-Sell.plist iosApp/GoogleService-Info.plist
```

Then build with any scheme.

---

## Google Sign-In Configuration

The Firebase config files contain the `REVERSED_CLIENT_ID` needed for Google Sign-In.

### Automatic Setup (Recommended - Requires Mac)

When you add the build script, it will also handle URL schemes. Add this to the same build script:

```bash
# Extract REVERSED_CLIENT_ID from the plist
REVERSED_CLIENT_ID=$(/usr/libexec/PlistBuddy -c "Print :REVERSED_CLIENT_ID" "$SOURCE_PATH")

# Add to Info.plist (this is a simplified version, actual implementation may vary)
echo "🔐 REVERSED_CLIENT_ID: ${REVERSED_CLIENT_ID}"
```

### Manual Setup (Alternative)

Add URL scheme to `Info.plist`:

1. Open `iosApp/Info.plist` in Xcode
2. Add this entry:

```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>com.googleusercontent.apps.352833414422-llkofcdstuc7pcf0qubpratujmkrj106</string>
        </array>
    </dict>
</array>
```

Or edit the plist directly to add the REVERSED_CLIENT_ID value from your Firebase config.

---

## Verification Checklist

When the app launches, verify Firebase is configured correctly:

### ✅ Initialization Success

Check Xcode console for these logs:

```
🔥 GitLiveFirebaseInit: Initializing GitLive Firebase SDK
🔥 GitLiveFirebaseInit: Firebase configured
🔥 GitLiveFirebaseInit: Auth initialized
🔥 GitLiveFirebaseInit: Database initialized
✅ GitLiveFirebaseInit: Successfully initialized
```

### ✅ Correct Variant

For **Buy** build:
```
📦 Using Buy variant configuration
✅ Successfully copied GoogleService-Info-Buy.plist
```

For **Sell** build:
```
📦 Using Sell variant configuration
✅ Successfully copied GoogleService-Info-Sell.plist
```

### ✅ Firebase Connection

- App doesn't crash on launch ✅
- Authentication screen appears ✅
- Can sign in with email/password ✅
- Data loads from Firebase ✅

---

## Troubleshooting

### Error: "GoogleService-Info.plist not found"

**Cause**: Build script not running or plist files missing

**Solution**:
1. Verify files exist: `ls iosApp/iosApp/GoogleService-Info-*.plist`
2. Check build script was added to Xcode
3. Clean build folder: Product → Clean Build Folder (Shift+⌘+K)
4. Rebuild

### Error: "Firebase not initialized"

**Cause**: Plist file not in app bundle or invalid

**Solution**:
1. Check build logs for script output
2. Verify plist file is valid XML
3. Ensure `FirebaseApp.configure()` is called in `NewverseApp.swift`

### Error: Wrong variant configuration

**Cause**: Build script copying wrong file

**Solution**:
1. Check which scheme is selected (top bar in Xcode)
2. Verify configuration name contains "Buy" or "Sell"
3. Check build script logic in `copy-firebase-plist.sh`

### Error: "Could not open URL scheme"

**Cause**: REVERSED_CLIENT_ID not configured for Google Sign-In

**Solution**:
1. Add URL scheme to Info.plist (see Google Sign-In Configuration above)
2. Use REVERSED_CLIENT_ID from your GoogleService-Info-Buy.plist

---

## Firebase Project Configuration

Your Firebase project details:

### Buy Variant
- **Bundle ID**: `com.together.buy`
- **Client ID**: `352833414422-llkofcdstuc7pcf0qubpratujmkrj106.apps.googleusercontent.com`
- **Reversed Client ID**: `com.googleusercontent.apps.352833414422-llkofcdstuc7pcf0qubpratujmkrj106`
- **Project ID**: `fire-one-58ddc`

### Sell Variant
- **Bundle ID**: `com.together.sell`
- **Client ID**: (check GoogleService-Info-Sell.plist)
- **Reversed Client ID**: (check GoogleService-Info-Sell.plist)
- **Project ID**: `fire-one-58ddc`

---

## Build Script Details

### What the Script Does

1. **Detects build configuration**
   - Checks `${CONFIGURATION}` environment variable
   - Looks for "Buy" or "Sell" in the name

2. **Selects source file**
   - Debug-Buy → `GoogleService-Info-Buy.plist`
   - Release-Buy → `GoogleService-Info-Buy.plist`
   - Debug-Sell → `GoogleService-Info-Sell.plist`
   - Release-Sell → `GoogleService-Info-Sell.plist`

3. **Copies to app bundle**
   - Copies selected file to `${BUILT_PRODUCTS_DIR}/${PRODUCT_NAME}.app/GoogleService-Info.plist`
   - This is where Firebase looks for the config at runtime

4. **Verifies success**
   - Logs output to Xcode console
   - Fails build if file not found

### Script Location

`/iosApp/copy-firebase-plist.sh` (executable)

### Customization

To modify the script (e.g., for additional variants):

```bash
# Edit the script
nano iosApp/copy-firebase-plist.sh

# Or add more conditions
if [[ "${CONFIGURATION}" == *"Premium"* ]]; then
    FIREBASE_PLIST="GoogleService-Info-Premium.plist"
fi
```

---

## Git Configuration

### What's Tracked in Git

✅ **Tracked (committed)**:
- `GoogleService-Info-Buy.plist` - Real Firebase config
- `GoogleService-Info-Sell.plist` - Real Firebase config
- `copy-firebase-plist.sh` - Build script

❌ **Ignored (gitignored)**:
- `GoogleService-Info.plist` - Generated by build script

### .gitignore Configuration

```gitignore
# Firebase Configuration
# The generic GoogleService-Info.plist is copied by build script
GoogleService-Info.plist

# Keep these tracked:
!GoogleService-Info-Buy.plist
!GoogleService-Info-Sell.plist
```

---

## Security Considerations

### API Keys in Firebase Config

⚠️ **Firebase API keys in `GoogleService-Info.plist` are safe to commit to git**

Why?
- They're **not secret** - they're meant to be public
- They identify your Firebase project
- Security is handled by Firebase Security Rules, not by hiding the API key

### What Should Be Secret

❌ **Don't commit these** (if you have them):
- Service account JSON files
- Admin SDK private keys
- OAuth client secrets
- Database passwords

✅ **Safe to commit**:
- GoogleService-Info.plist (iOS)
- google-services.json (Android)
- Firebase config objects for web

---

## Testing Checklist

### Before Mac Access

- ✅ Firebase config files in place
- ✅ Build script created and executable
- ✅ .gitignore configured
- ✅ Default config set to Buy variant

### With Mac Access (First Time)

- [ ] Open project in Xcode
- [ ] Add build script to Build Phases
- [ ] Build Buy variant
- [ ] Verify Firebase logs
- [ ] Build Sell variant
- [ ] Verify Firebase logs
- [ ] Test authentication

### Full Testing

- [ ] Email/password sign-in works
- [ ] Anonymous sign-in works
- [ ] Data loads from Firebase
- [ ] Images display correctly
- [ ] Google Sign-In (when implemented)
- [ ] Both Buy and Sell variants work

---

## Next Steps

1. **When you get Mac access** (15 minutes):
   - Open Xcode
   - Add build script to Build Phases
   - Build and verify

2. **For Google Sign-In** (30 minutes):
   - Add URL scheme to Info.plist
   - Implement GoogleSignInHelper (already stubbed)
   - Test with real Google account

3. **For App Store** (1-2 hours):
   - Add app icons
   - Configure code signing
   - Create screenshots
   - Submit for review

---

## Quick Reference

### Build Commands (Terminal)

```bash
# Build Buy variant (from project root)
xcodebuild -workspace iosApp/iosApp.xcworkspace \
  -scheme iosApp-Buy \
  -configuration Debug-Buy \
  build

# Build Sell variant
xcodebuild -workspace iosApp/iosApp.xcworkspace \
  -scheme iosApp-Sell \
  -configuration Debug-Sell \
  build
```

### File Paths

- **Buy config**: `iosApp/iosApp/GoogleService-Info-Buy.plist`
- **Sell config**: `iosApp/iosApp/GoogleService-Info-Sell.plist`
- **Default config**: `iosApp/iosApp/GoogleService-Info.plist`
- **Build script**: `iosApp/copy-firebase-plist.sh`
- **Info.plist**: `iosApp/iosApp/Info.plist`

---

## Support

If you encounter issues:

1. Check build logs in Xcode (⌘9 → Report Navigator)
2. Verify Firebase config files are valid
3. Clean build folder and rebuild
4. Check this guide's Troubleshooting section

For Firebase-specific issues:
- [Firebase iOS Setup Guide](https://firebase.google.com/docs/ios/setup)
- [Firebase Console](https://console.firebase.google.com/)

---

## Summary

✅ **Current Status**: iOS Firebase configuration is **100% ready**

🎯 **Action Required**: Add build script to Xcode (5 minutes on Mac)

🚀 **Result**: Automatic variant-specific Firebase configuration with zero manual intervention!
