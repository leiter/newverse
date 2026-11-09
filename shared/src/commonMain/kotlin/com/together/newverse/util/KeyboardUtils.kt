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
 * **This depends on a manifest flag with no reference in any Kotlin file.** Both
 * launcher activities set `android:windowSoftInputMode="adjustResize"`, which is
 * the documented pairing for `enableEdgeToEdge()`. It does not make the window
 * resize — `decorFitsSystemWindows=false` still prevents that, so everything
 * above still holds — it makes the IME inset *dispatched consistently, from the
 * first keyboard show*. Without it the window is `adjustUnspecified`, the
 * framework decides on the first show and keeps its decision, and the first
 * focused field on a screen is padded by the bottom bar's height **and** the
 * keyboard's instead of the larger of the two. That was a real bug on the
 * buyer's profile, seen on a Pixel 2 and a Pixel 7a and fixed on 2026-11-09;
 * `doc/plans/TODO.md` has the account. **Deleting the flag brings it back, and
 * nothing in the Kotlin will fail to compile when you do.**
 *
 * iOS needs the opposite. `ComposeUIViewController`'s default
 * [OnFocusBehavior.FocusableAboveKeyboard] already lifts the focused view above
 * the keyboard, and dialogs there are drawn inside the same Compose scene rather
 * than in a window of their own, so padding here as well would move a focused
 * field twice. Hence a no-op, not an oversight.
 */
expect fun Modifier.keyboardPadding(): Modifier

/**
 * Whether the soft keyboard is currently sitting over the bottom bar, leaving
 * whatever the bar holds out of the buyer's reach.
 *
 * The counterpart to [keyboardPadding], and platform-dependent for the same
 * reason. On Android the window is not resized, so the keyboard draws straight
 * over the bottom bar and the bar's items cannot be tapped. On iOS the whole
 * scene pans up instead, carrying the bar with it, so the bar is never covered
 * and the answer is simply no.
 *
 * This is a question about the bar, not about the keyboard: ask it only to decide
 * whether something the bottom bar offers has to be offered somewhere else.
 */
@Composable
expect fun isKeyboardHidingBottomBar(): Boolean
