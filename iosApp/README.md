# Newverse iOS App

iOS application for Newverse marketplace, built with SwiftUI and Kotlin Multiplatform.

## Quick Start

See the detailed guides in the `doc/` folder:
- [iOS Setup Guide](../doc/iOS-Setup-Guide.md) - Complete setup instructions
- [iOS Quick Start](../doc/iOS-Quick-Start.md) - Quick reference

## Prerequisites

- macOS Monterey or later
- Xcode 15.0+
- CocoaPods 1.11+

## Build & Run

```bash
# 1. Build shared framework
cd ..
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# 2. Install dependencies
cd iosApp
pod install

# 3. Open in Xcode
open iosApp.xcworkspace

# 4. Select scheme (Buy or Sell) and run
```

## Project Structure

- **iosApp/NewverseApp.swift** - App entry point, initializes Koin
- **iosApp/ContentView.swift** - SwiftUI wrapper for Compose UI
- **iosApp/Info.plist** - App configuration
- **iosApp/GoogleService-Info.plist** - Firebase config (REPLACE WITH REAL FILE)
- **Podfile** - CocoaPods dependencies

## Build Variants

### Buy Variant
- Bundle ID: `com.together.buy`
- Display Name: "Newverse Buy"
- Scheme: iosApp-Buy

### Sell Variant
- Bundle ID: `com.together.sell`
- Display Name: "Newverse Sell"
- Scheme: iosApp-Sell

## Important Configuration

### Firebase Setup Required

The placeholder `GoogleService-Info.plist` must be replaced with your actual Firebase configuration:

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Add iOS app with bundle ID:
   - Buy: `com.together.buy`
   - Sell: `com.together.sell`
3. Download `GoogleService-Info.plist`
4. Replace the placeholder file

### Code Signing

Set your development team in Xcode:
1. Select iosApp target
2. Signing & Capabilities tab
3. Set Team to your Apple Developer account

## Architecture

```
┌─────────────────────┐
│   SwiftUI Layer     │  ← NewverseApp.swift, ContentView.swift
├─────────────────────┤
│  Compose UI Layer   │  ← NewverseTheme { AppScaffold() }
├─────────────────────┤
│  Business Logic     │  ← Shared Kotlin (ViewModels, Repositories)
├─────────────────────┤
│  Data Layer         │  ← Firebase, Local Storage
└─────────────────────┘
```

## Shared Code

The iOS app uses shared Kotlin code from:
- `shared/src/commonMain/` - Cross-platform code
- `shared/src/iosMain/` - iOS-specific implementations

Key iOS-specific files:
- `shared/src/iosMain/kotlin/.../MainViewController.kt` - Compose UI wrapper
- `shared/src/iosMain/kotlin/.../KoinInitializer.kt` - Dependency injection setup
- `shared/src/iosMain/kotlin/.../di/IosDomainModule.kt` - iOS DI module

## Dependencies

### CocoaPods (defined in Podfile)
- Firebase/Auth
- Firebase/Database
- Firebase/Storage
- Firebase/Core
- GoogleSignIn

### Kotlin Multiplatform (shared module)
- Compose Multiplatform UI
- Koin (Dependency Injection)
- Kotlinx Coroutines
- GitLive Firebase SDK
- Coil (Image Loading)
- Navigation

## Development Workflow

1. Make changes to shared Kotlin code in `shared/`
2. Rebuild framework: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
3. Run app in Xcode

For Swift-only changes, just run in Xcode (no framework rebuild needed).

## Troubleshooting

### Framework Not Found
```bash
cd ..
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### Pod Errors
```bash
pod deintegrate
pod install
```

### Clean Build
```bash
# Clean Xcode: Shift + ⌘ + K
cd ..
./gradlew clean
cd iosApp
rm -rf Pods
pod install
```

## Next Steps

- [ ] Replace GoogleService-Info.plist with real Firebase config
- [ ] Add app icons to Assets.xcassets
- [ ] Configure code signing
- [ ] Test on real devices
- [ ] Set up separate Firebase projects for Buy/Sell variants
- [ ] Configure push notifications (if needed)
- [ ] Add App Store metadata

## License

See main project LICENSE file.
