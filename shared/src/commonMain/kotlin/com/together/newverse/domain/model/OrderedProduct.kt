package com.together.newverse.domain.model

import com.together.newverse.util.formatPrice

/**
 * Represents a product within an order
 */
data class OrderedProduct(
    /** Usually empty: the buyer app never assigns it. */
    val id: String = "",
    /**
     * The database id of the ordered [Article] — not its BNN article number, despite
     * the name. This is how the buyer app has always filled it.
     */
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
     * Format double with specified number of decimal places.
     *
     * Rounds in integer space (like [Money.toCents]) rather than splitting a rounded
     * Double into whole/fraction parts: that split re-introduces binary floating-point
     * noise, e.g. 1.2 kg rendered as "1,199" because (1.2 - 1) * 1000 lands just under 200.
     */
    private fun Double.formatWithDecimals(decimals: Int): String {
        val multiplier = when (decimals) {
            2 -> 100L
            3 -> 1000L
            else -> 100L
        }
        val scaled = Money.roundHalfAwayFromZero(this * multiplier)
        val whole = scaled / multiplier
        val fraction = (scaled % multiplier).toString().padStart(decimals, '0')
        return "$whole.$fraction"
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
