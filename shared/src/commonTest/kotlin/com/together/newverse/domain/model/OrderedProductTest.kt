package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderedProductTest {

    private fun kg(amountCount: Double) =
        OrderedProduct(unit = "kg", amountCount = amountCount).getFormattedAmount()

    private fun default(amountCount: Double) =
        OrderedProduct(unit = "l", amountCount = amountCount).getFormattedAmount()

    @Test
    fun `kg amounts that are inexact as Double are not shown a gram short`() {
        // 1.2 * 1000 rounds to 1200 exactly, but (1.2 - 1) * 1000 lands just under 200
        // in binary, which used to truncate to "1,199".
        assertEquals("1,200 kg", kg(1.2))
        assertEquals("0,300 kg", kg(0.3))
        assertEquals("2,100 kg", kg(2.1))
    }

    @Test
    fun `default unit amounts round to two decimals without truncating`() {
        assertEquals("1,20 l", default(1.2))
        assertEquals("0,30 l", default(0.3))
    }

    @Test
    fun `piece counts show as whole numbers`() {
        assertEquals("3 stück", OrderedProduct(unit = "stück", amountCount = 3.0).getFormattedAmount())
    }

    @Test
    fun `an explicit amount string is returned unchanged`() {
        assertEquals("2 Bund", OrderedProduct(unit = "kg", amount = "2 Bund", amountCount = 1.0).getFormattedAmount())
    }

    // The buy app used to store `quantity.toString()` in `amount`, so orders already in
    // Firebase hold "1.0" — no unit, a dot, and the seller cannot tell kg from pieces.
    // amountCount is the number of record, so those are formatted rather than echoed.
    @Test
    fun `a bare number left by the old buy app is formatted, not echoed`() {
        assertEquals(
            "2,000 kg",
            OrderedProduct(unit = "kg", amount = "2.0", amountCount = 2.0).getFormattedAmount()
        )
        assertEquals(
            "0,750 kg",
            OrderedProduct(unit = "kg", amount = "0.75", amountCount = 0.75).getFormattedAmount()
        )
        assertEquals(
            "3 stück",
            OrderedProduct(unit = "stück", amount = "3.0", amountCount = 3.0).getFormattedAmount()
        )
    }

    @Test
    fun `an empty amount is formatted from the count`() {
        assertEquals("1,500 kg", OrderedProduct(unit = "kg", amount = "", amountCount = 1.5).getFormattedAmount())
    }
}
