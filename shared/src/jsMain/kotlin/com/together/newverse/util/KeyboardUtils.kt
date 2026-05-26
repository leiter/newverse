package com.together.newverse.util

import androidx.compose.runtime.Composable

actual class KeyboardManager {
    actual fun hide() {}
}

@Composable
actual fun rememberKeyboardManager(): KeyboardManager = KeyboardManager()
