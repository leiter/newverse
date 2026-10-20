package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockRefillTest {

    private fun article(
        id: String,
        name: String = id,
        unit: String = "kg",
        reorderLevel: Double = 5.0
    ) = SellerArticle(
        article = Article(id = id, productName = name, unit = unit),
        sellerData = SellerArticleData(reorderLevel = reorderLevel)
    )

    private fun level(articleId: String, onHand: Double, countedAt: Long? = null) =
        StockLevel(articleId = articleId, onHand = onHand, unit = "kg", lastCountedAt = countedAt)

    @Test
    fun `plenty on hand needs nothing`() {
        val need = article("apple").refillNeed(level("apple", 12.0))
        assertEquals(RefillState.OK, need.state)
        assertTrue(!need.state.needsAttention)
        assertEquals(0.0, need.shortfall)
    }

    @Test
    fun `at or below the reorder level is low`() {
        assertEquals(RefillState.LOW, article("apple").refillNeed(level("apple", 5.0)).state)
        assertEquals(RefillState.LOW, article("apple").refillNeed(level("apple", 1.5)).state)
    }

    @Test
    fun `nothing left is empty`() {
        assertEquals(RefillState.EMPTY, article("apple").refillNeed(level("apple", 0.0)).state)
        // A ledger can go negative if a sale was booked before the intake.
        assertEquals(RefillState.EMPTY, article("apple").refillNeed(level("apple", -0.3)).state)
    }

    @Test
    fun `an article with no movements is uncounted, not empty`() {
        val need = article("apple").refillNeed(null)
        assertEquals(RefillState.UNCOUNTED, need.state)
        assertTrue(need.state.needsAttention)
        assertTrue(!need.wasEverCounted)
    }

    @Test
    fun `without a reorder level the seller is never warned`() {
        val unwatched = article("apple", reorderLevel = 0.0)
        assertEquals(RefillState.UNWATCHED, unwatched.refillNeed(level("apple", 0.0)).state)
        assertEquals(RefillState.UNWATCHED, unwatched.refillNeed(null).state)
        assertTrue(!unwatched.refillNeed(null).state.needsAttention)
    }

    @Test
    fun `an article with no seller data is not watched`() {
        val bare = SellerArticle(article = Article(id = "apple", unit = "kg"), sellerData = null)
        assertEquals(RefillState.UNWATCHED, bare.refillNeed(level("apple", 0.0)).state)
    }

    @Test
    fun `the shortfall is what it takes to reach the reorder level`() {
        assertEquals(3.5, article("apple", reorderLevel = 5.0).refillNeed(level("apple", 1.5)).shortfall)
        assertEquals(5.0, article("apple", reorderLevel = 5.0).refillNeed(level("apple", 0.0)).shortfall)
        // An article above its level needs nothing brought.
        assertEquals(0.0, article("apple", reorderLevel = 5.0).refillNeed(level("apple", 9.0)).shortfall)
    }

    @Test
    fun `the refill list is urgent first and leaves out what is fine`() {
        val catalog = listOf(
            article("apple", "Apfel", reorderLevel = 5.0),
            article("pear", "Birne", reorderLevel = 5.0),
            article("plum", "Pflaume", reorderLevel = 5.0),
            article("kale", "Grünkohl", reorderLevel = 5.0),
            article("nuts", "Nüsse", reorderLevel = 0.0)
        )
        val levels = listOf(
            level("apple", 12.0),   // OK, left out
            level("pear", 2.0),     // LOW
            level("plum", 0.0),     // EMPTY
            level("nuts", 0.0)      // unwatched, left out
            // kale has no level at all -> UNCOUNTED
        ).associateBy { it.articleId }

        val needs = catalog.refillNeeds(levels)

        assertEquals(listOf("plum", "pear", "kale"), needs.map { it.articleId })
        assertEquals(RefillState.EMPTY, needs[0].state)
        assertEquals(RefillState.LOW, needs[1].state)
        assertEquals(RefillState.UNCOUNTED, needs[2].state)
    }

    @Test
    fun `within one state the biggest shortfall comes first`() {
        val catalog = listOf(
            article("a", "A", reorderLevel = 10.0),
            article("b", "B", reorderLevel = 10.0)
        )
        val levels = listOf(level("a", 9.0), level("b", 1.0)).associateBy { it.articleId }

        // Both LOW; B is shorter by 9 kg, A only by 1.
        assertEquals(listOf("b", "a"), catalog.refillNeeds(levels).map { it.articleId })
    }

    @Test
    fun `the standing covers every article, the needs list only some`() {
        val catalog = listOf(article("apple", reorderLevel = 5.0), article("nuts", reorderLevel = 0.0))
        val levels = mapOf("apple" to level("apple", 12.0))

        assertEquals(2, catalog.refillStanding(levels).size)
        assertEquals(0, catalog.refillNeeds(levels).size)
    }

    @Test
    fun `the badge reports whether anything needs attention`() {
        val catalog = listOf(article("apple", reorderLevel = 5.0))
        assertTrue(!catalog.hasRefillNeeds(mapOf("apple" to level("apple", 12.0))))
        assertTrue(catalog.hasRefillNeeds(mapOf("apple" to level("apple", 2.0))))
        assertTrue(catalog.hasRefillNeeds(emptyMap()))
    }

    @Test
    fun `a counted level reports when it was confirmed`() {
        val need = article("apple").refillNeed(level("apple", 2.0, countedAt = 9_000L))
        assertEquals(9_000L, need.lastCountedAt)
        assertTrue(need.wasEverCounted)

        assertNull(article("apple").refillNeed(level("apple", 2.0)).lastCountedAt)
    }

    @Test
    fun `the article's own unit is reported, not the movement's`() {
        val inPieces = article("eggs", unit = "Stück", reorderLevel = 6.0)
        // The level was recorded while the article was still sold by weight.
        val stale = StockLevel(articleId = "eggs", onHand = 4.0, unit = "kg")
        assertEquals("Stück", inPieces.refillNeed(stale).unit)
    }

    // --- committed demand -----------------------------------------------------

    private fun demand(
        articleId: String = "apple",
        quantity: Double,
        unit: String = "kg",
        orderCount: Int = 1
    ) = ArticleDemand(articleId, quantity, setOf(unit), orderCount)

    @Test
    fun `sold more than is there is short, and short outranks everything`() {
        val need = article("apple").refillNeed(level("apple", 3.0), demand(quantity = 8.0))

        assertEquals(RefillState.SHORT, need.state)
        assertEquals(RefillState.SHORT.ordinal, 0)
        assertTrue(need.state.needsAttention)
    }

    @Test
    fun `an oversold article warns even when the seller never watched it`() {
        // The reorder level is the buffer, not the trigger: a buyer has already paid.
        val unwatched = article("nuts", reorderLevel = 0.0)

        val need = unwatched.refillNeed(level("nuts", 1.0), demand("nuts", quantity = 4.0))

        assertEquals(RefillState.SHORT, need.state)
        assertEquals(3.0, need.shortfall)
    }

    @Test
    fun `an unwatched article with orders it can cover stays quiet`() {
        val unwatched = article("nuts", reorderLevel = 0.0)

        val need = unwatched.refillNeed(level("nuts", 10.0), demand("nuts", quantity = 4.0))

        assertEquals(RefillState.OK, need.state)
        assertTrue(!need.needsAttention)
    }

    @Test
    fun `the shortfall covers the orders and the buffer together`() {
        // 4 kg on hand, 6 kg sold, 5 kg buffer wanted: bring 7 kg.
        val need = article("apple", reorderLevel = 5.0)
            .refillNeed(level("apple", 4.0), demand(quantity = 6.0))

        assertEquals(RefillState.SHORT, need.state)
        assertEquals(7.0, need.shortfall)
        assertEquals(-2.0, need.remaining)
    }

    @Test
    fun `orders eat into the buffer before the shelf is empty`() {
        // 8 kg on hand looks fine against a 5 kg buffer until 4 kg of it is sold.
        val need = article("apple", reorderLevel = 5.0)
            .refillNeed(level("apple", 8.0), demand(quantity = 4.0))

        assertEquals(RefillState.LOW, need.state)
        assertEquals(4.0, need.remaining)
        assertEquals(1.0, need.shortfall)
    }

    @Test
    fun `nothing committed behaves exactly as before`() {
        val withNone = article("apple", reorderLevel = 5.0).refillNeed(level("apple", 2.0))
        val withZero = article("apple", reorderLevel = 5.0)
            .refillNeed(level("apple", 2.0), demand(quantity = 0.0))

        assertEquals(RefillState.LOW, withNone.state)
        assertEquals(withNone.state, withZero.state)
        assertEquals(3.0, withNone.shortfall)
        assertEquals(withNone.shortfall, withZero.shortfall)
    }

    @Test
    fun `an empty shelf with orders behind it is short, not merely empty`() {
        assertEquals(
            RefillState.SHORT,
            article("apple").refillNeed(level("apple", 0.0), demand(quantity = 2.0)).state
        )
        assertEquals(
            RefillState.EMPTY,
            article("apple").refillNeed(level("apple", 0.0)).state
        )
    }

    @Test
    fun `demand cannot make a claim about an article never counted`() {
        // 0.0 is not a level here, so SHORT would be an assertion about nothing.
        val watched = article("apple", reorderLevel = 5.0).refillNeed(null, demand(quantity = 3.0))
        assertEquals(RefillState.UNCOUNTED, watched.state)

        // And demand alone does not drag every uncounted article onto the list,
        // which on a fresh ledger would be the whole catalogue.
        val unwatched = article("nuts", reorderLevel = 0.0)
            .refillNeed(null, demand("nuts", quantity = 3.0))
        assertEquals(RefillState.UNWATCHED, unwatched.state)
        assertTrue(!unwatched.needsAttention)
    }

    @Test
    fun `an oversold article sorts above an empty one`() {
        val catalog = listOf(article("a", "A", reorderLevel = 5.0), article("b", "B", reorderLevel = 5.0))
        val levels = listOf(level("a", 0.0), level("b", 2.0)).associateBy { it.articleId }
        val demands = mapOf("b" to demand("b", quantity = 9.0))

        assertEquals(listOf("b", "a"), catalog.refillNeeds(levels, demands).map { it.articleId })
    }

    // --- unit mismatch --------------------------------------------------------

    @Test
    fun `orders in another unit are refused rather than added`() {
        val inKg = article("apple", unit = "kg", reorderLevel = 5.0)

        val need = inKg.refillNeed(level("apple", 8.0), demand(quantity = 20.0, unit = "Stück"))

        assertTrue(need.unitMismatch)
        assertEquals(0.0, need.committed)
        assertEquals(0, need.committedOrderCount)
        // The state is whatever the stock alone says — 8 kg against a 5 kg buffer.
        assertEquals(RefillState.OK, need.state)
        // ...but it still has to be looked at.
        assertTrue(need.needsAttention)
    }

    @Test
    fun `a mismatch is listed so the seller can resolve it`() {
        val catalog = listOf(article("apple", unit = "kg", reorderLevel = 5.0))
        val demands = mapOf("apple" to demand(quantity = 20.0, unit = "Stück"))

        assertEquals(1, catalog.refillNeeds(mapOf("apple" to level("apple", 8.0)), demands).size)
        assertTrue(catalog.hasRefillNeeds(mapOf("apple" to level("apple", 8.0)), demands))
    }

    @Test
    fun `orders disagreeing among themselves are a mismatch too`() {
        val inKg = article("apple", unit = "kg", reorderLevel = 5.0)
        val mixed = ArticleDemand("apple", 20.0, setOf("kg", "Stück"), orderCount = 2)

        assertTrue(inKg.refillNeed(level("apple", 8.0), mixed).unitMismatch)
    }

    @Test
    fun `the unit is matched without regard to case`() {
        val inKg = article("apple", unit = "KG", reorderLevel = 5.0)

        val need = inKg.refillNeed(level("apple", 8.0), demand(quantity = 4.0, unit = "kg"))

        assertTrue(!need.unitMismatch)
        assertEquals(4.0, need.committed)
    }

    @Test
    fun `a ledger below zero with nothing ordered is empty, not oversold`() {
        // A sale booked before the intake it came out of leaves the level negative.
        // Nothing is owed to anyone, so there is nobody waiting.
        assertEquals(
            RefillState.EMPTY,
            article("apple").refillNeed(level("apple", -0.3), demand(quantity = 0.0)).state
        )
    }

    @Test
    fun `how many orders are affected is carried through`() {
        val need = article("apple").refillNeed(
            level("apple", 1.0),
            demand(quantity = 6.0, orderCount = 3)
        )
        assertEquals(3, need.committedOrderCount)
    }
}
