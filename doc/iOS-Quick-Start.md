# iOS Quick Start Guide

Quick reference for building and running the Newverse iOS app.

## First Time Setup

```bash
# 1. Build the shared framework
cd /path/to/newverse
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# 2. Install CocoaPods dependencies
cd iosApp
pod install

# 3. Open in Xcode
open iosApp.xcworkspace
```

## Daily Development

```bash
# Rebuild framework after Kotlin changes
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# Then in Xcode: ⌘R to run
```

## Build Commands

```bash
# iOS Simulator (M1/M2 Mac)
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :shared:linkReleaseFrameworkIosSimulatorArm64

# iOS Simulator (Intel Mac)
./gradlew :shared:linkDebugFrameworkIosX64
./gradlew :shared:linkReleaseFrameworkIosX64

# Real Device
./gradlew :shared:linkDebugFrameworkIosArm64
./gradlew :shared:linkReleaseFrameworkIosArm64

# All iOS targets
./gradlew :shared:linkDebugFrameworkIosArm64 \
          :shared:linkDebugFrameworkIosX64 \
          :shared:linkDebugFrameworkIosSimulatorArm64
```

## Schemes

- **iosApp-Buy**: Buyer app (com.together.buy)
- **iosApp-Sell**: Seller app (com.together.sell)

## Important Files to Configure

### Before First Run
1. `iosApp/iosApp/GoogleService-Info.plist` - Replace with real Firebase config
2. `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` - Add app icons
3. Code signing in Xcode (Signing & Capabilities tab)

## Troubleshooting

### Framework not found
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### CocoaPods issues
```bash
cd iosApp
pod deintegrate
pod install
```

### Clean everything
```bash
./gradlew clean
cd iosApp
rm -rf Pods
pod install
# In Xcode: Shift + ⌘ + K
```

## Build Configurations

| Variant | Bundle ID | Display Name |
|---------|-----------|--------------|
| Buy | com.together.buy | Newverse Buy |
| Sell | com.together.sell | Newverse Sell |

## Key Directories

```
iosApp/
├── iosApp/                      # Swift source code
│   ├── NewverseApp.swift       # App entry point
│   └── ContentView.swift       # Main view
├── iosApp.xcworkspace          # Open this in Xcode
└── Podfile                     # Dependencies

shared/src/iosMain/kotlin/      # iOS-specific Kotlin code
└── com/together/newverse/
    ├── MainViewController.kt    # Compose UI wrapper
    └── KoinInitializer.kt       # DI setup
```

## Requirements

- macOS Monterey or later
- Xcode 15.0+
- CocoaPods 1.11+
- iOS Deployment Target: 15.0+

## What Works Now (Without Mac)

✅ iOS app structure created
✅ Xcode project configured
✅ Build schemes for Buy/Sell variants
✅ SwiftUI wrapper code
✅ Kotlin shared framework setup
✅ CocoaPods configuration
✅ Firebase integration ready

## What Requires Mac

❌ Running the app
❌ Testing on simulators/devices
❌ Code signing
❌ App Store submission
❌ Installing CocoaPods dependencies (pod install)
❌ Xcode-specific features
