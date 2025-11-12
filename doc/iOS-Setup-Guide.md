# iOS App Setup Guide

This guide explains how to set up, build, and run the Newverse iOS app.

## Overview

The iOS app is built using:
- **SwiftUI** for the native iOS wrapper
- **Kotlin Multiplatform** shared module for business logic and UI
- **Compose Multiplatform** for cross-platform UI
- **Firebase** for authentication, database, and storage
- **CocoaPods** for dependency management

## Project Structure

```
iosApp/
├── iosApp/
│   ├── NewverseApp.swift          # App entry point
│   ├── ContentView.swift          # Main SwiftUI view
│   ├── Info.plist                 # App configuration
│   ├── GoogleService-Info.plist   # Firebase configuration (REPLACE WITH REAL FILE)
│   └── Assets.xcassets/           # App icons and assets
├── iosApp.xcodeproj/              # Xcode project
│   └── xcshareddata/
│       └── xcschemes/
│           ├── iosApp-Buy.xcscheme    # Buy variant scheme
│           └── iosApp-Sell.xcscheme   # Sell variant scheme
├── Podfile                        # CocoaPods dependencies
└── .gitignore
```

## Prerequisites

### Required Software
1. **macOS** (Monterey or later recommended)
2. **Xcode** 15.0 or later
   - Download from the Mac App Store
3. **CocoaPods** 1.11 or later
   ```bash
   sudo gem install cocoapods
   ```
4. **Kotlin Multiplatform Mobile plugin** for Xcode (optional but recommended)

### Firebase Setup
You need to configure Firebase for iOS:

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Add an iOS app (or use existing)
   - **Bundle ID for Buy variant**: `com.together.buy`
   - **Bundle ID for Sell variant**: `com.together.sell`
4. Download `GoogleService-Info.plist` for each variant
5. Replace the placeholder file at `iosApp/iosApp/GoogleService-Info.plist`

**Important**: You'll need separate Firebase iOS apps for Buy and Sell variants if you want different configurations.

## Initial Setup (First Time Only)

### 1. Build the Shared Kotlin Framework

Before opening the Xcode project, you need to build the shared Kotlin framework:

```bash
cd /path/to/newverse

# Build the framework for iOS Simulator (for development)
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# Or build for all iOS targets
./gradlew :shared:linkDebugFrameworkIosArm64
./gradlew :shared:linkDebugFrameworkIosX64
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

The framework will be generated at:
- `shared/build/bin/iosArm64/debugFramework/` (for real devices)
- `shared/build/bin/iosSimulatorArm64/debugFramework/` (for M1/M2 simulators)
- `shared/build/bin/iosX64/debugFramework/` (for Intel simulators)

### 2. Install CocoaPods Dependencies

```bash
cd iosApp
pod install
```

This will:
- Install Firebase dependencies
- Create `iosApp.xcworkspace` (use this instead of .xcodeproj)

### 3. Open the Project in Xcode

```bash
open iosApp/iosApp.xcworkspace
```

**Important**: Always open the `.xcworkspace` file, not the `.xcodeproj` file, when using CocoaPods.

## Building and Running

### Development Build (Debug)

1. Open Xcode
2. Select the scheme you want to build:
   - **iosApp-Buy** for the buyer app
   - **iosApp-Sell** for the seller app
3. Select your target device (simulator or physical device)
4. Click Run (⌘R)

### Release Build

1. Select the scheme (Buy or Sell)
2. Edit Scheme → Run → Build Configuration → Release
3. Archive the app: Product → Archive
4. Use Xcode Organizer to distribute

## Build Variants (Buy vs Sell)

The iOS app supports two variants, similar to Android flavors:

### Buy Variant
- **Bundle ID**: `com.together.buy`
- **Display Name**: "Newverse Buy"
- **Scheme**: iosApp-Buy
- **Build Configurations**: Debug-Buy, Release-Buy

### Sell Variant
- **Bundle ID**: `com.together.sell`
- **Display Name**: "Newverse Sell"
- **Scheme**: iosApp-Sell
- **Build Configurations**: Debug-Sell, Release-Sell

To switch between variants:
1. Select the desired scheme from the Xcode scheme selector
2. Build and run

## Gradle Integration

The iOS app integrates with Gradle for building the shared framework. You can automate framework building:

### Manual Framework Build

```bash
# Debug builds
./gradlew :shared:linkDebugFrameworkIosArm64           # For real devices
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64  # For M1/M2 simulators

# Release builds
./gradlew :shared:linkReleaseFrameworkIosArm64
./gradlew :shared:linkReleaseFrameworkIosSimulatorArm64
```

### Automatic Framework Build (Recommended)

You can add a build phase in Xcode to automatically build the framework:

1. In Xcode, select the iosApp target
2. Go to Build Phases
3. Click + → New Run Script Phase
4. Add this script:

```bash
cd "$SRCROOT/.."
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Adjust the Gradle task based on your target architecture.

## Common Build Configurations

### Info.plist Variables

These are set in the Xcode build settings:
- `APP_DISPLAY_NAME`: Set to "Newverse Buy" or "Newverse Sell"
- `PRODUCT_BUNDLE_IDENTIFIER`: Set to `com.together.buy` or `com.together.sell`
- `MARKETING_VERSION`: 1.0.0
- `CURRENT_PROJECT_VERSION`: 1

### Deployment Target

- **Minimum iOS Version**: 15.0
- Supports iPhone and iPad

## Troubleshooting

### Framework Not Found Error

**Error**: `Framework 'shared' not found`

**Solution**: Build the shared framework first:
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### CocoaPods Issues

**Error**: Pod-related errors

**Solution**:
```bash
cd iosApp
pod deintegrate
pod install
```

### Architecture Mismatch

**Error**: Building for wrong architecture

**Solution**: Ensure you're building the correct framework variant:
- For M1/M2 Mac simulator: use `IosSimulatorArm64`
- For Intel Mac simulator: use `IosX64`
- For real devices: use `IosArm64`

### Firebase Configuration Missing

**Error**: Firebase initialization fails

**Solution**: Replace the placeholder `GoogleService-Info.plist` with your real Firebase configuration file.

### Clean Build

If you encounter persistent issues:

```bash
# Clean Xcode build
# In Xcode: Product → Clean Build Folder (Shift + ⌘ + K)

# Clean Gradle build
./gradlew clean

# Clean CocoaPods
cd iosApp
rm -rf Pods
pod install
```

## Code Signing

For running on physical devices or distributing:

1. Open Xcode project
2. Select iosApp target
3. Go to Signing & Capabilities
4. Set your Team
5. Xcode will automatically manage provisioning profiles

For App Store distribution, you'll need:
- Apple Developer account
- Distribution certificate
- Provisioning profile

## Next Steps

### Before App Store Submission

1. **Replace placeholder GoogleService-Info.plist** with real Firebase config
2. **Add app icons** to Assets.xcassets/AppIcon.appiconset
3. **Configure code signing** with your Apple Developer account
4. **Test on real devices**
5. **Set up different Firebase projects** for Buy and Sell variants (if needed)
6. **Configure push notifications** (if using)
7. **Add privacy policy** and required App Store metadata

### Testing

1. Test both Buy and Sell variants
2. Test on multiple iOS versions (15.0+)
3. Test on different device sizes (iPhone, iPad)
4. Test authentication flows
5. Test image upload/download
6. Test offline functionality

## Development Workflow

### Daily Development

1. Make changes to shared Kotlin code
2. Rebuild the framework:
   ```bash
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
   ```
3. Run the iOS app in Xcode (⌘R)

### Making Changes to iOS-Specific Code

1. Edit Swift files in Xcode
2. Edit iOS-specific Kotlin files in `shared/src/iosMain/`
3. No need to rebuild framework if only changing Swift

## Resources

- [Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [Firebase iOS Documentation](https://firebase.google.com/docs/ios/setup)
- [CocoaPods Guide](https://guides.cocoapods.org/)
- [Xcode Documentation](https://developer.apple.com/documentation/xcode)

## Support

For issues specific to:
- **Shared Kotlin code**: Check `shared/` module
- **iOS wrapper**: Check `iosApp/` files
- **Build system**: Check Gradle configuration
- **Firebase**: Check Firebase console and configuration files
