package com.together.newverse.domain.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A market's price and the explanation of it are the same data, resolved once.
 *
 * The rules these pin — precedence, ordering, rounding, the floor — are the whole
 * of the behaviour; see `doc/market-pricing-and-access-design.md` §2.
 */
class MarketPricingTest {

    private val moehren = Article(id = "a1", productName = "Möhren", price = 2.80, category = "Gemüse")
    private val apfel = Article(id = "a2", productName = "Apfel", price = 3.50, category = "Obst")

    private fun pricing(vararg conditions: PriceCondition) =
        MarketPricing(marketId = "m1", conditions = conditions.toList())

    private fun cents(value: Double) = (value * 100).toInt()

    // ===== nothing configured changes nothing =====

    @Test
    fun `with no conditions the price is the article's own`() {
        val resolved = resolvePrice(moehren, MarketPricing.NONE)
        assertEquals(2.80, resolved.gross)
        assertEquals(2.80, resolved.base)
        assertTrue(resolved.components.isEmpty())
        assertFalse(resolved.isAdjusted)
    }

    // ===== the breakdown has to add up =====

    @Test
    fun `base plus every component equals the gross exactly`() {
        val resolved = resolvePrice(
            moehren,
            pricing(
                PriceCondition(PriceCause.STALL_FEE, PriceScope.AllArticles, PriceEffect.Surcharge(0.25), "Standgebühr"),
                PriceCondition(PriceCause.TRANSPORT, PriceScope.AllArticles, PriceEffect.Surcharge(0.15), "Transport")
            )
        )

        assertEquals(3.20, resolved.gross)
        assertEquals(
            cents(resolved.gross),
            cents(resolved.base) + resolved.components.sumOf { cents(it.amount) }
        )
    }

    // A breakdown whose figures disagree with the total is worse than none, so the
    // gross is the sum of the rounded parts rather than a separately rounded total.
    @Test
    fun `an awkward factor still produces a breakdown that adds up`() {
        val odd = Article(id = "a3", productName = "Odd", price = 1.11, category = "Gemüse")
        val resolved = resolvePrice(
            odd,
            pricing(
                PriceCondition(PriceCause.STALL_FEE, PriceScope.AllArticles, PriceEffect.Factor(1.075)),
                PriceCondition(PriceCause.HANDLING, PriceScope.AllArticles, PriceEffect.Factor(1.033))
            )
        )

        assertEquals(
            cents(resolved.gross),
            cents(resolved.base) + resolved.components.sumOf { cents(it.amount) },
            "components do not sum to the gross"
        )
        // Every amount is a whole number of cents.
        resolved.components.forEach {
            assertTrue(abs(it.amount * 100 - cents(it.amount)) < 1e-9, "unrounded component ${it.amount}")
        }
    }

    @Test
    fun `a component that rounds away to nothing is left out`() {
        val resolved = resolvePrice(
            moehren,
            pricing(PriceCondition(PriceCause.TRANSPORT, PriceScope.AllArticles, PriceEffect.Surcharge(0.001)))
        )
        // "+0,00 € Transport" explains nothing.
        assertTrue(resolved.components.isEmpty())
        assertEquals(2.80, resolved.gross)
    }

    // ===== factors are taken against the base, never compounded =====

    @Test
    fun `two factors add their effects rather than compounding`() {
        val resolved = resolvePrice(
            Article(id = "a4", productName = "X", price = 10.00),
            pricing(
                PriceCondition(PriceCause.STALL_FEE, PriceScope.AllArticles, PriceEffect.Factor(1.08)),
                PriceCondition(PriceCause.TRANSPORT, PriceScope.AllArticles, PriceEffect.Factor(1.05))
            )
        )
        // 13% of 10.00, not 13.4% — and independent of the stored order.
        assertEquals(11.30, resolved.gross)
    }

    @Test
    fun `the result does not depend on the order conditions are stored in`() {
        val a = PriceCondition(PriceCause.STALL_FEE, PriceScope.AllArticles, PriceEffect.Factor(1.08))
        val b = PriceCondition(PriceCause.TRANSPORT, PriceScope.AllArticles, PriceEffect.Surcharge(0.25))

        assertEquals(resolvePrice(moehren, pricing(a, b)).gross, resolvePrice(moehren, pricing(b, a)).gross)
    }

    // ===== scope precedence =====

    @Test
    fun `a condition on the article beats one on its category`() {
        val resolved = resolvePrice(
            moehren,
            pricing(
                PriceCondition(PriceCause.STALL_FEE, PriceScope.AllArticles, PriceEffect.Surcharge(0.50)),
                PriceCondition(PriceCause.STALL_FEE, PriceScope.Category("Gemüse"), PriceEffect.Surcharge(0.30)),
                PriceCondition(PriceCause.STALL_FEE, PriceScope.SingleArticle("a1"), PriceEffect.Surcharge(0.10))
            )
        )
        // One cause, one component: the narrowest that matches.
        assertEquals(1, resolved.components.size)
        assertEquals(0.10, resolved.components.single().amount)
        assertEquals(2.90, resolved.gross)
    }

    @Test
    fun `a category condition beats one on everything`() {
        val resolved = resolvePrice(
            moehren,
            pricing(
                PriceCondition(PriceCause.STALL_FEE, PriceScope.AllArticles, PriceEffect.Surcharge(0.50)),
                PriceCondition(PriceCause.STALL_FEE, PriceScope.Category("Gemüse"), PriceEffect.Surcharge(0.30))
            )
        )
        assertEquals(0.30, resolved.components.single().amount)
    }

    // A stall fee and a transport charge are two real costs, not two opinions.
    @Test
    fun `different causes stack even at the same scope`() {
        val resolved = resolvePrice(
            moehren,
            pricing(
                PriceCondition(PriceCause.STALL_FEE, PriceScope.AllArticles, PriceEffect.Surcharge(0.25)),
                PriceCondition(PriceCause.TRANSPORT, PriceScope.AllArticles, PriceEffect.Surcharge(0.15))
            )
        )
        assertEquals(2, resolved.components.size)
        assertEquals(3.20, resolved.gross)
    }

    @Test
    fun `a category condition does not touch another category`() {
        val conditions = pricing(
            PriceCondition(PriceCause.STALL_FEE, PriceScope.Category("Gemüse"), PriceEffect.Surcharge(0.30))
        )
        assertEquals(3.10, resolvePrice(moehren, conditions).gross)
        assertEquals(3.50, resolvePrice(apfel, conditions).gross)
    }

    @Test
    fun `category matching ignores case`() {
        val resolved = resolvePrice(
            moehren,
            pricing(PriceCondition(PriceCause.STALL_FEE, PriceScope.Category("gemüse"), PriceEffect.Surcharge(0.30)))
        )
        assertEquals(3.10, resolved.gross)
    }

    // ===== discounts, and the floor =====

    @Test
    fun `a discount lowers the price and reads as negative`() {
        val resolved = resolvePrice(
            moehren,
            pricing(PriceCondition(PriceCause.NEIGHBORHOOD_RATE, PriceScope.AllArticles, PriceEffect.Discount(0.30), "Kiezpreis"))
        )
        assertEquals(-0.30, resolved.components.single().amount)
        assertEquals(2.50, resolved.gross)
    }

    // The real floor is the seller's purchase price, which the buy app never sees,
    // so the only one enforceable here is zero.
    @Test
    fun `a discount cannot drive the price below zero`() {
        val resolved = resolvePrice(
            moehren,
            pricing(PriceCondition(PriceCause.NEIGHBORHOOD_RATE, PriceScope.AllArticles, PriceEffect.Discount(99.0)))
        )
        assertEquals(0.0, resolved.gross)
    }

    // ===== switching market =====

    @Test
    fun `only the articles whose price moved are reported`() {
        val from = MarketPricing.NONE
        val to = pricing(PriceCondition(PriceCause.STALL_FEE, PriceScope.Category("Gemüse"), PriceEffect.Surcharge(0.30)))

        val changes = priceChanges(listOf(moehren, apfel), from, to)

        assertEquals(1, changes.size)
        assertEquals("a1", changes.single().article.id)
        assertEquals(2.80, changes.single().from.gross)
        assertEquals(3.10, changes.single().to.gross)
        assertTrue(changes.single().isIncrease)
    }

    @Test
    fun `a move between markets that price the same reports nothing`() {
        val same = pricing(PriceCondition(PriceCause.STALL_FEE, PriceScope.AllArticles, PriceEffect.Surcharge(0.25)))
        assertTrue(priceChanges(listOf(moehren, apfel), same, same).isEmpty())
    }

    @Test
    fun `a move to a cheaper market is reported as a decrease`() {
        val from = pricing(PriceCondition(PriceCause.STALL_FEE, PriceScope.AllArticles, PriceEffect.Surcharge(0.25)))
        val change = priceChanges(listOf(moehren), from, MarketPricing.NONE).single()

        assertFalse(change.isIncrease)
        assertEquals(-0.25, change.difference)
    }
}
