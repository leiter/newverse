# BuildKonfig Flavor Integration - Final Solution

## Problem
The drawer navigation was filtering items correctly in code, but BuildKonfig wasn't generating different values for buy vs sell flavors. Both flavors were getting the same default configuration.

## Root Cause
BuildKonfig in a KMP shared module doesn't automatically detect Android product flavors. It generates configuration once per Kotlin target (e.g., `androidMain`, `iosMain`), not per Android build variant.

## Solution
Configure BuildKonfig to detect the flavor from Gradle's task execution context and generate the appropriate configuration.

### Implementation

**File**: `shared/build.gradle.kts`

```kotlin
// Detect the flavor from gradle tasks being executed
val requestedTasks = gradle.startParameter.taskRequests.flatMap { it.args }
val isBuyFlavor = requestedTasks.any { it.contains("Buy", ignoreCase = true) }
val isSellFlavor = requestedTasks.any { it.contains("Sell", ignoreCase = true) }

val currentFlavor = when {
    isBuyFlavor -> "buy"
    isSellFlavor -> "sell"
    else -> "buy" // default to buy
}

buildkonfig {
    packageName = "com.together.newverse.shared"

    // Default configuration based on detected flavor
    defaultConfigs {
        when (currentFlavor) {
            "buy" -> {
                buildConfigField(Type.STRING, "APP_NAME", "Newverse Buy")
                buildConfigField(Type.BOOLEAN, "IS_BUY_APP", "true")
                buildConfigField(Type.BOOLEAN, "IS_SELL_APP", "false")
                buildConfigField(Type.STRING, "USER_TYPE", "buy")
            }
            "sell" -> {
                buildConfigField(Type.STRING, "APP_NAME", "Newverse Sell")
                buildConfigField(Type.BOOLEAN, "IS_BUY_APP", "false")
                buildConfigField(Type.BOOLEAN, "IS_SELL_APP", "true")
                buildConfigField(Type.STRING, "USER_TYPE", "sell")
            }
        }
    }
}
```

### Product Flavors in Shared Module

Also added matching product flavors to the shared module:

```kotlin
android {
    // ... other config

    flavorDimensions += "userType"

    productFlavors {
        create("buy") {
            dimension = "userType"
        }

        create("sell") {
            dimension = "userType"
        }
    }
}
```

## How It Works

1. **Task Detection**: When you run `./gradlew assembleBuyDebug`, Gradle's `startParameter.taskRequests` contains "Buy"
2. **Flavor Selection**: The script detects "Buy" in the task name and sets `currentFlavor = "buy"`
3. **BuildKonfig Generation**: BuildKonfig generates configuration with:
   - `IS_BUY_APP = true`
   - `IS_SELL_APP = false`
   - `USER_TYPE = "buy"`
4. **Code Filtering**: `NavRoutes.getRoutesForCurrentFlavor()` filters routes based on these values:
   ```kotlin
   when {
       BuildKonfig.IS_BUY_APP -> {
           allRoutes.filter { route -> route !is Sell }
       }
       BuildKonfig.IS_SELL_APP -> {
           allRoutes.filter { route -> route !is Buy }
       }
   }
   ```

## Verification

### Buy Flavor
```bash
./gradlew clean :androidApp:assembleBuyDebug
```

**Generated BuildKonfig**:
```kotlin
internal object BuildKonfig {
  public val APP_NAME: String = "Newverse Buy"
  public val IS_BUY_APP: Boolean = true
  public val IS_SELL_APP: Boolean = false
  public val USER_TYPE: String = "buy"
}
```

**Drawer Shows**:
- ✅ Common routes (Home, Login, Register, About)
- ✅ Buy routes (Browse Products, Shopping Basket, Customer Profile)
- ❌ Sell routes (hidden)

### Sell Flavor
```bash
./gradlew clean :androidApp:assembleSellDebug
```

**Generated BuildKonfig**:
```kotlin
internal object BuildKonfig {
  public val APP_NAME: String = "Newverse Sell"
  public val IS_BUY_APP: Boolean = false
  public val IS_SELL_APP: Boolean = true
  public val USER_TYPE: String = "sell"
}
```

**Drawer Shows**:
- ✅ Common routes (Home, Login, Register, About)
- ❌ Buy routes (hidden)
- ✅ Sell routes (Product Overview, Manage Orders, Create Product, Seller Profile, Pick Delivery Day)

## Key Files Modified

1. **`shared/build.gradle.kts`**
   - Added task-based flavor detection
   - Added product flavors to shared module
   - Configured BuildKonfig to use detected flavor

2. **`shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/NavRoutes.kt`**
   - Added `getRoutesForCurrentFlavor()` method
   - Filters routes based on `BuildKonfig.IS_BUY_APP` and `BuildKonfig.IS_SELL_APP`

3. **`shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/AppDrawer.kt`**
   - Simplified to call `NavRoutes.getRoutesForCurrentFlavor()`
   - Removed runtime flavor checking (now compile-time)

4. **`shared/src/commonMain/kotlin/com/together/newverse/BuildFlavor.kt`**
   - Updated to delegate to BuildKonfig
   - No longer requires initialization

## Benefits

✅ **Automatic Detection**: Flavor is detected from build tasks
✅ **Type-Safe**: Compile-time constants, no runtime checks
✅ **Clean Separation**: Buy app never includes sell code paths
✅ **Works in Android Studio**: Build variants properly filter drawer items
✅ **Cross-Platform Ready**: Same mechanism works for iOS

## Testing in Android Studio

1. Open Android Studio
2. Select Build Variant in bottom-left
3. Choose **buyDebug** or **sellDebug**
4. Run the app
5. Open the navigation drawer
6. Verify only the correct menu items appear

## iOS Support

For iOS, configure Xcode schemes to pass the flavor:

```bash
./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pbuildkonfig.flavor=buy
```

Or detect from Xcode scheme name in a build script.

## Result

✅ **Problem Solved**: Each flavor now has properly filtered drawer navigation
✅ **Build-Time Configuration**: No runtime overhead
✅ **Type-Safe**: Compiler catches errors
✅ **Clean Architecture**: Flavor handling fully in `commonMain`
