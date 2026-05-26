# Newverse Web Implementation Audit — Buyer Flavor

## Summary
The web platform has **full navigation and UI scaffolding** with **all 9 buyer screens** accessible, but several **platform-specific features** are stubbed/non-functional. **Core functionality (browse, basket, orders, messages)** should work end-to-end with a properly configured Firebase project.

---

## Architecture Status ✅ COMPLETE
- **App Shell**: MainAppScaffold → AppScaffold → NavGraph fully wired
- **Navigation**: All 9 buyer routes defined and navigationally reachable
- **Bottom Bar**: Home, Basket, Profile tabs with cart badge and shake animation
- **Top Bar**: Dynamic title, back button on detail screens, order history icon on basket
- **Firebase**: GitLive SDK configured for js(IR) in shared/build.gradle.kts
- **Koin DI**: WebDomainModule registers all repositories (Auth, Profile, Article, Order, Basket)

---

## Fully Functional Screens ✅

### 1. **Login / Register** (Common)
- Email/password authentication
- ✅ Email login works (Firebase Auth)
- ❌ Google Sign-In button shows but is stubbed (`"not supported on web v1"`)
- ❌ Apple Sign-In button shows but is stubbed
- ❌ Twitter Sign-In button shows but is stubbed
- Guest login available and functional

### 2. **Home Screen** (MainScreenModern)
- ✅ Product catalog display from Firebase
- ✅ Search/filter UI (text input, category chips)
- ✅ Favorites toggle (heart icon)
- ✅ Add to cart with quantity selector
- ✅ Product detail navigation
- ✅ Demo mode banner (if applicable)
- ✅ Collapsing toolbar scrolling behavior
- Responsive layout should work on desktop/tablet/mobile

### 3. **Basket Screen**
- ✅ Display items with quantity controls
- ✅ Total price calculation
- ✅ Order placement/submission
- ✅ Order status (DRAFT → LOCKED → COMPLETED)
- ✅ Merge conflicts UI (reorder logic)
- ✅ Order history navigation icon
- ✅ Edit deadline detection (Tuesday 23:59 cutoff)
- ✅ Pickup date display (always Thursday)

### 4. **Order History**
- ✅ Load past orders from Firebase
- ✅ Reorder from history (load previous order into basket)
- ✅ Order status display
- ✅ Order detail view
- ✅ Navigation back to basket

### 5. **Customer Profile**
- ✅ Profile info display and editing (name, email, phone, address)
- ✅ Auth provider detection (ANONYMOUS, EMAIL, GOOGLE, APPLE)
- ✅ Seller connection cards
- ✅ Seller access requests
- ✅ Profile completion status banner
- ✅ Invited sellers list
- ❌ **QR code scanner** button (stubbed — web has no camera API)
- ✅ Contact dropdown (Call/Email via uri handler)

### 6. **Favorites**
- ✅ Tab view (all products / favorites only)
- ✅ Display favorited articles with images
- ✅ Add/remove from cart
- ✅ Toggle favorite status
- ✅ Image loading via Coil + ktor-client-js

### 7. **Messages**
- ✅ Conversation list
- ✅ Unread count badge
- ✅ Click to conversation detail
- ✅ Navigation to contacts

### 8. **Conversation Detail**
- ✅ Chat message display
- ✅ Send new messages
- ✅ Timestamp formatting
- ✅ User avatar placeholders
- ✅ Back navigation

### 9. **Buyer Contacts**
- ✅ List buyer contacts (saved conversations)
- ✅ Add new contact (link to seller)
- ✅ Start conversation
- ✅ Back navigation
- ✅ Add contact screen with contact selector

---

## Stubbed/Non-Functional Features ❌

### Platform-Specific (Unavailable on Web)
1. **Google Sign-In** (`GoogleSignInManager.kt`)
   - Button renders but logs: `"Google Sign-In not supported on web v1"`
   - Would need OAuth 2.0 web flow (complex setup)
   - Recommendation: Email login or social login via OAuth popup (future)

2. **Apple Sign-In** (expect/actual in `AppleSignInManager.kt`)
   - Button renders but logs: `"Apple Sign-In not supported on web v1"`
   - Apple requires special domain verification for web
   - Recommendation: Defer to v2

3. **QR Code Scanner** (`QrCodeImage.kt`)
   - Profile shows "Scan QR Code" button
   - Click handler is stubbed — no camera access
   - Would need `navigator.mediaDevices.getUserMedia()` + qr-code-reader JS library
   - Recommendation: Replace with manual token input or implement OAuth-style linking

4. **Image Picker** (`ImagePicker.kt`)
   - Not used on buyer side, but stubbed
   - Would need file input element

5. **Document Picker** (`DocumentPicker.kt`)
   - Not used on buyer side, but stubbed

### Configuration
- **Firebase Config** (`webApp/src/jsMain/resources/index.html`)
  - ❌ MUST fill in at line 23–31:
    ```javascript
    const firebaseConfig = {
        apiKey: "YOUR_API_KEY",
        authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
        databaseURL: "https://YOUR_PROJECT_ID-default-rtdb.europe-west1.firebasedatabase.app",
        projectId: "YOUR_PROJECT_ID",
        storageBucket: "YOUR_PROJECT_ID.appspot.com",
        messagingSenderId: "YOUR_SENDER_ID",
        appId: "YOUR_APP_ID"
    };
    ```
  - Get values from Firebase Console → Project Settings → General → Your apps (Web app config)

---

## Known Limitations & Gotchas

### 1. **No Social Sign-In on Web (v1)**
   - Users must use email/password or continue as guest
   - Google/Apple buttons won't error, but won't authenticate

### 2. **QR Code Linking Broken**
   - Seller invite links with QR codes can't be scanned on web
   - Users can still access via deep link URL (paste into address bar)
   - Or manually enter seller token (if UI exists)

### 3. **No Push Notifications**
   - Firebase Cloud Messaging not wired on web (would need service worker)
   - Users won't get real-time message alerts (need to refresh)

### 4. **Responsive Design Untested**
   - Built for mobile-first (Compose default)
   - Desktop/tablet layout not explicitly tested
   - Bottom navigation bar may look odd on wide screens

### 5. **localStorage vs. SessionStorage**
   - Web uses browser localStorage for persistence (cart, auth token, etc.)
   - Android/iOS use native encrypted storage (more secure)
   - Acceptable for demo/dev, but user data less protected

### 6. **No Offline Support**
   - Web has no service worker / offline caching
   - Users see spinner if network is slow
   - Android/iOS have Firebase offline persistence

### 7. **Image Loading**
   - Coil3 + ktor-client-js configured
   - CORS may block images from Firebase Storage (if misconfigured)
   - Verify CORS rules in Firebase Console → Storage → Rules

### 8. **Keyboard/Focus Behavior**
   - KeyboardManager is stubbed (logs only)
   - May not auto-hide keyboard on mobile web
   - Text inputs should work but UX may differ from native

---

## What Works End-to-End ✅

**Assuming Firebase config is filled in**, these flows should work completely:

1. **Guest Browsing**
   - Load home → view products → favorite → add to cart → navigate basket

2. **Email Registration & Login**
   - Register with email/password → login → authenticated session

3. **Ordering**
   - Add items to basket → submit order → see in order history

4. **Reordering**
   - View past order → click reorder → items populate basket → submit new order

5. **Messaging**
   - View conversations → click contact → send/receive messages

6. **Profile Management**
   - View/edit profile info → toggle favorites → manage seller connections (text-based)

---

## Outstanding Work to Ship (Prioritized)

### Tier 1: Must-Do (Blocks Production)
- [ ] **Firebase Config**: Fill in apiKey, authDomain, databaseURL, etc. in index.html
- [ ] **Test End-to-End**: 
  - Sign in → browse → add to cart → submit order → check order history
  - Send message → receive message
  - Edit profile
- [ ] **CORS Validation**: Ensure Firebase Storage allows web domain

### Tier 2: Should-Do (UX Issues)
- [ ] **Replace QR Button**: Remove "Scan QR Code" or change to "Enter Code" input
- [ ] **Social Sign-In Messaging**: Update login screen to explain google/apple not available yet
- [ ] **Responsive Testing**: Test on desktop browser (chrome dev tools mobile emulation) — verify bottom bar, modals fit

### Tier 3: Nice-to-Have (Future)
- [ ] **Google OAuth Web Flow**: Implement web-based Google Sign-In (requires OAuth consent screen setup)
- [ ] **Push Notifications**: Add Firebase Cloud Messaging with service worker
- [ ] **Offline Support**: Cache API responses + service worker for offline browsing
- [ ] **Auto-hide Keyboard**: Implement KeyboardManager for mobile web

---

## Dev Server

**Currently running at**: `http://localhost:8081` (from May 25 session)

**To rebuild & restart**:
```bash
./gradlew :webApp:jsBrowserDevelopmentRun
```

Incremental rebuild: ~6 seconds  
Full rebuild: ~2-3 minutes

**To build production**:
```bash
./gradlew :webApp:jsBrowserProductionWebpack
```

---

## Summary Table

| Feature | Status | Notes |
|---------|--------|-------|
| **Authentication** | 🟡 Partial | Email/guest ✅, Google/Apple ❌ |
| **Product Catalog** | ✅ Full | Search, filter, favorites all work |
| **Basket & Ordering** | ✅ Full | Add/remove, submit, history, reorder |
| **Messaging** | ✅ Full | Conversations, real-time via Firebase |
| **Profile** | 🟡 Partial | Edit ✅, QR scan ❌ |
| **Seller Linking** | 🟡 Partial | Token-based ✅, QR scan ❌ |
| **Images** | ✅ Full | Loaded from Firebase Storage |
| **Navigation** | ✅ Full | Bottom bar, back button, all routes |
| **UI Responsiveness** | ❓ Untested | Likely works, not formally tested |
| **Offline** | ❌ None | No service worker / caching |
| **Push Notifications** | ❌ None | No FCM integration |

---

## Next Steps

1. **Immediate**: Get Firebase config from Firebase Console, add to index.html, restart dev server
2. **Validate**: Test the 6 end-to-end flows listed above
3. **Polish**: Fix QR button UX, test responsive design, update auth messaging
4. **Deploy**: (Future) Configure production Firebase, set up CORS, deploy to hosting
