# BuildKonfig Setup Documentation

## Overview
BuildKonfig has been configured to provide **compile-time** configuration values that work across both Android and iOS platforms in your KMP project.

**✅ FULLY INTEGRATED**: The flavor handling has been migrated from `androidMain` to `commonMain`, meaning flavor detection now works at compile-time across all platforms without requiring runtime initialization.

## Configuration

### Flavors Configured
- **buy**: For the buyer-facing app
- **sell**: For the seller-facing app

### Available Configuration Fields

#### Common (Default)
- `APP_NAME`: "Newverse"
- `IS_BUY_APP`: false
- `IS_SELL_APP`: false

#### Buy Flavor
- `APP_NAME`: "Newverse Buy"
- `IS_BUY_APP`: true
- `IS_SELL_APP`: false
- `USER_TYPE`: "buy"

#### Sell Flavor
- `APP_NAME`: "Newverse Sell"
- `IS_BUY_APP`: false
- `IS_SELL_APP`: true
- `USER_TYPE`: "sell"

## Usage in Code

### Option 1: Direct BuildKonfig Access (Recommended for new code)

```kotlin
import com.together.newverse.shared.BuildKonfig

class MyViewModel {
    fun getAppName(): String {
        return BuildKonfig.APP_NAME
    }

    fun isBuyerApp(): Boolean {
        return BuildKonfig.IS_BUY_APP
    }

    fun getUserType(): String {
        return BuildKonfig.USER_TYPE
    }
}
```

### Option 2: Via BuildFlavor Wrapper (For backward compatibility)

The existing `BuildFlavor` object has been updated to use BuildKonfig internally, so existing code continues to work without changes:

```kotlin
import com.together.newverse.BuildFlavor

// Existing code continues to work
if (BuildFlavor.isBuyFlavor) {
    // Buyer logic
}

if (BuildFlavor.isSellFlavor) {
    // Seller logic
}

val appName = BuildFlavor.appName
val flavorName = BuildFlavor.flavorName
```

**Note**: The `BuildFlavor.initialize()` call has been removed from `NewverseApp.kt` as it's no longer needed. BuildKonfig provides values at compile-time.

### Conditional Logic Based on Flavor

```kotlin
import com.together.newverse.shared.BuildKonfig

fun showFeatureBasedOnFlavor() {
    when {
        BuildKonfig.IS_BUY_APP -> {
            // Show buyer-specific features
            println("Showing buyer UI")
        }
        BuildKonfig.IS_SELL_APP -> {
            // Show seller-specific features
            println("Showing seller UI")
        }
        else -> {
            // Default behavior
            println("Default UI")
        }
    }
}
```

### Real Example: AppDrawer Navigation

The `AppDrawer.kt` navigation already uses this pattern to filter menu items:

```kotlin
val filteredRoutes = when {
    BuildFlavor.isBuyFlavor -> {
        // Buy flavor: Show Common and Customer Features only
        allRoutes.filter { route -> route !is NavRoutes.Sell }
    }
    BuildFlavor.isSellFlavor -> {
        // Sell flavor: Show Common and Seller Features only
        allRoutes.filter { route -> route !is NavRoutes.Buy }
    }
    else -> allRoutes
}
```

## Building for Specific Flavors

### Android

```bash
# Build buy flavor (debug)
./gradlew :androidApp:assembleBuyDebug

# Build buy flavor (release)
./gradlew :androidApp:assembleBuyRelease

# Build sell flavor (debug)
./gradlew :androidApp:assembleSellDebug

# Build sell flavor (release)
./gradlew :androidApp:assembleSellRelease
```

### iOS

For iOS, you'll need to configure Xcode schemes and build configurations:

1. **Create Schemes in Xcode**:
   - Create "Buy" scheme
   - Create "Sell" scheme

2. **Configure Build Script** (in Xcode Build Phases):

Add this script to link the correct flavor:

```bash
#!/bin/bash

# Determine flavor based on scheme
if [[ "$SCHEME_NAME" == *"Buy"* ]]; then
    FLAVOR="buy"
elif [[ "$SCHEME_NAME" == *"Sell"* ]]; then
    FLAVOR="sell"
else
    FLAVOR="buy"  # default
fi

cd "$SRCROOT/../"
./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pflavor=$FLAVOR
```

## How It Works

1. **Build Time**: When you build your Android app with a specific flavor (buy or sell), Gradle automatically selects the corresponding BuildKonfig configuration.

2. **Generated Code**: BuildKonfig generates a Kotlin object with your configuration values at build time in:
   - `shared/build/buildkonfig/commonMain/com/together/newverse/shared/BuildKonfig.kt`

3. **Type Safety**: All values are type-safe and available at compile time, preventing runtime configuration errors.

4. **Cross-Platform**: The same `BuildKonfig` object works identically on Android, iOS, and any other Kotlin Multiplatform target.

## Adding New Configuration Fields

To add new configuration fields, edit `shared/build.gradle.kts`:

```kotlin
buildkonfig {
    packageName = "com.together.newverse.shared"

    defaultConfigs {
        buildConfigField(Type.STRING, "API_URL", "https://api.example.com")
        buildConfigField(Type.BOOLEAN, "DEBUG_MODE", "false")
        buildConfigField(Type.INT, "MAX_RETRIES", "3")
    }

    defaultConfigs("buy") {
        buildConfigField(Type.STRING, "API_URL", "https://buy-api.example.com")
        buildConfigField(Type.BOOLEAN, "DEBUG_MODE", "true")
    }

    defaultConfigs("sell") {
        buildConfigField(Type.STRING, "API_URL", "https://sell-api.example.com")
        buildConfigField(Type.BOOLEAN, "DEBUG_MODE", "true")
    }
}
```

Supported types:
- `Type.STRING`
- `Type.BOOLEAN`
- `Type.INT`
- `Type.LONG`
- `Type.FLOAT`

## Troubleshooting

### Build Fails
If you get build errors after modifying BuildKonfig:
```bash
./gradlew clean
./gradlew :shared:generateBuildKonfig
```

### Can't Access BuildKonfig
Make sure you've built the project at least once to generate the BuildKonfig object:
```bash
./gradlew :shared:build
```

## Benefits

1. **Type Safety**: Compile-time checking of configuration values
2. **Cross-Platform**: Works identically on Android and iOS
3. **No Runtime Overhead**: Values are constants, no performance cost
4. **Flavor Support**: Different configurations for different app variants
5. **Centralized Config**: All build configurations in one place
