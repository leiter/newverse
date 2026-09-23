package com.together.newverse.android.authentication

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.together.newverse.android.R
import com.together.newverse.android.data.LOW_PRICE_ARTICLE_COUNT
import com.together.newverse.android.data.LOW_PRICE_BNN_ASSET
import com.together.newverse.android.data.LOW_PRICE_MIN_MARKUP
import com.together.newverse.android.data.lowPriceArticles
import com.together.newverse.android.data.offerArticles
import com.together.newverse.android.utils.BaseTest
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.domain.model.ProductPricing
import com.together.newverse.domain.model.ProductUnit
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.TaxRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

/**
 * Uploads the whole Terra price list at low prices: 317 articles, of which only the 30
 * hand-picked offer articles are available. See LowPriceArticles.kt for the price tiers
 * and where the BNN file has to be put.
 *
 * Separate from [OfferProductDataTests]: that one uploads the 30 articles at the regular
 * markup. Each upload creates new articles, so clear the seller's articles before switching.
 *
 * Run with:
 *   ./gradlew :androidApp:connectedSellDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=\
 *       com.together.newverse.android.authentication.LowPriceOfferProductDataTests
 */
class LowPriceOfferProductDataTests : BaseTest() {

    private fun loadArticles(): List<SellerArticle> {
        val content = try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open(LOW_PRICE_BNN_ASSET)
                // BNN files from Terra are DOS-encoded, as in the seller's file import
                .bufferedReader(charset("IBM850")).use { it.readText() }
        } catch (e: FileNotFoundException) {
            throw AssertionError(
                "Terra BNN file missing: copy tmp/plf.bnn to " +
                    "tmp/androidTest-assets/$LOW_PRICE_BNN_ASSET and rebuild", e
            )
        }
        return lowPriceArticles(content)
    }

    /** Guards the generated data. Runs without network or login. */
    @Test
    fun lowPriceArticlesAreWellFormed() {
        val articles = loadArticles()
        assertEquals("Terra list has $LOW_PRICE_ARTICLE_COUNT rows", LOW_PRICE_ARTICLE_COUNT, articles.size)
        assertEquals(
            "Duplicate BNN article numbers",
            articles.size,
            articles.map { it.article.productId }.toSet().size
        )

        // Exactly the hand-picked offer is available, all of it.
        assertEquals(
            "Available articles must be the hand-picked offer",
            offerArticles.map { it.article.productId }.toSet(),
            articles.filter { it.article.available }.map { it.article.productId }.toSet()
        )

        val validUnits = ProductUnit.getAllDisplayNames()
        val validCategories = ProductCategory.getAllDisplayNames()

        articles.forEach { seller ->
            val article = seller.article
            val sellerData = seller.sellerData!!
            val name = article.productName
            assertTrue("${article.productId}: blank product name", name.isNotBlank())
            assertTrue("$name: unknown unit '${article.unit}'", article.unit in validUnits)
            assertTrue("$name: unknown category '${article.category}'", article.category in validCategories)
            assertTrue("$name: no supplier price", sellerData.hasAcquirePrice)
            assertTrue("$name: weightPerPiece must be > 0", article.weightPerPiece > 0.0)
            assertTrue(
                "$name: tax rate ${article.taxRate} is not one of the offered rates",
                TaxRate.entries.any { it.rate == article.taxRate }
            )
            assertTrue("$name: no description", article.detailInfo.isNotBlank())
            assertTrue(
                "$name: description does not state an origin: ${article.detailInfo}",
                article.detailInfo.contains("Angebaut in") ||
                    article.detailInfo.contains("regionalem Anbau")
            )

            assertTrue(
                "$name: markup ${sellerData.markupFactor} below the $LOW_PRICE_MIN_MARKUP floor",
                sellerData.markupFactor >= LOW_PRICE_MIN_MARKUP
            )
            val expected = ProductPricing.sellPrice(
                sellerData.acquirePrice, sellerData.markupFactor, article.taxRate
            )
            assertEquals("$name: price out of sync with markup", expected, article.price, 0.001)
            assertTrue(
                "$name: sell price ${article.price} must exceed acquire price ${sellerData.acquirePrice}",
                article.price > sellerData.acquirePrice
            )
            // A Kiste is never sold whole: the per-piece price stays in a shop range.
            assertFalse("$name: still carries a Kiste count", Regex("""\d+\s*St$""").containsMatchIn(name))
        }

        // Price table for review: adb logcat -s System.out | grep LowPrice
        articles.forEach { (article, sellerData) ->
            println(
                "LowPrice|${article.productId}|${article.productName}|${article.category}|" +
                    "${article.unit}|${sellerData!!.acquirePrice}|${sellerData.markupFactor}|" +
                    "${article.price}|${article.weightPerPiece}|${article.available}"
            )
        }
    }

    /**
     * Uploads all articles to the logged-in test seller. Requires the device to be signed
     * in as the test seller (Google) beforehand, like [OfferProductDataTests].
     */
    @Test
    fun uploadLowPriceProductsForTestSeller() {
        val testData = activityRule.activity.testData
        testData.isGoogleAuth = true
        assertTrue("Must be logged in to upload products", testData.isLoggedIn)

        testData.productList = loadArticles()

        Espresso.onView(withId(R.id.upload_products)).perform(click())
        // Wait for upload to complete (IdleMessenger handles loading state)
    }
}
