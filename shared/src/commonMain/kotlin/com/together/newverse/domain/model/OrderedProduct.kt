package com.together.newverse.domain.model

import com.together.newverse.util.formatPrice

/**
 * Represents a product within an order
 */
data class OrderedProduct(
    val id: String = "",
    val productId: String = "-1",
    val productName: String = "",
    val unit: String = "",
    val price: Double = 0.0,
    val amount: String = "",
    val amountCount: Double = 0.0,
    val piecesCount: Int = -1,
) {
    /**
     * Get formatted amount for display
     * Shows amount with proper formatting based on unit type
     */
    fun getFormattedAmount(): String {
        if (amount.isNotEmpty()) {
            return amount
        }

        // Format based on unit type
        return when (unit.lowercase()) {
            "kg" -> {
                // For kg, show 3 decimal places with comma separator
                val formatted = amountCount.formatWithDecimals(3).replace(".", ",")
                "$formatted $unit"
            }
            "stück", "stk" -> {
                // For pieces, show whole number
                "${amountCount.toInt()} $unit"
            }
            else -> {
                // Default: show with 2 decimal places
                val formatted = amountCount.formatWithDecimals(2).replace(".", ",")
                "$formatted $unit"
            }
        }
    }

    /**
     * Format double with specified number of decimal places
     */
    private fun Double.formatWithDecimals(decimals: Int): String {
        val multiplier = when (decimals) {
            2 -> 100.0
            3 -> 1000.0
            else -> 100.0
        }
        val rounded = kotlin.math.round(this * multiplier) / multiplier

        return when (decimals) {
            2 -> {
                val whole = rounded.toInt()
                val fraction = ((rounded - whole) * 100).toInt()
                "$whole.${fraction.toString().padStart(2, '0')}"
            }
            3 -> {
                val whole = rounded.toInt()
                val fraction = ((rounded - whole) * 1000).toInt()
                "$whole.${fraction.toString().padStart(3, '0')}"
            }
            else -> rounded.toString()
        }
    }

    /**
     * Calculate total price for this product (price per unit * amount)
     */
    fun getTotalPrice(): Double {
        return price * amountCount
    }

    /**
     * Get formatted total price
     */
    fun getFormattedTotalPrice(): String {
        return getTotalPrice().formatPrice()
    }

    /**
     * Get formatted price per unit
     */
    fun getFormattedPricePerUnit(): String {
        return price.formatPrice()
    }
}
