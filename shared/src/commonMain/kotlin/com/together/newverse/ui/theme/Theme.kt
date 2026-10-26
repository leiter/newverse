package com.together.newverse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,

    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,

    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,

    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,

    background = LightBackground,
    onBackground = LightOnBackground,

    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,

    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceBright = LightSurfaceContainerLowest,
    surfaceDim = LightSurfaceContainerHighest,

    outline = LightOutline,
    outlineVariant = LightOutlineVariant,

    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,

    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,

    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,

    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,

    background = DarkBackground,
    onBackground = DarkOnBackground,

    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,

    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceBright = DarkSurfaceContainerHighest,
    surfaceDim = DarkSurfaceContainerLowest,

    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,

    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
)

/**
 * The top bar's container colour and the colour of everything drawn on it.
 *
 * The bar is soil brown in both themes, the way the badge orange is the same
 * orange in both. That cannot be expressed with a single Material role:
 * `secondary` is an accent, so it flips from dark brown in the light scheme to
 * pale potato in the dark one, and a bar painted with it turns into the
 * brightest thing on a dark screen. The two schemes therefore reach the same
 * brown through different roles, and this pair carries the result.
 *
 * Content and container travel together so a future change cannot leave text
 * on a bar it has no contrast against. Read it as [LocalTopBarColors].
 */
@Immutable
data class TopBarColors(
    val container: Color,
    val content: Color,
)

private val LightTopBarColors = TopBarColors(
    container = LightSecondary,          // #5A3E2B soil brown
    content = LightOnSecondary,          // white, 9.7:1
)

private val DarkTopBarColors = TopBarColors(
    container = DarkSecondaryContainer,  // #5A3E2B, the same brown
    content = DarkOnSecondaryContainer,  // cream #F2DFCB, 7.5:1
)

/**
 * The top bar colours for the current theme. Always provided by
 * [NewverseTheme], [NewverseLightTheme] and [NewverseDarkTheme]; the default
 * is the light pair so a stray preview outside the theme still renders.
 */
val LocalTopBarColors = staticCompositionLocalOf { LightTopBarColors }

@Composable
private fun NewverseMaterialTheme(
    colorScheme: ColorScheme,
    topBarColors: TopBarColors,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTopBarColors provides topBarColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/**
 * Newverse App Theme — Bodenschätze palette
 *
 * Colours follow the neighbourhood flyer: paper background, brand green,
 * brand orange as badge/highlight fill, soil brown as secondary.
 * The dark variant ("night soil") keeps green and orange recognisable.
 *
 * @param darkTheme Whether to use dark theme (defaults to system preference)
 * @param content The composable content to theme
 */
@Composable
fun NewverseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    NewverseMaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        topBarColors = if (darkTheme) DarkTopBarColors else LightTopBarColors,
        content = content
    )
}

/** Light theme variant (always uses light colors) */
@Composable
fun NewverseLightTheme(content: @Composable () -> Unit) {
    NewverseMaterialTheme(
        colorScheme = LightColorScheme,
        topBarColors = LightTopBarColors,
        content = content
    )
}

/** Dark theme variant (always uses dark colors) */
@Composable
fun NewverseDarkTheme(content: @Composable () -> Unit) {
    NewverseMaterialTheme(
        colorScheme = DarkColorScheme,
        topBarColors = DarkTopBarColors,
        content = content
    )
}
