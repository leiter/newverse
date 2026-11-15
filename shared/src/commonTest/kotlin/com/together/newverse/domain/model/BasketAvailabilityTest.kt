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

    // --- what the order already holds -----------------------------------------
    //
    // A buyer who orders exactly the seller's whole stock closes the article for
    // everyone else, which is `available` working correctly. Their own line must not
    // then be reported back to them as withdrawn.

    @Test
    fun `an exact match on the whole stock is not reported at all`() {
        val committed = listOf(item("nuts", amount = 6.0))

        val unorderable = listOf(item("nuts", amount = 6.0))
            .unorderableAgainst(catalog, committed = committed)

        assertTrue(unorderable.isEmpty(), "asked the buyer to remove the line their own stock was set aside for")
    }

    @Test
    fun `reducing a line on a closed article is fine`() {
        val committed = listOf(item("nuts", amount = 6.0))

        val unorderable = listOf(item("nuts", amount = 2.0))
            .unorderableAgainst(catalog, committed = committed)

        assertTrue(unorderable.isEmpty())
    }

    @Test
    fun `asking for more than the order holds asks for a smaller number`() {
        val committed = listOf(item("nuts", amount = 6.0))

        val unorderable = listOf(item("nuts", amount = 8.0))
            .unorderableAgainst(catalog, committed = committed)
            .single()

        assertEquals(UnorderableReason.OVER_COMMITTED, unorderable.reason)
        assertEquals(6.0, unorderable.maxAmount)
        assertEquals("Nüsse", unorderable.productName)
    }

    @Test
    fun `a new line on a closed article is still withdrawn`() {
        // Nothing was set aside for it, so there is no smaller number to fall back to.
        val committed = listOf(item("apple", amount = 3.0))

        val unorderable = listOf(item("nuts", amount = 1.0))
            .unorderableAgainst(catalog, committed = committed)
            .single()

        assertEquals(UnorderableReason.WITHDRAWN, unorderable.reason)
        assertEquals(0.0, unorderable.maxAmount)
    }

    @Test
    fun `an article the catalogue lost is refused however much the order holds`() {
        val committed = listOf(item("pears", "Birnen", amount = 4.0))

        val unorderable = listOf(item("pears", "Birnen", amount = 4.0))
            .unorderableAgainst(catalog, committed = committed)
            .single()

        assertEquals(UnorderableReason.GONE, unorderable.reason)
    }

    @Test
    fun `a draft is judged exactly as before`() {
        // No committed amounts: every existing caller passes nothing, and must keep
        // the behaviour it had.
        val unorderable = listOf(item("nuts", amount = 6.0)).unorderableAgainst(catalog).single()

        assertEquals(UnorderableReason.WITHDRAWN, unorderable.reason)
    }

    @Test
    fun `the band is not said about an amount the order already holds`() {
        val scarce = listOf(article("apple", "Apfel").copy(scarcity = ArticleScarcity.LAST_FEW))
        val committed = listOf(item("apple", amount = 5.0))

        assertTrue(
            listOf(item("apple", amount = 5.0)).nearlyGoneAgainst(scarce, committed = committed).isEmpty(),
            "warned that nothing is reserving an amount a placed order reserves"
        )
    }

    @Test
    fun `the band is still said about an increase`() {
        val scarce = listOf(article("apple", "Apfel").copy(scarcity = ArticleScarcity.LAST_FEW))
        val committed = listOf(item("apple", amount = 5.0))

        assertEquals(
            setOf("apple"),
            listOf(item("apple", amount = 7.0)).nearlyGoneAgainst(scarce, committed = committed)
        )
    }

    @Test
    fun `the finding carries the unit, so an amount can be stated as 1 kg`() {
        // Without it the message said "max. 1" and did not say of what.
        val unorderable = listOf(item("nuts", amount = 8.0))
            .unorderableAgainst(catalog, committed = listOf(item("nuts", amount = 6.0)))
            .single()

        assertEquals("kg", unorderable.unit)
    }

    @Test
    fun `a withdrawn finding carries no amount to reduce to`() {
        val unorderable = listOf(item("nuts")).unorderableAgainst(catalog).single()

        assertEquals(0.0, unorderable.maxAmount)
        assertEquals("", unorderable.unit)
    }
}
