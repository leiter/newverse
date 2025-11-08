package com.together.newverse.util

/**
 * Format a double value to a string with 2 decimal places
 * Platform-agnostic implementation for Kotlin Multiplatform
 */
fun Double.formatPrice(): String {
    val rounded = (this * 100).toLong() / 100.0
    val intPart = rounded.toLong()
    val decimalPart = ((rounded - intPart) * 100).toLong()
    return "$intPart.${decimalPart.toString().padStart(2, '0')}"
}
