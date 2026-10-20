# Newverse - Kotlin Multiplatform Project

A modern Kotlin Multiplatform (KMP) project with shared UI using Jetpack Compose and Koin for dependency injection.

## Project Structure

```
newverse/
├── androidApp/          # Android-specific application module
│   └── src/main/
│       ├── kotlin/      # Android app code
│       └── res/         # Android resources
├── shared/              # Shared code module
│   └── src/
│       ├── commonMain/  # Shared Kotlin code
│       │   └── kotlin/
│       │       ├── di/       # Koin dependency injection
│       │       ├── domain/   # Business logic
│       │       └── ui/       # Compose Multiplatform UI
│       ├── androidMain/ # Android-specific implementations
│       └── iosMain/     # iOS-specific implementations
├── build.gradle.kts     # Root build configuration
├── settings.gradle.kts  # Project settings
└── gradle.properties    # Gradle properties
```

## Tech Stack

### Shared Code
- **Kotlin 2.0.21** - Latest Kotlin with K2 compiler
- **Compose Multiplatform 1.7.1** - Shared UI framework
- **Koin 4.0.0** - Dependency injection
- **Coroutines 1.9.0** - Asynchronous programming
- **Lifecycle ViewModel 2.8.0** - MVVM architecture
- **Navigation Compose** - Multi-platform navigation

### Android
- **Android Gradle Plugin 8.10.1**
- **Compile SDK 35**
- **Min SDK 23**
- **Target SDK 35**

### iOS
- iOS targets: iosX64, iosArm64, iosSimulatorArm64

## Building the Project

### Android
```bash
./gradlew :androidApp:assembleDebug
```

### iOS
The iOS framework is built in the shared module and can be consumed by an iOS app.

## Architecture

The project follows **Clean Architecture** principles with **MVVM** pattern:

- **Domain Layer** (`shared/src/commonMain/kotlin/domain/`)
  - Contains business logic and repositories
  - Platform-agnostic

- **UI Layer** (`shared/src/commonMain/kotlin/ui/`)
  - Compose Multiplatform screens and components
  - ViewModels with StateFlow
  - Shared across all platforms

- **DI Layer** (`shared/src/commonMain/kotlin/di/`)
  - Koin modules for dependency injection
  - Organized by feature

- **Platform-Specific** (`androidMain/`, `iosMain/`)
  - Platform-specific implementations
  - Expect/actual pattern for platform APIs

## Key Features

- **Cross-Platform:** Single codebase for Android and iOS using Kotlin Multiplatform.
- **Shared UI:** Jetpack Compose for building the UI for both platforms.
- **Modern Architecture:** Follows Clean Architecture and MVVM principles.
- **Dependency Injection:** Koin for managing dependencies.
- **Product Flavors:** Supports `buy` and `sell` flavors for different user-facing apps.
- **Authentication:** Robust authentication system supporting Google, Apple, Email, and anonymous sign-in.
- **Rich Feature Set:** Includes product management, order processing, customer management, and more.

For a detailed breakdown of feature implementation status, see [doc/plans/TODO.md](doc/plans/TODO.md).

## Roadmap

The project is under active development. Key areas for future work include:
- Push Notifications
- Standardization of error handling

For more details, please refer to the [feature status document](doc/plans/TODO.md).

## Theme System

The project uses a comprehensive Material3 theme system migrated from the Universe project:

### Brand Colors
- **Primary**: Teal/Green (#008577) - Main brand color
- **Secondary**: Dark Green (#0A6308) - Secondary actions
- **Tertiary**: Orange (#FA9C4D) - Accent color

### Features
- ✅ Light and dark theme support
- ✅ Material3 design system
- ✅ Complete typography scale
- ✅ Consistent rounded corners (4dp to 28dp)
- ✅ Semantic color roles for accessibility

For detailed theme documentation, see [doc/guides/theme.md](doc/guides/theme.md)

## Navigation System

The app features a complete navigation system with a modal drawer:

### Screen Categories
- **Common**: Home, About, Login
- **Customer Features**: Browse Products, Shopping Basket, Customer Profile
- **Seller Features**: Product Overview, Manage Orders, Create Product, Seller Profile, Pick Delivery Day

### Features
- ✅ Type-safe navigation routes
- ✅ Categorized navigation drawer
- ✅ Active route highlighting
- ✅ State preservation
- ✅ Dynamic screen titles

For detailed navigation documentation, see [doc/guides/navigation.md](doc/guides/navigation.md)

## Product Flavors

The project supports two product flavors:

### Android
- **Buy Flavor**: `com.together.newverse.buy` - Customer-facing app
- **Sell Flavor**: `com.together.newverse.sell` - Vendor-facing app

Build commands:
```bash
./gradlew :androidApp:assembleBuyDebug
./gradlew :androidApp:assembleSellDebug
```

### iOS
iOS flavor setup requires macOS with Xcode. See [doc/guides/ios-setup.md](doc/guides/ios-setup.md) for complete instructions.

## Documentation

Documentation lives in `doc/`, organized by lifecycle — **[doc/README.md](doc/README.md)
indexes everything with its status**:

| Folder | Means |
|--------|-------|
| [`doc/guides/`](doc/README.md#guides) | How the app works now; verified against the code |
| [`doc/design/`](doc/README.md#design) | Proposed, not built |
| [`doc/plans/`](doc/README.md#plans) | Work in flight |
| [`doc/reports/`](doc/README.md#reports) | Dated snapshots |
| [`doc/archive/`](doc/README.md#archive) | Superseded or done |

Most-used guides:

- **[architecture.md](doc/guides/architecture.md)** - MVVM, state hoisting, core state abstractions
- **[navigation.md](doc/guides/navigation.md)** - Routes and per-flavor nav graphs
- **[auth-and-accounts.md](doc/guides/auth-and-accounts.md)** - Startup auth, guest accounts, linking, deletion
- **[ordering-business-rules.md](doc/guides/ordering-business-rules.md)** - Per-market pickup, deadlines, order editing
- **[build-flavors.md](doc/guides/build-flavors.md)** - applicationIds, BuildKonfig, the combined build
- **[testing.md](doc/guides/testing.md)** - Source sets, fakes, how to run what
- **[theme.md](doc/guides/theme.md)** - Material 3 theme system
- **[localization.md](doc/guides/localization.md)** - String localization
- **[ios-setup.md](doc/guides/ios-setup.md)** - iOS app setup with Buy/Sell flavors

(`docs/` at the repo root is the published GitHub Pages site, not developer docs.)

## Development Notes

- The project uses Java 17
- Gradle daemon is configured with Java module exports for Kotlin compatibility
- iOS targets require macOS with Xcode for building
- Theme follows Material3 guidelines for cross-platform consistency
- Both Buy and Sell flavors can be installed side-by-side on the same device

## Running the App

### Android
1. Open the project in Android Studio
2. Select `androidApp` configuration
3. Run on emulator or device

### iOS (requires macOS)
1. Build the shared framework: `./gradlew :shared:linkDebugFrameworkIosX64`
2. Open iOS project in Xcode
3. Run on simulator or device

## License

[Your license here]
