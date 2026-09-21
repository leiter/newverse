package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProductPricingTest {

    @Test
    fun `sell price applies markup and tax`() {
        assertEquals(3.21, ProductPricing.sellPrice(2.0, 1.5, 0.07))
    }

    @Test
    fun `sell price rounds half up to cents`() {
        // 1.0 × 1.0 × 1.125 = 1.125 → 1.13 (round-half-even would give 1.12)
        assertEquals(1.13, ProductPricing.sellPrice(1.0, 1.0, 0.125))
    }

    @Test
    fun `markup factor inverts the sell price`() {
        assertEquals(1.25, ProductPricing.markupFactor(1.19, 0.80, 0.19), 1e-9)
    }

    @Test
    fun `percent and factor convert both ways`() {
        assertEquals(45.0, ProductPricing.factorToPercent(1.45))
        assertEquals(1.45, ProductPricing.percentToFactor(45.0), 1e-9)
    }

    @Test
    fun `factor from a percent has no floating point noise`() {
        assertEquals(1.558, ProductPricing.percentToFactor(55.8))
    }

    @Test
    fun `decimal comma and dot are both read`() {
        assertEquals(0.8, ProductPricing.parseDecimal("0,80"))
        assertEquals(0.8, ProductPricing.parseDecimal(" 0.80 "))
        assertEquals(12.0, ProductPricing.parseDecimal("12"))
    }

    @Test
    fun `blank or malformed input is not a number`() {
        assertNull(ProductPricing.parseDecimal(""))
        assertNull(ProductPricing.parseDecimal("1,2,3"))
        assertNull(ProductPricing.parseDecimal("abc"))
    }

    @Test
    fun `percent is formatted without a trailing zero`() {
        assertEquals("45", ProductPricing.formatPercent(45.0))
        assertEquals("12.5", ProductPricing.formatPercent(12.5))
        assertEquals("-10", ProductPricing.formatPercent(-10.0))
    }
}
