package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A seller is warned before they price an article below what it cost them.
 *
 * `resolvePrice` cannot do this: it runs in the buy app too, where `SellerArticleData`
 * deliberately does not exist, so the floor it enforces is zero. The real floor is
 * checked here, where both halves of an article are in hand — and it warns rather than
 * refuses, because a loss-leader is the seller's decision to make. What it must not be
 * is an accident. `doc/guides/market-pricing-and-access-design.md` §2.
 */
class PricingFloorTest {

    private fun article(id: String, name: String, price: Double, category: String = "Gemüse") =
        Article(id = id, productName = name, price = price, category = category)

    private fun costed(id: String, name: String, price: Double, acquire: Double, category: String = "Gemüse") =
        SellerArticle(
            article = article(id, name, price, category),
            sellerData = SellerArticleData(acquirePrice = acquire)
        )

    private fun uncosted(id: String, name: String, price: Double) =
        SellerArticle(article = article(id, name, price), sellerData = SellerArticleData())

    private fun discount(amount: Double, scope: PriceScope = PriceScope.AllArticles) =
        MarketPricing(
            marketId = "m1",
            conditions = listOf(
                PriceCondition(
                    cause = PriceCause.NEIGHBORHOOD_RATE,
                    scope = scope,
                    effect = PriceEffect.Discount(amount)
                )
            )
        )

    @Test
    fun `no conditions means nothing is below cost`() {
        val articles = listOf(costed("a1", "Möhren", 2.80, acquire = 2.00))

        assertTrue(pricingBelowCost(articles, MarketPricing.NONE).isEmpty())
    }

    @Test
    fun `a discount that stays above cost does not warn`() {
        val articles = listOf(costed("a1", "Möhren", 2.80, acquire = 2.00))

        // 2,80 − 0,50 = 2,30, still above the 2,00 it cost.
        assertTrue(pricingBelowCost(articles, discount(0.50)).isEmpty())
    }

    @Test
    fun `a discount that crosses cost warns`() {
        val articles = listOf(costed("a1", "Möhren", 2.80, acquire = 2.00))

        val below = pricingBelowCost(articles, discount(1.00)).single()

        assertEquals("a1", below.articleId)
        assertEquals("Möhren", below.productName)
        assertEquals(2.00, below.acquirePrice)
        assertEquals(1.80, below.gross)
        assertEquals(0.20, below.shortfall)
    }

    @Test
    fun `landing exactly on cost does not warn`() {
        val articles = listOf(costed("a1", "Möhren", 2.80, acquire = 2.00))

        // 2,80 − 0,80 = 2,00 exactly. Breaking even is not below cost.
        assertTrue(pricingBelowCost(articles, discount(0.80)).isEmpty())
    }

    @Test
    fun `an uncosted article is skipped rather than treated as free`() {
        // acquirePrice 0 means unknown, which is most of the catalogue after a BNN
        // import. Reading it as a floor would flag everything.
        val articles = listOf(uncosted("a1", "Möhren", 2.80))

        assertTrue(pricingBelowCost(articles, discount(2.50)).isEmpty())
    }

    @Test
    fun `a missing seller half is skipped`() {
        val articles = listOf(SellerArticle(article = article("a1", "Möhren", 2.80), sellerData = null))

        assertTrue(pricingBelowCost(articles, discount(2.50)).isEmpty())
    }

    @Test
    fun `the worst shortfall is reported first`() {
        val articles = listOf(
            costed("a1", "Möhren", 2.80, acquire = 2.60),   // → 1.80, short 0.80
            costed("a2", "Apfel", 3.50, acquire = 2.70),    // → 2.50, short 0.20
            costed("a3", "Birne", 4.00, acquire = 3.50)     // → 3.00, short 0.50
        )

        val below = pricingBelowCost(articles, discount(1.00))

        assertEquals(listOf("a1", "a3", "a2"), below.map { it.articleId })
    }

    @Test
    fun `a scoped condition only warns inside its scope`() {
        val articles = listOf(
            costed("a1", "Möhren", 2.80, acquire = 2.60, category = "Gemüse"),
            costed("a2", "Apfel", 3.50, acquire = 3.40, category = "Obst")
        )

        val below = pricingBelowCost(articles, discount(1.00, PriceScope.Category("Gemüse")))

        assertEquals(listOf("a1"), below.map { it.articleId })
    }

    @Test
    fun `a surcharge never puts anything below cost`() {
        val articles = listOf(costed("a1", "Möhren", 2.80, acquire = 2.79))
        val surcharge = MarketPricing(
            marketId = "m1",
            conditions = listOf(
                PriceCondition(cause = PriceCause.STALL_FEE, effect = PriceEffect.Surcharge(0.25))
            )
        )

        assertTrue(pricingBelowCost(articles, surcharge).isEmpty())
    }

    @Test
    fun `a factor below one can cross cost`() {
        val articles = listOf(costed("a1", "Möhren", 2.80, acquire = 2.50))
        val factor = MarketPricing(
            marketId = "m1",
            conditions = listOf(
                PriceCondition(cause = PriceCause.VOLUME_COMMITMENT, effect = PriceEffect.Factor(0.8))
            )
        )

        // 2,80 × 0,8 = 2,24, under the 2,50 it cost.
        val below = pricingBelowCost(articles, factor).single()
        assertEquals(2.24, below.gross)
    }

    @Test
    fun `the floor is compared in whole cents`() {
        // A sub-cent gap is not a warning worth raising, and float arithmetic produces
        // them: 2.80 - 0.79 does not land exactly on 2.01.
        val articles = listOf(costed("a1", "Möhren", 2.80, acquire = 2.01))

        assertTrue(pricingBelowCost(articles, discount(0.79)).isEmpty())
    }

    @Test
    fun `an empty catalogue warns about nothing`() {
        assertTrue(pricingBelowCost(emptyList(), discount(5.00)).isEmpty())
    }
}
