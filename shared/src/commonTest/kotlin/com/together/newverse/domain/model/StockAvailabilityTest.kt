package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StockAvailabilityTest {

    private fun article(
        id: String = "apple",
        available: Boolean = true,
        unit: String = "kg",
        sellerData: SellerArticleData? = SellerArticleData()
    ) = SellerArticle(
        article = Article(id = id, productName = id, available = available, unit = unit),
        sellerData = sellerData
    )

    private fun level(onHand: Double, articleId: String = "apple") =
        StockLevel(articleId = articleId, onHand = onHand, unit = "kg")

    private fun demand(quantity: Double, unit: String = "kg", articleId: String = "apple") =
        ArticleDemand(articleId, quantity, setOf(unit), orderCount = 1)

    // --- the seller's intent --------------------------------------------------

    @Test
    fun `an article saved before intent was stored carries it in the public flag`() {
        // The migration case: sellerAvailable is absent, so the only field there was
        // is the intent. Reading a missing intent as "off" would withdraw everything.
        val stored = article(available = true, sellerData = SellerArticleData(sellerAvailable = null))
        assertTrue(stored.sellerAvailable)

        val withdrawn = article(available = false, sellerData = SellerArticleData(sellerAvailable = null))
        assertTrue(!withdrawn.sellerAvailable)
    }

    @Test
    fun `an article with no seller half at all falls back the same way`() {
        assertTrue(article(available = true, sellerData = null).sellerAvailable)
        assertTrue(!article(available = false, sellerData = null).sellerAvailable)
    }

    @Test
    fun `a recorded intent wins over the public flag`() {
        // This is the point of the split: the flag is derived and may already have
        // been closed by the ledger while the seller still wants it offered.
        val stored = article(
            available = false,
            sellerData = SellerArticleData(sellerAvailable = true, stockGated = true)
        )
        assertTrue(stored.sellerAvailable)
    }

    // --- the gate -------------------------------------------------------------

    @Test
    fun `an ungated article is decided by the seller alone`() {
        val ungated = article(sellerData = SellerArticleData(stockGated = false))

        assertTrue(ungated.stockPermitsOrders(level = null))
        assertTrue(ungated.stockPermitsOrders(level(0.0)))
        assertTrue(ungated.stockPermitsOrders(level(5.0), demand(99.0)))
    }

    @Test
    fun `a gated article closes when orders have used up the shelf`() {
        val gated = article(sellerData = SellerArticleData(sellerAvailable = true, stockGated = true))

        assertTrue(gated.stockPermitsOrders(level(10.0), demand(4.0)))
        assertTrue(!gated.stockPermitsOrders(level(10.0), demand(10.0)))
        assertTrue(!gated.stockPermitsOrders(level(0.0)))
    }

    @Test
    fun `a gated article never counted stays open`() {
        // onHand is 0.0 both for an empty shelf and for one nothing is known about;
        // closing on the second would withdraw an article sitting there in full view.
        val gated = article(sellerData = SellerArticleData(sellerAvailable = true, stockGated = true))

        assertTrue(gated.stockPermitsOrders(level = null))
        assertTrue(gated.derivedAvailability(level = null))
    }

    @Test
    fun `a unit mismatch leaves a gated article open`() {
        // The demand is dropped as unusable, so the article is judged on stock
        // alone — open while the numbers are in doubt, rather than closed on
        // arithmetic nobody can trust.
        val gated = article(sellerData = SellerArticleData(sellerAvailable = true, stockGated = true))

        assertTrue(gated.stockPermitsOrders(level(8.0), demand(20.0, unit = "Stück")))
    }

    // --- the derived flag -----------------------------------------------------

    @Test
    fun `the seller's off beats a full shelf`() {
        val off = article(
            sellerData = SellerArticleData(sellerAvailable = false, stockGated = true)
        )
        assertTrue(!off.derivedAvailability(level(50.0)))
    }

    @Test
    fun `the shelf's empty beats the seller's on`() {
        val on = article(
            sellerData = SellerArticleData(sellerAvailable = true, stockGated = true)
        )
        assertTrue(!on.derivedAvailability(level(0.0)))
    }

    // --- the correction pass --------------------------------------------------

    @Test
    fun `only articles whose flag is wrong are corrected`() {
        val catalog = listOf(
            // Published as available, but gated and sold out: must close.
            article("sold_out", available = true, sellerData = SellerArticleData(sellerAvailable = true, stockGated = true)),
            // Published as unavailable, gated, and restocked: must open.
            article("restocked", available = false, sellerData = SellerArticleData(sellerAvailable = true, stockGated = true)),
            // Already right.
            article("fine", available = true, sellerData = SellerArticleData(sellerAvailable = true, stockGated = true))
        )
        val levels = mapOf(
            "sold_out" to level(0.0, "sold_out"),
            "restocked" to level(12.0, "restocked"),
            "fine" to level(12.0, "fine")
        )

        val corrections = catalog.availabilityCorrections(levels)

        assertEquals(mapOf("sold_out" to false, "restocked" to true), corrections)
    }

    @Test
    fun `a settled catalogue is corrected no further`() {
        val catalog = listOf(
            article("apple", available = false, sellerData = SellerArticleData(sellerAvailable = true, stockGated = true))
        )
        val levels = mapOf("apple" to level(0.0))

        assertTrue(catalog.availabilityCorrections(levels).isEmpty())
    }

    @Test
    fun `an article whose private half has not arrived is left alone`() {
        // Without it there is no stockGated to read, and assuming "not gated" would
        // publish an availability the seller never asked for.
        val catalog = listOf(article("apple", available = true, sellerData = null))

        assertTrue(catalog.availabilityCorrections(mapOf("apple" to level(0.0))).isEmpty())
    }

    @Test
    fun `a gated article with no recorded intent is not closed`() {
        // Closing it would be irreversible: sellerAvailable falls back to the public
        // flag, so the pass would read its own write back as the seller's wish.
        val catalog = listOf(
            article("apple", available = true, sellerData = SellerArticleData(stockGated = true))
        )

        assertTrue(catalog.availabilityCorrections(mapOf("apple" to level(0.0))).isEmpty())
    }

    @Test
    fun `ungated articles are never touched by the pass`() {
        val catalog = listOf(
            article("apple", available = true, sellerData = SellerArticleData(stockGated = false))
        )

        assertTrue(catalog.availabilityCorrections(mapOf("apple" to level(0.0))).isEmpty())
    }

    @Test
    fun `demand alone can close a gated article`() {
        val catalog = listOf(
            article("apple", available = true, sellerData = SellerArticleData(sellerAvailable = true, stockGated = true))
        )
        val levels = mapOf("apple" to level(6.0))

        assertTrue(catalog.availabilityCorrections(levels).isEmpty())
        assertEquals(
            mapOf("apple" to false),
            catalog.availabilityCorrections(levels, mapOf("apple" to demand(6.0)))
        )
    }
}
