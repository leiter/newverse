package com.together.newverse.ui.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun MainAppScaffold(onPlatformAction: (PlatformAction) -> Unit) {
    AppScaffold(onPlatformAction = onPlatformAction)
}
