# Hardcoded Strings Localization Audit

## Project: Newverse KMP
**Date:** 2025-11-11
**Purpose:** Complete audit of hardcoded strings in UI screens for localization task

---

## Summary

This document contains a comprehensive audit of all hardcoded strings found in the screen files within `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/`. The strings are categorized by screen, with indication of which strings require parameterization for dynamic content.

**Total Screen Files Analyzed:** 12
**Total ViewModel Files Analyzed:** 3

---

## 1. SELL MODULE SCREENS

### 1.1 OrdersScreen.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/sell/OrdersScreen.kt`

#### Hardcoded Strings:

| Line | String | Context | Type | Needs Parameterization |
|------|--------|---------|------|------------------------|
| 24 | "Manage Orders" | Screen title | Static | No |
| 67 | "Order #" | Order card label prefix | Static | Yes - concatenated with orderId |
| 83 | " items" | Item count suffix | Static | Yes - concatenated with itemCount |
| 90 | "Note: " | Message prefix | Static | Yes - concatenated with message |

#### Parameterized Strings:
```kotlin
// Line 67: "Order #${orderId.take(8)}"
// Suggested resource: order_id_format = "Order #%s"

// Line 71: "${total.formatPrice()}€"
// Suggested resource: price_with_euro = "%s€"

// Line 83: "$itemCount items"
// Suggested resource: item_count_format = "%d items"

// Line 90: "Note: $message"
// Suggested resource: note_prefix_format = "Note: %s"
```

---

### 1.2 CreateProductScreen.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/sell/CreateProductScreen.kt`

#### Hardcoded Strings:

| Line | String | Context | Type | Needs Parameterization |
|------|--------|---------|------|------------------------|
| 23 | "Create Product" | Screen title | Static | No |
| 31 | "Product Name" | Text field label | Static | No |
| 38 | "Price" | Text field label | Static | No |
| 40 | "$" | Price prefix | Static | No |
| 46 | "Description" | Text field label | Static | No |
| 57 | "Create Product" | Button text | Static | No |
| 64 | "Cancel" | Button text | Static | No |

---

### 1.3 SellerProfileScreen.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/sell/SellerProfileScreen.kt`

#### Hardcoded Strings:

| Line | String | Context | Type | Needs Parameterization |
|------|--------|---------|------|------------------------|
| 18 | "Seller Profile" | Screen title | Static | No |
| 34 | "Farm Fresh Market" | Seller name (placeholder) | Static | No |
| 39 | "seller@farmfresh.com" | Email (placeholder) | Static | No |
| 47 | "Business Stats" | Section header | Static | No |
| 55 | "Products" | Stat card label | Static | No |
| 55 | "12" | Stat value (placeholder) | Static | No |
| 56 | "Orders" | Stat card label | Static | No |
| 56 | "45" | Stat value (placeholder) | Static | No |
| 60 | "Account Settings" | Section header | Static | No |
| 69 | "Edit Profile" | Action button text | Static | No |
| 80 | "Payment Settings" | Action button text | Static | No |
| 92 | "Sign Out" | Button text | Static | No |

---

### 1.4 PickDayScreen.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/sell/PickDayScreen.kt`

#### Hardcoded Strings:

| Line | String | Context | Type | Needs Parameterization |
|------|--------|---------|------|------------------------|
| 16 | "Monday" | Day of week | Static | No |
| 16 | "Tuesday" | Day of week | Static | No |
| 16 | "Wednesday" | Day of week | Static | No |
| 16 | "Thursday" | Day of week | Static | No |
| 16 | "Friday" | Day of week | Static | No |
| 16 | "Saturday" | Day of week | Static | No |
| 16 | "Sunday" | Day of week | Static | No |
| 26 | "Pick Delivery Days" | Screen title | Static | No |
| 34 | "Select the days you're available for deliveries" | Description text | Static | No |
| 66 | "Save Delivery Days" | Button text | Static | No |

---

### 1.5 OverviewScreen.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/sell/OverviewScreen.kt`

#### Hardcoded Strings:

| Line | String | Context | Type | Needs Parameterization |
|------|--------|---------|------|------------------------|
| 24 | "Product Overview" | Screen title | Static | No |
| 37 | "Total Products" | Stat card title | Static | No |
| 42 | "Active Orders" | Stat card title | Static | No |
| 51 | "Your Products" | Section header | Static | No |

---

## 2. BUY MODULE SCREENS

### 2.1 BasketScreen.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/BasketScreen.kt`

#### Hardcoded Strings:

| Line | String | Context | Type | Needs Parameterization |
|------|--------|---------|------|------------------------|
| 48 | "Shopping Basket" | Screen title | Static | No |
| 67 | "Your basket is empty" | Empty state title | Static | No |
| 73 | "Add items from the Products screen" | Empty state description | Static | No |
| 110 | "Total:" | Total price label | Static | No |
| 127 | "Processing..." | Loading button text | Static | No |
| 127 | "Proceed to Checkout" | Button text | Static | No |
| 188 | "Remove" | Action button text | Static | No |

#### Parameterized Strings:
```kotlin
// Line 114: "${state.total.formatPrice()}€"
// Suggested resource: total_price_format = "%s€"

// Line 163: "${price.formatPrice()}€/$unit"
// Suggested resource: price_per_unit_format = "%s€/%s"

// Line 170: "x ${quantity.formatPrice()}"
// Suggested resource: quantity_format = "x %s"

// Line 174: "${(price * quantity).formatPrice()}€"
// Suggested resource: item_total_format = "%s€"
```

---

### 2.2 ProductsScreen.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/ProductsScreen.kt`

#### Hardcoded Strings:

| Line | String | Context | Type | Needs Parameterization |
|------|--------|---------|------|------------------------|
| 48 | "Browse Products" | Screen title | Static | No |
| 80 | "Retry" | Button text | Static | No |
| 92 | "No products available" | Empty state message | Static | No |

---

### 2.3 CustomerProfileScreenModern.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/CustomerProfileScreenModern.kt`

**Note:** This file uses stringResource() for localization (already localized!)

#### Already Localized String Resource Keys:
- `Res.string.default_market`
- `Res.string.profile_new_customer`
- `Res.string.profile_no_email`
- `Res.string.profile_verified`
- `Res.string.profile_member_since`
- `Res.string.section_personal_info`
- `Res.string.label_display_name`
- `Res.string.label_email`
- `Res.string.label_phone`
- `Res.string.section_delivery_preferences`
- `Res.string.label_marketplace`
- `Res.string.label_pickup_time`
- `Res.string.pickup_time_format`
- `Res.string.section_notifications`
- `Res.string.notification_order_updates`
- `Res.string.notification_push_desc`
- `Res.string.notification_newsletter`
- `Res.string.notification_newsletter_desc`
- `Res.string.membership_regular`
- `Res.string.membership_discount`
- `Res.string.quick_actions_title`
- `Res.string.action_orders`
- `Res.string.action_favorites`
- `Res.string.action_payment`
- `Res.string.action_help`
- `Res.string.button_cancel`
- `Res.string.button_save`
- `Res.string.button_confirm`
- `Res.string.dialog_save_title`
- `Res.string.dialog_save_message`

---

## 3. COMMON MODULE SCREENS

### 3.1 LoginScreen.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/common/LoginScreen.kt`

#### Hardcoded Strings:

| Line | String | Context | Type | Needs Parameterization |
|------|--------|---------|------|------------------------|
| 40 | "Email is required" | Validation error | Static | No |
| 41 | "Invalid email format" | Validation error | Static | No |
| 50 | "Password is required" | Validation error | Static | No |
| 51 | "Password must be at least 6 characters" | Validation error | Static | No |
| 71 | "🌿" | Logo emoji | Static | No |
| 79 | "Welcome Back" | Screen title | Static | No |
| 85 | "Sign in to continue" | Subtitle | Static | No |
| 98 | "Email" | Text field label | Static | No |
| 99 | "your@email.com" | Placeholder | Static | No |
| 117 | "Password" | Text field label | Static | No |
| 118 | "Enter your password" | Placeholder | Static | No |
| 131 | "Hide" | Toggle button text | Static | No |
| 131 | "Show" | Toggle button text | Static | No |
| 146 | "Forgot Password?" | Link text | Static | No |
| 172 | "Sign In" | Button text | Static | No |
| 185 | " OR " | Divider text | Static | No |
| 208 | "G" | Google logo placeholder | Static | No |
| 214 | "Sign in with Google" | Button text | Static | No |
| 234 | "𝕏" | Twitter logo | Static | No |
| 239 | "Sign in with Twitter" | Button text | Static | No |
| 255 | "Continue as Guest" | Button text | Static | No |
| 265 | "Don't have an account? " | Signup prompt | Static | No |
| 276 | "Sign Up" | Link text | Static | No |
| 308 | "Login successful! Redirecting..." | Success message | Static | No |

---

### 3.2 RegisterScreen.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/common/RegisterScreen.kt`

#### Hardcoded Strings:

| Line | String | Context | Type | Needs Parameterization |
|------|--------|---------|------|------------------------|
| 48 | "Name is required" | Validation error | Static | No |
| 49 | "Name must be at least 2 characters" | Validation error | Static | No |
| 57 | "Email is required" | Validation error | Static | No |
| 58 | "Invalid email format" | Validation error | Static | No |
| 66 | "Password is required" | Validation error | Static | No |
| 67 | "Password must be at least 6 characters" | Validation error | Static | No |
| 68 | "Password must contain at least one number" | Validation error | Static | No |
| 69 | "Password must contain at least one letter" | Validation error | Static | No |
| 77 | "Please confirm your password" | Validation error | Static | No |
| 78 | "Passwords do not match" | Validation error | Static | No |
| 85 | "You must accept the terms and conditions" | Validation error | Static | No |
| 102 | "🌿" | Logo emoji | Static | No |
| 110 | "Create Account" | Screen title | Static | No |
| 116 | "Join our organic marketplace" | Subtitle | Static | No |
| 129 | "Full Name" | Text field label | Static | No |
| 130 | "John Doe" | Placeholder | Static | No |
| 148 | "Email" | Text field label | Static | No |
| 149 | "your@email.com" | Placeholder | Static | No |
| 171 | "Password" | Text field label | Static | No |
| 172 | "At least 6 characters" | Placeholder | Static | No |
| 179 | "Must contain letters and numbers" | Helper text | Static | No |
| 187 | "Hide" | Toggle button text | Static | No |
| 187 | "Show" | Toggle button text | Static | No |
| 201 | "Confirm Password" | Text field label | Static | No |
| 202 | "Re-enter your password" | Placeholder | Static | No |
| 214 | "Passwords match" | Content description | Static | No |
| 222 | "Hide" | Toggle button text | Static | No |
| 222 | "Show" | Toggle button text | Static | No |
| 245 | "I agree to the Terms of Service and Privacy Policy" | Checkbox label | Static | No |
| 284 | "Create Account" | Button text | Static | No |
| 297 | " OR " | Divider text | Static | No |
| 311 | "Already have an account? " | Login prompt | Static | No |
| 321 | "Sign In" | Link text | Static | No |
| 364 | "Account created successfully!" | Success message | Static | No |
| 369 | "Please check your email to verify your account." | Success description | Static | No |

---

### 3.3 AboutScreenModern.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/common/AboutScreenModern.kt`

#### Hardcoded Strings (German):

| Line | String | Context | Language | Type | Needs Parameterization |
|------|--------|---------|----------|------|------------------------|
| 148 | "BODENSCHÄTZE" | App name | German | Static | No |
| 155 | "Bio • Regional • Frisch" | Tagline | German | Static | No |
| 168 | "Seit 2020" | Established year badge | German | Static | No |
| 211 | "Kontakt" | Section header | German | Static | No |
| 233 | "0172 - 46 23 741" | Phone number | German | Static | No |
| 279 | "bodenschaetze@posteo.de" | Email | German | Static | No |
| 315 | "Ökomarkt im Hansaviertel" | Location | German | Static | No |
| 355 | "Impressum" | Section header | German | Static | No |
| 365 | "Inhaber" | Label | German | Static | No |
| 366 | "Eric Dehn" | Owner name | German | Static | No |
| 372 | "Anschrift" | Label | German | Static | No |
| 373 | "Neue Gartenstraße\n15517 Fürstenwalde / Spree" | Address | German | Static | No |
| 379 | "Vertreten durch" | Label | German | Static | No |
| 380 | "Eric Dehn" | Representative name | German | Static | No |
| 419 | "Datenschutz" | Section header | German | Static | No |
| 429 | "Wir nehmen den Schutz aller persönlichen Daten sehr ernst." | Privacy intro | German | Static | No |
| 438 | "Alle personenbezogenen Informationen werden vertraulich und gemäß den geltenden Datenschutzbestimmungen behandelt. Ihre Daten werden ausschließlich zur Bestellabwicklung und Kommunikation verwendet." | Privacy description | German | Static | No |
| 459 | "Vollständige Datenschutzerklärung" | Button text | German | Static | No |
| 491 | "Unsere Mission" | Section header | German | Static | No |
| 501 | "Wir bringen frische, biologische und regionale Lebensmittel direkt vom Feld zu Ihnen nach Hause. Für eine nachhaltige Zukunft und gesunde Ernährung." | Mission statement | German | Static | No |

---

### 3.4 SplashScreen.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/SplashScreen.kt`

#### Hardcoded Strings (German):

| Line | String | Context | Language | Type | Needs Parameterization |
|------|--------|---------|----------|------|------------------------|
| 72 | "🥕" | App icon emoji | Static | Static | No |
| 81 | "BODENSCHÄTZE" | App name | German | Static | No |
| 91 | "Frisch vom Markt" | Tagline | German | Static | No |
| 115 | "Loading" | Loading text prefix | English | Static | Yes - concatenated with dots |

#### Parameterized Strings:
```kotlin
// Line 109: initializationStep + dots
// Suggested resource: loading_with_step = "%s..."

// Line 115: "Loading$dots"
// Suggested resource: loading_generic = "Loading..."
```

---

## 4. VIEW MODELS

### 4.1 LoginViewModel.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/common/LoginViewModel.kt`

#### Hardcoded Strings:

| Line | String | Context | Type | Needs Parameterization |
|------|--------|---------|------|------------------------|
| 34 | "Email and password are required" | Validation error | Static | No |
| 48 | "Login failed" | Default error message | Static | No |
| 55 | "Email and password are required" | Validation error | Static | No |
| 67 | "Sign up failed" | Default error message | Static | No |

---

### 4.2 ProductsViewModel.kt
**File Path:** `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/ProductsViewModel.kt`

#### Parameterized Strings:
```kotlin
// Line 65: "Failed to load products: ${e.message}"
// Suggested resource: error_load_products = "Failed to load products: %s"
```

---

## 5. STRING CATEGORIZATION

### 5.1 Static Strings (No Parameters)

#### Navigation & Titles:
- Screen titles: "Manage Orders", "Create Product", "Seller Profile", "Shopping Basket", "Browse Products", "Welcome Back", "Create Account", etc.
- Section headers: "Business Stats", "Account Settings", "Personal Information", "Delivery Preferences", etc.

#### Labels & Form Fields:
- Input labels: "Email", "Password", "Full Name", "Product Name", "Price", "Description"
- Placeholders: "your@email.com", "Enter your password", "John Doe"

#### Buttons & Actions:
- Action buttons: "Sign In", "Sign Up", "Cancel", "Save", "Retry", "Remove", "Proceed to Checkout"
- Link texts: "Forgot Password?", "Sign in with Google", "Continue as Guest"

#### Messages:
- Validation errors: "Email is required", "Password must be at least 6 characters", "Passwords do not match"
- Empty states: "Your basket is empty", "No products available"
- Success messages: "Account created successfully!", "Login successful! Redirecting..."

#### Days of Week:
- "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"

#### German Static Strings:
- "BODENSCHÄTZE", "Frisch vom Markt", "Bio • Regional • Frisch", "Seit 2020"
- "Kontakt", "Impressum", "Datenschutz", "Unsere Mission"
- "Inhaber", "Anschrift", "Vertreten durch"

---

### 5.2 Parameterized Strings (Require Variables)

#### Format Strings with Numeric Parameters:
```kotlin
// Price formats
"${price.formatPrice()}€"                    // price_format = "%s€"
"${price.formatPrice()}€/$unit"             // price_per_unit_format = "%s€/%s"
"x ${quantity.formatPrice()}"               // quantity_format = "x %s"

// Count formats
"$itemCount items"                          // item_count_format = "%d items"

// Total formats
"Total: ${total.formatPrice()}€"            // total_format = "Total: %s€"
```

#### Format Strings with String Parameters:
```kotlin
// Order/ID formats
"Order #${orderId.take(8)}"                 // order_id_format = "Order #%s"

// Note/Message formats
"Note: $message"                            // note_format = "Note: %s"

// Time formats
"${pickupTime}"                             // pickup_time_format = "%s"

// Error messages
"Failed to load products: ${e.message}"     // error_load_products = "Failed to load products: %s"

// Loading states
"Loading$dots"                              // loading_format = "Loading%s"
initializationStep + dots                   // loading_step_format = "%s..."
```

#### Conditional Strings:
```kotlin
// Button states
if (state.isCheckingOut) "Processing..." else "Proceed to Checkout"
if (passwordVisible) "Hide" else "Show"
```

---

## 6. LOCALIZATION STRATEGY RECOMMENDATIONS

### 6.1 Already Implemented
✅ **CustomerProfileScreenModern.kt** - Fully localized using `stringResource()` and `Res.string.*` keys

### 6.2 Priority 1 (High Priority - User-Facing Text)
1. **LoginScreen.kt** - Authentication flow
2. **RegisterScreen.kt** - User onboarding
3. **BasketScreen.kt** - Checkout process
4. **ProductsScreen.kt** - Product browsing
5. **SplashScreen.kt** - First user interaction

### 6.3 Priority 2 (Medium Priority - Feature Screens)
1. **OrdersScreen.kt** - Order management
2. **CreateProductScreen.kt** - Product creation
3. **SellerProfileScreen.kt** - Profile management
4. **PickDayScreen.kt** - Delivery scheduling
5. **OverviewScreen.kt** - Dashboard

### 6.4 Priority 3 (Low Priority - About/Info)
1. **AboutScreenModern.kt** - Company information (mostly static, German-specific)

### 6.5 ViewModels
- **LoginViewModel.kt** - Error messages
- **ProductsViewModel.kt** - Error messages
- **OrdersViewModel.kt** - (No hardcoded strings found)

---

## 7. RESOURCE FILE STRUCTURE SUGGESTION

```xml
<!-- Common strings -->
<string name="app_name">BODENSCHÄTZE</string>
<string name="app_tagline">Frisch vom Markt</string>

<!-- Navigation -->
<string name="nav_products">Browse Products</string>
<string name="nav_basket">Shopping Basket</string>
<string name="nav_orders">Manage Orders</string>
<string name="nav_profile">Profile</string>

<!-- Form Labels -->
<string name="label_email">Email</string>
<string name="label_password">Password</string>
<string name="label_name">Full Name</string>
<string name="label_phone">Phone</string>

<!-- Buttons -->
<string name="button_sign_in">Sign In</string>
<string name="button_sign_up">Sign Up</string>
<string name="button_cancel">Cancel</string>
<string name="button_save">Save</string>
<string name="button_retry">Retry</string>

<!-- Error Messages -->
<string name="error_email_required">Email is required</string>
<string name="error_email_invalid">Invalid email format</string>
<string name="error_password_required">Password is required</string>
<string name="error_password_length">Password must be at least 6 characters</string>

<!-- Format Strings -->
<string name="format_price_euro">%s€</string>
<string name="format_price_per_unit">%s€/%s</string>
<string name="format_item_count">%d items</string>
<string name="format_order_id">Order #%s</string>
<string name="format_note">Note: %s</string>
```

---

## 8. MIGRATION CHECKLIST

- [ ] Create string resource files for all languages (English, German)
- [ ] Replace all hardcoded strings in Priority 1 screens
- [ ] Replace all hardcoded strings in Priority 2 screens
- [ ] Replace all hardcoded strings in Priority 3 screens
- [ ] Update ViewModels with localized error messages
- [ ] Test all screens with both English and German locales
- [ ] Verify parameter substitution works correctly
- [ ] Add missing string resources for date/time formatting
- [ ] Review RTL layout considerations
- [ ] Update documentation with localization guidelines

---

## 9. NOTES

1. **CustomerProfileScreenModern.kt** serves as the best practice reference - it already uses `stringResource()` properly
2. **AboutScreenModern.kt** contains mostly German text and business-specific information that may need special handling
3. **SplashScreen.kt** has a mix of German ("BODENSCHÄTZE", "Frisch vom Markt") and English ("Loading")
4. Days of week in **PickDayScreen.kt** should use platform-specific date formatters for proper localization
5. Emojis (🌿, 🥕) are used as visual elements and may or may not need to be in resource files
6. Price formatting with € symbol should consider locale-specific currency formatting
7. Error messages in ViewModels should be localized for user-facing errors but kept in English for debugging logs

---

**End of Audit Report**
