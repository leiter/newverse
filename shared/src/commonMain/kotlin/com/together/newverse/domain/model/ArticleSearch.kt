package com.together.newverse.domain.model

/**
 * Free-text matching for the article lists, shared by both flavours.
 *
 * This is the matching only — deliberately not the list it runs on. The buyer list
 * shows available articles and nothing else, while the seller needs the unavailable
 * ones most of all, because those are the ones to fix. Each caller applies its own
 * baseline and then this.
 *
 * A blank query matches everything, so a collapsed or empty search field never hides
 * anything.
 */

/**
 * Whether [query] appears in this article's name, search terms or category.
 *
 * [includeProductId] adds the article number, which only the seller wants: they hold
 * a supplier invoice or a BNN printout with the number on it. A buyer typing "117"
 * means something else entirely, so it stays off by default.
 */
fun Article.matchesQuery(query: String, includeProductId: Boolean = false): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    return productName.lowercase().contains(needle) ||
        searchTerms.lowercase().contains(needle) ||
        category.lowercase().contains(needle) ||
        (includeProductId && productId.lowercase().contains(needle))
}

/** [matchesQuery] over a list, keeping order. */
fun List<Article>.filterByQuery(query: String, includeProductId: Boolean = false): List<Article> =
    if (query.isBlank()) this else filter { it.matchesQuery(query, includeProductId) }

/**
 * The categories actually present in [this], in the order [ProductCategory] declares
 * them, so the chip row is stable as articles come and go.
 *
 * Built from the articles rather than from [ProductCategory.entries] because the enum
 * carries thirteen categories and a real catalog uses a handful: chips for Fleisch and
 * Getränke that can only ever show an empty list are noise. A category the enum does
 * not know is still offered, so a hand-typed one does not become unreachable.
 */
fun List<Article>.presentCategories(): List<String> {
    val present = mapNotNull { it.category.takeIf(String::isNotBlank) }.toSet()
    val known = ProductCategory.entries.map { it.displayName }.filter { it in present }
    return known + present.filterNot { it in known }.sorted()
}
