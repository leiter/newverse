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

## Features Implemented

- ✅ Kotlin Multiplatform setup with Android & iOS targets
- ✅ Compose Multiplatform for shared UI
- ✅ Koin dependency injection
- ✅ MVVM architecture with ViewModels
- ✅ StateFlow for reactive UI state
- ✅ Platform-specific implementations (expect/actual)
- ✅ Sample screen demonstrating the setup
- ✅ **Material3 theme system migrated from Universe project**
  - Light & dark theme support
  - Brand colors (teal/green primary)
  - Complete typography scale
  - Consistent shape system
- ✅ **Complete navigation system with drawer**
  - 11 dummy screens mapped from Universe fragments
  - Modal navigation drawer with categorized menu
  - Type-safe navigation with sealed classes
  - State preservation and back stack management
  - Dynamic screen titles

## Next Steps

### Migration from Universe Project

1. **Business Logic Migration**
   - Move repositories from `universe/app/src/main/java/com/together/repository/`
   - Adapt to KMP structure in `shared/src/commonMain/kotlin/domain/`

2. **UI Components**
   - Convert Android Views to Compose Multiplatform
   - Migrate from `universe/app/src/main/java/com/together/` UI classes

3. **Dependency Injection**
   - Replace Hilt with Koin modules
   - Organize by feature in `shared/src/commonMain/kotlin/di/`

4. **Firebase Integration**
   - Use KMP-compatible Firebase libraries
   - Set up platform-specific configurations

5. **Add Product Flavors**
   - Similar to universe (buy/sell)
   - Configure in Android module

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

For detailed theme documentation, see [THEME_MIGRATION.md](THEME_MIGRATION.md)

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

For detailed navigation documentation, see [NAVIGATION.md](NAVIGATION.md)

## Development Notes

- The project uses Java 17
- Gradle daemon is configured with Java module exports for Kotlin compatibility
- iOS targets require macOS with Xcode for building
- Theme follows Material3 guidelines for cross-platform consistency

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
