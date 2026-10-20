package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BasketAvailabilityTest {

    private fun article(id: String, name: String = id, available: Boolean = true) =
        Article(id = id, productName = name, available = available, unit = "kg")

    private fun item(articleId: String, name: String = articleId, amount: Double = 1.0) =
        OrderedProduct(productId = articleId, productName = name, unit = "kg", amountCount = amount)

    private val catalog = listOf(
        article("apple", "Apfel"),
        article("nuts", "Nüsse", available = false)
    )

    @Test
    fun `an available article can be ordered`() {
        assertTrue(listOf(item("apple")).unorderableAgainst(catalog).isEmpty())
        assertTrue(listOf(item("apple")).allOrderable(catalog))
    }

    @Test
    fun `an article the seller withdrew cannot`() {
        val unorderable = listOf(item("nuts")).unorderableAgainst(catalog).single()

        assertEquals("nuts", unorderable.articleId)
        assertEquals(UnorderableReason.WITHDRAWN, unorderable.reason)
    }

    @Test
    fun `an article no longer in the catalogue cannot either`() {
        val unorderable = listOf(item("pears", "Birnen")).unorderableAgainst(catalog).single()

        assertEquals(UnorderableReason.GONE, unorderable.reason)
        assertEquals("Birnen", unorderable.productName)
    }

    @Test
    fun `the catalogue's name is reported, not the basket's`() {
        // The seller renamed it after the item went into the basket; the buyer is
        // about to go looking for it under the new name.
        val renamed = listOf(article("nuts", "Walnüsse", available = false))

        val unorderable = listOf(item("nuts", "Nüsse")).unorderableAgainst(renamed).single()

        assertEquals("Walnüsse", unorderable.productName)
    }

    @Test
    fun `an empty catalogue judges nothing`() {
        // "No articles" and "not loaded yet" look the same over a listener, and
        // refusing a basket because the network is slow is the worse failure.
        assertTrue(listOf(item("nuts")).unorderableAgainst(emptyList()).isEmpty())
        assertTrue(listOf(item("nuts")).allOrderable(emptyList()))
        assertEquals(1, listOf(item("nuts")).orderableOnly(emptyList()).size)
    }

    @Test
    fun `a line naming no article is not refused on that basis`() {
        // Old orders carry "-1"; they are not withdrawn goods.
        val nameless = OrderedProduct(productId = "-1", productName = "Alt", amountCount = 1.0)

        assertTrue(listOf(nameless).unorderableAgainst(catalog).isEmpty())
    }

    @Test
    fun `a line is matched by the key sales booking uses`() {
        val byId = OrderedProduct(id = "nuts", productId = "-1", amountCount = 1.0, unit = "kg")

        assertEquals(
            UnorderableReason.WITHDRAWN,
            listOf(byId).unorderableAgainst(catalog).single().reason
        )
    }

    @Test
    fun `one withdrawn item blocks the basket, the others are reported too`() {
        val items = listOf(item("apple"), item("nuts"), item("pears", "Birnen"))

        val unorderable = items.unorderableAgainst(catalog)

        assertEquals(2, unorderable.size)
        assertTrue(!items.allOrderable(catalog))
    }

    @Test
    fun `a reorder keeps what can still be ordered`() {
        val items = listOf(item("apple", amount = 2.0), item("nuts"), item("pears"))

        val kept = items.orderableOnly(catalog)

        assertEquals(listOf("apple"), kept.map { it.productId })
        assertEquals(2.0, kept.single().amountCount)
    }

    @Test
    fun `a basket of only withdrawn articles keeps nothing`() {
        assertTrue(listOf(item("nuts")).orderableOnly(catalog).isEmpty())
    }

    // --- nearly gone ----------------------------------------------------------

    private fun scarce(id: String, scarcity: ArticleScarcity) =
        Article(id = id, productName = id, available = true, unit = "kg", scarcity = scarcity)

    @Test
    fun `an article the shop is nearly out of is named`() {
        val shop = listOf(scarce("apple", ArticleScarcity.LAST_FEW))

        assertEquals(setOf("apple"), listOf(item("apple")).nearlyGoneAgainst(shop))
    }

    @Test
    fun `running down is not nearly gone`() {
        // LIMITED is browsing information: useful while choosing, noise beside
        // something already chosen.
        val shop = listOf(scarce("apple", ArticleScarcity.LIMITED))

        assertTrue(listOf(item("apple")).nearlyGoneAgainst(shop).isEmpty())
    }

    @Test
    fun `plenty and nothing-known say nothing`() {
        val plenty = listOf(scarce("apple", ArticleScarcity.PLENTIFUL))
        val ungated = listOf(scarce("apple", ArticleScarcity.UNKNOWN))

        assertTrue(listOf(item("apple")).nearlyGoneAgainst(plenty).isEmpty())
        assertTrue(listOf(item("apple")).nearlyGoneAgainst(ungated).isEmpty())
    }

    @Test
    fun `only the scarce items of a basket are named`() {
        val shop = listOf(
            scarce("apple", ArticleScarcity.LAST_FEW),
            scarce("pears", ArticleScarcity.PLENTIFUL)
        )

        assertEquals(
            setOf("apple"),
            listOf(item("apple"), item("pears")).nearlyGoneAgainst(shop)
        )
    }

    @Test
    fun `an empty catalogue says nothing here either`() {
        assertTrue(listOf(item("apple")).nearlyGoneAgainst(emptyList()).isEmpty())
    }

    @Test
    fun `a withdrawn article that is also scarce is still reported as withdrawn`() {
        // Both are true; the row shows the actionable one. This only checks that the
        // two questions are answered independently — the card decides which to say.
        val shop = listOf(
            Article(id = "nuts", productName = "Nüsse", available = false, unit = "kg", scarcity = ArticleScarcity.LAST_FEW)
        )

        assertEquals(
            UnorderableReason.WITHDRAWN,
            listOf(item("nuts")).unorderableAgainst(shop).single().reason
        )
        assertEquals(setOf("nuts"), listOf(item("nuts")).nearlyGoneAgainst(shop))
    }
}
