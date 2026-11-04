package com.together.newverse.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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

/**
 * Keeps content clear of the soft keyboard, so a scrollable screen can scroll a
 * focused field out from behind it.
 *
 * Android needs this. The app draws edge to edge, so the window is *not* resized
 * when the keyboard opens: without the inset the lower half of a form sits
 * behind the keyboard and the scroll range ends there too, leaving the field
 * unreachable. Padding by the IME inset shrinks the viewport, and that is what
 * gives the existing scroll container the range to bring the field into view.
 *
 * iOS needs the opposite. `ComposeUIViewController`'s default
 * [OnFocusBehavior.FocusableAboveKeyboard] already lifts the focused view above
 * the keyboard, and dialogs there are drawn inside the same Compose scene rather
 * than in a window of their own, so padding here as well would move a focused
 * field twice. Hence a no-op, not an oversight.
 */
expect fun Modifier.keyboardPadding(): Modifier
