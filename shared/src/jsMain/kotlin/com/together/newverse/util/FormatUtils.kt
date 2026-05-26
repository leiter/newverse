package com.together.newverse.util

actual fun formatString(format: String, vararg args: Any): String {
    var result = format
    args.forEach { arg ->
        result = result.replaceFirst("%s", arg.toString())
            .replaceFirst("%d", arg.toString())
    }
    return result
}
