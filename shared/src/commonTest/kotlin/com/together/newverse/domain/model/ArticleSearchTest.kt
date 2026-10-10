package com.together.newverse.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleSearchTest {

    private fun article(
        id: String = "a1",
        name: String = "",
        productId: String = "",
        category: String = "",
        searchTerms: String = "",
        available: Boolean = true
    ) = Article(
        id = id, productId = productId, productName = name,
        category = category, searchTerms = searchTerms, available = available
    )

    // --- what the buyer list already did, kept identical -------------------------

    @Test
    fun `matches the product name, case insensitively`() {
        val a = article(name = "Topaz Apfel")
        assertTrue(a.matchesQuery("topaz"))
        assertTrue(a.matchesQuery("APFEL"))
        assertTrue(a.matchesQuery("az Ap"))
        assertFalse(a.matchesQuery("birne"))
    }

    @Test
    fun `matches the search terms and the category`() {
        val a = article(name = "Robuschka", category = "Gemüse", searchTerms = "rote beete,bete")
        assertTrue(a.matchesQuery("beete"))
        assertTrue(a.matchesQuery("gemüse"))
        assertFalse(a.matchesQuery("obst"))
    }

    @Test
    fun `a blank query matches everything, so nothing is ever hidden by an empty field`() {
        val a = article(name = "Lauch")
        assertTrue(a.matchesQuery(""))
        assertTrue(a.matchesQuery("   "))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertTrue(article(name = "Fenchel").matchesQuery("  fenchel "))
    }

    // --- the seller-only addition -----------------------------------------------

    @Test
    fun `the article number matches only when asked for`() {
        val a = article(name = "Robuschka", productId = "122407")
        assertFalse(a.matchesQuery("122407"))
        assertTrue(a.matchesQuery("122407", includeProductId = true))
    }

    @Test
    fun `a partial article number matches, for typing a prefix off an invoice`() {
        val a = article(name = "Rote Bete", productId = "122407")
        assertTrue(a.matchesQuery("1224", includeProductId = true))
    }

    @Test
    fun `a shelf number still matches, since not every article carries a Terra number`() {
        val a = article(name = "Topinambur", productId = "85")
        assertTrue(a.matchesQuery("85", includeProductId = true))
    }

    // --- the list helpers --------------------------------------------------------

    @Test
    fun `filterByQuery keeps order and drops non-matches`() {
        val list = listOf(
            article(id = "1", name = "Zitrone"),
            article(id = "2", name = "Apfel Topaz"),
            article(id = "3", name = "Zitronengras")
        )
        assertEquals(listOf("1", "3"), list.filterByQuery("zitrone").map { it.id })
    }

    @Test
    fun `filterByQuery leaves an unavailable article in, because the baseline is the callers`() {
        val list = listOf(
            article(id = "1", name = "Rosenkohl", available = false),
            article(id = "2", name = "Rotkohl", available = true)
        )
        assertEquals(listOf("1", "2"), list.filterByQuery("kohl").map { it.id })
    }

    @Test
    fun `presentCategories returns only categories in use, in enum order`() {
        val list = listOf(
            article(category = "Pilze"),
            article(category = "Obst"),
            article(category = "Gemüse"),
            article(category = "Obst")
        )
        // Enum order is Obst, Gemüse, …, Pilze — not the order they appear in the list.
        assertEquals(listOf("Obst", "Gemüse", "Pilze"), list.presentCategories())
    }

    @Test
    fun `presentCategories ignores blanks and keeps an unknown category reachable`() {
        val list = listOf(
            article(category = ""),
            article(category = "Gemüse"),
            article(category = "Selbstgemacht")
        )
        assertEquals(listOf("Gemüse", "Selbstgemacht"), list.presentCategories())
    }

    @Test
    fun `presentCategories is empty for an empty catalog`() {
        assertEquals(emptyList(), emptyList<Article>().presentCategories())
    }
}
