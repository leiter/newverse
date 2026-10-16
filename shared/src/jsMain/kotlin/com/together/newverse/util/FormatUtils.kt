package com.together.newverse.util

actual fun formatString(format: String, vararg args: Any): String =
    substituteFormatArgs(format, args)
