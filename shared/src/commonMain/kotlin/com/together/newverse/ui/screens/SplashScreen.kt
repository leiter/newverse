package com.together.newverse.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.together.newverse.ui.state.InitializationStep
import com.together.newverse.ui.state.localizedMessage
import kotlinx.coroutines.delay
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long the splash stays up at the very least, however fast initialization
 * finishes.
 *
 * Without a floor the splash is a flash: on a warm start with a cached session the
 * whole of initializeApp resolves in well under the time it takes to recognise a
 * logo, so the first thing a buyer sees is a shape that is already gone. The point
 * of this screen is not the progress text — it is the mark and the name, and they
 * need a beat to land.
 *
 * 1600ms is long enough to read the lockup and short enough not to feel like a
 * stall. It is a floor, not a delay: a slow start is never made slower, because the
 * wait runs alongside initialization rather than after it.
 */
val SPLASH_MINIMUM_VISIBLE = 1600.milliseconds

/**
 * True while the splash should stay on screen: either the app is still starting up,
 * or it finished so quickly that [SPLASH_MINIMUM_VISIBLE] has not elapsed yet.
 *
 * Lives here rather than in either flavor's scaffold so the buy and the sell app
 * cannot drift apart on how long their first screen lasts. Call it unconditionally
 * at the point the scaffold decides what to draw — the timer starts with the first
 * composition, so it is already running while auth resolves.
 */
@Composable
fun rememberSplashVisible(isInitializing: Boolean): Boolean {
    var minimumElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MINIMUM_VISIBLE)
        minimumElapsed = true
    }
    return isInitializing || !minimumElapsed
}

/**
 * Splash screen shown during app initialization
 * Shows authentication check and data loading progress
 */
@Composable
fun SplashScreen(
    initializationStep: InitializationStep = InitializationStep.NotStarted,
    modifier: Modifier = Modifier
) {
    var dots by remember { mutableStateOf("") }

    // Animate loading dots
    LaunchedEffect(Unit) {
        while (true) {
            dots = when (dots.length) {
                0 -> "."
                1 -> ".."
                2 -> "..."
                else -> ""
            }
            delay(500)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // The logo's own mark — beet, potato plant, carrot — above the name, which
            // is the lockup the brand is drawn as (see art/logo/README.md). The bare
            // mark rather than the launcher tile: a rounded square inside the page
            // would be an icon of the app sitting in the app, and the orange tile
            // fights the background in both themes.
            //
            // The PNG is a black-on-transparent template and is tinted here, so the
            // mark follows the scheme instead of carrying a baked colour that only
            // works on one of them. Only the width is fixed; the 1.789:1 mark sets
            // its own height.
            Image(
                painter = painterResource(Res.drawable.logo_mark),
                // Decorative: the app name is spelled out directly beneath it, so
                // announcing the mark as well would read the brand out twice.
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.width(220.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // App Name
            Text(
                text = stringResource(Res.string.app_name),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = stringResource(Res.string.app_tagline),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status text
            Text(
                text = initializationStep.localizedMessage() + dots,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}
