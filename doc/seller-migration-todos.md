# Seller Migration TODOs

**Date:** 2025-11-18
**Status:** ~32% Complete

## Executive Summary

The new KMP project has the UI foundation in place but needs significant backend integration work. Product creation is fully functional, but seller profile management, market locations, orders, and background services are incomplete or missing.

---

## Code TODOs (Found via Grep)

### High Priority
1. **FirebaseProfileRepository.kt:97** - Implement Firebase seller profile fetching
2. **FirebaseProfileRepository.kt:108** - Implement Firebase seller profile saving
3. **GitLiveStorageRepository.kt:54** - Implement proper file upload for GitLive

### Medium Priority
4. **MainActivity.kt:144** - Implement Twitter sign-in
5. **AuthRepositoryFactory.kt:47** - Implement expect/actual pattern for platform-specific Firebase loading
6. **ProfileRepositoryFactory.kt:46** - Use expect/actual pattern for platform-specific loading

### iOS Platform
7. **ImagePicker.kt (iOS)** - Implement using UIImagePickerController
8. **GoogleSignInHelper.kt (iOS)** - Implement Google Sign-In for iOS

---

## Feature Migration Status

### ✅ COMPLETE (100%)

#### 1. Product Creation
- **File:** `CreateProductScreen.kt`, `CreateProductViewModel.kt`
- **Status:** Fully functional
- **Features:**
  - Create new products with all fields
  - Image picker (gallery/camera)
  - Image upload to Firebase Storage
  - Form validation in German
  - Category and Unit dropdowns
  - Weight per piece for countable units
  - Save to Firebase Realtime Database
  - Upload progress indicator
  - Success/error handling

---

### ⚠️ PARTIAL (10-50%)

#### 2. Seller Profile Screen
- **File:** `SellerProfileScreen.kt`, `SellerProfileViewModel.kt`
- **Status:** UI only, no backend integration
- **What's Done:**
  - Basic UI layout
  - Statistics cards (placeholder)
  - Profile display (mock data)
  - Edit/logout buttons (not functional)

- **Missing:**
  - ❌ Load seller profile from Firebase
  - ❌ Edit profile functionality
  - ❌ Address field editing (street, house number, city, zip)
  - ❌ Save profile to Firebase
  - ❌ Validation logic
  - ❌ Company name field

- **TODO:**
  1. Implement `loadSellerProfile()` in SellerProfileViewModel
  2. Create profile edit screen/mode
  3. Add address form fields
  4. Connect to ProfileRepository.saveSeller()
  5. Add form validation

#### 3. Orders Management
- **File:** `OrdersScreen.kt`, `OrdersViewModel.kt`
- **Status:** UI only with mock data
- **What's Done:**
  - Card-based order display
  - Order details (ID, customer, items, total, message)
  - Basic UI structure

- **Missing:**
  - ❌ Real-time order loading from Firebase
  - ❌ Filter orders by market/date
  - ❌ OrderRepository implementation
  - ❌ Order status updates
  - ❌ Order listener service

- **TODO:**
  1. Implement OrderRepository Firebase connection
  2. Add real-time order listener
  3. Connect OrdersViewModel to repository
  4. Filter orders by active market dates
  5. Add order status management

#### 4. Overview Dashboard
- **File:** `OverviewScreen.kt`, `OverviewViewModel.kt`
- **Status:** UI with mock statistics
- **What's Done:**
  - Statistics cards UI
  - Product list preview
  - Basic layout

- **Missing:**
  - ❌ Real product count
  - ❌ Real order count
  - ❌ Real product list
  - ❌ Filtering (available/unavailable)

- **TODO:**
  1. Load real product count from ArticleRepository
  2. Load real order count from OrderRepository
  3. Connect to actual product list
  4. Add filter functionality

#### 5. Authentication Flow
- **File:** `LoginScreen.kt`
- **Status:** Basic auth UI exists
- **What's Done:**
  - Login screen UI
  - Email/password fields
  - Google Sign-In button
  - Basic AuthRepository integration

- **Missing:**
  - ❌ Check for seller profile existence after login
  - ❌ Navigate to profile creation if no profile
  - ❌ Navigate to home if profile exists
  - ❌ Deep link support for order notifications

- **TODO:**
  1. Add profile existence check after successful login
  2. Navigate to profile creation for new sellers
  3. Navigate to overview for existing sellers

---

### ❌ MISSING (0%)

#### 6. Market Location Management
- **Old Files:** `MarketDialog.kt`, `PickDayFragment.kt`, `MarketAdapter.kt`
- **New Files:** `PickDayScreen.kt` (UI only)
- **Status:** Not implemented

- **Features from Old Project:**
  - Add new market location
  - Edit existing markets
  - Delete markets
  - Fields: name, street, house number, zip code, city
  - Day of week picker
  - Time pickers (begin time, end time)
  - Validation (begin < end)
  - Market list display
  - Connect markets to seller profile

- **TODO:**
  1. Create MarketDialog or MarketBottomSheet composable
  2. Add market form fields (name, address, day, times)
  3. Implement time pickers
  4. Add market validation logic
  5. Connect PickDayScreen to market creation
  6. Implement market CRUD operations
  7. Display market list in seller profile
  8. Save markets to Firebase under seller profile

#### 7. Product Edit & Delete
- **Status:** Not implemented
- **Old Project:** Full edit/delete in `CreateFragment.kt`

- **Missing Features:**
  - ❌ Edit existing product
  - ❌ Delete product from Firebase
  - ❌ Delete product images from Storage
  - ❌ Product selection from list
  - ❌ Load product for editing

- **TODO:**
  1. Add edit mode parameter to CreateProductScreen
  2. Load existing product data when editing
  3. Update instead of create when in edit mode
  4. Implement delete functionality
  5. Add delete confirmation dialog
  6. Delete from both Database and Storage

#### 8. Product Search & Filter
- **Status:** Not implemented
- **Old Project:** Search/filter in `CreateFragment.kt`, `ProductViewsFragment.kt`

- **Missing Features:**
  - ❌ Search products by name
  - ❌ Filter by category
  - ❌ Filter by availability (available/unavailable)
  - ❌ Product list with selection

- **TODO:**
  1. Create ProductListScreen
  2. Add search bar
  3. Implement filter dropdown
  4. Connect to ArticleRepository.observeArticles()
  5. Add product selection for edit/delete

#### 9. Background Order Notification Service
- **Status:** Completely missing
- **Old Project:** Full service with WorkManager

- **Old Components:**
  - `ListenerService.kt` - Foreground service for order listening
  - `SwitchWorker.kt` - WorkManager worker
  - `StartServiceWorker.kt` - Service starter
  - `WorkRequestProvider.kt` - Work configuration
  - `BootCompletedReceiver.kt` - Restart on boot

- **Features:**
  - Real-time order notifications
  - Foreground service with notification
  - Background WorkManager scheduling
  - Auto-restart on device boot
  - Order sound/vibration alerts

- **TODO (Android-specific):**
  1. Create expect/actual for NotificationService
  2. Implement Android ListenerService
  3. Add WorkManager dependency
  4. Create order notification channel
  5. Implement real-time Firebase listener
  6. Add notification sound/vibration
  7. Handle notification tap to open order
  8. Add service start/stop controls
  9. Implement BootCompletedReceiver

- **TODO (iOS):**
  1. Implement push notifications
  2. Handle background fetch
  3. Add notification handling

#### 10. Statistics & Analytics
- **Status:** Not implemented

- **Missing:**
  - ❌ Total products count
  - ❌ Active orders count
  - ❌ Revenue tracking
  - ❌ Order history
  - ❌ Popular products

- **TODO:**
  1. Calculate real statistics from Firebase
  2. Add revenue calculation
  3. Track order completion
  4. Show order history

---

## Repository Implementation Status

### ✅ Implemented
- **ArticleRepository** - Firebase & GitLive implementations
- **AuthRepository** - Firebase & GitLive implementations
- **StorageRepository** - Firebase implementation (GitLive placeholder)
- **BasketRepository** - In-memory implementation

### ⚠️ Partial
- **ProfileRepository** - Interface exists, implementation unclear
  - `saveSeller()` - Returns NotImplementedError
  - `loadSellerProfile()` - Returns NotImplementedError
  - Customer profile methods work

### ❌ Needs Implementation
- **OrderRepository** - Interface exists, implementation status unclear
  - Real-time order loading
  - Order filtering by market/date
  - Order status updates

---

## Migration Priority Roadmap

### Phase 1: Core Seller Functionality (HIGH PRIORITY)

1. **Seller Profile Management** (1-2 days)
   - [ ] Implement FirebaseProfileRepository.saveSeller()
   - [ ] Implement FirebaseProfileRepository.loadSellerProfile()
   - [ ] Create profile edit UI/mode in SellerProfileScreen
   - [ ] Add address fields (street, house number, city, zip)
   - [ ] Add company name field
   - [ ] Implement validation
   - [ ] Test profile save/load

2. **Market Location CRUD** (2-3 days)
   - [ ] Create MarketDialog composable
   - [ ] Add market form fields
   - [ ] Implement time pickers (begin/end)
   - [ ] Add validation (begin < end, required fields)
   - [ ] Implement add market
   - [ ] Implement edit market
   - [ ] Implement delete market
   - [ ] Display market list in profile
   - [ ] Connect PickDayScreen to save markets
   - [ ] Save markets array to Firebase profile

3. **Orders Real-time Loading** (2-3 days)
   - [ ] Implement OrderRepository Firebase methods
   - [ ] Add real-time listener for orders
   - [ ] Filter orders by market dates
   - [ ] Connect OrdersViewModel to repository
   - [ ] Replace mock data with real orders
   - [ ] Test order loading

4. **Product Edit/Delete** (1-2 days)
   - [ ] Add edit mode to CreateProductScreen
   - [ ] Load product data when editing
   - [ ] Update product instead of create
   - [ ] Implement delete product
   - [ ] Delete from Storage when deleting product
   - [ ] Add delete confirmation dialog
   - [ ] Test edit/delete flow

### Phase 2: Enhanced UX (MEDIUM PRIORITY)

5. **Product List & Search** (1-2 days)
   - [ ] Create ProductListScreen
   - [ ] Add search functionality
   - [ ] Implement category filter
   - [ ] Implement availability filter
   - [ ] Add product selection for edit/delete
   - [ ] Navigate to edit from list

6. **Overview Dashboard Data** (1 day)
   - [ ] Load real product statistics
   - [ ] Load real order statistics
   - [ ] Calculate revenue metrics
   - [ ] Connect to real product list
   - [ ] Add filter controls

7. **Login Flow Enhancement** (1 day)
   - [ ] Add seller profile check after login
   - [ ] Navigate to profile creation if new
   - [ ] Navigate to overview if existing
   - [ ] Add loading state

### Phase 3: Platform-Specific Features (LOW PRIORITY)

8. **Android Background Service** (2-3 days)
   - [ ] Create NotificationService expect/actual
   - [ ] Implement Android ListenerService
   - [ ] Add WorkManager dependency
   - [ ] Create notification channel
   - [ ] Implement Firebase real-time listener
   - [ ] Add notification with sound/vibration
   - [ ] Handle notification tap navigation
   - [ ] Add service controls in app
   - [ ] Implement BootCompletedReceiver
   - [ ] Test background notifications

9. **iOS Push Notifications** (2-3 days)
   - [ ] Set up APNs certificates
   - [ ] Implement push notification handling
   - [ ] Add background fetch capability
   - [ ] Handle notification tap
   - [ ] Test iOS notifications

10. **Deep Links** (1 day)
    - [ ] Define deep link schema
    - [ ] Handle order detail deep links
    - [ ] Navigate from notification to order

---

## Estimated Work Remaining

- **Phase 1 (Core):** 6-10 days
- **Phase 2 (Enhanced UX):** 3-4 days
- **Phase 3 (Platform Features):** 4-6 days

**Total:** ~13-20 days of development work

**Current Completion:** ~32%
**After Phase 1:** ~70%
**After Phase 2:** ~85%
**After Phase 3:** ~100%

---

## Testing Checklist (Per Feature)

For each feature implementation, ensure:
- [ ] UI matches design/old app
- [ ] Form validation works
- [ ] Firebase save succeeds
- [ ] Firebase load succeeds
- [ ] Error handling works
- [ ] Loading states display correctly
- [ ] Navigation works
- [ ] Data persists after app restart
- [ ] Works on both Android and iOS (if applicable)

---

## Notes

1. **GitLive vs Firebase:** Currently using Firebase Storage in production mode. GitLive implementations exist but are placeholders for cross-platform support.

2. **Architecture:** New project uses modern patterns (Compose, Koin DI, StateFlow, Repository pattern) compared to old project (XML views, RxJava, Fragments).

3. **Platform Features:** Background services and notifications will need expect/actual implementations for true cross-platform support.

4. **Data Models:** All core models (Article, SellerProfile, Market, Order, CustomerProfile) are defined and compatible with Firebase structure.

---

## Quick Reference: File Locations

### Old Project (universe)
- Seller screens: `/home/mandroid/Videos/universe/app/src/sell/java/com/together/universe/`
- View models: Same directory + `viewmodels/`
- Repositories: Same directory + `data/`

### New Project (newverse)
- Seller screens: `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/sell/`
- View models: Same directory as screens
- Repositories: `/home/mandroid/Videos/newverse/shared/src/.../data/repository/`
- Domain models: `/home/mandroid/Videos/newverse/shared/src/.../domain/model/`
