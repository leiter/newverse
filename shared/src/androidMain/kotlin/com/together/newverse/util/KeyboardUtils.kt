package com.together.newverse.util

import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * Android implementation of KeyboardManager.
 * Uses LocalSoftwareKeyboardController for keyboard operations.
 */
actual class KeyboardManager(
    private val keyboardController: SoftwareKeyboardController?
) {
    actual fun hide() {
        keyboardController?.hide()
    }
}

@Composable
actual fun rememberKeyboardManager(): KeyboardManager {
    val keyboardController = LocalSoftwareKeyboardController.current
    return KeyboardManager(keyboardController)
}

/**
 * The window is never resized for the keyboard here -- the app draws edge to
 * edge -- so the IME inset has to be applied by hand.
 */
actual fun Modifier.keyboardPadding(): Modifier = imePadding()
