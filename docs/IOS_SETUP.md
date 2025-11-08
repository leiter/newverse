# iOS App Setup Guide - Buy/Sell Flavors

This guide provides step-by-step instructions for creating the iOS app with Buy and Sell flavors for the Newverse KMP project.

## Prerequisites

- **macOS** with Xcode 15.0 or later
- **Xcode Command Line Tools** installed
- **CocoaPods** (optional, if using pods)
- The KMP shared framework already configured (✅ already done in this project)

## Overview

The Newverse project uses Kotlin Multiplatform (KMP) with a shared module that's already configured for iOS. This guide will help you:
1. Create the iOS app in Xcode
2. Set up two targets for Buy and Sell flavors
3. Link the KMP shared framework
4. Configure flavor-specific resources

---

## Part 1: Verify KMP Framework

Before creating the iOS app, ensure the KMP framework builds correctly.

### 1.1 Build the Framework

```bash
# From the project root on macOS
./gradlew :shared:linkDebugFrameworkIosX64
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :shared:linkDebugFrameworkIosArm64
```

**Verify outputs:**
- `shared/build/bin/iosX64/debugFramework/shared.framework`
- `shared/build/bin/iosSimulatorArm64/debugFramework/shared.framework`
- `shared/build/bin/iosArm64/debugFramework/shared.framework`

---

## Part 2: Create iOS App with Multiple Targets

### 2.1 Create New Xcode Project

1. Open Xcode
2. **File → New → Project**
3. Choose **iOS → App**
4. Configure:
   - **Product Name**: `NewverseBuy`
   - **Team**: Your Apple Developer Team
   - **Organization Identifier**: `com.together.newverse`
   - **Bundle Identifier**: `com.together.newverse.buy`
   - **Interface**: SwiftUI
   - **Language**: Swift
5. Save location: `/path/to/newverse/iosApp/`

### 2.2 Add Sell Target

1. In Xcode, select the project in the navigator
2. Click the **+** button at the bottom of the Targets list
3. Choose **Duplicate "NewverseBuy"**
4. Rename the duplicated target:
   - **Target Name**: `NewverseSell`
   - **Bundle Identifier**: `com.together.newverse.sell`
5. Rename the scheme: **Product → Scheme → Manage Schemes**
   - Rename "NewverseBuy copy" to "NewverseSell"

### 2.3 Project Structure

After setup, your structure should look like:

```
iosApp/
├── NewverseBuy/
│   ├── NewverseBuyApp.swift       # Buy app entry point
│   ├── Assets.xcassets            # Buy-specific assets
│   ├── Info.plist                 # Buy configuration
│   └── Preview Content/
├── NewverseSell/
│   ├── NewverseSellApp.swift      # Sell app entry point
│   ├── Assets.xcassets            # Sell-specific assets
│   ├── Info.plist                 # Sell configuration
│   └── Preview Content/
├── Shared/
│   ├── ContentView.swift          # Shared SwiftUI views
│   ├── Models/                    # Swift models (if needed)
│   └── ViewModels/                # SwiftUI ViewModels
└── iosApp.xcodeproj
```

---

## Part 3: Link KMP Framework

### 3.1 Add Framework Search Path

For **both targets** (NewverseBuy and NewverseSell):

1. Select target → **Build Settings**
2. Search for **Framework Search Paths**
3. Add: `$(SRCROOT)/../shared/build/bin/iosSimulatorArm64/debugFramework`
4. Add: `$(SRCROOT)/../shared/build/bin/iosArm64/debugFramework`

### 3.2 Add Framework to Target

1. Select target → **Build Phases**
2. Expand **Link Binary With Libraries**
3. Click **+** button
4. Click **Add Other... → Add Files...**
5. Navigate to `shared/build/bin/iosSimulatorArm64/debugFramework/`
6. Select `shared.framework`
7. Click **Add**
8. Repeat for both targets

### 3.3 Add Run Script Phase

To automatically rebuild the framework before each build:

1. Select target → **Build Phases**
2. Click **+** → **New Run Script Phase**
3. Name it: `Build Kotlin Framework`
4. Add script:

```bash
cd "$SRCROOT/.."
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

5. Move this phase **before** "Compile Sources"
6. Repeat for both targets

### 3.4 Configure Framework Embedding

1. Select target → **Build Phases**
2. Expand **Embed Frameworks** (create if doesn't exist)
3. Click **+** → Add `shared.framework`
4. Set **Code Sign On Copy**: ✅ Checked
5. Repeat for both targets

---

## Part 4: Configure Flavor-Specific Settings

### 4.1 App Names

**NewverseBuy Target:**
1. Select target → **Info**
2. Set **Bundle display name**: `Newverse Buy`

**NewverseSell Target:**
1. Select target → **Info**
2. Set **Bundle display name**: `Newverse Sell`

### 4.2 Bundle Identifiers

Verify in **Build Settings → Packaging**:
- **NewverseBuy**: `com.together.newverse.buy`
- **NewverseSell**: `com.together.newverse.sell`

### 4.3 App Icons

**For NewverseBuy:**
1. Open `NewverseBuy/Assets.xcassets`
2. Select **AppIcon**
3. Add icon images with Buy branding (teal/green theme)

**For NewverseSell:**
1. Open `NewverseSell/Assets.xcassets`
2. Select **AppIcon**
3. Add icon images with Sell branding (dark green theme)

### 4.4 Brand Colors

**NewverseBuy - Add to Assets.xcassets:**
```swift
// In Assets.xcassets, create Color Set
BrandPrimary: #008577 (BrightGreen)
BrandAccent: #FA9C4D (Orange)
```

**NewverseSell - Add to Assets.xcassets:**
```swift
// In Assets.xcassets, create Color Set
BrandPrimary: #0A6308 (FabGreen)
BrandAccent: #FA9C4D (Orange)
```

---

## Part 5: Use KMP Shared Code

### 5.1 Import Framework

In your Swift files:

```swift
import shared

class ContentViewModel: ObservableObject {
    private let greetingRepository = GreetingRepository()

    @Published var greeting: String = ""

    init() {
        self.greeting = greetingRepository.getGreeting()
    }
}
```

### 5.2 Example SwiftUI View

**Shared/ContentView.swift:**

```swift
import SwiftUI
import shared

struct ContentView: View {
    @StateObject private var viewModel = ContentViewModel()

    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text(viewModel.greeting)
                    .font(.title)
                    .padding()

                Text("Running on: \(Platform().name)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            .navigationTitle("Newverse")
        }
    }
}
```

### 5.3 App Entry Point

**NewverseBuy/NewverseBuyApp.swift:**

```swift
import SwiftUI

@main
struct NewverseBuyApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

**NewverseSell/NewverseSellApp.swift:**

```swift
import SwiftUI

@main
struct NewverseSellApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

---

## Part 6: Build Configurations

### 6.1 Debug vs Release

Configure build settings for both targets:

**Debug Configuration:**
- Framework: `linkDebugFrameworkIos{Architecture}`
- Optimization: `-Onone`
- Swift Optimization: `-Onone`

**Release Configuration:**
- Framework: `linkReleaseFrameworkIos{Architecture}`
- Optimization: `-O`
- Swift Optimization: `-O -whole-module-optimization`

### 6.2 Update Run Script for Release

Modify the "Build Kotlin Framework" script:

```bash
cd "$SRCROOT/.."

if [ "$CONFIGURATION" = "Debug" ]; then
    ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
else
    ./gradlew :shared:linkReleaseFrameworkIosSimulatorArm64
fi
```

---

## Part 7: Build & Run

### 7.1 Select Scheme & Device

1. In Xcode toolbar, click scheme selector
2. Choose **NewverseBuy** or **NewverseSell**
3. Choose simulator or device
4. Click **▶ Run** (Cmd+R)

### 7.2 Build Both Flavors

```bash
# Command line builds
xcodebuild -project iosApp.xcodeproj \
  -scheme NewverseBuy \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build

xcodebuild -project iosApp.xcodeproj \
  -scheme NewverseSell \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build
```

### 7.3 Side-by-Side Installation

Both apps can be installed simultaneously on the same device/simulator because they have different bundle identifiers:
- `com.together.newverse.buy`
- `com.together.newverse.sell`

---

## Part 8: Advanced Configuration

### 8.1 Flavor-Specific Code

Use compiler flags for flavor-specific logic:

**Build Settings → Swift Compiler - Custom Flags:**

**NewverseBuy:**
- **Other Swift Flags**: `-DBUY_FLAVOR`

**NewverseSell:**
- **Other Swift Flags**: `-DSELL_FLAVOR`

**Usage in Swift:**

```swift
#if BUY_FLAVOR
let flavorName = "Buy"
let primaryColor = Color(hex: "#008577")
#elseif SELL_FLAVOR
let flavorName = "Sell"
let primaryColor = Color(hex: "#0A6308")
#endif
```

### 8.2 Environment Configuration

Create a `Config.swift` file in Shared:

```swift
import Foundation

struct Config {
    #if BUY_FLAVOR
    static let flavorType = "buy"
    static let appName = "Newverse Buy"
    static let primaryColorHex = "#008577"
    #elseif SELL_FLAVOR
    static let flavorType = "sell"
    static let appName = "Newverse Sell"
    static let primaryColorHex = "#0A6308"
    #else
    static let flavorType = "unknown"
    static let appName = "Newverse"
    static let primaryColorHex = "#000000"
    #endif
}
```

---

## Part 9: Continuous Integration

### 9.1 GitHub Actions (Example)

```yaml
name: iOS Build

on: [push, pull_request]

jobs:
  ios-build:
    runs-on: macos-latest

    steps:
    - uses: actions/checkout@v3

    - name: Setup Java
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Build KMP Framework
      run: |
        ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
        ./gradlew :shared:linkDebugFrameworkIosArm64

    - name: Build iOS Buy
      run: |
        xcodebuild -project iosApp/iosApp.xcodeproj \
          -scheme NewverseBuy \
          -configuration Debug \
          -sdk iphonesimulator

    - name: Build iOS Sell
      run: |
        xcodebuild -project iosApp/iosApp.xcodeproj \
          -scheme NewverseSell \
          -configuration Debug \
          -sdk iphonesimulator
```

---

## Part 10: Troubleshooting

### Framework Not Found

**Problem:** `dyld: Library not loaded: @rpath/shared.framework/shared`

**Solution:**
1. Check **Framework Search Paths** includes framework location
2. Verify **Embed Frameworks** includes `shared.framework`
3. Clean build folder: **Product → Clean Build Folder** (Cmd+Shift+K)

### Architecture Mismatch

**Problem:** `Building for iOS Simulator, but linking in object file built for iOS`

**Solution:**
- For Simulator: Use `linkDebugFrameworkIosSimulatorArm64`
- For Device: Use `linkDebugFrameworkIosArm64`
- Update run script to detect architecture

### Swift/Kotlin Interop Issues

**Problem:** Cannot access Kotlin classes in Swift

**Solution:**
1. Ensure framework is properly linked
2. Import with `import shared` (lowercase)
3. Check framework is in **Build Phases → Link Binary With Libraries**
4. Rebuild KMP framework: `./gradlew :shared:clean :shared:linkDebugFrameworkIosSimulatorArm64`

---

## Summary Checklist

- [ ] Built KMP framework successfully
- [ ] Created Xcode project with iOS app
- [ ] Added Buy and Sell targets
- [ ] Configured bundle identifiers (`.buy`, `.sell`)
- [ ] Linked `shared.framework` to both targets
- [ ] Added "Build Kotlin Framework" run script phase
- [ ] Set up flavor-specific app names
- [ ] Added flavor-specific app icons
- [ ] Configured brand colors in Assets
- [ ] Tested build for both Buy and Sell schemes
- [ ] Verified side-by-side installation

---

## Next Steps

Once the iOS app is set up:

1. **Migrate UI Components** - Convert Android Compose screens to SwiftUI
2. **Implement Navigation** - Use SwiftUI NavigationStack
3. **Add ViewModels** - Create SwiftUI ViewModels that use KMP repositories
4. **Firebase Integration** - Add Firebase SDK for iOS
5. **Testing** - Write XCTest unit and UI tests

---

## Additional Resources

- [Kotlin Multiplatform Mobile Docs](https://kotlinlang.org/docs/multiplatform-mobile-getting-started.html)
- [KMP iOS Integration Guide](https://kotlinlang.org/docs/multiplatform-mobile-integrate-in-existing-app.html)
- [SwiftUI Documentation](https://developer.apple.com/documentation/swiftui/)
- [Xcode Build Settings Reference](https://developer.apple.com/documentation/xcode/build-settings-reference)

---

## Support

If you encounter issues during setup:
1. Check the KMP framework builds successfully on macOS
2. Verify Xcode version is 15.0+
3. Clean derived data: `rm -rf ~/Library/Developer/Xcode/DerivedData`
4. Rebuild framework: `./gradlew :shared:clean :shared:linkDebugFrameworkIosSimulatorArm64`

---

*This guide assumes familiarity with Xcode and iOS development. For complete beginners, refer to Apple's [Start Developing iOS Apps](https://developer.apple.com/tutorials/app-dev-training) tutorial first.*
