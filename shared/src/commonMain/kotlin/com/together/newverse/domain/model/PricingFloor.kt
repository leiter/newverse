package com.together.newverse.domain.model

import kotlin.math.roundToLong

/**
 * An article a market's price conditions would sell below what it cost to buy.
 *
 * [shortfall] is what the seller loses per unit, so the list can be ordered worst
 * first — a seller looking at a warning wants the one that hurts most, not the one
 * that happens to sort first alphabetically.
 */
data class BelowCostArticle(
    val articleId: String,
    val productName: String,
    val acquirePrice: Double,
    val gross: Double,
) {
    /**
     * Rounded to cents, like every other money figure here.
     *
     * A bare subtraction gives 0.19999999999999996 for 2,00 − 1,80, which is fine for
     * ordering and wrong the moment it is shown to anyone. `resolvePrice` rounds for
     * the same reason.
     */
    val shortfall: Double get() = ((acquirePrice - gross) * 100.0).roundToLong() / 100.0
}

/**
 * Which articles [pricing] would put below their purchase price.
 *
 * `resolvePrice` cannot enforce this. It runs in the buy app too, where the purchase
 * price does not exist — `SellerArticleData` is a separate node precisely so a buyer
 * who can list articles cannot read what they cost. So the floor `resolvePrice`
 * enforces is zero, and the real one is checked here, in the seller's editor, where
 * both halves of an article are in hand.
 *
 * A warning rather than a refusal: selling a loss-leader is a decision a seller is
 * allowed to make, and a market with a discount condition may well be exactly that.
 * What is not allowed is making it by accident.
 *
 * Articles with no recorded purchase price are skipped. `acquirePrice` of 0 means
 * unknown, not free — treating it as a floor would flag every article the seller has
 * not costed yet, which is most of them after a BNN import.
 *
 * See `doc/guides/market-pricing-and-access-design.md` §2.
 */
fun pricingBelowCost(
    articles: List<SellerArticle>,
    pricing: MarketPricing,
): List<BelowCostArticle> = articles.mapNotNull { seller ->
    val acquire = seller.sellerData?.acquirePrice ?: return@mapNotNull null
    if (acquire <= 0.0) return@mapNotNull null

    val gross = resolvePrice(seller.article, pricing).gross
    // Compared in whole cents: both sides are money, and a floating-point hair below
    // cost is not a warning worth showing.
    if ((gross * 100).toLong() >= (acquire * 100).toLong()) return@mapNotNull null

    BelowCostArticle(
        articleId = seller.article.id,
        productName = seller.article.productName,
        acquirePrice = acquire,
        gross = gross
    )
}.sortedByDescending { it.shortfall }
