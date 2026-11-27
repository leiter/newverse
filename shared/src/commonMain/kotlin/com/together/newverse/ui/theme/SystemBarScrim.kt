package com.together.newverse.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A brown band exactly the height of the system navigation bar, drawn at the
 * bottom edge of the window.
 *
 * The app draws edge to edge with a transparent navigation bar, so whatever is
 * painted at the bottom edge *is* the navigation bar's colour. In the stacked
 * layout that is the brown bottom bar. At Expanded width the bottom bar gives way
 * to the side rail, and without this band the cream page showed through instead —
 * the same app framed in brown in portrait and in cream in landscape.
 *
 * Put it in the Scaffold's `bottomBar` slot, not over the content: the slot's
 * height is what the Scaffold hands the content as bottom padding, so the band
 * keeps the page off the navigation bar the way the bottom bar did. Give it the
 * bar container colour and tell [SystemBarIcons] the icons sit on something dark.
 */
@Composable
fun SystemNavigationBarScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalBarColors.current.container)
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
    )
}
