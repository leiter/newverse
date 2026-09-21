package com.together.newverse.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SalesCsvTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    private fun millis(month: Int, day: Int, hour: Int = 17) =
        LocalDateTime(2026, month, day, hour, 30).toInstant(berlin).toEpochMilliseconds()

    private val apple = SaleLine("apple", "112108", "Apfel Topaz", "kg", 1.62, 319, 0.07, 196)
    private val sweet = SaleLine("sweet", "122654", "Süßkartoffel", "kg", 1.0, 462, 0.19, null)

    private val sale = Sale(
        id = "-P1abc", orderId = "-O9xyz", confirmedAt = millis(9, 24), pickUpDate = millis(9, 24, 16),
        lines = listOf(apple, sweet)
    )

    private fun rows(csv: String) = csv.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun `starts with a byte order mark and a header`() {
        val csv = SalesCsv.write(emptyList(), berlin)

        assertTrue(csv.startsWith("﻿"), "Excel needs the BOM to read umlauts as UTF-8")
        assertEquals(
            listOf("Datum;Beleg;Storno zu Beleg;Bestellung;Artikelnr;Artikel;Menge;Einheit;" +
                "Einzelpreis brutto;MwSt-Satz %;Netto;MwSt;Brutto"),
            rows(csv)
        )
    }

    @Test
    fun `one row per sold article in German number format`() {
        val rows = rows(SalesCsv.write(listOf(sale), berlin))

        assertEquals(
            "24.09.2026;V-P1abc;;B-O9xyz;112108;Apfel Topaz;1,62;kg;3,19;7;4,83;0,34;5,17",
            rows[1]
        )
        assertEquals(
            "24.09.2026;V-P1abc;;B-O9xyz;122654;Süßkartoffel;1;kg;4,62;19;3,88;0,74;4,62",
            rows[2]
        )
    }

    @Test
    fun `lines end with CRLF`() {
        val csv = SalesCsv.write(listOf(sale), berlin)

        assertEquals(3, csv.split("\r\n").count { it.isNotEmpty() })
        assertTrue(csv.replace("\r\n", "").none { it == '\n' || it == '\r' })
    }

    @Test
    fun `a cancellation is a negative row pointing at its sale`() {
        val storno = sale.reversal(confirmedAt = millis(9, 25)).copy(id = "-P2def")

        val rows = rows(SalesCsv.write(listOf(storno, sale), berlin))

        assertEquals(5, rows.size)
        assertTrue(rows[1].startsWith("24.09.2026;V-P1abc;;"), "sorted by date: the sale first")
        assertEquals(
            "25.09.2026;V-P2def;V-P1abc;B-O9xyz;112108;Apfel Topaz;-1,62;kg;3,19;7;-4,83;-0,34;-5,17",
            rows[3]
        )
    }

    @Test
    fun `amount columns add up to the Abrechnung totals`() {
        val other = Sale("-P3", "-O3", millis(9, 26), millis(9, 26), listOf(apple.copy(quantity = 2.5)))
        val sales = listOf(sale, other)

        val rows = rows(SalesCsv.write(sales, berlin)).drop(1).map { it.split(";") }
        fun sum(column: Int) = rows.sumOf { it[column].replace(",", "").toLong() }

        assertEquals(sales.sumOf { it.netCents }, sum(10))
        assertEquals(sales.sumOf { it.vatCents }, sum(11))
        assertEquals(sales.sumOf { it.grossCents }, sum(12))
    }

    @Test
    fun `dates are the seller's local dates`() {
        // 23:30 in Berlin on 30 Sep is still 30 Sep, although it is 21:30 UTC.
        val late = sale.copy(confirmedAt = millis(9, 30, 23))

        assertTrue(rows(SalesCsv.write(listOf(late), berlin))[1].startsWith("30.09.2026;"))
    }

    // --- fields ---

    @Test
    fun `fields with separators or quotes are quoted`() {
        assertEquals("\"Äpfel; lose\"", SalesCsv.escape("Äpfel; lose"))
        assertEquals("\"12\"\" Zoll\"", SalesCsv.escape("12\" Zoll"))
        assertEquals("Apfel, rot", SalesCsv.escape("Apfel, rot"))
    }

    @Test
    fun `text that a spreadsheet would run as a formula is defused`() {
        val evil = sale.copy(lines = listOf(apple.copy(productName = "=HYPERLINK(\"x\")", productId = "-1")))

        val row = rows(SalesCsv.write(listOf(evil), berlin))[1]

        assertTrue("'=HYPERLINK" in row, row)
        assertTrue(";'-1;" in row, row)
    }

    @Test
    fun `ids get a letter so Excel does not read them as formulas`() {
        assertEquals("V-P1abc", SalesCsv.receiptNumber("-P1abc"))
    }

    @Test
    fun `quantities show up to grams without trailing zeros`() {
        assertEquals("1,62", SalesCsv.formatQuantity(1.62))
        assertEquals("2", SalesCsv.formatQuantity(2.0))
        assertEquals("0,005", SalesCsv.formatQuantity(0.005))
        assertEquals("-0,5", SalesCsv.formatQuantity(-0.5))
        assertEquals("0,1", SalesCsv.formatQuantity(0.1 + 0.2 - 0.2))
    }

    @Test
    fun `tax rates show as percent`() {
        assertEquals("7", SalesCsv.formatRate(0.07))
        assertEquals("19", SalesCsv.formatRate(0.19))
        assertEquals("0", SalesCsv.formatRate(0.0))
        assertEquals("5,5", SalesCsv.formatRate(0.055))
    }

    @Test
    fun `file names name the period`() {
        assertEquals("Verkaeufe_2026-KW09.csv", SalesCsv.fileName(BookingPeriod.Week(2026, 9)))
        assertEquals("Verkaeufe_2026-09.csv", SalesCsv.fileName(BookingPeriod.Month(2026, 9)))
    }
}
