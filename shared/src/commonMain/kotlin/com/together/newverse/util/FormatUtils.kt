package com.together.newverse.util

/**
 * Format a double value to a string with 2 decimal places
 * Platform-agnostic implementation for Kotlin Multiplatform
 *
 * Uses German number formatting:
 * - Comma (,) as decimal separator
 * - Dot (.) as thousands separator
 *
 * Examples:
 * - 1234.56 -> "1.234,56"
 * - 99.99 -> "99,99"
 * - 0.5 -> "0,50"
 */
fun Double.formatPrice(): String {
    // Round to 2 decimal places
    val rounded = (this * 100).toLong() / 100.0
    val intPart = rounded.toLong()
    val decimalPart = ((rounded - intPart) * 100).toLong()

    // Format integer part with thousands separator
    val formattedIntPart = intPart.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()

    // Combine with decimal part using comma separator
    return "$formattedIntPart,${decimalPart.toString().padStart(2, '0')}"
}
