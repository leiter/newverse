package com.together.newverse.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window width classes following Material 3 breakpoints (600dp / 840dp).
 *
 * All adaptive layout decisions key off this class — never off orientation.
 * Phones are locked to portrait and therefore always [Compact]; tablets report
 * [Medium] (portrait) or [Expanded] (landscape); the web target follows the
 * browser window width.
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded;

    companion object {
        fun fromWidth(width: Dp): WindowWidthClass = when {
            width < 600.dp -> Compact
            width < 840.dp -> Medium
            else -> Expanded
        }
    }
}

val LocalWindowWidthClass = compositionLocalOf { WindowWidthClass.Compact }

/**
 * Measures the available window size and provides [LocalWindowWidthClass] to [content].
 * Must wrap the app root (AppScaffold) so every screen can read the width class.
 */
@Composable
fun ProvideWindowWidthClass(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalWindowWidthClass provides WindowWidthClass.fromWidth(maxWidth)
        ) {
            content()
        }
    }
}

/**
 * Shared sizing constants for adaptive layouts, so all screens adapt consistently.
 */
object AdaptiveDefaults {
    /** Maximum width for form/detail content (login, register, profile, create). */
    val FormMaxWidth = 480.dp

    /** Minimum item width for adaptive grids — yields 1 column on phones, 2+ on tablets. */
    val ListItemMinWidth = 300.dp

    /** Maximum width for single-column row lists (e.g. contacts) on wide screens. */
    val ListMaxWidth = 640.dp

    /** Maximum width for feed-style screens (hero + product grid) on wide screens. */
    val FeedMaxWidth = 960.dp
}

/**
 * Caps content width at [max] and centers it horizontally.
 *
 * On Compact windows this is a no-op (screens are narrower than [max]); on
 * Medium/Expanded windows it prevents forms and detail content from stretching
 * across the full screen width.
 */
fun Modifier.constrainedContentWidth(max: Dp = AdaptiveDefaults.FormMaxWidth): Modifier =
    fillMaxWidth()
        .wrapContentWidth(align = Alignment.CenterHorizontally)
        .widthIn(max = max)
