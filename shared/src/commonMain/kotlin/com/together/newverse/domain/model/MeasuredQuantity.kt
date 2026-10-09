package com.together.newverse.domain.model

/**
 * Where a quantity came from.
 *
 * Recorded with every amount the seller books, because the three sources are not
 * equally trustworthy: a scale reading is a measurement, a typed value is a claim,
 * and an ordered amount was never measured at all.
 */
enum class QuantitySource {
    /** Typed by the seller on the device. */
    MANUAL,

    /** Read from a connected scale. */
    SCALE,

    /** Carried over from what the buyer ordered; nobody weighed it. */
    ORDERED
}

/**
 * An amount in an article's unit, together with where it came from.
 *
 * The same type serves every place the seller puts a quantity in — confirming a
 * pickup, a walk-in sale, taking stock — so a screen can accept a value from the
 * scale or from the keyboard without knowing which it got. [QuantitySource] is
 * what survives into the records; the rest is for deciding whether to accept the
 * value at all.
 *
 * A scale reports a value continuously while the goods are still settling on the
 * platform. Only a settled value may be booked, which is what [isUsable] is for;
 * an unsettled one is fine to display as it changes.
 */
data class MeasuredQuantity(
    val value: Double,
    /** The article's unit, as [Article.unit] spells it ("kg", "Stück", …). */
    val unit: String,
    val source: QuantitySource,
    /** Epoch milliseconds the value was taken; null for a value typed by hand. */
    val measuredAt: Long? = null,
    /**
     * Whether the value stands still. A scale sets this from its own stability
     * flag; anything typed or ordered is settled by definition.
     */
    val isSettled: Boolean = true
) {
    /** Whether this amount may be booked: a positive, settled value. */
    val isUsable: Boolean get() = value > 0.0 && isSettled

    /** Whether this amount is expressed in the unit [article] is sold in. */
    fun matchesUnitOf(article: Article): Boolean =
        unit.equals(article.unit, ignoreCase = true)

    /**
     * The value as the quantity input fields hold it, so a reading can fill a field
     * the seller would otherwise type into. Mirrors [ProductPricing.parseDecimal],
     * which reads these strings back.
     */
    fun toInputString(): String = ProductPricing.formatQuantity(value)

    companion object {
        /** An amount the seller typed. */
        fun typed(value: Double, unit: String): MeasuredQuantity =
            MeasuredQuantity(value = value, unit = unit, source = QuantitySource.MANUAL)

        /** The amount a buyer ordered, carried over unmeasured. */
        fun ordered(value: Double, unit: String): MeasuredQuantity =
            MeasuredQuantity(value = value, unit = unit, source = QuantitySource.ORDERED)
    }
}
