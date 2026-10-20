# Localization Guide

**Status:** current — verified on 2026-10-20. German (`values/`) is the default
locale, English (`values-en/`) the only translation.


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
<string name="formatted">Hallo %1$s!</string>
```

### 2. Add translation to values-en/strings.xml
```xml
<string name="my_string">My Text</string>
<string name="formatted">Hello %1$s!</string>
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
- **Always use positional specifiers — `%1$s`, `%2$d` — never bare `%s`/`%d`.**
  Compose Resources only substitutes the positional form. A bare `%s` is not an
  error and does not warn: it reaches the user as the literal two characters `%s`.
  This has shipped as a visible bug before.

## Joining a name with a badge

Do not concatenate a localized name with a count in Kotlin — the order differs by
language. Pass both into one positional string and let the locale decide. The nav
items do this; see commit `2fb10cd`.

## Testing

Change the device language in Settings to test locales. German is the primary
locale and lives in `values/`; English is the only translation (`values-en/`), so
a missing English string falls back to German rather than to a key.
