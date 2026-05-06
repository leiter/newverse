# Newverse Development TODO - iOS Buy App

**Last Updated:** 2026-03-25

## Outstanding iOS Platform Actions (Buy App)

The following actions are defined in `PlatformAction` but are not yet implemented in the native iOS layer.

### 1. QR Code Scanning
**File:** `shared/src/iosBuy/kotlin/com/together/newverse/MainViewController.kt`  
**Status:** ❌ Pending  
**Description:** Triggered from `CustomerProfileScreenModern` to scan seller QR codes.  
**Task:**
- [ ] Add `onScanQrCodeRequested` callback to `MainViewControllerWithCallback`.
- [ ] Implement native camera scanning in `ContentView.swift`.

### 2. Native Sharing
**File:** `shared/src/iosBuy/kotlin/com/together/newverse/MainViewController.kt`  
**Status:** ❌ Pending  
**Description:** Triggered from `AddBuyerContactScreen` to share deep link invitations.  
**Task:**
- [ ] Add `onShareRequested(text: String)` callback to `MainViewControllerWithCallback`.
- [ ] Implement `UIActivityViewController` in `ContentView.swift`.

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
- Moved `KoinInitializer.kt` and `MainViewController.kt` to flavor-specific source sets (`iosBuy`, `iosSell`).
- This allows iOS targets to correctly resolve flavor-specific dependencies like `appModule` and `AppScaffold`.
