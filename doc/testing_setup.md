# Testing Setup for Newverse

## Overview
This document describes the testing infrastructure transferred from the universe project to the newverse KMP project.

## Testing Components

### 1. Test Container Activity
**Location:** `androidApp/src/debug/kotlin/com/together/newverse/android/TestContainerActivity.kt`

A debug-only activity that provides a UI for testing authentication and other features. It includes:
- Email/password login and account creation
- Google Sign-In integration
- User logout and account deletion
- Loading indicator for async operations
- Test data holder with random email generation

**Layout:** `androidApp/src/debug/res/layout/activity_test_container.xml`

### 2. Espresso Testing Utilities

#### IdleMessenger
**Location:** `androidApp/src/androidTest/kotlin/com/together/newverse/android/utils/IdleMessenger.kt`

An `IdlingResource` implementation that monitors the loading indicator in TestContainerActivity. This ensures Espresso waits for async operations to complete before proceeding with test assertions.

#### BaseTest
**Location:** `androidApp/src/androidTest/kotlin/com/together/newverse/android/utils/BaseTest.kt`

An abstract base class for all instrumentation tests that:
- Manages the `ActivityTestRule` for TestContainerActivity
- Registers/unregisters the IdleMessenger with Espresso
- Provides helper methods for common actions (login, logout)
- Includes a utility function to get the current activity

### 3. Test Cases

#### LoginCases
**Location:** `androidApp/src/androidTest/kotlin/com/together/newverse/android/authentication/LoginCases.kt`

Instrumentation tests for authentication flows:
- `createAccount()`: Tests account creation, login, and deletion flow
- `testGoogleAuth()`: Tests Google Sign-In authentication (requires additional implementation)

#### TestSuite
**Location:** `androidApp/src/androidTest/kotlin/com/together/newverse/android/TestSuite.kt`

A JUnit test suite that groups all test cases together for batch execution.

## Configuration

### Build Configuration
The `androidApp/build.gradle.kts` file has been updated with:

1. **Test Instrumentation Runner:**
   ```kotlin
   testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
   ```

2. **Testing Dependencies:**
   - AndroidX Test libraries (JUnit, Runner, Rules)
   - Espresso for UI testing
   - JUnit 4 for test assertions
   - Google Play Services Auth for Google Sign-In
   - Material Components for UI elements

## Running Tests

### From Android Studio:
1. Right-click on the test class or test method
2. Select "Run '<test_name>'"

### From Command Line:
```bash
# Run all instrumentation tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.together.newverse.android.authentication.LoginCases

# Run the test suite
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.together.newverse.android.TestSuite
```

## Differences from Universe Project

The testing setup has been adapted for the newverse KMP project:

1. **Package Structure:** Changed from `com.together` to `com.together.newverse.android`
2. **Architecture:** Adapted to use the KMP `AuthRepository` interface instead of direct Firebase calls
3. **Coroutines:** Uses Kotlin coroutines instead of RxJava for async operations
4. **Simplified Test Data:** Removed product and profile test data as they weren't part of the core testing requirements
5. **Compose Integration:** The newverse project uses Jetpack Compose, but tests still use the traditional View-based TestContainerActivity for easier testing

## Future Enhancements

- Add Compose UI tests for the main app screens
- Implement complete Google Sign-In flow in tests
- Add tests for other features (products, orders, etc.)
- Consider migrating to Compose testing framework for better integration with the main app
