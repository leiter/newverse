# iOS First Run Checklist

Quick checklist for when you get Mac access and want to run the iOS app for the first time.

## ✅ Prerequisites Check

- [ ] macOS Monterey or later
- [ ] Xcode 15.0+ installed (from Mac App Store)
- [ ] CocoaPods installed: `sudo gem install cocoapods`
- [ ] Kotlin Multiplatform project in `/path/to/newverse`

## 🚀 First Time Setup (15 minutes)

### Step 1: Build Shared Framework (5 min)

```bash
cd /path/to/newverse
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

**Expected output**: `BUILD SUCCESSFUL`
**Location**: `shared/build/bin/iosSimulatorArm64/debugFramework/shared.framework`

### Step 2: Install CocoaPods (5 min)

```bash
cd iosApp
pod install
```

**Expected output**: `Pod installation complete! There are X dependencies...`
**Creates**: `iosApp.xcworkspace` and `Pods/` directory

### Step 3: Configure Firebase (3 min)

1. Go to https://console.firebase.google.com/
2. Select your project or create new one
3. Add iOS app:
   - **For Buy variant**: Bundle ID = `com.together.buy`
   - **For Sell variant**: Bundle ID = `com.together.sell`
4. Download `GoogleService-Info.plist`
5. Replace `iosApp/iosApp/GoogleService-Info.plist`

**Note**: For production, you'll want separate plists for Buy/Sell. For testing, one is fine.

### Step 4: Open in Xcode (1 min)

```bash
cd iosApp
open iosApp.xcworkspace
```

**Important**: Always open `.xcworkspace`, NOT `.xcodeproj`!

### Step 5: Configure Code Signing (1 min)

In Xcode:
1. Select `iosApp` target (top left)
2. Go to `Signing & Capabilities` tab
3. Under `Signing`, set `Team` to your Apple Developer account
4. Xcode will auto-manage signing

## ▶️ Running the App

### Choose Your Variant

**Scheme Selector** (top center in Xcode):
- Select `iosApp-Buy` for buyer app
- Select `iosApp-Sell` for seller app

### Choose Your Target

**Device Selector** (next to scheme):
- Any iOS Simulator (iPhone 15, iPad, etc.)
- Your connected iPhone/iPad

### Run!

Press `⌘R` or click the Play button ▶️

**First launch**: May take 1-2 minutes
**Subsequent runs**: 10-30 seconds

## 🧪 Quick Tests

After the app launches, verify:

- [ ] App opens without crashes
- [ ] You see the login/register screen
- [ ] UI looks correct (Compose UI rendering)
- [ ] Can navigate between screens
- [ ] Firebase connection works (try sign up)

## ⚠️ Common First-Time Issues

### Issue: "Framework 'shared' not found"

**Solution**:
```bash
cd ..  # Go to project root
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### Issue: "module 'shared' not found"

**Solution**: Clean and rebuild
```bash
# In Xcode: Product → Clean Build Folder (Shift+⌘+K)
# Then: Product → Build (⌘B)
```

### Issue: Pod errors

**Solution**:
```bash
cd iosApp
pod deintegrate
pod install
```

### Issue: "No such module 'FirebaseAuth'"

**Solution**: Make sure you opened `.xcworkspace` not `.xcodeproj`

### Issue: Code signing error

**Solution**:
1. Xcode → Preferences → Accounts
2. Add your Apple ID
3. Select target → Signing & Capabilities → Set Team

### Issue: Simulator won't boot

**Solution**:
```bash
# Kill all simulators
killall Simulator
# Open Xcode and try again
```

## 📱 Testing Both Variants

### Build & Run Buy Variant

1. Select `iosApp-Buy` scheme
2. Select simulator
3. Press ⌘R
4. Note: App shows "Newverse Buy" as name

### Build & Run Sell Variant

1. **Stop the Buy app** (⌘.)
2. Select `iosApp-Sell` scheme
3. Select simulator
4. Press ⌘R
5. Note: App shows "Newverse Sell" as name

Both apps can be installed simultaneously since they have different bundle IDs.

## 🎯 Development Workflow

### After Kotlin Code Changes

```bash
# 1. Rebuild framework
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# 2. Clean Xcode build (Shift+⌘+K)

# 3. Build & run (⌘R)
```

### After Swift Code Changes

Just press ⌘R in Xcode (no framework rebuild needed)

## 📚 Next Steps

Once the app is running:

1. [ ] Add app icons to `Assets.xcassets/AppIcon.appiconset/`
2. [ ] Test on a real device (not just simulator)
3. [ ] Set up separate Firebase projects for Buy/Sell
4. [ ] Test image upload/download
5. [ ] Test authentication flows
6. [ ] Configure push notifications (if needed)
7. [ ] Prepare for TestFlight/App Store

## 📖 Documentation

- **Full Setup Guide**: `doc/iOS-Setup-Guide.md`
- **Quick Reference**: `doc/iOS-Quick-Start.md`
- **Implementation Details**: `doc/iOS-Implementation-Summary.md`
- **iOS App README**: `iosApp/README.md`

## 🆘 Getting Help

If stuck:
1. Check the full setup guide: `doc/iOS-Setup-Guide.md`
2. Clean everything and retry (see Troubleshooting below)
3. Check Xcode build logs for specific errors

### Nuclear Option (Clean Everything)

```bash
# Clean Gradle
./gradlew clean

# Clean CocoaPods
cd iosApp
rm -rf Pods
rm Podfile.lock
pod install

# Clean Xcode (in Xcode)
# Product → Clean Build Folder (Shift+⌘+K)

# Rebuild framework
cd ..
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# Try again
cd iosApp
open iosApp.xcworkspace
```

## ✅ Success Indicators

You know it's working when:
- ✅ Xcode builds without errors
- ✅ App launches on simulator
- ✅ You see the Compose UI rendering
- ✅ No red error messages in console
- ✅ Firebase initializes successfully
- ✅ You can navigate in the app

## 🎉 You're Done!

Once you see the app running, you're all set! From here it's normal iOS development.

**Estimated total time for first run**: 15-30 minutes

**Happy coding! 🚀**
