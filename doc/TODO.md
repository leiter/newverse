# Newverse Development TODO

**Last Updated:** 2026-02-06

## Current Status

| Area | Status | Details |
|------|--------|---------|
| Android Buy App | Production Ready | All core features working |
| Android Sell App | Production Ready | All core features working |
| iOS Apps | Production Ready | Platform implementations complete |
| Unit Tests | Complete | 234 tests passing |
| ViewModel Architecture | Refactored | Extension function pattern |

---

## Priority 1: iOS Platform Completion - COMPLETE

### 1.1 iOS Image Picker - COMPLETE
**File:** `shared/src/iosMain/.../util/ImagePicker.kt`
**Status:** ✅ Implemented

**Implementation:**
- `pickImage()` using `UIImagePickerController` with `.photoLibrary` source
- `takePhoto()` using `UIImagePickerController` with `.camera` source
- Image resizing to max 1920x1920 with aspect ratio preservation
- JPEG compression at 0.8 quality

### 1.2 iOS Google Sign-In - COMPLETE
**File:** `shared/src/iosMain/.../util/GoogleSignInHelper.kt`
**Status:** ✅ Implemented

**Implementation:**
- Kotlin interface with Swift callback pattern
- `signIn()`, `onSignInSuccess()`, `onSignInError()`, `onSignInCancelled()`
- Suspending `signInSuspend()` for coroutine usage
- Singleton `GoogleSignInHelper.shared` for Swift interop

### 1.3 iOS Document Picker - COMPLETE
**File:** `shared/src/iosMain/.../util/DocumentPicker.kt`
**Status:** ✅ Implemented

**Implementation:**
- `UIDocumentPickerViewController` with text file types
- Security-scoped resource access for sandboxed files
- Returns content as String with filename

### 1.4 iOS Platform Actions
**File:** `shared/src/iosMain/.../MainViewController.kt`
**Status:** Partial - wire up remaining platform actions as needed

---

## Priority 2: Core Feature Gaps - COMPLETE

### 2.1 Product Search (Buy App) - COMPLETE
**Status:** ✅ Implemented

**Implementation:**
- [x] Search bar UI in MainScreenModern
- [x] Real-time filtering by product name, searchTerms, category
- [x] Case-insensitive matching
- [x] Clear search button
- [x] "No results" empty state

**Files modified:**
- `BuyAppViewModelMainScreen.kt` - Added `updateSearchQuery()` action handler
- `UnifiedAppState.kt` - Added `searchQuery` state and filtering logic
- `MainScreenModern.kt` - Added search bar UI

### 2.2 Product Detail View - COMPLETE
**Status:** ✅ Implemented

**Implementation:**
- [x] Created `ProductDetailScreen.kt` with full product info
- [x] Large product image with AsyncImage
- [x] Product name, price, unit, category, description
- [x] Quantity selector with +/- buttons and text input
- [x] Add to cart / Update cart button
- [x] Favorite toggle in app bar
- [x] Navigation route `NavRoutes.Buy.ProductDetail`

### 2.3 Revenue Calculation (Sell App) - COMPLETE
**File:** `OverviewViewModel.kt`
**Status:** ✅ Implemented

**Implementation:**
- `calculateTotalRevenue()` sums all COMPLETED and LOCKED orders
- Revenue displayed as StatCard in OverviewScreen
- Formatted as currency using `formatPrice()`

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
- [x] iOS Image Picker (UIImagePickerController with photo library + camera)
- [x] iOS Google Sign-In (Kotlin/Swift interop pattern)
- [x] iOS Document Picker (UIDocumentPickerViewController)
- [x] Product Search (Buy App) - real-time filtering
- [x] Product Detail View - dedicated screen with full info
- [x] Revenue Calculation (Sell App) - StatCard in overview
- [x] TestFlight upload (2026-02-06)

---

## Recommended Next Steps

### If focusing on user experience:
1. Push Notifications - FCM for order updates
2. Pull-to-Refresh - Order History and Products list
3. Error handling standardization

### If focusing on business features:
1. Market Management (Sell App) - CRUD operations
2. Promo Codes (Buy App) - discount system

### If focusing on code quality:
1. SellAppViewModel refactoring (similar to BuyAppViewModel)
2. Integration tests for critical flows
3. Twitter Sign-In (low priority)
