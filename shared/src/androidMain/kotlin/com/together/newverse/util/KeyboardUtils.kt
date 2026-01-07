package com.together.newverse.util

import androidx.compose.runtime.Composable
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
