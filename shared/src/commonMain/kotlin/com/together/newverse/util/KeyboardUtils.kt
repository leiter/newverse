package com.together.newverse.util

import androidx.compose.runtime.Composable

/**
 * Platform-specific keyboard manager for hiding the soft keyboard.
 *
 * On Android, uses LocalSoftwareKeyboardController.
 * On iOS, uses UIKit's resignFirstResponder via sendAction.
 */
expect class KeyboardManager {
    fun hide()
}

/**
 * Remember a KeyboardManager instance for the current composition.
 */
@Composable
expect fun rememberKeyboardManager(): KeyboardManager
