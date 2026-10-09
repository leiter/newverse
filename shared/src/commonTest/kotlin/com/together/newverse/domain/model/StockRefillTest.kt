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
}
