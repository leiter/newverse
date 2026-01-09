# Newverse Feature Report

**Date:** 2025-12-16
**Apps:** Newverse Buy & Newverse Sell
**Platform:** Kotlin Multiplatform (Android + iOS)

---

## Executive Summary

Newverse is a marketplace platform consisting of two separate apps:
- **Newverse Buy** - Customer app for browsing products, managing orders, and checkout
- **Newverse Sell** - Seller app for product management, order fulfillment, and business operations

Both apps share a common codebase using Kotlin Multiplatform with Compose UI, Firebase backend, and GitLive SDK for cross-platform Firebase access.

---

## 1. Newverse Buy - Customer App

### 1.1 Implemented Features

| Feature | Status | Description |
|---------|--------|-------------|
| **Product Browsing** | Complete | Browse seller products with images, prices, and details |
| **Shopping Basket** | Complete | Add/remove items, adjust quantities, view totals |
| **Favorites** | Complete | Mark products as favorites, persisted to Firebase, filter chip works |
| **Order Placement** | Complete | Place orders with pickup date selection |
| **Order Editing** | Complete | Edit existing orders before Tuesday 23:59 deadline |
| **Order History** | Complete | View past orders, reorder functionality |
| **User Profile** | Complete | Manage name, email, phone, address |
| **Authentication** | Complete | Email/password, Google Sign-In, guest access |
| **Pickup Date Selection** | Complete | Thursday-only pickup with date picker |
| **Order Merge** | Complete | Conflict resolution when editing orders (ADD, KEEP, USE_NEW) |

### 1.2 User Flow

```
App Launch → Auth Check → Guest/Login
    ↓
Browse Products → Add to Basket → View Basket
    ↓
Select Pickup Date → Place Order → Confirmation
    ↓
(Later) Edit Order (if before deadline) or View History
```

### 1.3 Business Rules

- **Pickup Day:** Always Thursday
- **Edit Deadline:** Tuesday 23:59 before pickup Thursday
- **Order States:** DRAFT → PLACED → LOCKED → COMPLETED
- **Guest Access:** Allowed (anonymous Firebase auth)

---

## 2. Newverse Sell - Seller App

### 2.1 Implemented Features

| Feature | Status | Description |
|---------|--------|-------------|
| **Product Management** | Complete | Create, edit availability, delete products |
| **Bulk Operations** | Complete | Multi-select for availability toggle, deletion |
| **Product Import** | Complete | CSV/BNN format import with preview |
| **Image Upload** | Complete | Product images with progress indicator |
| **Order Management** | Complete | View incoming orders, filter by status |
| **Order Status Update** | Complete | Update order status (PENDING → COMPLETED) |
| **Seller Profile** | Complete | Business info, contact details |
| **Market Management** | Partial | Add/edit delivery market locations |
| **Delivery Days** | Partial | Configure operating days (UI exists) |
| **Notifications** | Partial | Notification preferences screen |
| **Dashboard** | Complete | Product stats, filtering, quick actions |

### 2.2 User Flow

```
App Launch → Forced Login (no guest access)
    ↓
Dashboard (Overview) → View Products/Orders
    ↓
Create Product → Upload Image → Save
    ↓
View Orders → Update Status → Mark Complete
```

### 2.3 Product Categories

13 predefined categories: Obst, Gemüse, Milchprodukte, Backwaren, Fleisch, Fisch, Getränke, Snacks, Gewürze, Konserven, Tiefkühl, Haushalt, Sonstiges

### 2.4 Product Units

12 unit types: kg, g, Liter, ml, Stück, Paket, Bund, Dose, Flasche, Glas, Becher, Schale

---

## 3. Shared Infrastructure

### 3.1 Authentication

| Method | Buy App | Sell App |
|--------|---------|----------|
| Email/Password | Yes | Yes |
| Google Sign-In | Yes | Yes |
| Guest/Anonymous | Yes | No (forced login) |
| Twitter Sign-In | Stubbed | Stubbed |

### 3.2 Data Layer

- **Firebase Realtime Database** - Products, orders, profiles
- **Firebase Storage** - Product images
- **GitLive SDK** - Cross-platform Firebase access
- **In-Memory Basket** - Local cart state

### 3.3 Platform Support

| Platform | Status |
|----------|--------|
| Android | Production Ready |
| iOS | In Development (significant TODOs) |

---

## 4. Improvement Opportunities

### 4.1 High Priority

#### 4.1.1 Search Functionality (Buy App)
**Current State:** Search UI exists but functionality is stubbed
**Impact:** High - Core user experience feature
**Files:** `BuyAppViewModel.kt:1555-1583`

**Recommendation:**
- Implement product search with text matching
- Add search history persistence
- Add category and price filters
- Consider Algolia or Firebase full-text search for scale

#### 4.1.2 iOS Platform Completion
**Current State:** Multiple iOS-specific features not implemented
**Impact:** High - Blocks iOS release
**Missing:**
- `ImagePicker.kt` - Camera/gallery access
- `DocumentPicker.kt` - File import
- `GoogleSignInHelper.kt` - OAuth flow

**Recommendation:**
- Prioritize image picker (required for product creation)
- Implement using PHPickerViewController (iOS 14+)
- Use ASAuthorizationController for Google Sign-In

#### 4.1.3 Revenue Calculation (Sell App)
**Current State:** Dashboard shows "0" for revenue
**Impact:** Medium - Seller business insights
**File:** `OverviewViewModel.kt:125`

**Recommendation:**
```kotlin
val revenue = orders
    .filter { it.status == OrderStatus.COMPLETED }
    .sumOf { order -> order.articles.sumOf { it.price * it.amountCount } }
```

#### 4.1.4 Product Detail View
**Current State:** Product click handler exists but navigation not implemented
**Impact:** Medium - Users can't view full product details
**Files:** `SellAppViewModel.kt:132`, `BuyAppViewModel.kt:1527`

**Recommendation:**
- Create `ProductDetailScreen.kt` with full product info
- Add edit capability for sellers
- Add "Add to Basket" for buyers

### 4.2 Medium Priority

#### 4.2.1 Favorites Persistence (Buy App)
**Current State:** ✅ FULLY IMPLEMENTED
**Impact:** Complete

**Implementation:**
- Favorites stored in `BuyerProfile.favouriteArticles`
- Synced to Firebase via `GitLiveProfileRepository.saveBuyerProfile()`
- Real-time observer keeps UI in sync
- "Favoriten" filter chip on browse screen works
- Survives app restart

#### 4.2.2 Order Notifications
**Current State:** Notification settings UI exists, actual notifications not implemented
**Impact:** Medium - User engagement

**Recommendation:**
- Implement Firebase Cloud Messaging (FCM)
- Notify buyers: order status changes, pickup reminders
- Notify sellers: new orders, deadline approaching

#### 4.2.3 Market Management Completion (Sell App)
**Current State:** UI exists but save functionality incomplete
**Impact:** Medium - Seller business setup
**File:** `PickDayScreen.kt:71`

**Recommendation:**
- Implement market CRUD operations
- Save delivery days to profile
- Validate business hours

#### 4.2.4 Password Reset
**Current State:** ✅ FULLY IMPLEMENTED
**Impact:** Complete

**Implementation:**
- `AuthRepository.sendPasswordResetEmail()` implemented in GitLiveAuthRepository
- ViewModel handler in `BuyAppViewModelAuth.kt`
- Action dispatch wired: `UnifiedUserAction.RequestPasswordReset`
- Uses Firebase Auth password reset email

### 4.3 Low Priority

#### 4.3.1 Twitter Sign-In
**Current State:** Button exists, functionality stubbed
**Impact:** Low - Alternative auth method

**Recommendation:**
- Evaluate if Twitter OAuth is needed for target audience
- If yes, implement using Firebase Twitter provider

#### 4.3.2 Promo Codes (Buy App)
**Current State:** Stubbed in ViewModel
**Impact:** Low - Marketing feature
**File:** `BuyAppViewModel.kt:1531`

**Recommendation:**
- Design promo code data model
- Add validation logic
- Apply discount to order total

#### 4.3.3 Pull-to-Refresh
**Current State:** Mentioned in TODOs for Order History
**Impact:** Low - UX polish

**Recommendation:**
- Add SwipeRefresh to list screens
- Implement refresh logic in ViewModels

### 4.4 Technical Debt

#### 4.4.1 ViewModel Size
**Issue:** `BuyAppViewModel.kt` is ~1600 lines
**Risk:** Maintenance difficulty, testing complexity

**Recommendation:**
- Extract feature-specific logic into use cases
- Consider splitting into feature ViewModels
- Keep unified action pattern but delegate handling

#### 4.4.2 Error Handling Consistency
**Issue:** Some screens show errors, others silently fail
**Files:** `OrderDetailScreen.kt:105`, various screens

**Recommendation:**
- Create unified error handling composable
- Standardize error state in all ViewModels
- Add retry mechanisms consistently

#### 4.4.3 Test Coverage
**Current State:** Buyer stories exist for integration testing
**Gap:** Unit tests for ViewModels and repositories

**Recommendation:**
- Add unit tests for business logic
- Mock repositories for ViewModel tests
- Add UI tests for critical flows

---

## 5. Feature Comparison Matrix

| Feature | Buy | Sell | Priority to Complete |
|---------|:---:|:----:|---------------------|
| Product Search | No | N/A | High |
| Product Detail View | Partial | No | High |
| Favorites | ✅ Yes | N/A | Complete |
| Push Notifications | No | No | Medium |
| Revenue Dashboard | N/A | No | Medium |
| Market Management | N/A | Partial | Medium |
| Password Reset | ✅ Yes | ✅ Yes | Complete |
| iOS Image Picker | No | No | High (blocks iOS) |
| iOS Document Picker | N/A | No | Medium |
| Promo Codes | No | N/A | Low |
| Twitter Sign-In | No | No | Low |
| Product Edit | N/A | Partial | Medium |

---

## 6. Recommended Roadmap

### Phase 1: Core Completion
1. Implement product search (Buy)
2. Add product detail view (Both)
3. Fix revenue calculation (Sell)
4. Complete iOS image picker

### Phase 2: User Experience
1. Push notifications (Both)
2. Password reset flow
3. Favorites persistence (Buy)
4. Pull-to-refresh on lists

### Phase 3: Business Features
1. Market management completion (Sell)
2. Product edit functionality (Sell)
3. Promo codes (Buy)
4. Advanced filters (Buy)

### Phase 4: Polish
1. Error handling standardization
2. ViewModel refactoring
3. Test coverage improvement
4. Performance optimization

---

## 7. Conclusion

Newverse has a solid foundation with well-architected code, proper separation of concerns, and comprehensive feature coverage for a marketplace app. The Buy app is more mature for end-users, while the Sell app has strong product management capabilities.

**Key Strengths:**
- Clean MVVM architecture with action pattern
- Cross-platform code sharing via KMP
- Sophisticated order management with deadlines
- Bulk import capability for sellers

**Critical Gaps:**
- Search functionality (high user impact)
- iOS platform features (blocks release)
- Product detail views (basic UX expectation)

Addressing the high-priority items would significantly improve user experience and enable iOS release.
