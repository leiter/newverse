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

        val corrections = catalog.publicationCorrections(levels)

        assertEquals(
            mapOf("sold_out" to false, "restocked" to true),
            corrections.mapValues { it.value.available }
        )
    }

    @Test
    fun `a settled catalogue is corrected no further`() {
        val catalog = listOf(
            article("apple", available = false, sellerData = SellerArticleData(sellerAvailable = true, stockGated = true))
        )
        val levels = mapOf("apple" to level(0.0))

        assertTrue(catalog.publicationCorrections(levels).isEmpty())
    }

    @Test
    fun `an article whose private half has not arrived is left alone`() {
        // Without it there is no stockGated to read, and assuming "not gated" would
        // publish an availability the seller never asked for.
        val catalog = listOf(article("apple", available = true, sellerData = null))

        assertTrue(catalog.publicationCorrections(mapOf("apple" to level(0.0))).isEmpty())
    }

    @Test
    fun `a gated article with no recorded intent is not closed`() {
        // Closing it would be irreversible: sellerAvailable falls back to the public
        // flag, so the pass would read its own write back as the seller's wish.
        val catalog = listOf(
            article("apple", available = true, sellerData = SellerArticleData(stockGated = true))
        )

        assertTrue(catalog.publicationCorrections(mapOf("apple" to level(0.0))).isEmpty())
    }

    @Test
    fun `ungated articles are never touched by the pass`() {
        val catalog = listOf(
            article("apple", available = true, sellerData = SellerArticleData(stockGated = false))
        )

        assertTrue(catalog.publicationCorrections(mapOf("apple" to level(0.0))).isEmpty())
    }

    @Test
    fun `demand alone can close a gated article`() {
        val catalog = listOf(
            article("apple", available = true, sellerData = SellerArticleData(sellerAvailable = true, stockGated = true))
        )
        val levels = mapOf("apple" to level(6.0))

        assertTrue(catalog.publicationCorrections(levels).isEmpty())
        assertEquals(
            mapOf("apple" to false),
            catalog.publicationCorrections(levels, mapOf("apple" to demand(6.0)))
                .mapValues { it.value.available }
        )
    }

    // --- the scarcity band ----------------------------------------------------

    private fun gated(
        reorderLevel: Double = 10.0,
        packageSize: Double = 0.0,
        available: Boolean = true
    ) = article(
        available = available,
        sellerData = SellerArticleData(
            reorderLevel = reorderLevel,
            packageSize = packageSize,
            sellerAvailable = true,
            stockGated = true
        )
    )

    @Test
    fun `an ungated article says nothing about how much is left`() {
        // Not the seller's business model: they bring what they bring.
        val ungated = article(sellerData = SellerArticleData(reorderLevel = 10.0))

        assertEquals(ArticleScarcity.UNKNOWN, ungated.scarcityOf(level(2.0)))
    }

    @Test
    fun `an article never counted says nothing`() {
        assertEquals(ArticleScarcity.UNKNOWN, gated().scarcityOf(level = null))
    }

    @Test
    fun `the reorder level is what few is measured against`() {
        // Half a kilo is nothing in potatoes and a lot in saffron, so the band is
        // relative to the amount this seller restocks at.
        assertEquals(ArticleScarcity.PLENTIFUL, gated(reorderLevel = 10.0).scarcityOf(level(40.0)))
        assertEquals(ArticleScarcity.LIMITED, gated(reorderLevel = 10.0).scarcityOf(level(10.0)))
        assertEquals(ArticleScarcity.LIMITED, gated(reorderLevel = 10.0).scarcityOf(level(6.0)))
        assertEquals(ArticleScarcity.LAST_FEW, gated(reorderLevel = 10.0).scarcityOf(level(5.0)))
        assertEquals(ArticleScarcity.LAST_FEW, gated(reorderLevel = 10.0).scarcityOf(level(0.5)))
    }

    @Test
    fun `orders count against what is left, as everywhere else`() {
        val gated = gated(reorderLevel = 10.0)

        assertEquals(ArticleScarcity.PLENTIFUL, gated.scarcityOf(level(40.0)))
        assertEquals(ArticleScarcity.LIMITED, gated.scarcityOf(level(40.0), demand(32.0)))
        assertEquals(ArticleScarcity.LAST_FEW, gated.scarcityOf(level(40.0), demand(38.0)))
    }

    @Test
    fun `the package size stands in where no reorder level is set`() {
        // The Gebinde the article is bought in — filled by the price-list import for
        // most articles, and a unit-aware quantity rather than a constant.
        val byPackage = gated(reorderLevel = 0.0, packageSize = 6.0)

        assertEquals(ArticleScarcity.PLENTIFUL, byPackage.scarcityOf(level(20.0)))
        assertEquals(ArticleScarcity.LIMITED, byPackage.scarcityOf(level(5.0)))
        assertEquals(ArticleScarcity.LAST_FEW, byPackage.scarcityOf(level(2.0)))
    }

    @Test
    fun `with no reference quantity nothing is published`() {
        // "Few" would be this code's opinion rather than the seller's.
        val noReference = gated(reorderLevel = 0.0, packageSize = 0.0)

        assertEquals(ArticleScarcity.UNKNOWN, noReference.scarcityOf(level(1.0)))
    }

    @Test
    fun `a sold-out gated article reads as last few, not plentiful`() {
        // available carries "sold out"; this must not contradict it.
        assertEquals(ArticleScarcity.LAST_FEW, gated().scarcityOf(level(0.0)))
        assertEquals(ArticleScarcity.LAST_FEW, gated().scarcityOf(level(5.0), demand(20.0)))
    }

    @Test
    fun `the pass publishes a band that has changed on its own`() {
        // Still orderable, so `available` does not move — but it is running out.
        val catalog = listOf(gated(reorderLevel = 10.0, available = true))
        val levels = mapOf("apple" to level(4.0))

        val correction = catalog.publicationCorrections(levels).getValue("apple")

        assertEquals(true, correction.available)
        assertEquals(ArticleScarcity.LAST_FEW, correction.scarcity)
    }

    @Test
    fun `a band that already agrees is not rewritten`() {
        val settled = SellerArticle(
            article = Article(
                id = "apple", productName = "apple", available = true,
                unit = "kg", scarcity = ArticleScarcity.LAST_FEW
            ),
            sellerData = SellerArticleData(reorderLevel = 10.0, sellerAvailable = true, stockGated = true)
        )

        assertTrue(listOf(settled).publicationCorrections(mapOf("apple" to level(4.0))).isEmpty())
    }

    @Test
    fun `an ungated article is left alone by the pass, band included`() {
        val ungated = article(available = true, sellerData = SellerArticleData(reorderLevel = 10.0))

        assertTrue(listOf(ungated).publicationCorrections(mapOf("apple" to level(0.0))).isEmpty())
    }
}
