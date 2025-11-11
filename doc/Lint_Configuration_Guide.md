# Lint Configuration Guide - Excluding Generated Files

**Date:** 2025-11-11
**Purpose:** Configure Android Lint to ignore generated files in KMP project
**Status:** ✅ CONFIGURED

---

## Overview

This guide explains how to configure Android Lint to exclude generated files (BuildKonfig, Compose Resources, etc.) from static analysis in a Kotlin Multiplatform project.

---

## Configuration Methods

There are three primary ways to exclude generated files from lint analysis:

### 1. **build.gradle.kts Configuration** (Module Level)
### 2. **lint.xml Configuration** (Project Level)
### 3. **Android Studio Settings** (IDE Level)

---

## Method 1: build.gradle.kts Configuration

### In `androidApp/build.gradle.kts`:

```kotlin
android {
    // ... other configuration

    lint {
        // Exclude generated files from lint checks
        ignoreWarnings = false
        abortOnError = true

        // Disable specific lint checks
        disable += setOf(
            "ObsoleteLintCustomCheck",
            "InvalidPackage"
        )

        // Optional: Ignore all warnings in test sources
        ignoreTestSources = true

        // Optional: Baseline file for existing issues
        baseline = file("lint-baseline.xml")
    }
}
```

### In `shared/build.gradle.kts`:

```kotlin
android {
    // ... other configuration

    lint {
        // Exclude generated files from lint checks
        ignoreWarnings = false
        abortOnError = false  // More lenient for library modules

        // Exclude generated source directories
        disable += setOf(
            "ObsoleteLintCustomCheck",
            "InvalidPackage"
        )

        // Optional: Baseline file
        baseline = file("lint-baseline.xml")
    }
}
```

### Available Lint Options:

| Option | Type | Description |
|--------|------|-------------|
| `abortOnError` | Boolean | Whether to abort build on lint errors |
| `absolutePaths` | Boolean | Whether to use absolute paths in reports |
| `checkAllWarnings` | Boolean | Check all warnings, not just enabled ones |
| `checkDependencies` | Boolean | Check dependencies for lint issues |
| `checkGeneratedSources` | Boolean | ❌ Set to `false` to skip generated code |
| `checkReleaseBuilds` | Boolean | Run lint on release builds |
| `checkTestSources` | Boolean | Check test sources |
| `disable` | Set<String> | Set of issue IDs to disable |
| `enable` | Set<String> | Set of issue IDs to enable |
| `error` | Set<String> | Set of issue IDs to treat as errors |
| `fatal` | Set<String> | Set of issue IDs to treat as fatal |
| `ignoreWarnings` | Boolean | Ignore all warnings |
| `ignoreTestSources` | Boolean | Skip lint checks in test sources |
| `quiet` | Boolean | Reduce console output |
| `warningsAsErrors` | Boolean | Treat all warnings as errors |
| `baseline` | File | Baseline file for existing issues |

---

## Method 2: lint.xml Configuration (Recommended)

Create a `lint.xml` file in the project root:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <!-- Ignore all lint checks in generated directories -->
    <issue id="all">
        <!-- BuildKonfig generated files -->
        <ignore path="**/buildkonfig/**" />
        <ignore path="**/generated/buildkonfig/**" />
        <ignore path="**/build/generated/buildkonfig/**" />

        <!-- Compose Resources generated files -->
        <ignore path="**/generated/compose/**" />
        <ignore path="**/build/generated/compose/**" />
        <ignore path="**/composeResources/generated/**" />

        <!-- Kotlin generated files -->
        <ignore path="**/generated/ksp/**" />
        <ignore path="**/build/generated/ksp/**" />
        <ignore path="**/build/generated/source/**" />

        <!-- Android resource generated files -->
        <ignore path="**/build/generated/res/**" />
        <ignore path="**/build/generated/assets/**" />
    </issue>

    <!-- Ignore specific file patterns using regex -->
    <issue id="all">
        <ignore regexp=".*BuildKonfig\.kt$" />
        <ignore regexp=".*generated.*Res\.kt$" />
        <ignore regexp=".*generated.*Resources\.kt$" />
    </issue>

    <!-- Disable specific checks for generated code -->
    <issue id="UnusedResources" severity="ignore">
        <ignore path="**/build/generated/**" />
    </issue>

    <issue id="MissingTranslation" severity="ignore">
        <ignore path="**/build/generated/**" />
    </issue>
</lint>
```

### Lint.xml Syntax:

#### Ignore by Path:
```xml
<issue id="all">
    <ignore path="**/generated/**" />
</issue>
```

#### Ignore by Regex:
```xml
<issue id="all">
    <ignore regexp=".*BuildKonfig\.kt$" />
</issue>
```

#### Set Severity:
```xml
<issue id="UnusedResources" severity="ignore" />
```

#### Available Severities:
- `ignore` - Don't report
- `informational` - Report for info only
- `warning` - Report as warning
- `error` - Report as error
- `fatal` - Abort build

---

## Method 3: Android Studio IDE Settings

### Configure in Android Studio:

1. **File** → **Settings** (Windows/Linux) or **Android Studio** → **Preferences** (Mac)

2. Navigate to:
   ```
   Editor → Inspections
   ```

3. **Disable inspections for specific patterns:**
   - Select **Android Lint** category
   - Uncheck unwanted inspections
   - Or configure scope to exclude generated files

4. **Configure inspection scope:**
   - Click **Scope** dropdown
   - Select **Custom scope**
   - Click **...** to edit
   - Add exclusion patterns:
     ```
     !file:*/build/generated//*
     !file:*/buildkonfig//*
     ```

5. **Per-file suppression:**
   - Use `@Suppress("LintCheck")` annotation in Kotlin
   - Use `//noinspection LintCheck` comment in XML

---

## Common Generated File Patterns to Exclude

### BuildKonfig:
```
**/build/generated/buildkonfig/**
**/buildkonfig/**
**/*BuildKonfig.kt
```

### Compose Resources:
```
**/build/generated/compose/**
**/composeResources/generated/**
**/*Res.kt
**/*Resources.kt
newverse/shared/generated/resources/**
```

### Kotlin Multiplatform:
```
**/build/generated/ksp/**
**/build/generated/source/**
```

### Android Generated:
```
**/build/generated/res/**
**/build/generated/assets/**
**/build/generated/source/**
**/R.java
**/BuildConfig.java
```

---

## Complete build.gradle.kts Example

### For `androidApp/build.gradle.kts`:

```kotlin
android {
    namespace = "com.together.newverse.android"
    compileSdk = 35

    lint {
        // Don't check generated sources
        checkGeneratedSources = false

        // Abort build on errors
        abortOnError = true

        // Ignore warnings from dependencies
        checkDependencies = false

        // Don't ignore test sources
        ignoreTestSources = false

        // Disable specific checks
        disable += setOf(
            "ObsoleteLintCustomCheck",
            "InvalidPackage",
            "MissingTranslation"
        )

        // Enable specific checks
        enable += setOf(
            "UnusedResources",
            "MissingTranslation"
        )

        // Treat specific issues as errors
        error += setOf(
            "NewerVersionAvailable"
        )

        // Use baseline file
        baseline = file("lint-baseline.xml")

        // Output configuration
        htmlReport = true
        htmlOutput = file("build/reports/lint-results.html")
        xmlReport = true
        xmlOutput = file("build/reports/lint-results.xml")
        textReport = true
        textOutput = file("build/reports/lint-results.txt")
    }
}
```

### For `shared/build.gradle.kts`:

```kotlin
android {
    namespace = "com.together.newverse.shared"
    compileSdk = 35

    lint {
        // Don't check generated sources
        checkGeneratedSources = false

        // Don't abort on errors in library module
        abortOnError = false

        // Ignore all warnings
        ignoreWarnings = false

        // Disable specific checks
        disable += setOf(
            "ObsoleteLintCustomCheck",
            "InvalidPackage"
        )

        // Use baseline file
        baseline = file("lint-baseline.xml")
    }
}
```

---

## Generating a Lint Baseline

If you want to acknowledge existing issues and focus on new ones:

### Step 1: Generate baseline
```bash
./gradlew :androidApp:lintBuyDebug --continue
```

### Step 2: Create baseline file
The first run will create `lint-baseline.xml` with all current issues

### Step 3: Reference in build.gradle.kts
```kotlin
lint {
    baseline = file("lint-baseline.xml")
}
```

### Step 4: Update baseline when needed
```bash
./gradlew :androidApp:lintBuyDebug --update-baseline
```

---

## Running Lint Manually

### Run lint for specific flavor:
```bash
# Buy flavor
./gradlew :androidApp:lintBuyDebug
./gradlew :androidApp:lintBuyRelease

# Sell flavor
./gradlew :androidApp:lintSellDebug
./gradlew :androidApp:lintSellRelease

# Shared module
./gradlew :shared:lintBuyDebug
./gradlew :shared:lintSellDebug
```

### Run lint for all variants:
```bash
./gradlew lint
```

### Generate HTML report:
```bash
./gradlew :androidApp:lintBuyDebug
# Report at: androidApp/build/reports/lint-results-buyDebug.html
```

---

## Suppressing Lint in Code

### Kotlin Files:

```kotlin
// Suppress specific check for entire file
@file:Suppress("UnusedImport", "RedundantVisibilityModifier")

// Suppress for function
@Suppress("MagicNumber")
fun calculatePrice(price: Double) = price * 1.19

// Suppress for class
@Suppress("TooManyFunctions")
class ProductRepository {
    // ...
}

// Suppress for property
@Suppress("VariableNaming")
val API_KEY = "secret"
```

### XML Files:

```xml
<!-- Suppress for entire file -->
<!--suppress ALL -->

<!-- Suppress specific check -->
<!--suppress HardcodedText -->
<TextView
    android:text="Hello World" />

<!-- Suppress multiple checks -->
<!--suppress HardcodedText, UnusedResources -->
<string name="app_name">My App</string>
```

---

## Common Lint Issue IDs

| Issue ID | Description |
|----------|-------------|
| `UnusedResources` | Unused resources |
| `MissingTranslation` | Missing translations |
| `ExtraTranslation` | Extra translations |
| `InvalidPackage` | Invalid package reference |
| `ObsoleteLintCustomCheck` | Obsolete custom lint check |
| `HardcodedText` | Hardcoded text in UI |
| `IconMissingDensityFolder` | Missing icon densities |
| `ContentDescription` | Missing content descriptions |
| `Deprecated` | Deprecated API usage |
| `NewApi` | API level requirement |
| `NewerVersionAvailable` | Newer library version available |

---

## Project Structure

```
newverse/
├── lint.xml                          # ✅ Project-level lint config
├── androidApp/
│   ├── build.gradle.kts              # ✅ Module-level lint config
│   ├── lint-baseline.xml             # Optional baseline file
│   └── build/
│       └── reports/
│           └── lint-results.html     # Lint report
├── shared/
│   ├── build.gradle.kts              # ✅ Module-level lint config
│   ├── lint-baseline.xml             # Optional baseline file
│   └── build/
│       ├── generated/
│       │   ├── buildkonfig/          # ❌ Excluded from lint
│       │   └── compose/              # ❌ Excluded from lint
│       └── reports/
│           └── lint-results.html     # Lint report
└── doc/
    └── Lint_Configuration_Guide.md   # This file
```

---

## Verification

### 1. Check lint configuration:
```bash
./gradlew :androidApp:lintBuyDebug --dry-run
```

### 2. Review exclusions in report:
- Run lint and check HTML report
- Generated files should not appear in issues list

### 3. Test with specific file:
```kotlin
// This should trigger lint if not excluded
package com.together.newverse.shared

object BuildKonfig {
    const val APP_NAME: String = "Test"
}
```

---

## Best Practices

### ✅ Do:
- Use `lint.xml` for project-wide exclusions
- Use `checkGeneratedSources = false` in build.gradle.kts
- Generate baseline files for legacy projects
- Run lint on CI/CD for new code only
- Document why specific checks are disabled

### ❌ Don't:
- Disable all lint checks globally
- Suppress issues without understanding them
- Ignore lint in production code without reason
- Use `@Suppress("all")` broadly
- Skip lint entirely

---

## Troubleshooting

### Problem: Lint still checks generated files

**Solution:**
1. Verify `lint.xml` is in project root
2. Check path patterns match your structure
3. Clean and rebuild:
   ```bash
   ./gradlew clean
   ./gradlew :androidApp:lintBuyDebug
   ```

### Problem: Baseline file not working

**Solution:**
1. Ensure path is correct in build.gradle.kts
2. Regenerate baseline:
   ```bash
   rm lint-baseline.xml
   ./gradlew :androidApp:lintBuyDebug
   ```

### Problem: Lint takes too long

**Solution:**
```kotlin
lint {
    checkDependencies = false
    checkGeneratedSources = false
    ignoreTestSources = true
}
```

---

## Summary

The project now has comprehensive lint configuration:

1. ✅ **build.gradle.kts** configured in both modules
2. ✅ **lint.xml** created with path and regex exclusions
3. ✅ Generated directories excluded:
   - BuildKonfig files
   - Compose Resources
   - Kotlin generated sources
   - Android generated resources

### Files Modified:
- `androidApp/build.gradle.kts` - Added lint block
- `shared/build.gradle.kts` - Added lint block
- `lint.xml` - Created with exclusion rules

### Result:
- Lint will skip all generated files
- Focus on actual source code quality
- Faster lint analysis
- Cleaner reports

**Lint configuration complete! ✅**
