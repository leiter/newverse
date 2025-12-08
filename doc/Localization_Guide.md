# Localization Guide

## Overview

Localization uses **Compose Multiplatform Resources** for cross-platform string management.

## File Structure

```
shared/src/commonMain/composeResources/
├── values/           # Default (German)
│   └── strings.xml
└── values-en/        # English
    └── strings.xml
```

## Usage

```kotlin
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun MyScreen() {
    // Simple string
    Text(text = stringResource(Res.string.profile_new_customer))

    // With parameters
    Text(text = stringResource(Res.string.profile_member_since, "2023"))
}
```

## Adding Strings

### 1. Add to values/strings.xml (German default)
```xml
<string name="my_string">Mein Text</string>
<string name="formatted">Hallo %s!</string>
```

### 2. Add translation to values-en/strings.xml
```xml
<string name="my_string">My Text</string>
<string name="formatted">Hello %s!</string>
```

### 3. Rebuild
```bash
./gradlew :shared:generateComposeResClass
```

## Adding New Languages

```bash
mkdir -p shared/src/commonMain/composeResources/values-<code>
# Copy and translate strings.xml
# Examples: values-es (Spanish), values-fr (French)
```

## Best Practices

- Always use `stringResource()` instead of hardcoded text
- Use descriptive key names: `label_email`, `button_save`, `error_required`
- Group strings by screen/feature in the XML file
- Use format strings (`%s`, `%d`) for dynamic content

## Testing

Change device language in Settings to test different locales.
