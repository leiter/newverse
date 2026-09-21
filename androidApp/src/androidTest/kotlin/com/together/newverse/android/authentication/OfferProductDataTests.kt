package com.together.newverse.android.authentication

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.together.newverse.android.R
import com.together.newverse.android.data.OFFER_ARTICLE_COUNT
import com.together.newverse.android.data.offerArticles
import com.together.newverse.android.utils.BaseTest
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.domain.model.ProductPricing
import com.together.newverse.domain.model.ProductUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uploads the 30 long-storable fruit & vegetable articles from `offer.md` to the test seller.
 *
 * Companion to [ProductDataTests], which uploads the small generic `testArticles` set.
 * The upload button in TestContainerActivity iterates `testData.productList` at click time,
 * so the test only has to swap that list before clicking.
 *
 * Run with:
 *   ./gradlew :androidApp:connectedSellDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=\
 *       com.together.newverse.android.authentication.OfferProductDataTests
 */
class OfferProductDataTests : BaseTest() {

    /**
     * Guards the offer data itself. Runs without network or login, so a broken
     * price/unit/category stays a fast local failure instead of bad Firebase data.
     */
    @Test
    fun offerArticlesAreWellFormed() {
        assertEquals("offer.md defines 30 products", OFFER_ARTICLE_COUNT, offerArticles.size)

        val validUnits = ProductUnit.getAllDisplayNames()
        val validCategories = ProductCategory.getAllDisplayNames()

        offerArticles.forEach { offer ->
            val article = offer.article
            val sellerData = offer.sellerData
            val name = article.productName
            assertTrue("Blank product name", name.isNotBlank())
            assertTrue("$name: missing BNN article number", article.productId.isNotBlank())
            assertTrue("$name: unknown unit '${article.unit}'", article.unit in validUnits)
            assertTrue(
                "$name: unknown category '${article.category}'",
                article.category in validCategories
            )
            assertTrue("$name: no seller-only data", sellerData != null)
            sellerData!!
            assertTrue("$name: no supplier price", sellerData.hasAcquirePrice)
            assertTrue("$name: no supplier code", sellerData.supplier.isNotBlank())
            assertTrue("$name: no origin code", sellerData.origin.isNotBlank())
            assertTrue("$name: no certification code", sellerData.certification.isNotBlank())
            assertTrue("$name: weightPerPiece must be > 0", article.weightPerPiece > 0.0)
            assertTrue("$name: search terms missing", article.searchTerms.isNotBlank())

            // Every uploaded article must reach the buyer with a description, and it
            // must say where the food comes from — that is the point of the field.
            assertTrue("$name: no description", article.detailInfo.isNotBlank())
            assertTrue(
                "$name: description does not state an origin: ${article.detailInfo}",
                article.detailInfo.contains("Angebaut in") ||
                    article.detailInfo.contains("regionalem Anbau")
            )
            // Trade details are seller-facing and must not leak into the buyer text.
            listOf("Gebinde", "Handelsklasse", "netto", "EUR").forEach { term ->
                assertFalse(
                    "$name: description leaks trade detail '$term': ${article.detailInfo}",
                    article.detailInfo.contains(term)
                )
            }
            assertTrue(
                "$name: sell price ${article.price} must exceed acquire price " +
                    "${sellerData.acquirePrice}",
                article.price > sellerData.acquirePrice
            )

            // Same formula as the seller's product form, so the stored markup matches
            // what the form shows for this price.
            val expected = ProductPricing.sellPrice(
                sellerData.acquirePrice, sellerData.markupFactor, article.taxRate
            )
            assertEquals("$name: price out of sync with markup", expected, article.price, 0.001)
        }

        assertEquals(
            "Duplicate BNN article numbers in offer",
            offerArticles.size,
            offerArticles.map { it.article.productId }.toSet().size
        )
    }

    /**
     * Uploads the offer to the logged-in test seller's `articles` collection.
     * Requires the device to be signed in as the test seller (Google) beforehand,
     * same precondition as [ProductDataTests.uploadProductsForTestSeller].
     */
    @Test
    fun uploadOfferProductsForTestSeller() {
        val testData = activityRule.activity.testData
        testData.isGoogleAuth = true
        assertTrue("Must be logged in to upload products", testData.isLoggedIn)

        testData.productList = offerArticles

        Espresso.onView(withId(R.id.upload_products)).perform(click())
        // Wait for upload to complete (IdleMessenger handles loading state)
    }
}
