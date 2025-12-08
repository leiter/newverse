# iOS Setup Guide

## Overview

The iOS app uses SwiftUI wrapper with Kotlin Multiplatform shared code, Compose Multiplatform UI, and GitLive Firebase SDK.

## Prerequisites

- macOS Monterey+
- Xcode 15.0+
- CocoaPods: `sudo gem install cocoapods`

## First Time Setup (15 min)

### 1. Build Shared Framework

```bash
cd /path/to/newverse
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### 2. Install CocoaPods

```bash
cd iosApp
pod install
```

### 3. Open in Xcode

```bash
open iosApp.xcworkspace  # NOT .xcodeproj
```

### 4. Configure Code Signing

1. Select iosApp target
2. Signing & Capabilities → Set Team
3. Xcode auto-manages signing

## Firebase Configuration

### Variant Files

```
iosApp/iosApp/
├── GoogleService-Info-Buy.plist   # Buy variant
├── GoogleService-Info-Sell.plist  # Sell variant
└── GoogleService-Info.plist       # Active (copied by build script)
```

### Build Script Setup

Add to Xcode Build Phases (before Compile Sources):
```bash
"${PROJECT_DIR}/copy-firebase-plist.sh"
```

### Manual Switching (Alternative)

```bash
cp iosApp/GoogleService-Info-Buy.plist iosApp/GoogleService-Info.plist
# or
cp iosApp/GoogleService-Info-Sell.plist iosApp/GoogleService-Info.plist
```

## Build Variants

| Variant | Bundle ID | Scheme |
|---------|-----------|--------|
| Buy | com.together.buy | iosApp-Buy |
| Sell | com.together.sell | iosApp-Sell |

## Build Commands

```bash
# Simulator (M1/M2)
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# Simulator (Intel)
./gradlew :shared:linkDebugFrameworkIosX64

# Real Device
./gradlew :shared:linkDebugFrameworkIosArm64
```

## Daily Development

1. Make changes to shared Kotlin code
2. Rebuild framework: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
3. Run in Xcode: ⌘R

Swift-only changes don't need framework rebuild.

## GitLive + Firebase Architecture

```
Your Kotlin Code (commonMain)
    ↓
GitLive Firebase SDK (dev.gitlive:firebase-*)
    ↓
Native Firebase SDK (CocoaPods)
```

Both GitLive AND native Firebase dependencies are required:
- **GitLive**: Cross-platform Kotlin API
- **Native Firebase**: Actual implementation

## Troubleshooting

### Framework not found
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### Module 'FirebaseAuth' not found
Open `.xcworkspace`, not `.xcodeproj`

### CocoaPods issues
```bash
cd iosApp
pod deintegrate && pod install
```

### Clean everything
```bash
./gradlew clean
cd iosApp
rm -rf Pods && pod install
# Xcode: Shift+⌘+K
```

## Key Directories

```
iosApp/
├── iosApp/
│   ├── NewverseApp.swift       # App entry
│   └── ContentView.swift       # Main view
├── iosApp.xcworkspace          # Open this
└── Podfile                     # Dependencies

shared/src/iosMain/kotlin/      # iOS-specific Kotlin
├── MainViewController.kt       # Compose wrapper
└── KoinInitializer.kt          # DI setup
```

## Success Indicators

- Xcode builds without errors
- App launches on simulator
- Firebase logs show initialization
- Can navigate between screens
