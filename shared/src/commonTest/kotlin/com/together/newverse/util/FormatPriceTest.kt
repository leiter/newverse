package com.together.newverse.util

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatPriceTest {

    @Test
    fun `prices that are inexact as Double are not shown a cent too low`() {
        // All of these are x.xx9999… in binary and used to truncate.
        assertEquals("19,99", 19.99.formatPrice())
        assertEquals("0,29", 0.29.formatPrice())
        assertEquals("4,35", 4.35.formatPrice())
        assertEquals("1,13", 1.13.formatPrice())
    }

    @Test
    fun `every price from 0 to 999,99 is shown exactly`() {
        for (cents in 0..99_999) {
            val expected = "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
            assertEquals(expected, (cents / 100.0).formatPrice().replace(".", ""), "cents=$cents")
        }
    }

    @Test
    fun `thousands are grouped and fractions of a cent rounded`() {
        assertEquals("1.234,50", 1234.5.formatPrice())
        assertEquals("3,10", 3.103.formatPrice())
        assertEquals("3,11", 3.105.formatPrice())
        assertEquals("0,00", 0.0.formatPrice())
    }
}
