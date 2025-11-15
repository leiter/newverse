# Newverse Kotlin Multiplatform App - Project Structure Investigation Report

**Investigation Date**: November 15, 2025  
**Codebase**: Newverse  
**Repository Branch**: iOS_integration  
**Project Type**: Kotlin Multiplatform (KMP) with Compose Multiplatform UI

---

## Executive Summary

This is a **Compose Multiplatform application** with a sophisticated flavor system supporting two distinct product variants: "Buy" (customer-facing) and "Sell" (merchant-facing). The project uses:

- **Kotlin Multiplatform** for code sharing across Android and iOS
- **Compose Multiplatform** for UI with Compose Material 3
- **Product Flavors** (Android) with dual variants: buy and sell
- **BuildKonfig** for compile-time flavor configuration
- **Navigation Compose** for routing and navigation
- **Firebase/GitLive** for cross-platform authentication and database
- **Koin** for dependency injection

---

## 1. PROJECT STRUCTURE OVERVIEW

### Root Directory Layout
```
/home/mandroid/Videos/newverse/
├── androidApp/                 # Android application module
├── shared/                     # Shared KMP module (commonMain, androidMain, iosMain)
├── iosApp/                     # iOS application (Xcode project)
├── build.gradle.kts            # Root build configuration
├── settings.gradle.kts         # Project settings (includes androidApp and shared)
├── gradle.properties           # Gradle configuration
├── gradle/                     # Gradle wrapper and utilities
├── doc/                        # Documentation (including BuildKonfig setup)
└── .gradle/                    # Gradle build cache
```

### Module Breakdown

#### Shared Module (Kotlin Multiplatform)
```
shared/
├── src/
│   ├── commonMain/             # Code shared across all platforms
│   │   ├── kotlin/
│   │   │   └── com/together/newverse/
│   │   │       ├── domain/           # Business logic, models, repositories
│   │   │       │   ├── model/        # Data models (Article, Order, etc.)
│   │   │       │   └── repository/   # Repository interfaces
│   │   │       ├── data/             # Data layer
│   │   │       │   ├── config/       # Configuration (FeatureFlags, AuthProvider)
│   │   │       │   ├── firebase/     # GitLive Firebase implementations
│   │   │       │   ├── parser/       # Data parsing utilities
│   │   │       │   └── repository/   # Repository implementations
│   │   │       ├── ui/               # Compose UI (shared across platforms)
│   │   │       │   ├── navigation/   # NavRoutes, NavGraph, AppScaffold, AppDrawer
│   │   │       │   ├── screens/      # Screen components
│   │   │       │   │   ├── common/   # LoginScreen, RegisterScreen, AboutScreen
│   │   │       │   │   ├── buy/      # Customer screens (BasketScreen, CustomerProfileScreen)
│   │   │       │   │   └── sell/     # Merchant screens (CreateProductScreen, OrdersScreen)
│   │   │       │   ├── state/        # State management (UnifiedAppViewModel, AppState)
│   │   │       │   ├── theme/        # Theme configuration
│   │   │       │   ├── components/   # Reusable UI components
│   │   │       │   └── MainScreenModern.kt  # Main product browsing screen
│   │   │       ├── di/               # Dependency injection (appModule)
│   │   │       └── util/             # Utility functions
│   │   └── composeResources/         # Shared Compose resources
│   │       ├── drawable/             # SVG and vector drawables
│   │       └── values/               # String resources (localization)
│   │
│   ├── androidMain/            # Android-specific implementations
│   │   ├── kotlin/.../
│   │   │   ├── di/                  # androidDomainModule (Android-specific DI)
│   │   │   ├── data/
│   │   │   │   ├── config/          # Android-specific Firebase config
│   │   │   │   ├── firebase/        # Native Firebase implementations
│   │   │   │   └── repository/      # Android-specific repository implementations
│   │   │   └── util/                # Android-specific utilities
│   │   └── resources/               # Android resources (not used for Compose app)
│   │
│   └── iosMain/                # iOS-specific implementations
│       ├── kotlin/.../
│       │   ├── data/config/         # iOS-specific Firebase/GitLive config
│       │   └── di/                  # iOS-specific DI modules
│       └── resources/               # iOS resources
│
└── build.gradle.kts            # Shared module build configuration with flavors and BuildKonfig

androidApp/
├── src/
│   ├── main/                   # Common Android app resources
│   │   ├── kotlin/.../
│   │   │   ├── MainActivity.kt       # Main Activity with Google Sign-In handling
│   │   │   └── NewverseApp.kt        # Application class with Koin initialization
│   │   └── res/
│   │       ├── mipmap-anydpi-v26/   # Adaptive app icon (Buy flavor icon reference)
│   │       │   └── ic_vegi.xml      # Points to @drawable/ic_vegi and @mipmap/ic_vegi
│   │       ├── drawable/            # Common drawables
│   │       │   ├── ic_launcher_foreground.xml
│   │       │   ├── ic_launcher_background.xml
│   │       │   ├── ic_vegi_background.xml
│   │       │   ├── ic_bodenschatz.xml
│   │       │   └── splash.xml
│   │       ├── values/              # String resources
│   │       │   ├── strings.xml      # "Bodenschätze" (default/shared)
│   │       │   ├── colors.xml
│   │       │   └── themes.xml
│   │       └── mipmap-*/ directories # App icons for different densities
│   │
│   ├── buy/                    # Buy Flavor Resources (Customer App)
│   │   └── res/
│   │       ├── mipmap-anydpi-v26/
│   │       │   └── ic_vegi.xml      # Buy-specific icon configuration
│   │       ├── drawable/
│   │       │   └── ic_launcher_foreground.xml  # Buy-specific foreground icon
│   │       ├── values/
│   │       │   ├── strings.xml      # "Bodenschätze Buy"
│   │       │   └── colors.xml       # Buy-specific colors
│   │       └── mipmap-*/            # Density-specific icons for Buy flavor
│   │
│   ├── sell/                   # Sell Flavor Resources (Merchant App)
│   │   └── res/
│   │       ├── mipmap-anydpi-v26/
│   │       │   └── ic_launcher.xml  # Sell-specific icon configuration
│   │       ├── drawable/
│   │       │   └── ic_launcher_foreground.xml  # Sell-specific foreground icon
│   │       ├── values/
│   │       │   ├── strings.xml      # "Bodenschätze Sell", flavor_type="sell"
│   │       │   └── colors.xml       # Sell-specific colors
│   │       └── mipmap-*/            # Density-specific icons for Sell flavor
│   │
│   ├── debug/                  # Debug build type configuration
│   │   └── kotlin/
│   │
│   └── release/                # Release build type configuration
│
├── build.gradle.kts            # Android app module configuration
└── proguard-rules.pro          # ProGuard/R8 obfuscation rules

iosApp/
├── (Xcode project structure)
└── (Will integrate shared framework with flavor support)
```

---

## 2. BUILD CONFIGURATION & FLAVORS

### 2.1 Project-Level Build Configuration (`build.gradle.kts`)

**Location**: `/home/mandroid/Videos/newverse/build.gradle.kts`

```kotlin
plugins {
    // Kotlin Multiplatform
    kotlin("multiplatform").version("2.0.21").apply(false)
    kotlin("plugin.compose").version("2.0.21").apply(false)
    kotlin("plugin.serialization").version("2.0.21").apply(false)

    // Android
    id("com.android.application").version("8.10.1").apply(false)
    id("com.android.library").version("8.10.1").apply(false)

    // Compose
    id("org.jetbrains.compose").version("1.7.1").apply(false)

    // Google Services
    id("com.google.gms.google-services").version("4.4.2").apply(false)

    // BuildKonfig for build configurations
    id("com.codingfeline.buildkonfig").version("0.15.2").apply(false)
}
```

### 2.2 Android App Module Build Configuration (`androidApp/build.gradle.kts`)

**Key Configuration**:
- **Compilation SDK**: 35
- **Min SDK**: 23
- **Target SDK**: 35
- **Java Version**: 17
- **Compose Enabled**: Yes
- **BuildConfig Enabled**: Yes

**Product Flavors Configuration**:

```kotlin
android {
    // ...
    
    flavorDimensions += "userType"
    
    productFlavors {
        create("buy") {
            dimension = "userType"
            applicationIdSuffix = ".buy"
            versionCode = 1
            versionNameSuffix = "-buy"
        }
        
        create("sell") {
            dimension = "userType"
            applicationIdSuffix = ".sell"
            versionCode = 1
            versionNameSuffix = "-sell"
        }
    }
    
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(...)
        }
    }
}
```

**Package Details**:
- **Base Package**: `com.together.newverse.android`
- **Buy Flavor Package**: `com.together.newverse.android.buy`
- **Sell Flavor Package**: `com.together.newverse.android.sell`

**Build Variants Generated**:
- `buyDebug`, `buyRelease`
- `sellDebug`, `sellRelease`

### 2.3 Shared Module Build Configuration (`shared/build.gradle.kts`)

**Kotlin Multiplatform Targets**:
```kotlin
kotlin {
    androidTarget { ... }
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }
}
```

**Product Flavors in Shared Module**:
```kotlin
android {
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

**BuildKonfig Configuration** (Compile-Time Flavor Detection):

```kotlin
// Detect the flavor from gradle tasks being executed
val requestedTasks = gradle.startParameter.taskRequests.flatMap { it.args }
val isBuyFlavor = requestedTasks.any { it.contains("Buy", ignoreCase = true) }
val isSellFlavor = requestedTasks.any { it.contains("Sell", ignoreCase = true) }

val currentFlavor = when {
    isBuyFlavor -> "buy"
    isSellFlavor -> "sell"
    else -> "buy" // default
}

buildkonfig {
    packageName = "com.together.newverse.shared"
    
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
            else -> {
                buildConfigField(Type.STRING, "APP_NAME", "Newverse")
                buildConfigField(Type.BOOLEAN, "IS_BUY_APP", "false")
                buildConfigField(Type.BOOLEAN, "IS_SELL_APP", "false")
                buildConfigField(Type.STRING, "USER_TYPE", "default")
            }
        }
    }
}
```

**BuildKonfig Generated Location**: `shared/build/buildkonfig/commonMain/com/together/newverse/shared/BuildKonfig.kt`

---

## 3. NAVIGATION CONFIGURATION

### 3.1 Navigation Structure

**Type**: Jetpack Navigation Compose with NavHost  
**Location**: `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/`

**Key Files**:
- `NavRoutes.kt` - Route definitions with flavor-aware filtering
- `NavGraph.kt` - Navigation graph setup
- `AppScaffold.kt` - Main app scaffold with drawer and top bar
- `AppDrawer.kt` - Navigation drawer implementation

### 3.2 Route Definitions (`NavRoutes.kt`)

**Route Organization**:

```kotlin
sealed class NavRoutes(val route: String) {
    // Common routes (shown in both flavors)
    data object Home : NavRoutes("home")
    data object Login : NavRoutes("login")
    data object Register : NavRoutes("register")
    data object About : NavRoutes("about")
    
    // Buy (Customer) routes
    sealed class Buy(route: String) : NavRoutes(route) {
        data object Basket : Buy("buy/basket")
        data object Profile : Buy("buy/profile")
        data object OrderHistory : Buy("buy/order_history")
    }
    
    // Sell (Merchant) routes
    sealed class Sell(route: String) : NavRoutes(route) {
        data object Overview : Sell("sell/overview")
        data object Orders : Sell("sell/orders")
        data object Create : Sell("sell/create")
        data object Profile : Sell("sell/profile")
        data object PickDay : Sell("sell/pick_day")
    }
}
```

### 3.3 Flavor-Aware Route Filtering

```kotlin
companion object {
    // Get routes filtered by current build flavor
    fun getRoutesForCurrentFlavor(): List<NavRoutes> {
        val allRoutes = getAllRoutesUnfiltered()
        
        return when {
            com.together.newverse.shared.BuildKonfig.IS_BUY_APP -> {
                // Buy flavor: Show Common and Customer Features only
                allRoutes.filter { route -> route !is Sell }
            }
            com.together.newverse.shared.BuildKonfig.IS_SELL_APP -> {
                // Sell flavor: Show Common and Seller Features only
                allRoutes.filter { route -> route !is Buy }
            }
            else -> {
                // Default/fallback: show all routes
                allRoutes
            }
        }
    }
    
    // Get display name for route (returns StringResource)
    fun getDisplayNameRes(route: NavRoutes): StringResource = when (route) {
        Home -> Res.string.nav_home
        About -> Res.string.nav_about
        Login -> Res.string.nav_login
        Register -> Res.string.nav_register
        Buy.Basket -> Res.string.nav_shopping_basket
        Buy.Profile -> Res.string.nav_customer_profile
        Buy.OrderHistory -> Res.string.action_orders
        Sell.Overview -> Res.string.nav_product_overview
        Sell.Orders -> Res.string.nav_manage_orders
        Sell.Create -> Res.string.nav_create_product
        Sell.Profile -> Res.string.nav_seller_profile
        Sell.PickDay -> Res.string.nav_pick_delivery_day
    }
    
    // Get category for grouping in drawer (returns StringResource)
    fun getCategoryRes(route: NavRoutes): StringResource = when (route) {
        Home, About, Login, Register -> Res.string.nav_category_common
        is Buy -> Res.string.nav_category_customer
        is Sell -> Res.string.nav_category_seller
    }
}
```

### 3.4 Navigation Graph (`NavGraph.kt`)

Defines composable destinations for all routes with proper state and action handling. Example:

```kotlin
@Composable
fun NavGraph(
    navController: NavHostController,
    appState: UnifiedAppState,
    onAction: (UnifiedAppAction) -> Unit,
    startDestination: String = NavRoutes.Home.route
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoutes.Home.route) {
            MainScreenModern(state = appState.screens.mainScreen, onAction = onAction)
        }
        // ... other destinations
    }
}
```

### 3.5 App Scaffold (`AppScaffold.kt`)

**Features**:
- Modal navigation drawer with flavor-filtered items
- Top app bar with menu button and cart badge
- Snackbar host for notifications
- Google Sign-In and Twitter Sign-In integration (platform-specific)
- Basket animation (shake effect when items added/removed)

**Key Composables**:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    onPlatformAction: (PlatformAction) -> Unit = {}
)

sealed interface PlatformAction {
    data object GoogleSignIn : PlatformAction
    data object TwitterSignIn : PlatformAction
}
```

### 3.6 Navigation Drawer (`AppDrawer.kt`)

```kotlin
@Composable
fun AppDrawer(
    currentRoute: String,
    onNavigate: (NavRoutes) -> Unit,
    onClose: () -> Unit
) {
    // Gets routes filtered by current build flavor
    val filteredRoutes = NavRoutes.getRoutesForCurrentFlavor()
    
    // Groups routes by category (Common, Customer, Seller)
    val groupedRoutes = filteredRoutes.groupBy { 
        NavRoutes.getCategoryRes(it) 
    }
    
    // Renders drawer items with categories
    // ...
}
```

---

## 4. COMPOSE MULTIPLATFORM CONFIGURATION

### 4.1 UI Technology Stack

**Framework**: Compose Multiplatform (shared Kotlin DSL UI)

**Dependencies** (from `shared/build.gradle.kts`):

```kotlin
commonMain.dependencies {
    // Compose Multiplatform
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(compose.components.uiToolingPreview)
    
    // Navigation
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
    
    // Koin for DI
    implementation("io.insert-koin:koin-compose:4.0.0")
    implementation("io.insert-koin:koin-compose-viewmodel:4.0.0")
    
    // Image loading (cross-platform)
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
    
    // Cross-platform Firebase (GitLive)
    implementation("dev.gitlive:firebase-auth:2.1.0")
    implementation("dev.gitlive:firebase-database:2.1.0")
    implementation("dev.gitlive:firebase-storage:2.1.0")
}
```

### 4.2 Key UI Components

**Location**: `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/`

**Main Screens**:
1. **MainScreenModern.kt** - Product browsing with hero section and grid
2. **LoginScreen.kt** - Authentication with Google and Twitter sign-in
3. **RegisterScreen.kt** - User registration
4. **AboutScreen.kt** - App information
5. **BasketScreen.kt** (Buy) - Shopping cart
6. **CustomerProfileScreen.kt** (Buy) - Customer profile
7. **OrderHistoryScreen.kt** (Buy) - Order history
8. **OverviewScreen.kt** (Sell) - Seller dashboard
9. **OrdersScreen.kt** (Sell) - Manage orders
10. **CreateProductScreen.kt** (Sell) - Create/edit products
11. **SellerProfileScreen.kt** (Sell) - Seller profile
12. **PickDayScreen.kt** (Sell) - Delivery day selection

**Material Design**: Material 3 with custom theme configuration in `ui/theme/`

---

## 5. FLAVOR-SPECIFIC RESOURCES

### 5.1 Android Resources Overview

#### Main/Common Resources (used by both flavors as base)
- **Location**: `androidApp/src/main/res/`
- **App Icon**: References `@drawable/ic_vegi` and `@drawable/ic_vegi_background`
- **Strings**: `app_name="Bodenschätze"` (generic name)
- **Colors**: Base color definitions
- **Drawable**: 
  - `ic_launcher_background.xml` - Generic launcher background
  - `ic_launcher_foreground.xml` - Generic launcher foreground
  - `ic_vegi_background.xml` - Vegetable-themed background
  - `ic_bodenschatz.xml` - Logo SVG
  - `splash.xml` - Splash screen

#### Buy Flavor Resources
- **Location**: `androidApp/src/buy/res/`
- **App Icon**: Custom `ic_vegi.xml` (adaptive icon configuration)
- **Foreground Icon**: `ic_launcher_foreground.xml` (Buy-specific design)
- **Background Icon**: Inherits from main `ic_vegi_background.xml`
- **App Strings**:
  - `app_name="Bodenschätze Buy"`
  - `flavor_type="buy"`
- **Colors**: Buy-specific color palette

#### Sell Flavor Resources
- **Location**: `androidApp/src/sell/res/`
- **App Icon**: Custom `ic_launcher.xml` (adaptive icon configuration)
- **Foreground Icon**: `ic_launcher_foreground.xml` (Sell-specific design)
- **Background Icon**: Inherits from main `ic_vegi_background.xml`
- **App Strings**:
  - `app_name="Bodenschätze Sell"`
  - `flavor_type="sell"`
- **Colors**: Sell-specific color palette

### 5.2 Adaptive Icon System

**Buy Flavor Icon** (`androidApp/src/buy/res/mipmap-anydpi-v26/ic_vegi.xml`):
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_vegi_background"/>
    <foreground android:drawable="@mipmap/ic_vegi"/>
</adaptive-icon>
```

**Sell Flavor Icon** (`androidApp/src/sell/res/mipmap-anydpi-v26/ic_launcher.xml`):
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_vegi_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

**Icon Densities**: Each flavor has density-specific versions:
- `mipmap-mdpi/`
- `mipmap-hdpi/`
- `mipmap-xhdpi/`
- `mipmap-xxhdpi/`
- `mipmap-xxxhdpi/`

### 5.3 App Naming by Flavor

| Flavor | App Name | Application ID Suffix | Version Suffix |
|--------|----------|------------------------|-----------------|
| Buy | "Bodenschätze Buy" | `.buy` | `-buy` |
| Sell | "Bodenschätze Sell" | `.sell` | `-sell` |
| Default | "Bodenschätze" | (none) | (none) |

---

## 6. STATE MANAGEMENT

### 6.1 Unified App State Architecture

**Location**: `/home/mandroid/Videos/newverse/shared/src/commonMain/kotlin/com/together/newverse/ui/state/`

**Pattern**: Single-source-of-truth with modular composition

```kotlin
data class UnifiedAppState(
    val common: CommonState = CommonState(),        // Cross-cutting state
    val screens: ScreenStates = ScreenStates(),     // Screen-specific states
    val features: FeatureStates = FeatureStates(),  // Feature flags
    val meta: AppMetaState = AppMetaState()         // App metadata
)
```

**Common State** (shared across all screens):
```kotlin
data class CommonState(
    val user: UserState = UserState.Guest,
    val basket: BasketState = BasketState(),
    val navigation: NavigationState = NavigationState(),
    val ui: GlobalUiState = GlobalUiState(),
    val connection: ConnectionState = ConnectionState.Connected,
    val notifications: NotificationState = NotificationState(),
    val triggerGoogleSignIn: Boolean = false,
    val triggerTwitterSignIn: Boolean = false
)
```

### 6.2 Screen-Specific States

- **MainScreenState**: Product browsing, selection, favorites
- **AuthState**: Login/register form state
- **BasketState**: Shopping cart items and total
- **CustomerProfileState**: User profile and preferences
- **OrderHistoryState**: Past orders list
- **SellerProfileState**: Merchant profile
- **OrdersScreenState**: Orders management
- **CreateProductScreenState**: Product creation form

### 6.3 View Model

**UnifiedAppViewModel**: Single view model managing all app state

---

## 7. DEPENDENCY INJECTION

### 7.1 Koin Setup

**Location**: `/home/mandroid/Videos/newverse/androidApp/src/main/kotlin/.../NewverseApp.kt`

```kotlin
class NewverseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        FirebaseApp.initializeApp(this)
        
        startKoin {
            androidLogger()
            androidContext(this@NewverseApp)
            modules(appModule, androidDomainModule)
        }
    }
}
```

**Modules**:
- `appModule` - Common module definitions (from commonMain)
- `androidDomainModule` - Android-specific implementations

**Key Injections**:
- `UnifiedAppViewModel` - Main app view model
- `AuthRepository` - Authentication service
- `BasketRepository` - Shopping basket service
- `ArticleRepository` - Products/articles service
- Platform-specific implementations for Firebase

---

## 8. ANDROID ENTRY POINTS

### 8.1 Main Activity (`MainActivity.kt`)

**Location**: `androidApp/src/main/kotlin/com/together/newverse/android/MainActivity.kt`

**Responsibilities**:
- Google Sign-In integration with result handling
- Firebase authentication flow
- Composable setup with theme and scaffold
- Platform action handling for native operations

**Key Features**:
```kotlin
class MainActivity : ComponentActivity() {
    private val authRepository: AuthRepository by inject()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoinContext {
                NewverseTheme {
                    AppScaffoldWithGoogleSignIn()
                }
            }
        }
    }
}
```

### 8.2 Theme Configuration

**Location**: `shared/src/commonMain/kotlin/com/together/newverse/ui/theme/`

- Material 3 theme with custom colors
- Supports both light and dark modes (system preference)
- Flavor-specific color schemes possible

---

## 9. FLAVOR DETECTION & BUILDKONFIG

### 9.1 How Flavor Detection Works

**Step 1**: Build Task Detection
```kotlin
// In shared/build.gradle.kts
val requestedTasks = gradle.startParameter.taskRequests.flatMap { it.args }
val isBuyFlavor = requestedTasks.any { it.contains("Buy", ignoreCase = true) }
val isSellFlavor = requestedTasks.any { it.contains("Sell", ignoreCase = true) }
```

**Step 2**: Flavor Determination
```kotlin
val currentFlavor = when {
    isBuyFlavor -> "buy"
    isSellFlavor -> "sell"
    else -> "buy" // default
}
```

**Step 3**: BuildKonfig Generation
```kotlin
buildkonfig {
    packageName = "com.together.newverse.shared"
    
    defaultConfigs {
        when (currentFlavor) {
            "buy" -> {
                buildConfigField(Type.STRING, "APP_NAME", "Newverse Buy")
                buildConfigField(Type.BOOLEAN, "IS_BUY_APP", "true")
                buildConfigField(Type.BOOLEAN, "IS_SELL_APP", "false")
                buildConfigField(Type.STRING, "USER_TYPE", "buy")
            }
            // ... sell flavor config
        }
    }
}
```

**Step 4**: Runtime Usage (Compile-time constants)
```kotlin
// In NavRoutes.kt or any code
when {
    BuildKonfig.IS_BUY_APP -> { /* show buy features */ }
    BuildKonfig.IS_SELL_APP -> { /* show sell features */ }
}
```

### 9.2 Generated BuildKonfig Location

**File**: `shared/build/buildkonfig/commonMain/com/together/newverse/shared/BuildKonfig.kt`

**Example (Buy Flavor)**:
```kotlin
internal object BuildKonfig {
    public val APP_NAME: String = "Newverse Buy"
    public val IS_BUY_APP: Boolean = true
    public val IS_SELL_APP: Boolean = false
    public val USER_TYPE: String = "buy"
}
```

---

## 10. BUILD COMMANDS

### 10.1 Building for Different Flavors

**Android Buy Flavor**:
```bash
./gradlew clean :androidApp:assembleBuyDebug    # Debug APK
./gradlew clean :androidApp:assembleBuyRelease  # Release APK
```

**Android Sell Flavor**:
```bash
./gradlew clean :androidApp:assembleSellDebug    # Debug APK
./gradlew clean :androidApp:assembleSellRelease  # Release APK
```

**Run in Emulator**:
```bash
./gradlew :androidApp:installBuyDebug          # Install and run Buy flavor
./gradlew :androidApp:installSellDebug         # Install and run Sell flavor
```

### 10.2 Building Shared Library for iOS

```bash
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

With flavor parameter (if needed):
```bash
./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pbuildkonfig.flavor=buy
```

---

## 11. PROJECT STRUCTURE SUMMARY

### 11.1 Key Characteristics

**Compose Multiplatform**: 
- Uses shared Compose UI code
- Same codebase runs on Android and iOS
- Material 3 design system

**Product Flavors**:
- Dual app variants: Buy (customer) and Sell (merchant)
- Flavor-specific resources (icons, strings, colors)
- Flavor-specific navigation routes (filtered at compile-time)
- Flavor-specific screen implementations (buy/screens and sell/screens)

**Architecture**:
- **Pattern**: MVVM with unified state management
- **DI**: Koin for dependency injection
- **Navigation**: Jetpack Navigation Compose with flavor filtering
- **State**: Single UnifiedAppState with modular composition
- **Platform-Specific**: androidMain and iosMain for native features

**Cross-Platform Features**:
- GitLive Firebase (multiplatform alternative to native Firebase)
- Coil image loading (supports Android and iOS)
- Kotlin Coroutines
- Kotlinx Serialization

### 11.2 Flavor Implementation Strategy

**Resource Overlay**:
- `androidApp/src/main/` - Base resources for both flavors
- `androidApp/src/buy/` - Buy flavor overrides
- `androidApp/src/sell/` - Sell flavor overrides

**Code Filtering** (Compile-Time):
- Routes filtered in `NavRoutes.getRoutesForCurrentFlavor()`
- BuildKonfig provides IS_BUY_APP / IS_SELL_APP flags
- Conditional code paths eliminated at compile time

**Screen Separation**:
- `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/common/` - Common screens
- `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/buy/` - Customer-only screens
- `shared/src/commonMain/kotlin/com/together/newverse/ui/screens/sell/` - Merchant-only screens

**UI Theming**:
- Material 3 with customizable colors
- Can have flavor-specific theme variants
- Drawable resources support flavor variants (via resource overlays)

---

## 12. DOCUMENTATION

**Available Documentation**:
- `/home/mandroid/Videos/newverse/doc/BuildKonfig_Setup.md` - Detailed BuildKonfig setup
- `/home/mandroid/Videos/newverse/doc/BuildKonfig_Flavor_Integration.md` - Flavor integration details
- `/home/mandroid/Videos/newverse/doc/BuildKonfig_Migration_Summary.md` - Migration notes

---

## 13. KEY FILES REFERENCE

| Purpose | File Path |
|---------|-----------|
| Root Build Config | `build.gradle.kts` |
| Android App Config | `androidApp/build.gradle.kts` |
| Shared Module Config | `shared/build.gradle.kts` |
| Route Definitions | `shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/NavRoutes.kt` |
| Navigation Graph | `shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/NavGraph.kt` |
| App Scaffold | `shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/AppScaffold.kt` |
| Navigation Drawer | `shared/src/commonMain/kotlin/com/together/newverse/ui/navigation/AppDrawer.kt` |
| Main Activity | `androidApp/src/main/kotlin/com/together/newverse/android/MainActivity.kt` |
| App Application | `androidApp/src/main/kotlin/com/together/newverse/android/NewverseApp.kt` |
| Buy Flavor Strings | `androidApp/src/buy/res/values/strings.xml` |
| Sell Flavor Strings | `androidApp/src/sell/res/values/strings.xml` |
| Buy Flavor Icon | `androidApp/src/buy/res/mipmap-anydpi-v26/ic_vegi.xml` |
| Sell Flavor Icon | `androidApp/src/sell/res/mipmap-anydpi-v26/ic_launcher.xml` |
| Unified App State | `shared/src/commonMain/kotlin/com/together/newverse/ui/state/UnifiedAppState.kt` |
| Main Screen | `shared/src/commonMain/kotlin/com/together/newverse/ui/MainScreenModern.kt` |

---

## 14. SUMMARY

This is a sophisticated **Kotlin Multiplatform application** using **Compose Multiplatform** for shared UI across Android and iOS. The project implements a dual-flavor system:

1. **Buy Flavor** (Customer-facing)
   - Browse and purchase products
   - Shopping basket
   - Order history
   - Customer profile

2. **Sell Flavor** (Merchant-facing)
   - Create and manage products
   - Manage orders
   - Set delivery days
   - Seller profile

**Key Technologies**:
- Kotlin Multiplatform with Compose Multiplatform UI
- Product Flavors (Android) for app variants
- BuildKonfig for compile-time flavor configuration
- Navigation Compose with flavor-aware routing
- Firebase/GitLive for cross-platform backend
- Koin for dependency injection
- Material 3 design system

**Flavor Implementation**:
- Task-based flavor detection at build time
- Resource overlays for app icons and strings
- Compile-time route filtering
- Separate screen implementations per flavor
- Single codebase, two deployable products

