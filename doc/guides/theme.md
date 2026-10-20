# Theme Guide

**Status:** current — colors verified against `ui/theme/Color.kt` on 2026-10-20


## Overview

Material3 theme migrated from Universe XML themes to Compose.

## Brand Colors

| Color | Hex | Usage |
|-------|-----|-------|
| BrightGreen | `#008577` | Primary (light) — the original teal-green, kept |
| DarkGreen | `#00574B` | Primary container |
| FabGreen | `#2E7D32` | Secondary, FAB — Material Green 700 |
| Orange | `#FF6F00` | Tertiary accent, CTAs |
| ErrorRed | `#D32F2F` | Error states — Material Error |

Values read from `ui/theme/Color.kt`; three of them were restated wrongly in
earlier versions of this guide, so take the source over any table.

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
