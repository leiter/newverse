# Newverse Feature Status

**Last Updated:** 2024-10-27

*Note: This file replaces the previous TODO.md. The TODO.md file in the root directory is now obsolete and should be deleted.*

This document outlines the current implementation status of core features and a roadmap for future development.

---

## Implemented Features

### Core Infrastructure
- ✅ Kotlin Multiplatform setup with Android & iOS targets
- ✅ Compose Multiplatform for shared UI
- ✅ Koin for dependency injection
- ✅ MVVM architecture with refactored ViewModels
- ✅ Clean Architecture principles
- ✅ Complete Material3 theme system (light/dark themes)
- ✅ Type-safe, flavor-aware navigation system with modal drawer

### iOS Platform Features
- ✅ Image Picker (photo library and camera)
- ✅ Google Sign-In
- ✅ Document Picker
- ✅ Native Sharing
- ✅ QR Code Scanning

### Buyer Features (Buy App)
- ✅ User authentication (Google, Apple, Email, Anonymous)
- ✅ Product browsing and real-time search
- ✅ Product detail view
- ✅ Shopping basket and checkout flow
- ✅ Order editing with deadlines
- ✅ Merge conflict resolution for existing orders
- ✅ Order history
- ✅ Favorites persistence
- ✅ Password reset flow

### Seller Features (Sell App)
- ✅ User authentication
- ✅ Product and order overview with revenue calculation
- ✅ Market management (CRUD operations for market locations)
- ✅ Customer management (approve, block, unblock buyers)
- ✅ QR code / deep link based invitations for buyers

---

## Roadmap & Future Work

The following features are planned but not yet implemented, or are only partially complete.

### High Priority: User Experience
- **Push Notifications**
  - **Status:** Not Implemented
  - **Tasks:**
    - Set up Firebase Cloud Messaging (FCM) for Android and iOS.
    - Implement notifications for order status changes (buyer) and new orders (seller).

- **Pull-to-Refresh**
  - **Status:** Not Implemented
  - **Tasks:**
    - Add pull-to-refresh functionality to the Order History screen.
    - Add pull-to-refresh to the main Products list screen.

- **Error Handling Standardization**
  - **Status:** Partially Implemented
  - **Issue:** Error display is inconsistent across different screens.
  - **Tasks:**
    - Create a unified error component (e.g., a full-screen error message with a retry button).
    - Standardize all ViewModels to use a single error state pattern (e.g., `AsyncState.Error` or a global dialog).

### Medium Priority: Business Features
- **Promo Codes (Buy App)**
  - **Status:** Stubbed
  - **Description:** The UI action and state holders exist, but the logic is not implemented.
  - **Tasks:**
    - Design and implement a data model for promo codes.
    - Add validation logic in the `BuyAppViewModel`.
    - Apply discounts to the order total during checkout.

### Low Priority & On Hold
- **Twitter Sign-In**
  - **Status:** Stubbed
  - **Description:** The UI button exists and is wired up to the ViewModel, but the native platform implementation is missing on both Android and iOS.
  - **Priority:** Low - needs evaluation of whether it's required for the target audience.
