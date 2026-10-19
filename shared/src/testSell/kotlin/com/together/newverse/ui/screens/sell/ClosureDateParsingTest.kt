package com.together.newverse.ui.screens.sell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClosureDateParsingTest {

    @Test
    fun `a German date becomes the stored key`() {
        assertEquals("20241023", parseClosureDate("23.10.2024"))
    }

    @Test
    fun `single digits are padded`() {
        assertEquals("20240105", parseClosureDate("5.1.2024"))
    }

    @Test
    fun `slashes and dashes are accepted as separators`() {
        assertEquals("20241023", parseClosureDate("23/10/2024"))
        assertEquals("20241023", parseClosureDate("23-10-2024"))
    }

    @Test
    fun `surrounding spaces do not matter`() {
        assertEquals("20241023", parseClosureDate("  23.10.2024 "))
    }

    // A half-typed date stored as a closure would take a market day away with no
    // way for the seller to see why, so parsing refuses anything doubtful.
    @Test
    fun `nonsense is rejected rather than guessed at`() {
        assertNull(parseClosureDate(""))
        assertNull(parseClosureDate("23.10"))
        assertNull(parseClosureDate("23.13.2024"))
        assertNull(parseClosureDate("32.10.2024"))
        assertNull(parseClosureDate("abc"))
        assertNull(parseClosureDate("23.10.24"))
    }

    @Test
    fun `month lengths are respected`() {
        assertNull(parseClosureDate("31.04.2024"))
        assertEquals("20240430", parseClosureDate("30.04.2024"))
    }

    @Test
    fun `february knows about leap years`() {
        assertEquals("20240229", parseClosureDate("29.02.2024"))
        assertNull(parseClosureDate("29.02.2023"))
        // 2000 is a leap year, 1900 and 2100 are not.
        assertEquals("20000229", parseClosureDate("29.02.2000"))
        assertNull(parseClosureDate("29.02.2100"))
    }

    @Test
    fun `the stored key reads back as it was typed`() {
        assertEquals("23.10.2024", formatClosureDate("20241023"))
        // Anything unexpected is shown as-is rather than sliced into nonsense.
        assertEquals("oops", formatClosureDate("oops"))
    }
}
