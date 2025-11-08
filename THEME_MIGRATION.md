# Theme Migration Guide

This document describes the migration of the Universe project theme to the Newverse KMP project using Compose Material3.

## Overview

The theme has been successfully migrated from XML-based Android themes to Compose Material3 design system while maintaining the brand identity and color scheme.

## Color Scheme

### Primary Colors (Teal/Green)
These are the main brand colors carried over from the Universe project:

| Color Name | Hex Value | Usage |
|------------|-----------|-------|
| BrightGreen | #008577 | Primary brand color (light theme) |
| DarkGreen | #00574B | Primary container (light theme) |
| FabGreen | #0A6308 | Secondary actions, FAB buttons |

### Accent Colors
| Color Name | Hex Value | Usage |
|------------|-----------|-------|
| Orange | #FA9C4D | Tertiary accent |
| BrownAccent | #7A5012 | Tertiary containers |

### Semantic Colors
| Color Name | Hex Value | Usage |
|------------|-----------|-------|
| ErrorRed | #D51D47 | Error states |
| InfoYellow | #EAD434 | Information/warning states |
| SoftBrown | #C9B9A0 | Soft accents |

## Material3 Color System

The theme implements full Material3 color roles for both light and dark themes:

### Light Theme
- **Primary**: Bright teal (#008577)
- **Secondary**: Dark green (#0A6308)
- **Tertiary**: Orange (#FA9C4D)
- **Background**: White
- **Surface**: White with variants

### Dark Theme
- **Primary**: Light teal (#80D4CA)
- **Secondary**: Light green (#9DD499)
- **Tertiary**: Light orange (#FFCC80)
- **Background**: Dark (#1A1C1A)
- **Surface**: Dark with variants

## Typography

The typography system follows Material3 guidelines with a complete type scale:

### Display Styles (Largest)
- `displayLarge`: 57sp - Hero text
- `displayMedium`: 45sp - Large headers
- `displaySmall`: 36sp - Section headers

### Headline Styles
- `headlineLarge`: 32sp - Page titles
- `headlineMedium`: 28sp - Card headers
- `headlineSmall`: 24sp - List headers

### Title Styles
- `titleLarge`: 22sp - AppBar titles
- `titleMedium`: 16sp - Card titles
- `titleSmall`: 14sp - List item titles

### Body Styles (Most Common)
- `bodyLarge`: 16sp - Primary text
- `bodyMedium`: 14sp - Secondary text
- `bodySmall`: 12sp - Captions

### Label Styles (UI Elements)
- `labelLarge`: 14sp - Button text
- `labelMedium`: 12sp - Chip text
- `labelSmall`: 11sp - Small labels

## Shapes

Consistent rounded corners across components:

| Shape | Radius | Components |
|-------|--------|------------|
| extraSmall | 4dp | Chips |
| small | 8dp | Buttons, TextFields, Cards |
| medium | 12dp | Dialogs, Menus |
| large | 16dp | Bottom Sheets, Drawers |
| extraLarge | 28dp | Large containers |

## Usage

### Basic Theme Application

```kotlin
@Composable
fun MyApp() {
    NewverseTheme {
        // Your app content
    }
}
```

### Force Light Theme

```kotlin
@Composable
fun MyApp() {
    NewverseLightTheme {
        // Always light theme
    }
}
```

### Force Dark Theme

```kotlin
@Composable
fun MyApp() {
    NewverseDarkTheme {
        // Always dark theme
    }
}
```

### Using Theme Colors

```kotlin
@Composable
fun MyComponent() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Text(
            text = "Hello",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
```

### Using Custom Brand Colors

The original brand colors are still available for special cases:

```kotlin
import com.together.newverse.ui.theme.*

Box(
    modifier = Modifier.background(FabGreen)
)
```

## Migration from Universe

### Old (XML Theme)
```xml
<style name="AppTheme" parent="Theme.MaterialComponents.Light.NoActionBar">
    <item name="colorPrimary">@color/fab_green</item>
    <item name="colorPrimaryDark">@color/fab_green</item>
    <item name="colorAccent">@color/colorAccent</item>
</style>
```

### New (Compose Material3)
```kotlin
NewverseTheme {
    // Theme automatically applies:
    // - Primary: BrightGreen
    // - Secondary: FabGreen
    // - Full Material3 color system
    // - Typography scale
    // - Shape system
}
```

## Component Styling Examples

### Button
```kotlin
Button(
    onClick = { },
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondary
    )
) {
    Text("Action", style = MaterialTheme.typography.labelLarge)
}
```

### Card
```kotlin
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    )
) {
    Text(
        text = "Content",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
}
```

### TopAppBar
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTopBar() {
    TopAppBar(
        title = { Text("Title") },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
```

## File Structure

```
shared/src/commonMain/kotlin/com/together/newverse/ui/theme/
├── Color.kt      # Color definitions and light/dark schemes
├── Type.kt       # Typography scale
├── Shape.kt      # Shape system
└── Theme.kt      # Main theme composable
```

## Benefits of Material3

1. **Unified Design System**: Consistent look across Android 12+ and other platforms
2. **Dynamic Colors**: Support for Material You (Android 12+)
3. **Accessibility**: Better contrast and legibility
4. **Dark Theme**: Built-in dark mode support
5. **Responsive**: Adapts to different screen sizes
6. **Type Safety**: Compile-time color and style checking

## Future Enhancements

1. **Custom Fonts**: Add brand-specific fonts
2. **Dynamic Theming**: Support Material You dynamic colors
3. **Product Flavors**: Separate themes for buy/sell variants
4. **Animations**: Add theme transitions
5. **Extended Colors**: Additional semantic colors for specific use cases
