package com.together.newverse.util

/**
 * iOS implementation of formatString.
 * Uses pure Kotlin string replacement to avoid NSString.stringWithFormat issues
 * where boxed Kotlin values (passed as Any) are treated as ObjC object pointers,
 * causing %ld/%d to read pointer addresses as numbers instead of actual values.
 */
actual fun formatString(format: String, vararg args: Any): String {
    var result = format
    for (arg in args) {
        result = result.replaceFirst(Regex("%[sd]"), arg.toString())
    }
    return result
}
