package com.together.newverse.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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

/**
 * The keyboard draws over the bottom bar here, because the window is never
 * resized for it.
 *
 * Read through derivedStateOf so the caller recomposes when the answer flips and
 * not on every frame of the keyboard's slide: the inset animates, the boolean
 * does not.
 */
@Composable
actual fun isKeyboardHidingBottomBar(): Boolean {
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val hiding by remember(density, ime) { derivedStateOf { ime.getBottom(density) > 0 } }
    return hiding
}
