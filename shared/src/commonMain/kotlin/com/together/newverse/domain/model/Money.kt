package com.together.newverse.domain.model

import kotlin.math.abs
import kotlin.math.floor

/**
 * Amounts for the books are whole cents (Long), never Double euros: sums of cents
 * are exact, so net + VAT always adds up to gross, and a cancellation cancels a sale
 * to the cent.
 */
object Money {

    /** Euros to cents, rounded half away from zero. */
    fun toCents(euros: Double): Long = roundHalfAwayFromZero(euros * 100.0)

    /**
     * Rounds half away from zero, so that rounding a negative amount gives exactly
     * the negation of rounding the positive one (a cancellation mirrors its sale).
     */
    fun roundHalfAwayFromZero(value: Double): Long {
        // The small epsilon absorbs binary noise such as 0.1 * 3 * 100 = 30.000000000000004
        // or 1.005 * 100 = 100.49999999999999, which should round as the decimal it stands for.
        val rounded = floor(abs(value) + 0.5 + 1e-9).toLong()
        return if (value < 0) -rounded else rounded
    }
}
