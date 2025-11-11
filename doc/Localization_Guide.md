# Localization Guide for Newverse KMP

## Overview
Localization has been implemented using **Compose Multiplatform Resources** which works across Android, iOS, and other platforms.

## Structure

### Resource Files Location
```
shared/src/commonMain/composeResources/
├── drawable/           # Images and drawable resources
├── values/            # Default strings (German)
│   └── strings.xml
└── values-en/         # English translations
    └── strings.xml
```

### String Resources Format
Resources are defined in XML files following Android's resource format:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="key_name">Value</string>
    <string name="formatted_string">Hello %s!</string>
</resources>
```

## Usage in Code

### Import Required Dependencies
```kotlin
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
```

### Simple Strings
```kotlin
@Composable
fun MyScreen() {
    Text(text = stringResource(Res.string.profile_new_customer))
}
```

### Formatted Strings (with parameters)
```kotlin
@Composable
fun MemberBadge(year: String) {
    Text(text = stringResource(Res.string.profile_member_since, year))
}
```

### Using in State Variables
```kotlin
@Composable
fun MyScreen() {
    val defaultMarket = stringResource(Res.string.default_market)
    var selectedMarket by remember { mutableStateOf(defaultMarket) }
}
```

## Available Locales

### German (Default)
- File: `values/strings.xml`
- Automatically used when system language is German or as fallback

### English
- File: `values-en/strings.xml`
- Used when system language is set to English

## Adding New Languages

### 1. Create New Language Directory
```bash
mkdir -p shared/src/commonMain/composeResources/values-<language-code>
```

Examples:
- Spanish: `values-es`
- French: `values-fr`
- Italian: `values-it`

### 2. Copy and Translate
```bash
cp shared/src/commonMain/composeResources/values/strings.xml \
   shared/src/commonMain/composeResources/values-es/strings.xml
```

Then translate all values in the new file.

### 3. Build and Test
```bash
./gradlew :shared:generateComposeResClass
./gradlew :androidApp:assembleBuyDebug
```

## CustomerProfileScreenModern Strings

All strings from CustomerProfileScreenModern have been localized:

### Personal Information
- `profile_new_customer` - "Neuer Kunde" / "New Customer"
- `profile_no_email` - "Keine E-Mail" / "No Email"
- `profile_verified` - "Verifiziert" / "Verified"
- `profile_member_since` - "Kunde seit %s" / "Member since %s"
- `section_personal_info` - "Persönliche Daten" / "Personal Information"
- `label_display_name` - "Anzeigename" / "Display Name"
- `label_email` - "E-Mail-Adresse" / "Email Address"
- `label_phone` - "Telefonnummer" / "Phone Number"

### Delivery Preferences
- `section_delivery_preferences` - "Abholpräferenzen" / "Pickup Preferences"
- `label_marketplace` - "Marktplatz" / "Marketplace"
- `label_pickup_time` - "Abholzeit" / "Pickup Time"
- `pickup_time_format` - "%s Uhr" / "%s"
- `default_market` - "Ökomarkt im Hansaviertel" / "Organic Market at Hansaviertel"

### Notifications
- `section_notifications` - "Benachrichtigungen" / "Notifications"
- `notification_order_updates` - "Bestellupdates" / "Order Updates"
- `notification_push_desc` - "Push-Benachrichtigungen erhalten" / "Receive push notifications"
- `notification_newsletter` - "Newsletter" / "Newsletter"
- `notification_newsletter_desc` - "Wöchentliche Angebote" / "Weekly offers"

### Membership
- `membership_regular` - "Stammkunde" / "Regular Customer"
- `membership_discount` - "5% Rabatt auf alle Bestellungen" / "5% discount on all orders"

### Quick Actions
- `quick_actions_title` - "Schnellaktionen" / "Quick Actions"
- `action_orders` - "Bestellungen" / "Orders"
- `action_favorites` - "Favoriten" / "Favorites"
- `action_payment` - "Zahlung" / "Payment"
- `action_help` - "Hilfe" / "Help"

### Buttons & Dialogs
- `button_cancel` - "Abbrechen" / "Cancel"
- `button_save` - "Speichern" / "Save"
- `button_confirm` - "Bestätigen" / "Confirm"
- `dialog_save_title` - "Änderungen speichern?" / "Save changes?"
- `dialog_save_message` - "Ihre Profiländerungen werden gespeichert." / "Your profile changes will be saved."

## Best Practices

### 1. Always Use String Resources
❌ **Don't:**
```kotlin
Text("Neuer Kunde")
```

✅ **Do:**
```kotlin
Text(stringResource(Res.string.profile_new_customer))
```

### 2. Extract All User-Visible Text
- Button labels
- Dialog messages
- Screen titles
- Field labels
- Error messages
- Toast messages

### 3. Use Descriptive Key Names
```kotlin
// Good
label_display_name
section_personal_info
button_save

// Bad
text1
str_label
name
```

### 4. Group Related Strings
Organize strings by screen or feature:
```xml
<!-- Login Screen -->
<string name="login_title">Login</string>
<string name="login_email_hint">Email</string>
<string name="login_password_hint">Password</string>
```

### 5. Format Strings Correctly
For strings with variables:
```xml
<string name="welcome_message">Welcome, %s!</string>
<string name="items_count">You have %d items</string>
```

Usage:
```kotlin
stringResource(Res.string.welcome_message, userName)
stringResource(Res.string.items_count, itemCount)
```

## Testing Localization

### On Android
1. Open Settings → System → Languages
2. Add and select the target language
3. Relaunch the app
4. Verify all strings are translated

### Programmatically (for testing)
```kotlin
// Change locale in Android
val config = resources.configuration
config.setLocale(Locale("en"))
resources.updateConfiguration(config, resources.displayMetrics)
```

### On iOS
1. Open Settings → General → Language & Region
2. Select target language
3. Relaunch the app

## Resource Generation

Resources are automatically generated during build. The generated code is located in:
```
shared/build/generated/compose/resourceGenerator/
```

You can manually trigger resource generation:
```bash
./gradlew :shared:generateComposeResClass
```

## Troubleshooting

### Strings Not Found
If you get "Unresolved reference" errors:
1. Clean and rebuild: `./gradlew clean build`
2. Sync Gradle files in Android Studio
3. Check string name matches exactly in XML

### Wrong Language Displayed
1. Check device/simulator language settings
2. Verify locale folder name format (`values-<code>`)
3. Ensure XML file is in the correct directory

### Build Errors
If resources fail to generate:
1. Check XML syntax in all strings.xml files
2. Ensure all opening tags have closing tags
3. Verify special characters are escaped

## Migration Guide

To localize an existing screen:

1. **Extract strings** from the Kotlin file
2. **Add to strings.xml** with descriptive keys
3. **Import resources** in the Kotlin file
4. **Replace hardcoded strings** with `stringResource()`
5. **Add translations** to other language files
6. **Test** with different locales

## Example: Complete Screen

```kotlin
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileScreen() {
    val name = stringResource(Res.string.profile_name)
    val email = stringResource(Res.string.profile_email)
    val since = stringResource(Res.string.profile_member_since, "2023")

    Column {
        Text(name)
        Text(email)
        Text(since)
        Button(
            onClick = { /* ... */ }
        ) {
            Text(stringResource(Res.string.button_save))
        }
    }
}
```

## Files Modified

### Localization Implementation
- `shared/src/commonMain/composeResources/values/strings.xml` - German strings (default)
- `shared/src/commonMain/composeResources/values-en/strings.xml` - English translations
- `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/CustomerProfileScreenModern.kt` - Localized screen
- `shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/NavGraph.kt` - Fixed navigation

## Result

✅ **CustomerProfileScreenModern is now fully localized**
✅ **Supports German (default) and English**
✅ **Ready for additional languages**
✅ **Cross-platform compatible (Android & iOS)**
✅ **Type-safe string access**
