package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An order records what was charged, not a recipe for recomputing it.
 *
 * These pin the snapshot itself: that a priced item carries its own explanation,
 * that the explanation adds up to the number, and — the part that matters for the
 * books — that an order written before per-market pricing still reads as
 * unadjusted rather than as free. See
 * `doc/guides/market-pricing-and-access-design.md` §2 "Orders snapshot, never
 * reference".
 */
class OrderPriceSnapshotTest {

    private val moehren = Article(id = "a1", productName = "Möhren", price = 2.80, category = "Gemüse")

    private val kreuzberg = MarketPricing(
        marketId = "m1",
        revision = 7L,
        conditions = listOf(
            PriceCondition(
                cause = PriceCause.STALL_FEE,
                scope = PriceScope.AllArticles,
                effect = PriceEffect.Surcharge(0.25),
                note = "Standgebühr Markt Kreuzberg"
            )
        )
    )

    private fun item(price: Double = 2.80) = OrderedProduct(
        productId = "a1",
        productName = "Möhren",
        unit = "kg",
        price = price,
        amountCount = 2.0
    )

    // ===== pricedWith keeps the number and its reason together =====

    @Test
    fun `pricedWith stores the gross, the base and the components`() {
        val priced = item().pricedWith(resolvePrice(moehren, kreuzberg))

        assertEquals(3.05, priced.price)
        assertEquals(2.80, priced.basePrice)
        assertEquals(1, priced.priceComponents.size)
        assertEquals(PriceCause.STALL_FEE, priced.priceComponents.single().cause)
        assertEquals(0.25, priced.priceComponents.single().amount)
        assertEquals("Standgebühr Markt Kreuzberg", priced.priceComponents.single().note)
    }

    @Test
    fun `the stored components add up to the stored gross`() {
        val priced = item().pricedWith(resolvePrice(moehren, kreuzberg))
        val sum = priced.basePrice + priced.priceComponents.sumOf { it.amount }

        assertEquals(priced.price, sum)
    }

    @Test
    fun `the charged price drives the line total, not the catalogue price`() {
        val priced = item().pricedWith(resolvePrice(moehren, kreuzberg))

        // 3,05 € × 2 kg — the surcharge is in the money, not only in the explanation.
        assertEquals(6.10, priced.getTotalPrice())
    }

    @Test
    fun `an unadjusted market leaves no components and no adjustment flag`() {
        val priced = item().pricedWith(resolvePrice(moehren, MarketPricing.NONE))

        assertEquals(2.80, priced.price)
        assertEquals(2.80, priced.basePrice)
        assertTrue(priced.priceComponents.isEmpty())
        assertFalse(priced.isPriceAdjusted)
    }

    @Test
    fun `repricing replaces the previous explanation rather than appending to it`() {
        val once = item().pricedWith(resolvePrice(moehren, kreuzberg))
        val twice = once.pricedWith(resolvePrice(moehren, kreuzberg))

        assertEquals(1, twice.priceComponents.size)
        assertEquals(3.05, twice.price)
        assertEquals(2.80, twice.basePrice)
    }

    @Test
    fun `moving back to an unpriced market clears the adjustment`() {
        val adjusted = item().pricedWith(resolvePrice(moehren, kreuzberg))
        val back = adjusted.pricedWith(resolvePrice(moehren, MarketPricing.NONE))

        assertEquals(2.80, back.price)
        assertTrue(back.priceComponents.isEmpty())
        assertFalse(back.isPriceAdjusted)
    }

    // ===== legacy orders =====

    @Test
    fun `an order written before basePrice existed reads as unadjusted, not as free`() {
        // basePrice defaults to 0.0 because the field was absent in Firebase.
        val legacy = item(price = 2.80)

        assertEquals(0.0, legacy.basePrice)
        assertEquals(2.80, legacy.basePriceOrPrice)
        assertFalse(legacy.isPriceAdjusted)
        assertEquals(2.80, legacy.resolvedPrice().base)
        assertEquals(2.80, legacy.resolvedPrice().gross)
    }

    @Test
    fun `resolvedPrice round-trips a priced item back into a breakdown`() {
        val priced = item().pricedWith(resolvePrice(moehren, kreuzberg))
        val resolved = priced.resolvedPrice()

        assertEquals(resolvePrice(moehren, kreuzberg), resolved)
        assertTrue(resolved.isAdjusted)
    }

    // ===== the revision on the order =====

    @Test
    fun `an order carries the revision its prices were resolved against`() {
        val order = Order(
            marketId = "m1",
            pricingRevision = kreuzberg.revision,
            articles = listOf(item().pricedWith(resolvePrice(moehren, kreuzberg)))
        )

        assertEquals(7L, order.pricingRevision)
    }

    @Test
    fun `a later revision does not retroactively change what was charged`() {
        val placed = Order(
            marketId = "m1",
            pricingRevision = kreuzberg.revision,
            articles = listOf(item().pricedWith(resolvePrice(moehren, kreuzberg)))
        )

        // The seller doubles the stall fee afterwards.
        val raised = kreuzberg.copy(
            revision = 8L,
            conditions = listOf(
                PriceCondition(
                    cause = PriceCause.STALL_FEE,
                    effect = PriceEffect.Surcharge(0.50)
                )
            )
        )

        assertEquals(3.05, placed.articles.single().price)
        assertEquals(3.30, resolvePrice(moehren, raised).gross)
        // The order still says which conditions produced its figures.
        assertEquals(7L, placed.pricingRevision)
        assertTrue(placed.pricingRevision != raised.revision)
    }

    @Test
    fun `an order predating per-market pricing carries revision zero`() {
        assertEquals(0L, Order(marketId = "m1", articles = listOf(item())).pricingRevision)
    }
}
