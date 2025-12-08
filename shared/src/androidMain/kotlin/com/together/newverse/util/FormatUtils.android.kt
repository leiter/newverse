package com.together.newverse.util

/**
 * Android implementation of formatString using String.format()
 */
actual fun formatString(format: String, vararg args: Any): String {
    return String.format(format, *args)
}
