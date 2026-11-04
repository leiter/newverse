package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.MarketPricing
import com.together.newverse.domain.model.PriceCause
import com.together.newverse.domain.model.PriceCondition
import com.together.newverse.domain.model.PriceEffect
import com.together.newverse.domain.model.PriceScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shelf must quote the price the basket will charge.
 *
 * Retaking the App Store screenshots on 2026-11-04 caught the two disagreeing: the
 * product list and hero card read 2,30 €/kg while the basket charged 2,48 €/kg for
 * the same article. The catalogue rendered `Article.price` raw — the seller's base
 * price, with no market conditions — while the basket snapshotted
 * `BuyAppState.priceOf`, which does apply them. These pin the single price the whole
 * catalogue is now drawn from.
 */
class CataloguePricingTest {

    private val moehren = Article(id = "a1", productName = "Möhren", price = 2.30, category = "Gemüse")

    /** A stall fee of 18 cents: 2,30 € on the shelf became 2,48 € in the basket. */
    private val withStallFee = MarketPricing(
        marketId = "hansaviertel",
        conditions = listOf(
            PriceCondition(
                cause = PriceCause.STALL_FEE,
                scope = PriceScope.AllArticles,
                effect = PriceEffect.Surcharge(0.18),
                note = "Standgebühr"
            )
        )
    )

    @Test
    fun `the catalogue prices against the market in context`() {
        val state = MainScreenState(articles = listOf(moehren), pricing = withStallFee)

        val shown = state.priceOf(moehren)

        assertEquals(2.48, shown.gross)
        assertEquals(2.30, shown.base)
        assertTrue(shown.isAdjusted, "a stall fee is an adjustment the buyer should see explained")
    }

    @Test
    fun `with no market in context the catalogue quotes the seller's own price`() {
        val state = MainScreenState(articles = listOf(moehren))

        val shown = state.priceOf(moehren)

        assertEquals(2.30, shown.gross)
        assertEquals(2.30, shown.base)
        assertFalse(shown.isAdjusted)
    }

    /**
     * The regression itself: whatever the shelf shows has to be what an item costs
     * when it enters the basket, which is `resolvePrice` against the same pricing.
     */
    @Test
    fun `shelf price equals the price the basket snapshots`() {
        val state = MainScreenState(articles = listOf(moehren), pricing = withStallFee)

        val onTheShelf = state.priceOf(moehren).gross
        val inTheBasket = com.together.newverse.domain.model
            .resolvePrice(moehren, withStallFee).gross

        assertEquals(inTheBasket, onTheShelf)
    }

    /**
     * A gross price must never be fed back in as a base one. This is why `priceOf`
     * returns a `ResolvedPrice` rather than writing the number onto the article:
     * resolving twice would charge the stall fee twice.
     */
    @Test
    fun `resolving an already resolved price would double the surcharge`() {
        val once = com.together.newverse.domain.model.resolvePrice(moehren, withStallFee)
        val twice = com.together.newverse.domain.model
            .resolvePrice(moehren.copy(price = once.gross), withStallFee)

        assertEquals(2.48, once.gross)
        assertEquals(2.66, twice.gross, "guards the reason the resolved price is not written onto the Article")
    }
}
