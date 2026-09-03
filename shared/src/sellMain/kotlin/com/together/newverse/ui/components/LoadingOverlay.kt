package com.together.newverse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.splash_loading
import org.jetbrains.compose.resources.stringResource

/**
 * Loading overlay component for seller app
 */
@Composable
fun LoadingOverlay() {
    val loadingLabel = stringResource(Res.string.splash_loading)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            // Announce the busy state and collapse the scrim into one node.
            .semantics(mergeDescendants = true) {
                contentDescription = loadingLabel
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
