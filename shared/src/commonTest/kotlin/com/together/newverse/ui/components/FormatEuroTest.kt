package com.together.newverse.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The breakdown's figures have to read as money and add up on screen, so the
 * formatter is pinned rather than eyeballed.
 */
class FormatEuroTest {

    @Test
    fun `a plain amount reads as euros and cents`() {
        assertEquals("2,80 €", formatEuro(2.80))
        assertEquals("3,20 €", formatEuro(3.20))
    }

    @Test
    fun `cents are always two digits`() {
        assertEquals("2,05 €", formatEuro(2.05))
        assertEquals("2,00 €", formatEuro(2.0))
        assertEquals("0,09 €", formatEuro(0.09))
    }

    @Test
    fun `whole euros above ten are not truncated`() {
        assertEquals("12,50 €", formatEuro(12.5))
        assertEquals("100,00 €", formatEuro(100.0))
    }

    // A component is a change to the price, so its sign is the point of it.
    @Test
    fun `a signed amount carries its direction`() {
        assertEquals("+0,25 €", formatEuro(0.25, signed = true))
        assertEquals("−0,15 €", formatEuro(-0.15, signed = true))
    }

    @Test
    fun `an unsigned amount never shows a minus`() {
        assertEquals("0,15 €", formatEuro(-0.15))
    }

    @Test
    fun `floating point noise does not leak into the display`() {
        // 0.1 + 0.2 is 0.30000000000000004 in binary floating point.
        assertEquals("0,30 €", formatEuro(0.1 + 0.2))
    }
}
