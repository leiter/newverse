package com.together.newverse.util

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication

/**
 * iOS implementation of KeyboardManager.
 * Uses UIKit's resignFirstResponder to dismiss the keyboard.
 */
actual class KeyboardManager {
    @OptIn(ExperimentalForeignApi::class)
    actual fun hide() {
        // Send resignFirstResponder action to dismiss keyboard
        // This is the standard iOS way to dismiss the keyboard
        UIApplication.sharedApplication.sendAction(
            action = NSSelectorFromString("resignFirstResponder"),
            to = null,
            from = null,
            forEvent = null
        )
    }
}

@Composable
actual fun rememberKeyboardManager(): KeyboardManager {
    return KeyboardManager()
}
