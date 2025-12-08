package com.together.newverse.util

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

/**
 * iOS implementation of formatString.
 * Converts Java-style format specifiers to iOS format specifiers and uses NSString.stringWithFormat
 */
actual fun formatString(format: String, vararg args: Any): String {
    // Convert Java format specifiers to iOS format specifiers
    // %s -> %@ (for strings/objects)
    // %d -> %ld (for integers)
    // %f -> %f (same for floats)
    var iosFormat = format
        .replace("%s", "%@")
        .replace("%d", "%ld")

    // For simple cases with 1-3 arguments, use direct formatting
    return when (args.size) {
        0 -> iosFormat
        1 -> NSString.stringWithFormat(iosFormat, args[0])
        2 -> NSString.stringWithFormat(iosFormat, args[0], args[1])
        3 -> NSString.stringWithFormat(iosFormat, args[0], args[1], args[2])
        else -> {
            // Fallback: manual replacement for more arguments
            var result = format
            args.forEach { arg ->
                result = result.replaceFirst("%s", arg.toString())
                    .replaceFirst("%d", arg.toString())
                    .replaceFirst("%@", arg.toString())
                    .replaceFirst("%ld", arg.toString())
            }
            result
        }
    }
}
