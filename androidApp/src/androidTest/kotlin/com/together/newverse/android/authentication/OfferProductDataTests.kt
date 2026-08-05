package com.together.newverse.android.authentication

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.together.newverse.android.R
import com.together.newverse.android.data.OFFER_ARTICLE_COUNT
import com.together.newverse.android.data.offerArticles
import com.together.newverse.android.utils.BaseTest
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.domain.model.ProductUnit
import org.junit.Assert.assertEquals
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

        offerArticles.forEach { article ->
            val name = article.productName
            assertTrue("Blank product name", name.isNotBlank())
            assertTrue("$name: missing BNN article number", article.productId.isNotBlank())
            assertTrue("$name: unknown unit '${article.unit}'", article.unit in validUnits)
            assertTrue(
                "$name: unknown category '${article.category}'",
                article.category in validCategories
            )
            assertTrue("$name: no supplier price", article.acquirePrice > 0.0)
            assertTrue("$name: weightPerPiece must be > 0", article.weightPerPiece > 0.0)
            assertTrue("$name: search terms missing", article.searchTerms.isNotBlank())
            assertTrue(
                "$name: sell price ${article.price} must exceed acquire price " +
                    "${article.acquirePrice}",
                article.price > article.acquirePrice
            )

            // Same formula as CreateProductViewModel.recalculateSellPrice, so reopening the
            // product in the seller form does not silently recalculate a different price.
            val expected = (article.acquirePrice * article.markupFactor *
                (1.0 + article.taxRate) * 100).toLong() / 100.0
            assertEquals("$name: price out of sync with markup", expected, article.price, 0.001)
        }

        assertEquals(
            "Duplicate BNN article numbers in offer",
            offerArticles.size,
            offerArticles.map { it.productId }.toSet().size
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
