package com.together.newverse.util

import com.together.newverse.domain.model.Money
import kotlin.math.abs

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
fun Double.formatPrice(): String {
    // Round to whole cents. Truncating showed about one price in twenty a cent too
    // low: 19.99 * 100 is 1998.9999… as a Double.
    val cents = Money.toCents(this)
    val sign = if (cents < 0) "-" else ""
    val absCents = abs(cents)

    // Format integer part with thousands separator
    val formattedIntPart = (absCents / 100).toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()

    // Combine with decimal part using comma separator
    return "$sign$formattedIntPart,${(absCents % 100).toString().padStart(2, '0')}"
}
