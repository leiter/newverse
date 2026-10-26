package com.together.newverse.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun SystemBarIcons(
    lightStatusIcons: Boolean,
    lightNavIcons: Boolean,
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            // The flags name the *bar*, not the icons: a light bar gets dark icons.
            isAppearanceLightStatusBars = !lightStatusIcons
            isAppearanceLightNavigationBars = !lightNavIcons
        }
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
