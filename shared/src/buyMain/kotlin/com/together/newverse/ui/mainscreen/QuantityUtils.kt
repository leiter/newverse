package com.together.newverse.ui.mainscreen

/**
 * Formats quantity for display based on whether it's weight-based or piece-based
 */
internal fun formatQuantity(quantity: Double, isWeightBased: Boolean): String {
    return if (isWeightBased) {
        if (quantity == 0.0) {
            "0"
        } else {
            // Format with 3 decimal places and trim trailing zeros
            val formatted = (quantity * 1000).toInt() / 1000.0
            val parts = formatted.toString().split('.')
            if (parts.size == 2) {
                val intPart = parts[0]
                val decPart = parts[1].take(3).trimEnd('0')
                if (decPart.isEmpty()) intPart else "$intPart.$decPart"
            } else {
                parts[0]
            }
        }
    } else {
        quantity.toInt().toString()
    }
}
