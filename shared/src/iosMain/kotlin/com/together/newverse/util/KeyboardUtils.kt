package com.together.newverse.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/**
 * Nothing to do: `ComposeUIViewController` runs with the default
 * `OnFocusBehavior.FocusableAboveKeyboard`, which already lifts the focused
 * view above the keyboard for the whole scene, dialogs included. Adding the IME
 * inset on top would shift a focused field twice.
 */
actual fun Modifier.keyboardPadding(): Modifier = this

/**
 * Never, on this platform: `OnFocusBehavior.FocusableAboveKeyboard` pans the whole
 * scene, so the bottom bar rides up with it and stays reachable.
 */
@Composable
actual fun isKeyboardHidingBottomBar(): Boolean = false
