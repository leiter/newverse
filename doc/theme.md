# Theme Guide

## Overview

Material3 theme migrated from Universe XML themes to Compose.

## Brand Colors

| Color | Hex | Usage |
|-------|-----|-------|
| BrightGreen | #008577 | Primary (light) |
| DarkGreen | #00574B | Primary container |
| FabGreen | #0A6308 | Secondary, FAB |
| Orange | #FA9C4D | Tertiary accent |
| ErrorRed | #D51D47 | Error states |

## Usage

```kotlin
@Composable
fun MyApp() {
    NewverseTheme {
        // App content - auto light/dark
    }
}

// Force specific theme
NewverseLightTheme { }
NewverseDarkTheme { }
```

## Using Theme Values

```kotlin
@Composable
fun MyComponent() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Text(
            text = "Hello",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

// Direct brand colors
Box(modifier = Modifier.background(FabGreen))
```

## Typography Scale

| Style | Size | Usage |
|-------|------|-------|
| displayLarge | 57sp | Hero text |
| headlineMedium | 28sp | Card headers |
| titleLarge | 22sp | AppBar titles |
| bodyLarge | 16sp | Primary text |
| labelLarge | 14sp | Button text |

## Shapes

| Shape | Radius | Components |
|-------|--------|------------|
| small | 8dp | Buttons, Cards |
| medium | 12dp | Dialogs |
| large | 16dp | Bottom Sheets |

## Files

```
shared/src/commonMain/.../ui/theme/
├── Color.kt    # Color definitions
├── Type.kt     # Typography
├── Shape.kt    # Shapes
└── Theme.kt    # Main theme
```
