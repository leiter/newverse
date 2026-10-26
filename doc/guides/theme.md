# Theme Guide

**Status:** current — colors verified against `ui/theme/Color.kt` and
`ui/theme/Theme.kt` on 2026-10-26
**Scope:** the Bodenschätze palette, both color schemes, how screens consume
them, and the Android window background

## Overview

Material 3 theme in Compose Multiplatform. Since 2026-10-26 the colors are the
ones the neighbourhood flyer is printed in, so flyer, app icon and app look like
one thing. The dark scheme is "Nachterde": the same brand green and badge orange
on warm near-black soil instead of paper.

Every text-on-background pair in both schemes is at 4.5:1 or better (WCAG AA).

## The brand palette

Raw brand values, in `Color.kt` as `Boden*`:

| Name | Hex | Role on the flyer |
|------|-----|-------------------|
| `BodenPaper` | `#F6F2E8` | background |
| `BodenCard` | `#FFFDF8` | cards |
| `BodenInk` | `#1B2A16` | text |
| `BodenMuted` | `#4A5145` | secondary text |
| `BodenGreen` | `#0A6308` | headings, icons, primary |
| `BodenOrange` | `#FA9C4D` | Testphase badge, number circles, app icon |
| `BodenSoil` | `#5A3E2B` | section labels, earth |
| `BodenPotato` | `#C9A27A` | light earth tones |
| `BodenNight` | `#14120E` | dark scheme background |

**The orange is a fill, never a text color.** `#FA9C4D` on paper is 1.9:1, far
under the bar. It is therefore `tertiaryContainer`, with `onTertiaryContainer`
(Ink) on top at 7.2:1. For orange text and orange buttons use `tertiary`
(`#8F4A0E`, 6.0:1 on paper).

The badge orange is deliberately identical in both schemes, so a Testphase badge
and the app icon stay recognisable in dark mode.

## Material 3 role mapping

| Role | Light | Dark |
|------|-------|------|
| primary | `#0A6308` | `#8FD47F` |
| onPrimary | `#FFFFFF` | `#053902` |
| primaryContainer | `#C6E8BC` | `#0A6308` |
| onPrimaryContainer | `#052E03` | `#C6E8BC` |
| secondary | `#5A3E2B` | `#E2C3A3` |
| onSecondary | `#FFFFFF` | `#3F2B1D` |
| secondaryContainer | `#EADBC8` | `#5A3E2B` |
| onSecondaryContainer | `#2B1C11` | `#F2DFCB` |
| tertiary | `#8F4A0E` | `#FFB77C` |
| onTertiary | `#FFFFFF` | `#4A2500` |
| tertiaryContainer | `#FA9C4D` | `#FA9C4D` |
| onTertiaryContainer | `#1B2A16` | `#1B2A16` |
| background / surface | `#F6F2E8` | `#14120E` |
| onBackground / onSurface | `#1B2A16` | `#ECE6D8` |
| surfaceVariant | `#ECE5D6` | `#3A342A` |
| onSurfaceVariant | `#4A5145` | `#C9C1AE` |
| surfaceContainerLowest | `#FFFFFF` | `#0F0D0A` |
| surfaceContainerLow | `#FFFDF8` | `#1C1914` |
| surfaceContainer | `#F0EBDF` | `#211D17` |
| surfaceContainerHigh | `#EAE4D6` | `#2B261F` |
| surfaceContainerHighest | `#E4DDCD` | `#36302A` |
| outline | `#7A7466` | `#968E7C` |
| outlineVariant | `#D9D0BC` | `#4A4338` |
| error / onError | `#B3261E` / `#FFFFFF` | `#F2B8B5` / `#601410` |
| errorContainer / on | `#F9DEDC` / `#410E0B` | `#8C1D18` / `#F9DEDC` |
| inverseSurface / on | `#2E2A22` / `#F6F2E8` | `#ECE6D8` / `#2E2A22` |
| inversePrimary | `#8FD47F` | `#0A6308` |

`Theme.kt` sets the `surfaceContainer*`, `surfaceTint` and `inverse*` roles
explicitly. Without them Cards, NavigationBar, menus and Snackbars fall back to
Material's default grey and clash with the paper tone.

## Usage

```kotlin
@Composable
fun MyApp() {
    NewverseTheme {
        // App content - follows the system light/dark setting
    }
}

// Force one scheme (previews, or a deliberate dark surface)
NewverseLightTheme { }
NewverseDarkTheme { }
```

Read colors from the scheme, never from the palette constants:

```kotlin
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    )
) {
    Text(
        text = "Hello",
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.headlineMedium,
    )
}
```

A screen that imports `BodenGreen` or one of the deprecated aliases directly
gets the same color in both schemes, which means **that screen silently ignores
dark mode**. That was the case in `ProfileInputField` and `AboutScreenModern`
until 2026-10-26; both now use scheme roles, and no file outside
`ui/theme/` imports a color constant any more. If one is added back, the
deprecation warning on the alias is the signal.

If something sits on `tertiaryContainer`, its content must use
`onTertiaryContainer`, not white.

The Google red in `ForcedLoginScreen.kt` (`0xFFDB4437`) is a foreign brand color
and stays a literal.

### Deprecated aliases

The pre-Bodenschätze names (`FabGreen`, `BrandOrange`, `LightCream`, …) still
exist in `Color.kt` as `@Deprecated` aliases pointing at the new palette, so
older code keeps compiling. Migration table:

| Old | New |
|-----|-----|
| `FabGreen`, `BrightGreen` | `colorScheme.primary` |
| `LeafGreen.copy(alpha = …)` | `colorScheme.primaryContainer` |
| `BrandOrange`, `SoftOrange` | `colorScheme.tertiaryContainer` |
| `Orange` (text, buttons) | `colorScheme.tertiary` |
| `BrownAccent` | `colorScheme.secondary` |
| `ErrorRed` | `colorScheme.error` |
| `LightCream`, `SurfaceElevated` | `colorScheme.surfaceContainerLow` |
| `BeigeCard`, `SandColor` | `colorScheme.surfaceContainerHigh(est)` |
| `SoftBrown` (dividers) | `colorScheme.outlineVariant` |

`SuccessGreen` and `InfoBlue` have no Material role. Use `primary` or
`secondary`, or add extension colors through a `CompositionLocal` with a light
and a dark value.

## The bars that frame the app

The buy flavour's top app bar, bottom navigation bar and navigation rail are
all soil brown `#5A3E2B` in **both** themes, the way the badge orange is the
same orange in both.

No single Material role does that. `secondary` is an accent role, so it flips
from dark brown in the light scheme to pale potato `#E2C3A3` in the dark one —
and a bar painted with `secondary` becomes the brightest thing on a dark
screen, which reads at a glance as "the theme did not switch". The two schemes
therefore reach the same brown by different roles:

| | Light | Dark |
|---|---|---|
| container | `secondary` `#5A3E2B` | `secondaryContainer` `#5A3E2B` |
| content | `onSecondary` `#FFFFFF` (9.7:1) | `onSecondaryContainer` `#F2DFCB` (7.5:1) |

Both halves live in `BarColors`, provided by the theme and read as
`LocalBarColors.current`. Container and content travel as one value so a later
change cannot leave text on a bar it has no contrast against; the top bar's
title, navigation icon, action icons and the five tints inside it all use
`barColors.content`.

### The selected tab

`NavigationBarItem`'s default indicator is `secondaryContainer`, which in the
dark scheme is this very brown — the selected tab would have disappeared into
the bar. The indicator is therefore the badge orange (`selectionIndicator`,
identical in both themes, 4.6:1 against the brown) with Ink on it (7.2:1).
Unselected items are `content` at `UnselectedItemAlpha` (0.74), which still
clears 4.5:1 on the brown in both themes (6.2:1 light, 4.9:1 dark).

### The system bars

The status bar and the navigation bar are **transparent**, and the app's own
bars paint behind them, so the brown runs to both edges of the screen. This is
the only portable way to own that space: `Window.setNavigationBarColor` is
deprecated and a no-op for apps targeting SDK 35+, and `targetSdk` is 36, so a
coloured system bar would appear on an old phone and silently not on a new one.

`BuyMainActivity` and `DebugMainActivity` therefore call
`enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))`
— `dark` meaning light icons, which is what the brown needs in both themes —
and set `isNavigationBarContrastEnforced = false` on API 29+. Without that last
line the system lays a translucent scrim over the transparent navigation bar,
which is what used to put a near-white strip under the bottom bar in the light
theme.

To make the bars recede in dark mode instead, change `DarkBarColors` in
`Theme.kt` to `surfaceContainer` / `onSurface`. That is the whole change,
though the system bar icons would then want `SystemBarStyle.auto`.

The sell flavour's scaffold sets no bar colours, is on Material defaults, and
still calls `enableEdgeToEdge()` bare; it has not been reviewed for the
Bodenschätze palette.

## Why dark mode can look like light mode

The theme itself is wired correctly: every entry point (`BuyMainActivity`,
`SellMainActivity`, `MainActivity`, `DebugMainActivity`, `MainViewController`,
web `Main.kt`) wraps content in `NewverseTheme`, nothing calls
`setDefaultNightMode`, and no activity declares `configChanges="uiMode"`. If
light and dark still look the same, it is one of these:

1. **Android Studio previews.** A plain `@Preview` has no `uiMode`, so it always
   renders light whatever the IDE is set to. Use the `@LightDarkPreview`
   annotation in `androidApp/src/debug/.../preview/ThemePreviews.kt`, which
   renders "Hell" and "Dunkel" side by side.
2. **Web.** Depending on the Compose Multiplatform version,
   `isSystemInDarkTheme()` returns `false` in the browser, so the web build stays
   light. Web is not maintained anyway.
3. **A screen with hardcoded colors.** See above — there are none left, but this
   is what to check first if a new screen misbehaves.

Quickest test on a device: set `NewverseTheme(darkTheme = true)` in the activity
temporarily. If the app turns dark, the theme is fine and the system-mode
detection is the question; if it stays light, the screen has a fixed color.

## Android window background

`AppTheme` in `androidApp/src/main/res/values/themes.xml` points
`android:windowBackground` at `@color/window_background`, which is `#F6F2E8` in
`values/colors.xml` and `#14120E` in `values-night/colors.xml`. Without that,
the window flashes white on launch in dark mode before Compose draws.

The parent stays `android:Theme.Material.Light.NoActionBar`:
`Theme.DeviceDefault.DayNight` needs API 29 and `minSdk` is 23. Compose draws
every pixel of the UI, so the window background is the only thing the platform
theme still contributes.

`AppTheme.Splash` keeps `@drawable/splash`, which is brand orange in both modes.

`enableEdgeToEdge()` is already called and adapts the status-bar icons.

## Typography Scale

| Style | Size | Usage |
|-------|------|-------|
| displayLarge | 57sp | Hero text |
| headlineMedium | 28sp | Card headers |
| titleLarge | 22sp | AppBar titles |
| bodyLarge | 16sp | Primary text |
| labelLarge | 14sp | Button text |

The flyer uses **Bricolage Grotesque** for headings and **Atkinson
Hyperlegible** for body text, both SIL OFL. They are *not* bundled yet. To add
them: drop the TTFs in `shared/src/commonMain/composeResources/font/`, build two
`FontFamily` values with `Font(Res.font.…)` inside a `@Composable` (compose-
resources' `Font()` is composable), then point `display*`, `headline*` and
`titleLarge` at Bricolage and the rest at Atkinson.

## Shapes

| Shape | Radius | Components |
|-------|--------|------------|
| small | 8dp | Buttons, Cards |
| medium | 12dp | Dialogs |
| large | 16dp | Bottom Sheets |

## Files

```
shared/src/commonMain/.../ui/theme/
├── Color.kt    # Bodenschätze palette, both schemes, deprecated aliases
├── Type.kt     # Typography
├── Shape.kt    # Shapes
└── Theme.kt    # NewverseTheme / NewverseLightTheme / NewverseDarkTheme

androidApp/src/debug/.../preview/ThemePreviews.kt  # @LightDarkPreview
androidApp/src/main/res/values/colors.xml          # window_background (day)
androidApp/src/main/res/values-night/colors.xml    # window_background (night)
```
