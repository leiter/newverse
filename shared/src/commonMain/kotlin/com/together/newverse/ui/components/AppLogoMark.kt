package com.together.newverse.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.logo_mark
import org.jetbrains.compose.resources.painterResource

/**
 * The brand's own mark — beet, potato plant, carrot (see `art/logo/README.md`).
 *
 * The splash screen drew this correctly from `3ac2a0b`; the login, forced-login and
 * register screens kept the placeholder it replaced, a 🌿 emoji in a `primary` card.
 * The three of them now share one definition so they cannot drift apart again.
 *
 * `logo_mark.png` is a black-on-transparent template, tinted from the running scheme,
 * so one file serves both themes and both flavors. Only the width is given; the
 * 1.789:1 mark sets its own height. Decorative by default: every screen that shows it
 * spells the app or screen name out directly beneath, so announcing the mark as well
 * would read the brand out twice.
 */
@Composable
fun AppLogoMark(
    modifier: Modifier = Modifier,
    width: Dp = 180.dp,
    tint: Color = MaterialTheme.colorScheme.onBackground,
) {
    Image(
        painter = painterResource(Res.drawable.logo_mark),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.width(width)
    )
}
