package com.together.newverse.domain.model

/**
 * What a buyer is told about how much is left.
 *
 * Three states and no numbers, for two independent reasons. A quantity read by a
 * buyer whose app is minutes behind is a promise the shop did not make, while a band
 * stays true while it is stale; and `/articles` is readable by every signed-in user,
 * so a number is a turnover curve for anyone who writes it down daily. A band tells
 * a competitor roughly what walking past the stall would.
 *
 * `doc/design/stock-driven-availability-design.md` §4f.
 */
enum class ArticleScarcity {
    /**
     * Nothing is said: the article is not gated on stock, has never been counted, or
     * has no reference quantity to call "few" (see `scarcityOf`). The default, and
     * what every article published before this existed reads as.
     */
    UNKNOWN,

    /** Enough on the shelf that saying anything would be noise. */
    PLENTIFUL,

    /** Running down: at or below what the seller would restock at. */
    LIMITED,

    /** Nearly gone. */
    LAST_FEW;

    /** Whether this is worth showing a buyer at all. */
    val isWorthShowing: Boolean get() = this == LIMITED || this == LAST_FEW
}
