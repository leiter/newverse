package com.together.newverse.ui.navigation

import androidx.compose.runtime.Composable

/**
 * Common entry point for the App Scaffold, implemented differently for Buy and Sell flavors.
 */
@Composable
expect fun MainAppScaffold(onPlatformAction: (PlatformAction) -> Unit)
