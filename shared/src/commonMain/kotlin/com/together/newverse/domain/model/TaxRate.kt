package com.together.newverse.domain.model

/**
 * Common tax rates for products.
 * Stored as raw Double in Firebase for forward compatibility.
 */
enum class TaxRate(val rate: Double, val displayName: String) {
    ZERO(0.0, "0%"),
    REDUCED(0.07, "7%"),
    STANDARD(0.19, "19%");

    companion object {
        val default = REDUCED

        fun fromRate(rate: Double): TaxRate =
            entries.find { it.rate == rate } ?: REDUCED
    }
}
