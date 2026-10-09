package com.together.newverse.android.authentication

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.together.newverse.android.R
import com.together.newverse.android.data.LOW_PRICE_ARTICLE_COUNT
import com.together.newverse.android.data.LOW_PRICE_BNN_ASSET
import com.together.newverse.android.data.imageAssetPath
import com.together.newverse.android.data.lowPriceArticles
import com.together.newverse.android.data.offerArticles
import com.together.newverse.android.data.terraImageStoragePath
import com.together.newverse.android.utils.BaseTest
import com.together.newverse.domain.model.SellerArticle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

/**
 * Uploads the whole Terra price list with its images: all [LOW_PRICE_ARTICLE_COUNT]
 * articles with their selling price, each carrying the download URL of its product
 * image, of which only the 30 hand-picked [offerArticles] are available to buyers.
 *
 * This is the one to run to put the catalog in front of a buyer. The two tests it grew
 * out of stay for narrower checks: [LowPriceOfferProductDataTests] uploads the same
 * articles without images, [ImageUploadArticleTests] proves the image path on five of
 * them, and [OfferProductDataTests] uploads only the 30 at the regular markup.
 *
 * Unlike those, a re-run does not duplicate anything and does not need the seller's
 * articles cleared first:
 *  - every article is keyed by its Terra article number, so a save upserts
 *    (see `GitLiveSellerArticleRepository.saveSellerArticles`),
 *  - an image already uploaded is reused instead of sent again, so a run that died
 *    halfway resumes from where it stopped,
 *  - image objects are keyed by article number too, so a re-upload overwrites rather
 *    than orphaning the previous object.
 *
 * Requires the device to be signed in as the test seller (Google) beforehand, like
 * [OfferProductDataTests].
 *
 * Run with:
 *   ./gradlew :androidApp:connectedSellDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=\
 *       com.together.newverse.android.authentication.CatalogUploadTests
 */
class CatalogUploadTests : BaseTest() {

    private fun loadArticles(): List<SellerArticle> {
        val content = try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open(LOW_PRICE_BNN_ASSET)
                // BNN files from Terra are DOS-encoded, as in the seller's file import
                .bufferedReader(charset("IBM850")).use { it.readText() }
        } catch (e: FileNotFoundException) {
            throw AssertionError(
                "Terra BNN file missing: copy tmp/plf.bnn to " +
                    "tmp/androidTest-assets/$LOW_PRICE_BNN_ASSET and rebuild",
                e
            )
        }
        return lowPriceArticles(content)
    }

    private fun readImageBytes(productId: String): ByteArray {
        val path = imageAssetPath(productId)
        return try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open(path).use { it.readBytes() }
        } catch (e: FileNotFoundException) {
            throw AssertionError(
                "Terra image missing: copy tmp/terra-images/$productId.webp to " +
                    "tmp/androidTest-assets/$path and rebuild",
                e
            )
        }
    }

    /**
     * Every article has an image packed and is keyed by its article number — the two
     * things the upload below depends on. Runs without network or login.
     */
    @Test
    fun everyCatalogArticleIsReadyToUpload() {
        val articles = loadArticles()
        assertEquals(
            "Terra list has $LOW_PRICE_ARTICLE_COUNT rows",
            LOW_PRICE_ARTICLE_COUNT,
            articles.size
        )

        val missingImages = articles.map { it.article.productId }
            .filter { productId ->
                runCatching { readImageBytes(productId) }.isFailure
            }
        assertTrue(
            "No image packed for ${missingImages.size} article(s): " +
                missingImages.take(10).joinToString() +
                " — copy tmp/terra-images to tmp/androidTest-assets/terra-images and rebuild",
            missingImages.isEmpty()
        )

        // The article number is the database key: without it a re-run would duplicate.
        articles.forEach { seller ->
            assertEquals(
                "${seller.article.productName}: id must be the Terra article number",
                seller.article.productId,
                seller.article.id
            )
        }
        assertEquals(
            "Duplicate article ids would collapse rows into one",
            articles.size,
            articles.map { it.article.id }.toSet().size
        )
    }

    /**
     * Uploads every article with its image to the logged-in test seller. Safe to
     * re-run: see the class comment.
     */
    @Test
    fun uploadCatalogWithImagesForTestSeller() {
        val activity = activityRule.activity
        val testData = activity.testData
        testData.isGoogleAuth = true
        assertTrue("Must be logged in to upload products", testData.isLoggedIn)

        val sellerId = requireNotNull(FirebaseAuth.getInstance().currentUser?.uid) {
            "Must be signed in as the test seller to upload the catalog"
        }

        val withImages = runBlocking {
            // What is already stored, so a re-run does not pay for the images again.
            val storedImageUrls = activity.sellerArticleRepository
                .observeSellerArticles(sellerId)
                .first()
                .filter { it.article.imageUrl.isNotBlank() }
                .associate { it.article.productId to it.article.imageUrl }
            println("Catalog|reusing ${storedImageUrls.size} already uploaded image(s)")

            loadArticles().map { sellerArticle ->
                val productId = sellerArticle.article.productId
                val imageUrl = storedImageUrls[productId] ?: activity.storageRepository
                    .uploadImage(readImageBytes(productId), terraImageStoragePath(productId))
                    .getOrElse { error("Image upload failed for $productId: ${it.message}") }
                sellerArticle.copy(article = sellerArticle.article.copy(imageUrl = imageUrl))
            }
        }

        assertTrue(
            "Every article must carry an image URL before saving",
            withImages.all { it.article.imageUrl.isNotBlank() }
        )

        testData.productList = withImages

        Espresso.onView(withId(R.id.upload_products)).perform(click())
        // Wait for upload to complete (IdleMessenger handles loading state)
    }
}
