package com.together.newverse.domain.model

import kotlin.math.abs
import kotlin.math.floor

/**
 * How a selling price relates to the purchase price.
 *
 *     sellPrice = acquirePrice × markupFactor × (1 + taxRate)
 *
 * The selling price is gross (what the buyer pays), the purchase price net.
 * The markup is stored as a factor (1.45) and shown to sellers as a percentage (45 %).
 */
object ProductPricing {

    /** Gross selling price, rounded to cents. */
    fun sellPrice(acquirePrice: Double, markupFactor: Double, taxRate: Double): Double =
        roundTo(acquirePrice * markupFactor * (1.0 + taxRate), 2)

    /** The markup factor that turns [acquirePrice] into [sellPrice] at [taxRate]. */
    fun markupFactor(sellPrice: Double, acquirePrice: Double, taxRate: Double): Double =
        sellPrice / (acquirePrice * (1.0 + taxRate))

    fun factorToPercent(markupFactor: Double): Double = roundTo((markupFactor - 1.0) * 100.0, 1)

    /** Rounded so a stored factor reads 1.558, not 1.5579999999999998. */
    fun percentToFactor(markupPercent: Double): Double = roundTo(1.0 + markupPercent / 100.0, 4)

    /**
     * Reads a number typed by a person: accepts a decimal comma ("0,80") as well as
     * a dot, and surrounding spaces. Returns null for anything else, including blank.
     */
    fun parseDecimal(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()

    /** A percentage for display: "45", "12.5", never "45.0". */
    fun formatPercent(percent: Double): String {
        val rounded = roundTo(percent, 1)
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    /** Rounds half away from zero, as prices are rounded (kotlin.math.round rounds half to even). */
    private fun roundTo(value: Double, decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        val rounded = floor(abs(value) * factor + 0.5) / factor
        return if (value < 0) -rounded else rounded
    }
}
