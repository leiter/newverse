package com.together.newverse.domain.availability

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.ArticleScarcity
import com.together.newverse.domain.model.MeasuredQuantity
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderOrigin
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.StockMovement
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeSellerArticleRepository
import com.together.newverse.test.FakeStockRepository
import com.together.newverse.test.MainDispatcherRule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * The derived half of the catalogue, and who writes it.
 *
 * The first eight cases came from `StockViewModelTest` when the pass moved out of that
 * screen (`doc/design/derived-price-and-availability.md` § 3). The rest are new and are
 * about the move itself — that the publisher follows the signed-in seller rather than
 * calling `getCurrentUserId()` once at construction, and that an order arriving with no
 * screen open is enough to close an article.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AvailabilityPublisherTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeSellerArticleRepository
    private lateinit var stockRepository: FakeStockRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var authRepository: FakeAuthRepository

    /** Ungated: decided by the seller's intent alone, so the pass owes it nothing. */
    private val apple = SellerArticle(
        Article(id = "apple", productName = "Apfel", unit = "kg"),
        SellerArticleData(acquirePrice = 1.96, reorderLevel = 5.0)
    )

    private val gatedApple = SellerArticle(
        Article(id = "apple", productName = "Apfel", unit = "kg", available = true),
        SellerArticleData(reorderLevel = 5.0, sellerAvailable = true, stockGated = true)
    )

    /**
     * The same article with the band it would already have been published with, so a
     * first pass over 6 kg finds nothing to correct and the next write is provably the
     * one the test caused. Without this the band alone — `UNKNOWN` becoming
     * `PLENTIFUL` — counts as a correction and the test cannot tell the two apart.
     */
    private val settledApple = gatedApple.copy(
        article = gatedApple.article.copy(scarcity = ArticleScarcity.PLENTIFUL)
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeSellerArticleRepository()
        stockRepository = FakeStockRepository()
        orderRepository = FakeOrderRepository()
        authRepository = FakeAuthRepository()
        authRepository.setCurrentUserId("seller_123")
        articleRepository.setArticles(listOf(apple))
    }

    @AfterTest
    fun tearDown() {
        articleRepository.reset()
        stockRepository.reset()
        orderRepository.reset()
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun publisher() = AvailabilityPublisher(
        sellerArticleRepository = articleRepository,
        stockRepository = stockRepository,
        orderRepository = orderRepository,
        authRepository = authRepository
    )

    /** Started and given a turn to see every listener's first emission. */
    private fun TestScope.running(): AvailabilityPublisher =
        publisher().also { it.start(); advanceUntilIdle() }

    // Demand only counts an order whose pickup is still ahead; a fixed epoch would
    // read as long since collected.
    private val tomorrow = Clock.System.now().plus(1.days).toEpochMilliseconds()

    private fun order(id: String, amount: Double = 8.0, unit: String = "kg") = Order(
        id = id,
        status = OrderStatus.PLACED,
        origin = OrderOrigin.PRODUCTION,
        pickUpDate = tomorrow,
        articles = listOf(
            OrderedProduct(productId = "apple", productName = "apple", unit = unit, amountCount = amount)
        )
    )

    private fun stocked(amount: Double) = stockRepository.setMovements(listOf(
        StockMovement.intake("apple", MeasuredQuantity.typed(amount, "kg"), recordedAt = 1_000L)
    ))

    // --- the derivation --------------------------------------------------------

    @Test
    fun `a gated article is closed to buyers once orders use up the shelf`() = runTest {
        articleRepository.setArticles(listOf(gatedApple))
        stocked(6.0)
        orderRepository.setOrders(listOf(order("o1", amount = 6.0)))

        running()

        assertEquals(listOf(mapOf("apple" to false)), articleRepository.availabilityWrites)
    }

    @Test
    fun `a gated article is opened again when stock arrives`() = runTest {
        articleRepository.setArticles(listOf(gatedApple.copy(
            article = gatedApple.article.copy(available = false)
        )))
        stocked(20.0)

        running()

        assertEquals(listOf(mapOf("apple" to true)), articleRepository.availabilityWrites)
    }

    @Test
    fun `the band is published even when availability does not move`() = runTest {
        // 20 kg against a 5 kg reorder level: still orderable, so `available` stays
        // true — but buyers have been told nothing about how much is left.
        articleRepository.setArticles(listOf(gatedApple))
        stocked(20.0)

        running()

        val written = articleRepository.publicationWrites.single().getValue("apple")
        assertEquals(true, written.available)
        assertEquals(ArticleScarcity.PLENTIFUL, written.scarcity)
    }

    @Test
    fun `a published article that already agrees is not written again`() = runTest {
        articleRepository.setArticles(listOf(gatedApple.copy(
            article = gatedApple.article.copy(scarcity = ArticleScarcity.PLENTIFUL)
        )))
        stocked(20.0)

        running()

        assertTrue(articleRepository.publicationWrites.isEmpty())
    }

    @Test
    fun `the pass settles instead of writing the same correction twice`() = runTest {
        articleRepository.setArticles(listOf(gatedApple))
        stocked(2.0)
        orderRepository.setOrders(listOf(order("o1", amount = 2.0)))
        running()
        assertEquals(1, articleRepository.availabilityWrites.size)

        // The write re-emits the catalogue; the next evaluation must find nothing.
        advanceUntilIdle()

        assertEquals(1, articleRepository.availabilityWrites.size)
    }

    @Test
    fun `an ungated article is never written by the pass`() = runTest {
        stocked(0.0)
        orderRepository.setOrders(listOf(order("o1", amount = 50.0)))

        running()

        assertTrue(articleRepository.availabilityWrites.isEmpty())
    }

    @Test
    fun `a gated article whose intent was never recorded is left alone`() = runTest {
        articleRepository.setArticles(listOf(gatedApple.copy(
            sellerData = SellerArticleData(reorderLevel = 5.0, stockGated = true)
        )))
        stocked(0.0)

        running()

        assertTrue(
            articleRepository.availabilityWrites.isEmpty(),
            "closed an article that could then never be reopened"
        )
    }

    @Test
    fun `a gated article the ledger has never seen is left open`() = runTest {
        articleRepository.setArticles(listOf(gatedApple))

        running()

        assertTrue(
            articleRepository.availabilityWrites.isEmpty(),
            "a never-counted article was withdrawn from buyers"
        )
    }

    // --- being app-scoped ------------------------------------------------------
    //
    // The point of the move: the catalogue is corrected by an order arriving, which no
    // screen is watching for, and against whoever is signed in now rather than whoever
    // was signed in when the object was built.

    @Test
    fun `an order arriving with no screen open closes the article`() = runTest {
        articleRepository.setArticles(listOf(settledApple))
        stocked(6.0)
        running()
        assertTrue(articleRepository.publicationWrites.isEmpty(), "settled fixture was corrected")

        // Nothing the seller did: a buyer ordered.
        orderRepository.setOrders(listOf(order("o1", amount = 6.0)))
        advanceUntilIdle()

        assertEquals(listOf(mapOf("apple" to false)), articleRepository.availabilityWrites)
    }

    @Test
    fun `nothing is written while nobody is signed in`() = runTest {
        authRepository.setCurrentUserId(null)
        articleRepository.setArticles(listOf(gatedApple))
        stocked(6.0)
        orderRepository.setOrders(listOf(order("o1", amount = 6.0)))

        running()

        assertTrue(articleRepository.availabilityWrites.isEmpty())
    }

    @Test
    fun `signing in starts the pass without rebuilding the publisher`() = runTest {
        authRepository.setCurrentUserId(null)
        articleRepository.setArticles(listOf(gatedApple))
        stocked(6.0)
        orderRepository.setOrders(listOf(order("o1", amount = 6.0)))
        running()

        authRepository.setCurrentUserId("seller_123")
        advanceUntilIdle()

        assertEquals(listOf(mapOf("apple" to false)), articleRepository.availabilityWrites)
    }

    @Test
    fun `stopping leaves the catalogue alone`() = runTest {
        articleRepository.setArticles(listOf(settledApple))
        stocked(6.0)
        val publisher = running()

        publisher.stop()
        orderRepository.setOrders(listOf(order("o1", amount = 6.0)))
        advanceUntilIdle()

        assertTrue(articleRepository.availabilityWrites.isEmpty())
    }

    @Test
    fun `starting twice leaves one watcher`() = runTest {
        articleRepository.setArticles(listOf(gatedApple))
        stocked(6.0)
        orderRepository.setOrders(listOf(order("o1", amount = 6.0)))

        val publisher = publisher()
        publisher.start()
        publisher.start()
        advanceUntilIdle()

        assertEquals(1, articleRepository.availabilityWrites.size)
    }

    @Test
    fun `it can be started again after being stopped`() = runTest {
        // The publisher is a Koin `single` and outlives the ViewModel that starts it:
        // backing out of the app clears that ViewModel, the next one starts the same
        // instance. A scope cancelled for good on the first stop left it silently dead
        // for the rest of the process — worse than the screen-scoped pass it replaced,
        // because nothing said so.
        articleRepository.setArticles(listOf(settledApple))
        stocked(6.0)
        val publisher = running()
        publisher.stop()

        publisher.start()
        advanceUntilIdle()
        orderRepository.setOrders(listOf(order("o1", amount = 6.0)))
        advanceUntilIdle()

        assertEquals(
            listOf(mapOf("apple" to false)),
            articleRepository.availabilityWrites,
            "a restarted publisher never corrected anything"
        )
    }

    @Test
    fun `stopping twice is harmless`() = runTest {
        articleRepository.setArticles(listOf(settledApple))
        stocked(6.0)
        val publisher = running()

        publisher.stop()
        publisher.stop()

        assertTrue(articleRepository.publicationWrites.isEmpty())
    }
}
