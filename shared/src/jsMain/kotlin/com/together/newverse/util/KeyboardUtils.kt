package com.together.newverse.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual class KeyboardManager {
    actual fun hide() {}
}

@Composable
actual fun rememberKeyboardManager(): KeyboardManager = KeyboardManager()

/** A browser has no soft keyboard inset to respect. */
actual fun Modifier.keyboardPadding(): Modifier = this

/** A browser has no soft keyboard to cover anything. */
@Composable
actual fun isKeyboardHidingBottomBar(): Boolean = false
