package com.together.newverse.domain.model

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Why a price differs at one market.
 *
 * The cause is stored, not the resulting number: the buyer is owed an explanation,
 * and a breakdown derived from the same data that produced the price cannot drift
 * away from it. See `doc/guides/market-pricing-and-access-design.md` §2.
 */
enum class PriceCause { STALL_FEE, TRANSPORT, HANDLING, NEIGHBORHOOD_RATE, VOLUME_COMMITMENT }

/** What a condition applies to. */
sealed interface PriceScope {
    data object AllArticles : PriceScope
    data class Category(val name: String) : PriceScope
    data class SingleArticle(val articleId: String) : PriceScope

    /** Narrower beats wider for the same cause. */
    val precedence: Int
        get() = when (this) {
            is SingleArticle -> 2
            is Category -> 1
            AllArticles -> 0
        }

    fun matches(article: Article): Boolean = when (this) {
        AllArticles -> true
        is Category -> article.category.equals(name, ignoreCase = true)
        is SingleArticle -> article.id == articleId
    }
}

/** How a condition changes the price. */
sealed interface PriceEffect {
    /** A proportion of the base price: 1.08 adds eight percent. */
    data class Factor(val value: Double) : PriceEffect

    /** A fixed amount added, in euros. */
    data class Surcharge(val amount: Double) : PriceEffect

    /** A fixed amount taken off, in euros. */
    data class Discount(val amount: Double) : PriceEffect
}

data class PriceCondition(
    val cause: PriceCause = PriceCause.STALL_FEE,
    val scope: PriceScope = PriceScope.AllArticles,
    val effect: PriceEffect = PriceEffect.Surcharge(0.0),
    /** The seller's own words, shown to the buyer as written. */
    val note: String = "",
)

/**
 * One market's price conditions.
 *
 * [revision] is bumped whenever the seller changes them and snapshotted into each
 * order, so a price that was charged can always be told apart from the price the
 * same basket would cost today.
 */
data class MarketPricing(
    val marketId: String = "",
    val revision: Long = 0L,
    val conditions: List<PriceCondition> = emptyList(),
) {
    val isEmpty: Boolean get() = conditions.isEmpty()

    companion object {
        val NONE = MarketPricing()
    }
}

/** One line of the explanation: what it is, how much, and why. */
data class PriceComponent(
    val cause: PriceCause,
    /** Signed, in euros, already rounded to cents. */
    val amount: Double,
    val note: String = "",
)

/**
 * A price and the reasoning behind it.
 *
 * [base] plus every [components] amount equals [gross] exactly — see
 * [resolvePrice] for why that is arranged rather than merely hoped for.
 */
data class ResolvedPrice(
    val base: Double,
    val gross: Double,
    val components: List<PriceComponent> = emptyList(),
) {
    /** Whether anything about this market changed the price. */
    val isAdjusted: Boolean get() = components.isNotEmpty()
}

/** Round to whole cents, away from zero on a tie. */
private fun Double.toCents(): Long = (this * 100.0).roundToLong()

private fun Long.fromCents(): Double = this / 100.0

/**
 * What [article] costs at a market, and why.
 *
 * Four rules, each of which the behaviour depends on:
 *
 * **Scope precedence.** For one cause, the narrowest matching condition wins —
 * a condition on the article beats one on its category beats one on everything.
 * Different causes always stack, because a stall fee and a transport charge are
 * two real costs, not two opinions about one.
 *
 * **Order.** Every factor is taken against the base price, then fixed amounts are
 * added. Factors are not compounded with one another: a seller who sets eight
 * percent and five percent means thirteen percent of the base, not 13.4, and more
 * importantly the answer cannot then depend on what order the conditions happen to
 * be stored in.
 *
 * **Rounding.** Every component is rounded to cents, and the gross is their sum.
 * The alternative — round the exact total, round each component for display — can
 * produce a breakdown that does not add up, and a buyer reading an explanation
 * whose figures disagree with the total has been given something worse than no
 * explanation. The cost is at most a cent against the unrounded arithmetic.
 *
 * **Floor.** Never below zero. The real floor is the seller's purchase price, but
 * that is seller-only data the buy app never sees, so it is enforced where it can
 * be: as a warning in the seller's editor, not a silent clamp here.
 */
fun resolvePrice(article: Article, pricing: MarketPricing): ResolvedPrice {
    val baseCents = article.price.toCents()
    if (pricing.isEmpty) {
        return ResolvedPrice(base = baseCents.fromCents(), gross = baseCents.fromCents())
    }

    // One condition per cause: the narrowest that matches. Ties within a scope level
    // keep the first, so a seller with two conditions of equal reach gets the one
    // they entered first rather than an arbitrary pick.
    val winners = pricing.conditions
        .filter { it.scope.matches(article) }
        .groupBy { it.cause }
        .mapNotNull { (_, forCause) -> forCause.maxByOrNull { it.scope.precedence } }

    val components = winners.mapNotNull { condition ->
        val cents = when (val effect = condition.effect) {
            is PriceEffect.Factor -> (article.price * (effect.value - 1.0)).toCents()
            is PriceEffect.Surcharge -> effect.amount.toCents()
            is PriceEffect.Discount -> -effect.amount.toCents()
        }
        // A condition that rounds away to nothing is not worth a line in the
        // explanation; it would read as "+0,00 € Transport".
        if (cents == 0L) null
        else PriceComponent(condition.cause, cents.fromCents(), condition.note)
    }

    val grossCents = (baseCents + components.sumOf { it.amount.toCents() }).coerceAtLeast(0L)

    return ResolvedPrice(
        base = baseCents.fromCents(),
        gross = grossCents.fromCents(),
        // Stable, so the same basket reads the same way twice: by cause.
        components = components.sortedBy { it.cause.ordinal }
    )
}

/**
 * This item priced as [resolved] says, carrying the explanation with the number.
 *
 * The single place a basket item's price is set from a market's conditions, so the
 * three fields cannot drift apart: a `price` without its `basePrice` and components
 * is a total nobody can explain, and components that do not sum to `price` are worse
 * than none. Used when an item enters the basket and again whenever the basket moves
 * market.
 */
fun OrderedProduct.pricedWith(resolved: ResolvedPrice): OrderedProduct = copy(
    price = resolved.gross,
    basePrice = resolved.base,
    priceComponents = resolved.components
)

/**
 * What changes for a basket moved from one market to another.
 *
 * A buyer who switches market with a full basket must be shown this and asked,
 * rather than having the total silently recalculated under them — see
 * `doc/guides/market-pricing-and-access-design.md` §2.
 */
data class PriceChange(
    val article: Article,
    val from: ResolvedPrice,
    val to: ResolvedPrice,
) {
    val difference: Double get() = to.gross - from.gross
    val isIncrease: Boolean get() = difference > 0
}

/** Every article in [articles] whose price differs between the two markets. */
fun priceChanges(
    articles: List<Article>,
    from: MarketPricing,
    to: MarketPricing,
): List<PriceChange> = articles.mapNotNull { article ->
    val before = resolvePrice(article, from)
    val after = resolvePrice(article, to)
    // Compared in cents: both are already rounded, so this is exact, but the
    // epsilon keeps a float representation of the same cents from reading as a
    // difference.
    if (abs(after.gross - before.gross) < 0.005) null
    else PriceChange(article, before, after)
}
