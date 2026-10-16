package com.together.newverse.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the formatting used on iOS and JS, where there is no String.format.
 * The positional cases are the regression: "Fehlende Angaben: %1$s" reached the
 * profile screen unsubstituted, so the buyer read the specifier itself.
 */
class SubstituteFormatArgsTest {

    private fun format(format: String, vararg args: Any) = substituteFormatArgs(format, args)

    @Test
    fun `positional specifiers take their argument`() {
        assertEquals(
            "Fehlende Angaben: Name, Telefon",
            format("Fehlende Angaben: %1\$s", "Name, Telefon")
        )
        assertEquals("3 ungelesen", format("%1\$d ungelesen", 3))
    }

    @Test
    fun `bare specifiers take their argument in order`() {
        assertEquals("2 Artikel", format("%d Artikel", 2))
        assertEquals("Bestellt: Tomaten 500 g", format("Bestellt: %s %s", "Tomaten", "500 g"))
    }

    @Test
    fun `positional specifiers are read by their own index, in any order`() {
        assertEquals(
            "KW 42/2026 · Mo – Do",
            format("KW %1\$d/%2\$d · %3\$s – %4\$s", 42, 2026, "Mo", "Do")
        )
        assertEquals("b a", format("%2\$s %1\$s", "a", "b"))
    }

    @Test
    fun `one argument can be used more than once`() {
        assertEquals("Terra, Terra", format("%1\$s, %1\$s", "Terra"))
    }

    @Test
    fun `a double percent is a literal percent sign`() {
        assertEquals("Hochladen: 42%", format("Hochladen: %d%%", 42))
    }

    @Test
    fun `a specifier with no argument is left in place`() {
        assertEquals("Verbunden mit: %1\$s", format("Verbunden mit: %1\$s"))
        assertEquals("a %s", format("%s %s", "a"))
    }

    @Test
    fun `text without specifiers is unchanged`() {
        assertEquals("Profil unvollständig", format("Profil unvollständig"))
        assertEquals("100 % Bio", format("100 % Bio"))
    }
}
