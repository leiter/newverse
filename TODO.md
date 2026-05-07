# Newverse Development TODO - iOS Buy App

**Last Updated:** 2026-03-25

## Outstanding iOS Platform Actions (Buy App)

The following actions are defined in `PlatformAction` but are not yet implemented in the native iOS layer.

### 1. QR Code Scanning
**File:** `shared/src/iosMain/kotlin/com/together/newverse/MainViewController.kt`  
**Status:** ✅ Fixed (2026-05-07)  
**Description:** Triggered from `CustomerProfileScreenModern` to scan seller QR codes.  
**Implementation:**
- Added `onScanQrCodeRequested` callback to `MainViewControllerWithCallback`.
- Implemented `QrScannerViewController` (AVFoundation) in `ContentView.swift`; scanned value is forwarded via `handleDeepLinkUrl`.

### 2. Native Sharing
**File:** `shared/src/iosMain/kotlin/com/together/newverse/MainViewController.kt`  
**Status:** ✅ Fixed (2026-05-07)  
**Description:** Triggered from `AddBuyerContactScreen` to share deep link invitations.  
**Implementation:**
- Added `onShareRequested(text: String)` callback to `MainViewControllerWithCallback`.
- Implemented `UIActivityViewController` in `ContentView.swift` with iPad popover support.

### 3. Google Sign-In on iOS
**Files:** `iosApp/iosApp/NewverseApp.swift`, `iosApp/iosApp/ContentView.swift`  
**Status:** ✅ Fixed (2026-05-07)  
**Description:** Google Sign-In returned an immediate error on iOS because `handleGoogleSignIn()` was a stub.  
**Implementation:**
- Added `GIDSignIn` configuration in `NewverseApp.init` using the Firebase `clientID`.
- Replaced the stub with a real `GIDSignIn.sharedInstance.signIn(withPresenting:)` call.
- Added `GIDSignIn.sharedInstance.handle(url)` in `.onOpenURL` before forwarding deep links.

---

## Infrastructure & Bug Fixes

### 3. Auto-Relogin UX Fix
**Status:** ✅ Fixed (2026-03-25)  
**Implementation:**
- Initialized `BuyAppState` with `isInitializing = true` to show splash screen immediately.
- Added a `"-1"` startup signal in `GitLiveAuthRepository` to distinguish between "initializing" and "logged out".
- Handled empty string UIDs from Firebase as `NotAuthenticated`.
- Forced navigation to Home if an auto-relogin occurs while the user is on the Login screen.

### 4. iOS DI & Flavor Compilation Fix
**Status:** ✅ Fixed (2026-03-25)  
**Implementation:**
- Created a `MainAppScaffold` bridge using `expect/actual` to abstract flavor-specific entry points.
- Shared `PlatformAction` interface in `commonMain` to harmonize native actions.
- Configured `shared/build.gradle.kts` to dynamically link iOS targets to the correct flavor (`buyMain`/`sellMain`) and ensure `iosMain` is correctly included in the dependency chain.
- This allows a common `MainViewController.kt` to drive different flavor variants on iOS.


