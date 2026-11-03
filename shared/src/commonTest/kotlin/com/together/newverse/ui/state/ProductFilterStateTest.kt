package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The buy home screen's chip row: the two filter axes and how they compose.
 *
 * Written against [MainScreenState.filteredArticles] rather than the chips, because
 * that is where the composition lives and it needs no composition to test.
 */
class ProductFilterStateTest {

    private fun article(
        id: String,
        name: String = id,
        category: String = "",
        searchTerms: String = "",
        available: Boolean = true
    ) = Article(
        id = id,
        productName = name,
        category = category,
        searchTerms = searchTerms,
        available = available
    )

    private val apple = article("1", "Apfel", category = "Obst")
    private val banana = article("2", "Banane", searchTerms = "Banane,obst")
    private val carrot = article("3", "Möhre", category = "Gemüse")
    private val potato = article("4", "Kartoffel", searchTerms = "Kartoffel,gemüse")

    private fun state(
        filterState: ProductFilterState = ProductFilterState.Unfiltered,
        favourites: List<String> = emptyList(),
        query: String = "",
        articles: List<Article> = listOf(apple, banana, carrot, potato)
    ) = MainScreenState(
        articles = articles,
        favouriteArticles = favourites,
        filterState = filterState,
        searchQuery = query
    )

    private fun List<Article>.ids() = map { it.id }

    // ----- the favourite axis: three states -----

    @Test
    fun `favourite axis bypassed by default keeps everything`() {
        assertNull(ProductFilterState.Unfiltered.includeFavourite)
        assertEquals(listOf("1", "2", "3", "4"), state().filteredArticles.ids())
    }

    @Test
    fun `favourite axis true keeps only favourites`() {
        val filtered = state(
            filterState = ProductFilterState(includeFavourite = true),
            favourites = listOf("1", "3")
        ).filteredArticles
        assertEquals(listOf("1", "3"), filtered.ids())
    }

    @Test
    fun `favourite axis false keeps only non-favourites`() {
        val filtered = state(
            filterState = ProductFilterState(includeFavourite = false),
            favourites = listOf("1", "3")
        ).filteredArticles
        assertEquals(listOf("2", "4"), filtered.ids())
    }

    @Test
    fun `favourite axis true with no favourites recorded is empty, not everything`() {
        val filtered = state(filterState = ProductFilterState(includeFavourite = true)).filteredArticles
        assertTrue(filtered.isEmpty())
    }

    // ----- toggling: every state one tap in, one tap out -----

    @Test
    fun `tapping the favourite chip twice turns the axis off`() {
        val once = ProductFilterState.Unfiltered.toggleFavourite(true)
        assertEquals(true, once.includeFavourite)
        assertNull(once.toggleFavourite(true).includeFavourite)
    }

    @Test
    fun `tapping the other favourite chip switches ends rather than clearing`() {
        val positive = ProductFilterState(includeFavourite = true)
        assertEquals(false, positive.toggleFavourite(false).includeFavourite)
    }

    @Test
    fun `tapping the active category chip clears it`() {
        val obst = ProductFilterState.Unfiltered.toggleCategory("Obst")
        assertEquals("Obst", obst.category)
        assertNull(obst.toggleCategory("Obst").category)
    }

    @Test
    fun `categories are exclusive - a second category replaces the first`() {
        val obst = ProductFilterState(category = "Obst")
        assertEquals("Gemüse", obst.toggleCategory("Gemüse").category)
    }

    // ----- Alle -----

    @Test
    fun `Unfiltered reports itself unfiltered and both axes set do not`() {
        assertTrue(ProductFilterState.Unfiltered.isUnfiltered)
        assertFalse(ProductFilterState(includeFavourite = false).isUnfiltered)
        assertFalse(ProductFilterState(category = "Obst").isUnfiltered)
    }

    @Test
    fun `resetting to Unfiltered clears both axes but the query is not part of it`() {
        // The Alle chip resets this object; the search query lives elsewhere in
        // MainScreenState precisely so a chip tap cannot wipe the buyer's typing.
        val narrowed = ProductFilterState(includeFavourite = true, category = "Obst")
        assertTrue(ProductFilterState.Unfiltered.isUnfiltered)
        assertFalse(narrowed.isUnfiltered)

        val reset = state(filterState = ProductFilterState.Unfiltered, query = "apfel")
        assertEquals(listOf("1"), reset.filteredArticles.ids())
    }

    // ----- the axes compose -----

    @Test
    fun `category honours the favourite axis`() {
        val filtered = state(
            filterState = ProductFilterState(includeFavourite = true, category = "Obst"),
            favourites = listOf("1", "3")   // one fruit, one vegetable
        ).filteredArticles
        assertEquals(listOf("1"), filtered.ids())
    }

    @Test
    fun `category honours the negative favourite axis too`() {
        val filtered = state(
            filterState = ProductFilterState(includeFavourite = false, category = "Gemüse"),
            favourites = listOf("3")
        ).filteredArticles
        assertEquals(listOf("4"), filtered.ids())
    }

    @Test
    fun `search query narrows on top of both axes`() {
        val filtered = state(
            filterState = ProductFilterState(includeFavourite = true, category = "Obst"),
            favourites = listOf("1", "2"),
            query = "banane"
        ).filteredArticles
        assertEquals(listOf("2"), filtered.ids())
    }

    // ----- category matching over both fields -----

    @Test
    fun `category matches the category field and the search terms tag`() {
        // Apfel carries category="Obst", Banane carries it as a searchTerms tag. The
        // old chips matched searchTerms only, so every imported article was invisible.
        val filtered = state(filterState = ProductFilterState(category = "Obst")).filteredArticles
        assertEquals(listOf("1", "2"), filtered.ids())
    }

    @Test
    fun `unavailable articles stay hidden whatever the chips say`() {
        val withdrawn = article("5", "Birne", category = "Obst", available = false)
        val filtered = state(
            filterState = ProductFilterState(category = "Obst"),
            articles = listOf(apple, withdrawn)
        ).filteredArticles
        assertEquals(listOf("1"), filtered.ids())
    }

    @Test
    fun `an unavailable article is hidden even when it is a favourite`() {
        val withdrawn = article("5", "Birne", category = "Obst", available = false)
        val filtered = state(
            filterState = ProductFilterState(includeFavourite = true),
            favourites = listOf("5"),
            articles = listOf(apple, withdrawn)
        ).filteredArticles
        assertTrue(filtered.isEmpty())
    }
}
