package com.together.newverse.android.authentication

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.together.newverse.android.R
import com.together.newverse.android.data.IMAGE_UPLOAD_ARTICLE_IDS
import com.together.newverse.android.data.imageAssetPath
import com.together.newverse.android.data.offerArticles
import com.together.newverse.android.utils.BaseTest
import com.together.newverse.domain.model.SellerArticle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.FileNotFoundException

/**
 * Proves the article-plus-image upload path on a handful of [offerArticles] before wiring all
 * 317 Terra articles: for each picked article, uploads its Terra image via [StorageRepository]
 * first, attaches the resulting download URL, then saves the article like the other
 * upload tests.
 *
 * Requires the device to be signed in as the test seller (Google) beforehand, like
 * [OfferProductDataTests].
 *
 * Run with:
 *   ./gradlew :androidApp:connectedSellDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=\
 *       com.together.newverse.android.authentication.ImageUploadArticleTests
 */
class ImageUploadArticleTests : BaseTest() {

    private fun loadPickedArticles(): List<SellerArticle> {
        val picked = offerArticles.filter { it.article.productId in IMAGE_UPLOAD_ARTICLE_IDS }
        assertTrue(
            "Expected ${IMAGE_UPLOAD_ARTICLE_IDS.size} picked articles, found ${picked.size}",
            picked.size == IMAGE_UPLOAD_ARTICLE_IDS.size
        )
        return picked
    }

    private fun readImageBytes(productId: String): ByteArray {
        val path = imageAssetPath(productId)
        return try {
            InstrumentationRegistry.getInstrumentation().context.assets.open(path).use { it.readBytes() }
        } catch (e: FileNotFoundException) {
            throw AssertionError(
                "Terra image missing: copy tmp/terra-images/$productId.webp to " +
                    "tmp/androidTest-assets/$path and rebuild", e
            )
        }
    }

    /**
     * Uploads each picked article's image, then saves the article with the resulting URL.
     * Each upload creates a new article, so clear the seller's articles before re-running.
     */
    @Test
    fun uploadArticlesWithImagesForTestSeller() {
        val activity = activityRule.activity
        val testData = activity.testData
        testData.isGoogleAuth = true
        assertTrue("Must be logged in to upload products", testData.isLoggedIn)

        val articlesWithImages = runBlocking {
            loadPickedArticles().map { sellerArticle ->
                val imageData = readImageBytes(sellerArticle.article.productId)
                val imageUrl = activity.storageRepository
                    .uploadImage(imageData, "${sellerArticle.article.productId}.webp")
                    .getOrElse {
                        fail("Image upload failed for ${sellerArticle.article.productId}: ${it.message}")
                        return@getOrElse ""
                    }
                sellerArticle.copy(article = sellerArticle.article.copy(imageUrl = imageUrl))
            }
        }

        testData.productList = articlesWithImages

        Espresso.onView(withId(R.id.upload_products)).perform(click())
        // Wait for upload to complete (IdleMessenger handles loading state)
    }
}
