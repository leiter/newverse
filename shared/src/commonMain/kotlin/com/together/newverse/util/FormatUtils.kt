package com.together.newverse.util

/**
 * Platform-specific string formatting.
 * Replaces %s and %d placeholders with provided arguments.
 *
 * On Android: Uses String.format()
 * On iOS: Uses NSString stringWithFormat with converted placeholders
 */
expect fun formatString(format: String, vararg args: Any): String

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
/**
 * Derive an acquisition price from a selling price using a markup factor.
 * Default markup of 30% (factor = 1.30) is typical for organic produce.
 *
 * Example: 3.90.acquirePriceFromMarkup() → 3.00
 */
fun Double.acquirePriceFromMarkup(markupPercent: Double = 30.0): Double =
    this / (1.0 + markupPercent / 100.0)

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
