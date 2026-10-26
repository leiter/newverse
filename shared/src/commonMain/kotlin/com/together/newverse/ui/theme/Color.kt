package com.together.newverse.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Bodenschätze palette (taken from the neighbourhood flyer)
// =============================================================================
// Paper   #F6F2E8  warm off-white background
// Card    #FFFDF8  slightly lighter card surface
// Ink     #1B2A16  near-black with a green cast, for text
// Muted   #4A5145  secondary text
// Green   #0A6308  brand green (headings, primary buttons)
// Orange  #FA9C4D  brand orange (badges, highlights, app icon)
// Soil    #5A3E2B  soil brown (section labels, secondary actions)
// Potato  #C9A27A  light earth tone
//
// Every text/background pair below meets WCAG AA (4.5:1) in both themes.
// The brand orange is never used as a text colour on light backgrounds
// (only 2:1); it is a container fill with Ink on top (7.2:1).
// =============================================================================

// Brand raw values
val BodenPaper = Color(0xFFF6F2E8)
val BodenCard = Color(0xFFFFFDF8)
val BodenInk = Color(0xFF1B2A16)
val BodenMuted = Color(0xFF4A5145)
val BodenGreen = Color(0xFF0A6308)
val BodenOrange = Color(0xFFFA9C4D)
val BodenSoil = Color(0xFF5A3E2B)
val BodenPotato = Color(0xFFC9A27A)
val BodenNight = Color(0xFF14120E)   // "dark soil", dark theme background

// Semantic colours (unchanged)
val ErrorRed = Color(0xFFD32F2F)
val SuccessGreen = Color(0xFF4CAF50)
val InfoBlue = Color(0xFF1976D2)
val WarningOrange = Color(0xFFF57C00)

// Neutral colours (unchanged)
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
val Gray50 = Color(0xFFFAFAFA)
val Gray100 = Color(0xFFF5F5F5)
val Gray200 = Color(0xFFEEEEEE)
val Gray300 = Color(0xFFE0E0E0)
val Gray400 = Color(0xFFBDBDBD)
val Gray500 = Color(0xFF9E9E9E)
val Gray600 = Color(0xFF757575)
val Gray700 = Color(0xFF616161)
val Gray800 = Color(0xFF424242)
val Gray900 = Color(0xFF212121)

// =============================================================================
// Light theme
// =============================================================================
val LightPrimary = BodenGreen
val LightOnPrimary = White
val LightPrimaryContainer = Color(0xFFC6E8BC)
val LightOnPrimaryContainer = Color(0xFF052E03)

val LightSecondary = BodenSoil
val LightOnSecondary = White
val LightSecondaryContainer = Color(0xFFEADBC8)
val LightOnSecondaryContainer = Color(0xFF2B1C11)

val LightTertiary = Color(0xFF8F4A0E)            // darkened orange, usable as text/button
val LightOnTertiary = White
val LightTertiaryContainer = BodenOrange         // the flyer's badge orange
val LightOnTertiaryContainer = BodenInk

val LightError = Color(0xFFB3261E)
val LightOnError = White
val LightErrorContainer = Color(0xFFF9DEDC)
val LightOnErrorContainer = Color(0xFF410E0B)

val LightBackground = BodenPaper
val LightOnBackground = BodenInk
val LightSurface = BodenPaper
val LightOnSurface = BodenInk
val LightSurfaceVariant = Color(0xFFECE5D6)
val LightOnSurfaceVariant = BodenMuted

val LightSurfaceContainerLowest = White
val LightSurfaceContainerLow = BodenCard         // elevated cards, like the flyer cards
val LightSurfaceContainer = Color(0xFFF0EBDF)    // navigation bar, menus
val LightSurfaceContainerHigh = Color(0xFFEAE4D6)
val LightSurfaceContainerHighest = Color(0xFFE4DDCD) // filled cards, text fields

val LightOutline = Color(0xFF7A7466)
val LightOutlineVariant = Color(0xFFD9D0BC)      // the flyer's card border

val LightInverseSurface = Color(0xFF2E2A22)
val LightInverseOnSurface = BodenPaper
val LightInversePrimary = Color(0xFF8FD47F)

// =============================================================================
// Dark theme ("night soil")
// =============================================================================
val DarkPrimary = Color(0xFF8FD47F)              // light leaf green
val DarkOnPrimary = Color(0xFF053902)
val DarkPrimaryContainer = BodenGreen            // the brand green as container
val DarkOnPrimaryContainer = Color(0xFFC6E8BC)

val DarkSecondary = Color(0xFFE2C3A3)            // potato
val DarkOnSecondary = Color(0xFF3F2B1D)
val DarkSecondaryContainer = BodenSoil
val DarkOnSecondaryContainer = Color(0xFFF2DFCB)

val DarkTertiary = Color(0xFFFFB77C)
val DarkOnTertiary = Color(0xFF4A2500)
val DarkTertiaryContainer = BodenOrange          // badges stay recognisably orange
val DarkOnTertiaryContainer = BodenInk

val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF8C1D18)
val DarkOnErrorContainer = Color(0xFFF9DEDC)

val DarkBackground = BodenNight
val DarkOnBackground = Color(0xFFECE6D8)
val DarkSurface = BodenNight
val DarkOnSurface = Color(0xFFECE6D8)
val DarkSurfaceVariant = Color(0xFF3A342A)
val DarkOnSurfaceVariant = Color(0xFFC9C1AE)

val DarkSurfaceContainerLowest = Color(0xFF0F0D0A)
val DarkSurfaceContainerLow = Color(0xFF1C1914)
val DarkSurfaceContainer = Color(0xFF211D17)
val DarkSurfaceContainerHigh = Color(0xFF2B261F)
val DarkSurfaceContainerHighest = Color(0xFF36302A)

val DarkOutline = Color(0xFF968E7C)
val DarkOutlineVariant = Color(0xFF4A4338)

val DarkInverseSurface = Color(0xFFECE6D8)
val DarkInverseOnSurface = Color(0xFF2E2A22)
val DarkInversePrimary = BodenGreen

// =============================================================================
// Legacy names — kept so the build stays green while screens migrate.
// They now point at the new palette. Replace usages with
// MaterialTheme.colorScheme.* (see the migration table in the guide),
// otherwise these screens ignore the dark theme.
// =============================================================================
@Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
val BrightGreen = BodenGreen
@Deprecated("Use MaterialTheme.colorScheme.onPrimaryContainer", ReplaceWith("MaterialTheme.colorScheme.onPrimaryContainer"))
val DarkGreen = Color(0xFF052E03)
@Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
val FabGreen = BodenGreen
@Deprecated("Use MaterialTheme.colorScheme.primaryContainer", ReplaceWith("MaterialTheme.colorScheme.primaryContainer"))
val LeafGreen = Color(0xFF8FD47F)
@Deprecated("Use MaterialTheme.colorScheme.tertiary", ReplaceWith("MaterialTheme.colorScheme.tertiary"))
val Orange = Color(0xFF8F4A0E)
@Deprecated("Use MaterialTheme.colorScheme.tertiaryContainer", ReplaceWith("MaterialTheme.colorScheme.tertiaryContainer"))
val SoftOrange = BodenOrange
@Deprecated("Use MaterialTheme.colorScheme.tertiaryContainer", ReplaceWith("MaterialTheme.colorScheme.tertiaryContainer"))
val BrandOrange = BodenOrange
@Deprecated("Use MaterialTheme.colorScheme.secondary", ReplaceWith("MaterialTheme.colorScheme.secondary"))
val BrownAccent = BodenSoil
@Deprecated("Use MaterialTheme.colorScheme.background", ReplaceWith("MaterialTheme.colorScheme.background"))
val OrganicBeige = BodenPaper
@Deprecated("Use MaterialTheme.colorScheme.surfaceContainerLow", ReplaceWith("MaterialTheme.colorScheme.surfaceContainerLow"))
val LightCream = BodenCard
@Deprecated("Use MaterialTheme.colorScheme.surfaceContainerHighest", ReplaceWith("MaterialTheme.colorScheme.surfaceContainerHighest"))
val SandColor = Color(0xFFE4DDCD)
@Deprecated("Use MaterialTheme.colorScheme.surfaceContainerHigh", ReplaceWith("MaterialTheme.colorScheme.surfaceContainerHigh"))
val BeigeCard = Color(0xFFEAE4D6)
@Deprecated("Use MaterialTheme.colorScheme.surfaceContainerLow", ReplaceWith("MaterialTheme.colorScheme.surfaceContainerLow"))
val SurfaceElevated = BodenCard
@Deprecated("Use MaterialTheme.colorScheme.outlineVariant", ReplaceWith("MaterialTheme.colorScheme.outlineVariant"))
val SoftBrown = Color(0xFFD9D0BC)
