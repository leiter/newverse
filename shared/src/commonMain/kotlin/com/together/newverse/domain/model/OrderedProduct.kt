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
    /**
     * What this item costs per unit, as charged — the market's adjusted price, not the
     * catalogue one. [basePrice] and [priceComponents] say how it got here.
     *
     * This is the number of record: an order snapshots what was charged and never
     * recomputes it against today's conditions. See
     * `doc/guides/market-pricing-and-access-design.md` §2 "Orders snapshot, never
     * reference".
     */
    val price: Double = 0.0,
    /**
     * The catalogue price before any market condition applied.
     *
     * `0.0` means "not recorded", which is every item written before per-market
     * pricing existed and every item at a market with no conditions. Read it through
     * [basePriceOrPrice] rather than testing the sentinel: an order from before this
     * field existed has to read as unadjusted, not as free.
     */
    val basePrice: Double = 0.0,
    /** Why [price] differs from [basePrice], as resolved when the item was priced. */
    val priceComponents: List<PriceComponent> = emptyList(),
    val amount: String = "",
    val amountCount: Double = 0.0,
    val piecesCount: Int = -1,
) {
    /** [basePrice] where it was recorded, otherwise [price] — never 0.0 for a priced item. */
    val basePriceOrPrice: Double get() = if (basePrice > 0.0) basePrice else price

    /** Whether a market condition moved this item's price. */
    val isPriceAdjusted: Boolean get() = priceComponents.isNotEmpty()

    /** This item's price and its explanation, in the shape the breakdown UI takes. */
    fun resolvedPrice(): ResolvedPrice = ResolvedPrice(
        base = basePriceOrPrice,
        gross = price,
        components = priceComponents
    )

    /**
     * Get formatted amount for display
     * Shows amount with proper formatting based on unit type
     */
    fun getFormattedAmount(): String {
        // [amount] is a display string — "1,500 kg" — and every branch below names the
        // unit, so a real one always carries letters. Orders written while the buy app
        // stored `quantity.toString()` here hold a bare number instead ("1.0"), which
        // tells the seller neither the unit nor the decimals the rest of the app shows.
        // Those are ignored rather than migrated: [amountCount] is the number of record
        // and formatting from it heals the orders already in Firebase.
        if (amount.any { it.isLetter() }) {
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
