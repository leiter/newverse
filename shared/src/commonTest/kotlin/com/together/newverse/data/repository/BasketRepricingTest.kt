package com.together.newverse.data.repository

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.MarketPricing
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.PriceCause
import com.together.newverse.domain.model.PriceCondition
import com.together.newverse.domain.model.PriceEffect
import com.together.newverse.domain.model.resolvePrice
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Moving the basket to another market changes what it costs and nothing else.
 *
 * A market change must never be able to alter what is in the basket — only what the
 * contents cost — because the buyer chose the contents and did not choose the
 * surcharge.
 */
class BasketRepricingTest {

    private val moehren = Article(id = "a1", productName = "Möhren", price = 2.80)
    private val apfel = Article(id = "a2", productName = "Apfel", price = 3.50)

    private val withStallFee = MarketPricing(
        marketId = "m2",
        revision = 3L,
        conditions = listOf(
            PriceCondition(cause = PriceCause.STALL_FEE, effect = PriceEffect.Surcharge(0.25))
        )
    )

    private fun basketOf(vararg articles: Article) = articles.map { article ->
        OrderedProduct(
            productId = article.id,
            productName = article.productName,
            unit = "kg",
            price = article.price,
            basePrice = article.price,
            amountCount = 2.0,
            piecesCount = -1
        )
    }

    private suspend fun repo(vararg articles: Article): InMemoryBasketRepository {
        val repo = InMemoryBasketRepository()
        basketOf(*articles).forEach { repo.addItem(it) }
        return repo
    }

    @Test
    fun `repricing raises the price and records why`() = runTest {
        val repo = repo(moehren, apfel)

        repo.repriceItems(
            mapOf(
                "a1" to resolvePrice(moehren, withStallFee),
                "a2" to resolvePrice(apfel, withStallFee)
            )
        )

        val items = repo.observeBasket().value
        assertEquals(3.05, items.first { it.productId == "a1" }.price)
        assertEquals(3.75, items.first { it.productId == "a2" }.price)
        assertTrue(items.all { it.isPriceAdjusted })
        assertEquals(PriceCause.STALL_FEE, items.first().priceComponents.single().cause)
    }

    @Test
    fun `the total follows the repriced items`() = runTest {
        val repo = repo(moehren)
        assertEquals(5.60, repo.getTotal())

        repo.repriceItems(mapOf("a1" to resolvePrice(moehren, withStallFee)))

        // This is the assertion the feature existed for: the warning the buyer
        // accepted now shows up in the money.
        assertEquals(6.10, repo.getTotal())
    }

    @Test
    fun `repricing leaves quantities and item count alone`() = runTest {
        val repo = repo(moehren, apfel)

        repo.repriceItems(mapOf("a1" to resolvePrice(moehren, withStallFee)))

        val items = repo.observeBasket().value
        assertEquals(2, items.size)
        assertTrue(items.all { it.amountCount == 2.0 })
        assertEquals(listOf("a1", "a2"), items.map { it.productId })
    }

    @Test
    fun `an item absent from the prices keeps the price it had`() = runTest {
        val repo = repo(moehren, apfel)

        // Only Möhren is in the catalogue this time; Apfel must not be guessed at.
        repo.repriceItems(mapOf("a1" to resolvePrice(moehren, withStallFee)))

        assertEquals(3.50, repo.observeBasket().value.first { it.productId == "a2" }.price)
    }

    @Test
    fun `repricing to a market with no conditions restores the base price`() = runTest {
        val repo = repo(moehren)
        repo.repriceItems(mapOf("a1" to resolvePrice(moehren, withStallFee)))
        assertEquals(3.05, repo.observeBasket().value.single().price)

        repo.repriceItems(mapOf("a1" to resolvePrice(moehren, MarketPricing.NONE)))

        val item = repo.observeBasket().value.single()
        assertEquals(2.80, item.price)
        assertTrue(item.priceComponents.isEmpty())
    }

    @Test
    fun `an empty price map is a no-op`() = runTest {
        val repo = repo(moehren)

        repo.repriceItems(emptyMap())

        assertEquals(2.80, repo.observeBasket().value.single().price)
        assertEquals(1, repo.observeBasket().value.size)
    }

    @Test
    fun `repricing twice to the same market is idempotent`() = runTest {
        val repo = repo(moehren)
        val prices = mapOf("a1" to resolvePrice(moehren, withStallFee))

        repo.repriceItems(prices)
        val afterFirst = repo.observeBasket().value.single()
        repo.repriceItems(prices)

        assertEquals(afterFirst, repo.observeBasket().value.single())
    }
}
