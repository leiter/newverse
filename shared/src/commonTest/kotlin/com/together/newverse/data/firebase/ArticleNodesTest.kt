package com.together.newverse.data.firebase

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.TaxRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleNodesTest {

    private val sellerId = "seller1"
    private val articleId = "a1"

    private val article = Article(
        id = articleId,
        productId = "112108",
        productName = "Apfel Topaz",
        available = true,
        unit = "kg",
        price = 3.5,
        weightPerPiece = 0.17,
        category = "Obst",
        searchTerms = "apfel,topaz",
        detailInfo = "Angebaut in Deutschland nach Bioland-Richtlinien.",
        taxRate = TaxRate.REDUCED.rate
    )

    private val sellerData = SellerArticleData(
        acquirePrice = 1.96,
        markupFactor = 1.67,
        supplier = "BOA",
        origin = "DE",
        certification = "DB",
        quality = "I",
        barcode = "4012345678901",
        packageSize = 6.0
    )

    // --- save ---

    @Test
    fun `save writes both halves under their own roots`() {
        val update = ArticleNodes.saveUpdate(sellerId, articleId, SellerArticle(article, sellerData))

        val publicKeys = update.keys.filter { it.startsWith("articles/seller1/a1/") }
        val privateKeys = update.keys.filter { it.startsWith("seller_articles/seller1/a1/") }
        assertEquals(publicKeys.size + privateKeys.size, update.size, "unexpected paths: ${update.keys}")
        assertEquals(3.5, update["articles/seller1/a1/price"])
        assertEquals(1.96, update["seller_articles/seller1/a1/acquirePrice"])
        assertEquals("BOA", update["seller_articles/seller1/a1/supplier"])
    }

    @Test
    fun `save without seller data writes the public half only`() {
        val update = ArticleNodes.saveUpdate(sellerId, articleId, SellerArticle(article, sellerData = null))

        assertTrue(update.isNotEmpty())
        assertTrue(
            update.keys.none { it.startsWith("seller_articles/") },
            "the private half must not be touched: ${update.keys}"
        )
    }

    @Test
    fun `public half never carries purchase data`() {
        val update = ArticleNodes.saveUpdate(sellerId, articleId, SellerArticle(article, sellerData))
        val publicFields = update.keys
            .filter { it.startsWith("articles/") }
            .map { it.substringAfterLast('/') }

        listOf("acquirePrice", "markupFactor", "supplier").forEach { field ->
            assertTrue(field !in publicFields, "$field leaked into the public node")
        }
    }

    @Test
    fun `public half stores the tax rate`() {
        val update = ArticleNodes.saveUpdate(
            sellerId, articleId,
            SellerArticle(article.copy(taxRate = TaxRate.STANDARD.rate), sellerData)
        )

        assertEquals(0.19, update["articles/seller1/a1/taxRate"])
    }

    @Test
    fun `save writes one path per field so unknown fields survive`() {
        val update = ArticleNodes.saveUpdate(sellerId, articleId, SellerArticle(article, sellerData))

        assertTrue(update.keys.none { it == "articles/seller1/a1" || it == "seller_articles/seller1/a1" })
        assertTrue(update.values.none { it is Map<*, *> })
    }

    @Test
    fun `save needs an assigned id`() {
        assertFailsWith<IllegalArgumentException> {
            ArticleNodes.saveUpdate(sellerId, "", SellerArticle(article, sellerData))
        }
    }

    // --- delete ---

    @Test
    fun `delete removes both halves`() {
        assertEquals(
            mapOf("articles/seller1/a1" to null, "seller_articles/seller1/a1" to null),
            ArticleNodes.deleteUpdate(sellerId, articleId)
        )
    }

    @Test
    fun `delete refuses an empty id instead of wiping the seller's catalog`() {
        assertFailsWith<IllegalArgumentException> { ArticleNodes.deleteUpdate(sellerId, "") }
    }

    // --- read ---

    @Test
    fun `public half round-trips`() {
        val stored = ArticleNodes.publicFields(article)

        assertEquals(article, ArticleNodes.articleFromMap(articleId, stored))
    }

    @Test
    fun `private half round-trips`() {
        assertEquals(sellerData, ArticleNodes.sellerDataFromMap(ArticleNodes.privateFields(sellerData)))
    }

    @Test
    fun `whole numbers from the database are read as doubles`() {
        val parsed = ArticleNodes.articleFromMap(articleId, mapOf("price" to 4L, "taxRate" to 0L))
        val data = ArticleNodes.sellerDataFromMap(mapOf("acquirePrice" to 2L, "packageSize" to 10L))

        assertEquals(4.0, parsed.price)
        assertEquals(0.0, parsed.taxRate)
        assertEquals(2.0, data.acquirePrice)
        assertEquals(10.0, data.packageSize)
    }

    @Test
    fun `articles saved before taxRate was stored get the default rate`() {
        val parsed = ArticleNodes.articleFromMap(articleId, mapOf("productName" to "Alt"))

        assertEquals(TaxRate.default.rate, parsed.taxRate)
    }

    @Test
    fun `missing private fields fall back to neutral values`() {
        val data = ArticleNodes.sellerDataFromMap(emptyMap<String, Any>())

        assertEquals(SellerArticleData(), data)
    }

    // --- join ---

    @Test
    fun `join pairs articles with their private half`() {
        val joined = ArticleNodes.join(mapOf(articleId to article), mapOf(articleId to sellerData))

        assertEquals(listOf(SellerArticle(article, sellerData)), joined)
    }

    @Test
    fun `join leaves seller data null when the private half is missing`() {
        val joined = ArticleNodes.join(mapOf(articleId to article), emptyMap())

        assertEquals(1, joined.size)
        assertNull(joined.single().sellerData)
    }

    @Test
    fun `join drops private halves without a public article`() {
        val joined = ArticleNodes.join(
            mapOf(articleId to article),
            mapOf(articleId to sellerData, "orphan" to SellerArticleData(acquirePrice = 9.0))
        )

        assertEquals(listOf(articleId), joined.map { it.id })
    }

    @Test
    fun `join keeps the public order`() {
        val ids = listOf("c", "a", "b")
        val joined = ArticleNodes.join(
            ids.associateWith { article.copy(id = it) },
            mapOf("b" to sellerData)
        )

        assertEquals(ids, joined.map { it.id })
    }
}
