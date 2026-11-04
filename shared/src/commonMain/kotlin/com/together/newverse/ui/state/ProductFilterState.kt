package com.together.newverse.ui.state

/**
 * What the buyer's home-screen chip row currently narrows the catalogue to.
 *
 * Modelled on `ItemManager.FilterState`: every axis is independent and nullable,
 * where **null means the axis is bypassed** rather than "no" — that is what lets a
 * single field carry three states, and what lets two axes hold at once. The old
 * single `ProductFilter` enum could not: it had one slot, so picking Obst threw away
 * Favoriten, and a buyer could never ask for their favourite vegetables.
 *
 * The axes compose by AND. Favoriten + Obst is the favourite fruit and nothing else.
 *
 * The search query is deliberately *not* an axis here. It is typed text with its own
 * clear affordance, it lives in [MainScreenState.searchQuery], and [reset] must not
 * wipe it: a buyer who taps a chip to widen the list has not asked for their typing
 * to be thrown away.
 */
data class ProductFilterState(
    /**
     * Favourites: null bypasses, true keeps only favourites, false keeps only the
     * rest.
     *
     * The negative is worth having and not symmetry for its own sake — "what have I
     * not starred yet" is how a buyer finds something new in a catalogue they have
     * already picked over, which is what the Entdecken tab on the favourites screen
     * exists for.
     */
    val includeFavourite: Boolean? = null,
    /**
     * The one category to keep, by [com.together.newverse.domain.model.Article.category]
     * display name, or null for every category.
     *
     * One slot, because the categories are exclusive by nature: an article is fruit
     * or it is vegetables, so Obst AND Gemüse at once can only ever be the empty
     * list. Tapping a second category chip replaces the first instead.
     */
    val category: String? = null
) {
    /** Whether nothing is narrowed — the state the "Alle" chip puts this in. */
    val isUnfiltered: Boolean get() = includeFavourite == null && category == null

    /** The favourite axis set to [include]; null bypasses it. */
    fun withFavourite(include: Boolean?): ProductFilterState = copy(includeFavourite = include)

    /** The category axis set to [name]; null clears it. */
    fun withCategory(name: String?): ProductFilterState = copy(category = name)

    /**
     * The favourite axis after one tap of its chip: bypassed -> favourites ->
     * everything but favourites -> bypassed.
     *
     * One chip for all three states, which only works because the chip *shows* all
     * three. The inverted state is the dangerous one -- a buyer looking at a list
     * with their favourites missing has to be able to see why -- so it carries both
     * its own label and a cancel icon, not just a fill. Three taps is the whole
     * cycle, so no state is more than two taps from being off.
     */
    fun cycleFavourite(): ProductFilterState = withFavourite(
        when (includeFavourite) {
            null -> true
            true -> false
            false -> null
        }
    )

    /** The category axis after tapping [name]'s chip; tapping the active one clears it. */
    fun toggleCategory(name: String): ProductFilterState =
        withCategory(if (category == name) null else name)

    companion object {
        /**
         * Every axis bypassed.
         *
         * The "Alle" chip is this and not a filter of its own, which is why it shows
         * as selected exactly when [isUnfiltered] holds — including when the buyer
         * has turned the last chip off by hand rather than tapping Alle.
         */
        val Unfiltered = ProductFilterState()
    }
}
