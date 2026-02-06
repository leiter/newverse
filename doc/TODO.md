# Newverse Development TODO

**Last Updated:** 2026-02-06

## Current Status

| Area | Status | Details |
|------|--------|---------|
| Android Buy App | Production Ready | All core features working |
| Android Sell App | Production Ready | All core features working |
| iOS Apps | Blocked | Missing platform implementations |
| Unit Tests | Complete | 234 tests passing |
| ViewModel Architecture | Refactored | Extension function pattern |

---

## Priority 1: iOS Platform Completion (Blocks Release)

### 1.1 iOS Image Picker
**File:** `shared/src/iosMain/.../util/ImagePicker.kt`
**Status:** Stubbed with TODO

**Required:**
- Implement `pickImage()` using `PHPickerViewController` (iOS 14+)
- Implement `captureImage()` using `UIImagePickerController`
- Handle permissions for photo library and camera

**Approach:**
```swift
// Use PHPickerViewController for gallery
// Use UIImagePickerController for camera
// Return image as ByteArray via callback
```

### 1.2 iOS Google Sign-In
**File:** `shared/src/iosMain/.../util/GoogleSignInHelper.kt`
**Status:** Stubbed with TODO

**Required:**
- Implement using Google Sign-In SDK for iOS
- Configure OAuth client ID in Info.plist
- Handle sign-in flow and token retrieval

### 1.3 iOS Document Picker
**File:** `shared/src/iosMain/.../util/DocumentPicker.kt`
**Status:** Stubbed with TODO

**Required:**
- Implement using `UIDocumentPickerViewController`
- Support CSV and BNN file types for product import

### 1.4 iOS Platform Actions
**File:** `shared/src/iosMain/.../MainViewController.kt`
**Status:** TODO comment at line with platform actions

---

## Priority 2: Core Feature Gaps (High User Impact)

### 2.1 Product Search (Buy App)
**Current:** Search UI exists, functionality stubbed
**Impact:** High - Core user experience

**Tasks:**
- [ ] Implement text search matching product names
- [ ] Add search by category
- [ ] Add search history (optional)
- [ ] Consider debouncing for performance

**Files to modify:**
- `BuyAppViewModelMainScreen.kt` - Add search logic
- `MainScreenState` - Add search query state

### 2.2 Product Detail View
**Current:** Click handler exists, navigation not implemented
**Impact:** Medium-High - Users can't see full product info

**Tasks:**
- [ ] Create `ProductDetailScreen.kt`
- [ ] Add navigation route
- [ ] Show full product info, images, description
- [ ] Add "Add to Basket" button (Buy app)
- [ ] Add "Edit" button (Sell app)

### 2.3 Revenue Calculation (Sell App)
**File:** `OverviewViewModel.kt:125`
**Current:** Shows "0" for revenue

**Fix:**
```kotlin
val revenue = orders
    .filter { it.status == OrderStatus.COMPLETED }
    .sumOf { order -> order.articles.sumOf { it.price * it.amountCount } }
```

---

## Priority 3: User Experience Improvements

### 3.1 Push Notifications
**Status:** UI exists, not implemented

**Tasks:**
- [ ] Set up Firebase Cloud Messaging (FCM)
- [ ] Buyer notifications: order status, pickup reminders
- [ ] Seller notifications: new orders, deadline approaching

### 3.2 Error Handling Standardization
**Issue:** Inconsistent error display across screens

**Tasks:**
- [ ] Create unified error composable
- [ ] Standardize error state in ViewModels
- [ ] Add retry mechanisms

### 3.3 Pull-to-Refresh
**Status:** Mentioned in TODOs

**Tasks:**
- [ ] Add SwipeRefresh to Order History
- [ ] Add SwipeRefresh to Products list

---

## Priority 4: Business Features

### 4.1 Market Management (Sell App)
**Status:** UI exists, save incomplete

**Tasks:**
- [ ] Implement market CRUD operations
- [ ] Save delivery days to profile
- [ ] Validate business hours

### 4.2 Promo Codes (Buy App)
**Status:** Stubbed

**Tasks:**
- [ ] Design promo code data model
- [ ] Add validation logic
- [ ] Apply discount to order total

### 4.3 Twitter Sign-In
**Status:** Button exists, functionality stubbed
**Priority:** Low - evaluate if needed for target audience

---

## Completed Items

- [x] BuyAppViewModel refactoring (77% size reduction)
- [x] Unit test coverage (234 tests)
- [x] Apple Sign-In implementation
- [x] Email linking for guest accounts
- [x] Auto-login and display name detection
- [x] Favorites persistence
- [x] Password reset flow
- [x] Order editing with deadlines
- [x] Merge conflict resolution

---

## Recommended Next Steps

### If focusing on iOS release:
1. iOS Image Picker (required for Sell app product creation)
2. iOS Google Sign-In (required for full auth support)
3. iOS Document Picker (required for product import)

### If focusing on Android improvements:
1. Product Search - highest user impact
2. Product Detail View - basic UX expectation
3. Revenue Calculation - quick win for sellers

### If focusing on code quality:
1. Error handling standardization
2. SellAppViewModel refactoring (similar to BuyAppViewModel)
3. Integration tests for critical flows
