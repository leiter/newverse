# BuildKonfig Setup

## Overview

BuildKonfig provides compile-time configuration values for cross-platform flavor detection.

## Configuration Values

| Field | Buy Flavor | Sell Flavor |
|-------|------------|-------------|
| `APP_NAME` | "Newverse Buy" | "Newverse Sell" |
| `IS_BUY_APP` | true | false |
| `IS_SELL_APP` | false | true |
| `USER_TYPE` | "buy" | "sell" |

## Usage

```kotlin
import com.together.newverse.shared.BuildKonfig

// Direct access
val appName = BuildKonfig.APP_NAME
val isBuyer = BuildKonfig.IS_BUY_APP

// Via BuildFlavor wrapper (backward compatible)
if (BuildFlavor.isBuyFlavor) { /* ... */ }
if (BuildFlavor.isSellFlavor) { /* ... */ }
```

## Build Commands

```bash
# Android
./gradlew :androidApp:assembleBuyDebug
./gradlew :androidApp:assembleSellDebug
./gradlew :androidApp:assembleBuyRelease
./gradlew :androidApp:assembleSellRelease

# Generate BuildKonfig manually
./gradlew :shared:generateBuildKonfig
```

## Adding New Fields

In `shared/build.gradle.kts`:

```kotlin
buildkonfig {
    packageName = "com.together.newverse.shared"

    defaultConfigs {
        buildConfigField(Type.STRING, "API_URL", "https://api.example.com")
    }

    defaultConfigs("buy") {
        buildConfigField(Type.STRING, "API_URL", "https://buy-api.example.com")
    }

    defaultConfigs("sell") {
        buildConfigField(Type.STRING, "API_URL", "https://sell-api.example.com")
    }
}
```

Supported types: `Type.STRING`, `Type.BOOLEAN`, `Type.INT`, `Type.LONG`, `Type.FLOAT`

## Troubleshooting

```bash
# Rebuild after config changes
./gradlew clean :shared:generateBuildKonfig
```
