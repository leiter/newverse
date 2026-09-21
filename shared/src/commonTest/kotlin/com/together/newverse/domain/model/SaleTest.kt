package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaleTest {

    private fun line(
        quantity: Double = 1.0,
        unitPriceCents: Long = 100,
        taxRate: Double = TaxRate.REDUCED.rate,
        acquirePriceCents: Long? = null
    ) = SaleLine(
        articleId = "a1",
        productId = "112108",
        productName = "Apfel Topaz",
        unit = "kg",
        quantity = quantity,
        unitPriceCents = unitPriceCents,
        taxRate = taxRate,
        acquirePriceCents = acquirePriceCents
    )

    private fun sale(vararg lines: SaleLine) = Sale(
        id = "s1", orderId = "o1", confirmedAt = 1_000L, pickUpDate = 900L, lines = lines.toList()
    )

    // --- Money ---

    @Test
    fun `euros become cents rounded half away from zero`() {
        assertEquals(304L, Money.toCents(3.04))
        assertEquals(101L, Money.toCents(1.005))      // 100.49999… in binary
        assertEquals(-101L, Money.toCents(-1.005))
        assertEquals(0L, Money.toCents(0.0))
    }

    @Test
    fun `cents format exactly as German decimals`() {
        assertEquals("19,99", Money.formatCents(1999))
        assertEquals("0,05", Money.formatCents(5))
        assertEquals("-0,05", Money.formatCents(-5))
        assertEquals("1234,50", Money.formatCents(123450))
        assertEquals("0,00", Money.formatCents(0))
    }

    // --- line amounts ---

    @Test
    fun `gross is quantity times unit price, rounded to the cent`() {
        // 1.62 kg weighed at 3.19 €/kg = 5.1678 €
        assertEquals(517L, line(quantity = 1.62, unitPriceCents = 319).grossCents)
    }

    @Test
    fun `net and VAT are split off the gross amount`() {
        val l = line(quantity = 1.62, unitPriceCents = 319, taxRate = 0.07)

        assertEquals(483L, l.netCents)                // 517 / 1.07 = 483.18
        assertEquals(34L, l.vatCents)
    }

    @Test
    fun `net plus VAT always equals gross`() {
        val rates = listOf(0.0, 0.07, 0.19)
        val prices = listOf(1L, 19L, 99L, 119L, 304L, 462L, 2979L)
        val quantities = listOf(0.001, 0.37, 1.0, 1.62, 3.0, 12.5)
        for (rate in rates) for (price in prices) for (qty in quantities) {
            val l = line(quantity = qty, unitPriceCents = price, taxRate = rate)
            assertEquals(l.grossCents, l.netCents + l.vatCents, "rate=$rate price=$price qty=$qty")
        }
    }

    @Test
    fun `standard rate splits 19 percent`() {
        val l = line(quantity = 1.0, unitPriceCents = 462, taxRate = 0.19)

        assertEquals(388L, l.netCents)                // 462 / 1.19 = 388.24
        assertEquals(74L, l.vatCents)
    }

    @Test
    fun `purchase cost follows the quantity, or is unknown`() {
        assertEquals(318L, line(quantity = 1.62, acquirePriceCents = 196).acquireCostCents)
        assertNull(line(acquirePriceCents = null).acquireCostCents)
    }

    // --- totals ---

    @Test
    fun `sale totals are the sum of its lines`() {
        val s = sale(
            line(quantity = 1.62, unitPriceCents = 319, taxRate = 0.07),
            line(quantity = 1.0, unitPriceCents = 462, taxRate = 0.19)
        )

        assertEquals(517L + 462L, s.grossCents)
        assertEquals(483L + 388L, s.netCents)
        assertEquals(34L + 74L, s.vatCents)
    }

    @Test
    fun `VAT totals group by rate`() {
        val sales = listOf(
            sale(line(unitPriceCents = 107, taxRate = 0.07), line(unitPriceCents = 119, taxRate = 0.19)),
            sale(line(unitPriceCents = 214, taxRate = 0.07))
        )

        val totals = sales.vatTotals()

        assertEquals(listOf(0.07, 0.19), totals.map { it.taxRate })
        assertEquals(VatTotal(0.07, netCents = 300, vatCents = 21, grossCents = 321), totals[0])
        assertEquals(VatTotal(0.19, netCents = 100, vatCents = 19, grossCents = 119), totals[1])
    }

    // --- cancellation ---

    @Test
    fun `a cancellation negates every amount exactly`() {
        val original = sale(
            line(quantity = 1.62, unitPriceCents = 319, taxRate = 0.07, acquirePriceCents = 196),
            line(quantity = 1.0, unitPriceCents = 462, taxRate = 0.19)
        )

        val storno = original.reversal(confirmedAt = 2_000L)

        assertTrue(storno.isReversal)
        assertEquals("s1", storno.reverses)
        assertEquals(-original.grossCents, storno.grossCents)
        assertEquals(-original.netCents, storno.netCents)
        assertEquals(-original.vatCents, storno.vatCents)
        assertEquals(-318L, storno.lines[0].acquireCostCents)
        assertTrue(listOf(original, storno).vatTotals().all { it.grossCents == 0L && it.vatCents == 0L })
    }

    @Test
    fun `a cancellation cannot be cancelled`() {
        val storno = sale(line()).reversal(confirmedAt = 2_000L).copy(id = "s2")

        assertFailsWith<IllegalArgumentException> { storno.reversal(confirmedAt = 3_000L) }
    }

    @Test
    fun `only a stored sale can be cancelled`() {
        assertFailsWith<IllegalArgumentException> { sale(line()).copy(id = "").reversal(2_000L) }
    }

    // --- order to sale ---

    // As the buyer app writes order lines: id empty, the article's database id in productId.
    private val order = Order(
        id = "order_1",
        pickUpDate = 900L,
        articles = listOf(
            OrderedProduct(productId = "apple", productName = "Apfel Topaz",
                unit = "kg", price = 3.04, amountCount = 1.5),
            OrderedProduct(productId = "sweet", productName = "Süßkartoffel",
                unit = "kg", price = 4.62, amountCount = 1.0),
            OrderedProduct(productId = "gone", productName = "Teesieb",
                unit = "Stück", price = 1.19, amountCount = 2.0)
        )
    )

    private val catalog = mapOf(
        "apple" to SellerArticle(
            Article(id = "apple", productId = "112108", taxRate = TaxRate.REDUCED.rate),
            SellerArticleData(acquirePrice = 1.96)
        ),
        "sweet" to SellerArticle(
            Article(id = "sweet", productId = "122654", taxRate = TaxRate.STANDARD.rate),
            SellerArticleData(acquirePrice = 0.0)   // recorded as unknown
        )
        // "gone" was deleted from the catalog since it was ordered
    )

    @Test
    fun `an order becomes a sale of what was actually handed over`() {
        val s = order.toSale(listOf(1.62, 1.0, 2.0), catalog, confirmedAt = 1_000L)

        assertEquals("order_1", s.orderId)
        assertEquals(1_000L, s.confirmedAt)
        assertEquals(900L, s.pickUpDate)
        assertEquals(listOf(1.62, 1.0, 2.0), s.lines.map { it.quantity })
        assertEquals(304L, s.lines[0].unitPriceCents)
    }

    @Test
    fun `VAT rate and purchase price come from the catalog`() {
        val s = order.toSale(listOf(1.5, 1.0, 2.0), catalog, confirmedAt = 1_000L)

        assertEquals(0.07, s.lines[0].taxRate)
        assertEquals(196L, s.lines[0].acquirePriceCents)
        assertEquals(0.19, s.lines[1].taxRate)
        assertNull(s.lines[1].acquirePriceCents, "a purchase price of 0 is unknown, not free")
    }

    @Test
    fun `an article gone from the catalog is booked at the default rate, cost unknown`() {
        val s = order.toSale(listOf(1.5, 1.0, 2.0), catalog, confirmedAt = 1_000L)

        assertEquals(TaxRate.default.rate, s.lines[2].taxRate)
        assertNull(s.lines[2].acquirePriceCents)
        assertEquals("gone", s.lines[2].articleId)
        assertEquals("", s.lines[2].productId, "no article number rather than a database id")
    }

    @Test
    fun `order lines find their article by the database id in productId`() {
        // The case found on a device: a 19 % article booked at 7 % because the
        // lookup used OrderedProduct.id, which the buyer app leaves empty.
        val s = order.toSale(listOf(1.5, 1.0, 2.0), catalog, confirmedAt = 1_000L)

        assertEquals(listOf("apple", "sweet"), s.lines.take(2).map { it.articleId })
        assertEquals(0.19, s.lines[1].taxRate)
    }

    @Test
    fun `the article number comes from the catalog, not the order line`() {
        val s = order.toSale(listOf(1.5, 1.0, 2.0), catalog, confirmedAt = 1_000L)

        assertEquals(listOf("112108", "122654"), s.lines.take(2).map { it.productId })
    }

    @Test
    fun `a line that names its article in id is still found`() {
        val legacy = order.copy(articles = listOf(
            OrderedProduct(id = "sweet", productId = "", productName = "Süßkartoffel", price = 4.62, amountCount = 1.0)
        ))

        assertEquals(0.19, legacy.toSale(listOf(1.0), catalog, 1_000L).lines.single().taxRate)
    }

    @Test
    fun `items that were missing are left out`() {
        val s = order.toSale(listOf(1.5, 0.0, 2.0), catalog, confirmedAt = 1_000L)

        assertEquals(listOf("apple", "gone"), s.lines.map { it.articleId })
    }

    @Test
    fun `quantities must match the order lines and not be negative`() {
        assertFailsWith<IllegalArgumentException> { order.toSale(listOf(1.0), catalog, 1_000L) }
        assertFailsWith<IllegalArgumentException> { order.toSale(listOf(1.0, -1.0, 1.0), catalog, 1_000L) }
    }
}
