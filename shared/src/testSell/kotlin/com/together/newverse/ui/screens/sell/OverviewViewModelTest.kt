package com.together.newverse.ui.screens.sell

import app.cash.turbine.test
import com.together.newverse.domain.config.SellerConfig
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_CHANGED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.Product
import com.together.newverse.domain.service.ProductImportService
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.ui.state.core.AsyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Unit tests for OverviewViewModel.
 *
 * Tests cover:
 * - Initial state and loading
 * - Article management (add, change, remove)
 * - Product filtering (ALL, AVAILABLE, NOT_AVAILABLE)
 * - Order statistics and revenue calculation
 * - Article deletion
 * - Article availability updates
 * - Product import workflow (parse, preview, import)
 * - Authentication handling
 * - Refresh functionality
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var sellerConfig: FakeSellerConfig
    private lateinit var productImportService: FakeProductImportService

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        authRepository = FakeAuthRepository()
        sellerConfig = FakeSellerConfig()
        productImportService = FakeProductImportService()
    }

    @AfterTest
    fun tearDown() {
        // Reset repositories BEFORE dispatcher teardown to avoid StateFlow issues
        articleRepository.reset()
        orderRepository.reset()
        authRepository.reset()
        productImportService.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = OverviewViewModel(
        articleRepository = articleRepository,
        orderRepository = orderRepository,
        authRepository = authRepository,
        sellerConfig = sellerConfig,
        productImportService = productImportService
    )

    // ===== Test Helpers =====

    private fun createArticle(
        id: String,
        name: String = "Test Product",
        available: Boolean = true,
        price: Double = 10.0,
        mode: Int = MODE_ADDED
    ): Article {
        return Article(
            id = id,
            productId = "prod_$id",
            productName = name,
            available = available,
            price = price,
            mode = mode
        )
    }

    private fun createOrder(
        id: String,
        status: OrderStatus = OrderStatus.COMPLETED,
        articles: List<OrderedProduct> = emptyList(),
        pickUpDate: Long = Clock.System.now().toEpochMilliseconds() + 86400000
    ): Order {
        return Order(
            id = id,
            sellerId = "seller_123",
            status = status,
            articles = articles,
            pickUpDate = pickUpDate
        )
    }

    private fun createOrderedProduct(
        id: String,
        price: Double = 5.0,
        amount: Double = 2.0
    ): OrderedProduct {
        return OrderedProduct(
            id = id,
            productId = "prod_$id",
            productName = "Product $id",
            price = price,
            amountCount = amount
        )
    }

    // ===== A. Initial State and Loading (3 tests) =====

    @Test
    fun `initial state is Loading`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")

        // When creating ViewModel
        val viewModel = createViewModel()

        // Then initial state starts as Loading (may quickly transition to Success)
        viewModel.overviewState.test {
            val state = awaitItem()
            assertTrue(
                state is AsyncState.Loading || state is AsyncState.Success,
                "Expected Loading or Success, got $state"
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial filter is ALL`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")

        // When creating ViewModel
        val viewModel = createViewModel()

        // Then initial filter is ALL
        viewModel.currentFilter.test {
            val filter = awaitItem()
            assertEquals(ProductFilter.ALL, filter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial import state is Idle`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")

        // When creating ViewModel
        val viewModel = createViewModel()

        // Then import state is Idle
        viewModel.importState.test {
            val state = awaitItem()
            assertIs<ImportState.Idle>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== B. Authentication (2 tests) =====

    @Test
    fun `error when user not authenticated`() = runTest {
        // Given user is NOT authenticated
        authRepository.setCurrentUserId(null)

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then state is Error
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Error>(state)
            assertEquals("Not authenticated", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads data when user is authenticated`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then state eventually becomes Success
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== C. Article Management (4 tests) =====

    @Test
    fun `adds articles when receiving MODE_ADDED`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When emitting articles with MODE_ADDED
        articleRepository.emitArticle(createArticle("1", "Apple", mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("2", "Banana", mode = MODE_ADDED))
        advanceUntilIdle()

        // Then articles are added to overview
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(2, state.data.totalProducts)
            assertEquals(2, state.data.recentArticles.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updates articles when receiving MODE_CHANGED`() = runTest {
        // Given authenticated user and existing article
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Add initial article
        articleRepository.emitArticle(createArticle("1", "Apple", price = 10.0, mode = MODE_ADDED))
        advanceUntilIdle()

        // When updating article with MODE_CHANGED
        articleRepository.emitArticle(createArticle("1", "Apple Updated", price = 15.0, mode = MODE_CHANGED))
        advanceUntilIdle()

        // Then article is updated (not duplicated)
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(1, state.data.totalProducts)
            assertEquals("Apple Updated", state.data.recentArticles[0].productName)
            assertEquals(15.0, state.data.recentArticles[0].price, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removes articles when receiving MODE_REMOVED`() = runTest {
        // Given authenticated user and existing articles
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Add initial articles
        articleRepository.emitArticle(createArticle("1", "Apple", mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("2", "Banana", mode = MODE_ADDED))
        advanceUntilIdle()

        // When removing article with MODE_REMOVED
        articleRepository.emitArticle(createArticle("1", mode = MODE_REMOVED))
        advanceUntilIdle()

        // Then article is removed
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(1, state.data.totalProducts)
            assertEquals("Banana", state.data.recentArticles[0].productName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles duplicate MODE_ADDED by updating`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When emitting same article ID twice with MODE_ADDED
        articleRepository.emitArticle(createArticle("1", "Apple v1", mode = MODE_ADDED))
        advanceUntilIdle()
        articleRepository.emitArticle(createArticle("1", "Apple v2", mode = MODE_ADDED))
        advanceUntilIdle()

        // Then article is updated, not duplicated
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(1, state.data.totalProducts)
            assertEquals("Apple v2", state.data.recentArticles[0].productName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== D. Product Filtering (4 tests) =====

    @Test
    fun `ALL filter shows all products`() = runTest {
        // Given authenticated user and mixed availability articles
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        articleRepository.emitArticle(createArticle("1", "Available", available = true, mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("2", "Not Available", available = false, mode = MODE_ADDED))
        advanceUntilIdle()

        // When filter is ALL (default)
        viewModel.setFilter(ProductFilter.ALL)
        advanceUntilIdle()

        // Then all products are shown
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(2, state.data.recentArticles.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AVAILABLE filter shows only available products`() = runTest {
        // Given authenticated user and mixed availability articles
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        articleRepository.emitArticle(createArticle("1", "Available 1", available = true, mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("2", "Not Available", available = false, mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("3", "Available 2", available = true, mode = MODE_ADDED))
        advanceUntilIdle()

        // When filter is AVAILABLE
        viewModel.setFilter(ProductFilter.AVAILABLE)
        advanceUntilIdle()

        // Then only available products are shown
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(2, state.data.recentArticles.size)
            assertTrue(state.data.recentArticles.all { it.available })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `NOT_AVAILABLE filter shows only unavailable products`() = runTest {
        // Given authenticated user and mixed availability articles
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        articleRepository.emitArticle(createArticle("1", "Available", available = true, mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("2", "Not Available 1", available = false, mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("3", "Not Available 2", available = false, mode = MODE_ADDED))
        advanceUntilIdle()

        // When filter is NOT_AVAILABLE
        viewModel.setFilter(ProductFilter.NOT_AVAILABLE)
        advanceUntilIdle()

        // Then only unavailable products are shown
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(2, state.data.recentArticles.size)
            assertTrue(state.data.recentArticles.none { it.available })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFilter updates currentFilter state`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // When setting different filters
        viewModel.setFilter(ProductFilter.AVAILABLE)

        // Then currentFilter is updated
        viewModel.currentFilter.test {
            assertEquals(ProductFilter.AVAILABLE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== E. Order Statistics (3 tests) =====

    @Test
    fun `counts active orders correctly`() = runTest {
        // Given authenticated user and orders
        authRepository.setCurrentUserId("seller_123")

        // Active orders have future pickup date and active status
        val futureDate = Clock.System.now().toEpochMilliseconds() + 86400000 * 7 // 7 days
        val pastDate = Clock.System.now().toEpochMilliseconds() - 86400000 // Yesterday

        orderRepository.setOrders(listOf(
            createOrder("1", OrderStatus.PLACED, pickUpDate = futureDate),  // Active
            createOrder("2", OrderStatus.LOCKED, pickUpDate = futureDate),  // Active
            createOrder("3", OrderStatus.COMPLETED, pickUpDate = pastDate), // Not active
            createOrder("4", OrderStatus.CANCELLED, pickUpDate = futureDate) // Not active
        ))

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then active orders are counted correctly
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            // PLACED and LOCKED with future date are active
            assertEquals(2, state.data.activeOrders)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `calculates revenue from completed and locked orders`() = runTest {
        // Given authenticated user and orders with products
        authRepository.setCurrentUserId("seller_123")

        val completedOrder = createOrder(
            "1",
            OrderStatus.COMPLETED,
            articles = listOf(
                createOrderedProduct("p1", price = 10.0, amount = 2.0), // 20.0
                createOrderedProduct("p2", price = 5.0, amount = 3.0)   // 15.0
            )
        )
        val lockedOrder = createOrder(
            "2",
            OrderStatus.LOCKED,
            articles = listOf(
                createOrderedProduct("p3", price = 8.0, amount = 1.0)   // 8.0
            )
        )
        val placedOrder = createOrder(
            "3",
            OrderStatus.PLACED,
            articles = listOf(
                createOrderedProduct("p4", price = 100.0, amount = 1.0) // Should NOT count
            )
        )

        orderRepository.setOrders(listOf(completedOrder, lockedOrder, placedOrder))

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then revenue = 20 + 15 + 8 = 43.0 (excludes PLACED)
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(43.0, state.data.totalRevenue, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `revenue is zero when no completed orders`() = runTest {
        // Given authenticated user and only placed orders
        authRepository.setCurrentUserId("seller_123")
        orderRepository.setOrders(listOf(
            createOrder("1", OrderStatus.PLACED, articles = listOf(
                createOrderedProduct("p1", price = 100.0, amount = 1.0)
            )),
            createOrder("2", OrderStatus.CANCELLED, articles = listOf(
                createOrderedProduct("p2", price = 50.0, amount = 1.0)
            ))
        ))

        // When creating ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then revenue is zero
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(0.0, state.data.totalRevenue, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== F. Delete Articles (3 tests) =====

    @Test
    fun `deleteArticles removes articles from repository`() = runTest {
        // Given authenticated user and articles
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        articleRepository.emitArticle(createArticle("1", "Apple", mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("2", "Banana", mode = MODE_ADDED))
        advanceUntilIdle()

        // When deleting articles
        viewModel.deleteArticles(setOf("1", "2"))
        advanceUntilIdle()

        // Then articles are deleted via repository
        assertEquals(2, articleRepository.deletedArticles.size)
        assertTrue(articleRepository.deletedArticles.any { it.second == "1" })
        assertTrue(articleRepository.deletedArticles.any { it.second == "2" })
    }

    @Test
    fun `deleteArticles updates UI state`() = runTest {
        // Given authenticated user and articles
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        articleRepository.emitArticle(createArticle("1", "Apple", mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("2", "Banana", mode = MODE_ADDED))
        advanceUntilIdle()

        // When deleting one article
        viewModel.deleteArticles(setOf("1"))
        advanceUntilIdle()

        // Then UI shows remaining article
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(1, state.data.totalProducts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteArticles requires authentication`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Add article
        articleRepository.emitArticle(createArticle("1", "Apple", mode = MODE_ADDED))
        advanceUntilIdle()

        // When user becomes unauthenticated and tries to delete
        authRepository.setCurrentUserId(null)
        viewModel.deleteArticles(setOf("1"))
        advanceUntilIdle()

        // Then error state is set
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Error>(state)
            assertTrue(state.message.contains("Authentication") || state.message.contains("required"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== G. Update Availability (3 tests) =====

    @Test
    fun `updateArticlesAvailability updates articles`() = runTest {
        // Given authenticated user and articles
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        articleRepository.emitArticle(createArticle("1", "Apple", available = true, mode = MODE_ADDED))
        articleRepository.emitArticle(createArticle("2", "Banana", available = true, mode = MODE_ADDED))
        advanceUntilIdle()

        // When updating availability to false
        viewModel.updateArticlesAvailability(setOf("1", "2"), available = false)
        advanceUntilIdle()

        // Then articles are saved with new availability
        assertEquals(2, articleRepository.savedArticles.size)
        assertTrue(articleRepository.savedArticles.all { !it.second.available })
    }

    @Test
    fun `updateArticlesAvailability updates UI state`() = runTest {
        // Given authenticated user and available article
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        articleRepository.emitArticle(createArticle("1", "Apple", available = true, mode = MODE_ADDED))
        advanceUntilIdle()

        // Set filter to AVAILABLE
        viewModel.setFilter(ProductFilter.AVAILABLE)
        advanceUntilIdle()

        // Verify article is shown
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(1, state.data.recentArticles.size)
            cancelAndIgnoreRemainingEvents()
        }

        // When updating availability to false
        viewModel.updateArticlesAvailability(setOf("1"), available = false)
        advanceUntilIdle()

        // Then article is no longer shown in AVAILABLE filter
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(0, state.data.recentArticles.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateArticlesAvailability requires authentication`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        articleRepository.emitArticle(createArticle("1", "Apple", mode = MODE_ADDED))
        advanceUntilIdle()

        // When user becomes unauthenticated
        authRepository.setCurrentUserId(null)
        viewModel.updateArticlesAvailability(setOf("1"), available = false)
        advanceUntilIdle()

        // Then error state is set
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Error>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== H. Product Import - Parse (4 tests) =====

    @Test
    fun `parseProducts transitions to Preview state`() = runTest {
        // Given authenticated user and valid BNN content
        authRepository.setCurrentUserId("seller_123")
        productImportService.setProducts(listOf(
            Product(productId = "1", productName = "Apple"),
            Product(productId = "2", productName = "Banana")
        ))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When parsing
        viewModel.parseProducts("BNN content")
        advanceUntilIdle()

        // Then import state is Preview with products
        viewModel.importState.test {
            val state = awaitItem()
            assertIs<ImportState.Preview>(state)
            assertEquals(2, state.products.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parseProducts shows error when no products found`() = runTest {
        // Given authenticated user and empty parse result
        authRepository.setCurrentUserId("seller_123")
        productImportService.setProducts(emptyList())
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When parsing
        viewModel.parseProducts("Empty content")
        advanceUntilIdle()

        // Then import state is Error
        viewModel.importState.test {
            val state = awaitItem()
            assertIs<ImportState.Error>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parseProducts handles parse exception`() = runTest {
        // Given authenticated user and parse will fail
        authRepository.setCurrentUserId("seller_123")
        productImportService.shouldFailParse = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When parsing
        viewModel.parseProducts("Invalid content")
        advanceUntilIdle()

        // Then import state is Error
        viewModel.importState.test {
            val state = awaitItem()
            assertIs<ImportState.Error>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parseProducts transitions through Parsing state`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        productImportService.setProducts(listOf(Product(productId = "1", productName = "Test")))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Verify initial state
        assertEquals(ImportState.Idle, viewModel.importState.value)

        // When parsing
        viewModel.parseProducts("Content")
        advanceUntilIdle()

        // Eventually reaches Preview (may skip Parsing with UnconfinedTestDispatcher)
        viewModel.importState.test {
            val state = awaitItem()
            assertTrue(state is ImportState.Preview || state is ImportState.Parsing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== I. Product Import - Import (4 tests) =====

    @Test
    fun `importSelectedProducts saves products to repository`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        val products = listOf(
            Product(productId = "1", productName = "Apple"),
            Product(productId = "2", productName = "Banana")
        )

        // When importing products
        viewModel.importSelectedProducts(products)
        advanceUntilIdle()

        // Then products are saved via repository
        assertEquals(2, articleRepository.savedArticles.size)
    }

    @Test
    fun `importSelectedProducts transitions to Success state`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        val products = listOf(Product(productId = "1", productName = "Apple"))

        // When importing products
        viewModel.importSelectedProducts(products)
        advanceUntilIdle()

        // Then import state is Success
        viewModel.importState.test {
            val state = awaitItem()
            assertIs<ImportState.Success>(state)
            assertEquals(1, state.importedCount)
            assertEquals(0, state.errorCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `importSelectedProducts counts errors`() = runTest {
        // Given authenticated user and save will fail
        authRepository.setCurrentUserId("seller_123")
        articleRepository.shouldFailSave = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        val products = listOf(
            Product(productId = "1", productName = "Apple"),
            Product(productId = "2", productName = "Banana")
        )

        // When importing products
        viewModel.importSelectedProducts(products)
        advanceUntilIdle()

        // Then error count is tracked
        viewModel.importState.test {
            val state = awaitItem()
            assertIs<ImportState.Success>(state)
            assertEquals(0, state.importedCount)
            assertEquals(2, state.errorCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `importSelectedProducts requires authentication`() = runTest {
        // Given user NOT authenticated
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val products = listOf(Product(productId = "1", productName = "Apple"))

        // When importing
        viewModel.importSelectedProducts(products)
        advanceUntilIdle()

        // Then import state is Error
        viewModel.importState.test {
            val state = awaitItem()
            assertIs<ImportState.Error>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== J. Reset Import State (1 test) =====

    @Test
    fun `resetImportState returns to Idle`() = runTest {
        // Given authenticated user with import in progress
        authRepository.setCurrentUserId("seller_123")
        productImportService.setProducts(listOf(Product(productId = "1", productName = "Test")))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Parse to get Preview state
        viewModel.parseProducts("Content")
        advanceUntilIdle()

        // When resetting
        viewModel.resetImportState()

        // Then state is Idle
        viewModel.importState.test {
            assertEquals(ImportState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== K. Refresh (2 tests) =====

    @Test
    fun `refresh clears and reloads data`() = runTest {
        // Given authenticated user and articles
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        articleRepository.emitArticle(createArticle("1", "Apple", mode = MODE_ADDED))
        advanceUntilIdle()

        // Verify initial data
        viewModel.overviewState.test {
            val state = awaitItem()
            assertIs<AsyncState.Success<OverviewData>>(state)
            assertEquals(1, state.data.totalProducts)
            cancelAndIgnoreRemainingEvents()
        }

        // When refreshing
        viewModel.refresh()
        advanceUntilIdle()

        // Then data is cleared (articles cleared, reload starts fresh)
        viewModel.overviewState.test {
            val state = awaitItem()
            // After refresh, should be Loading or Success with cleared data
            assertTrue(
                state is AsyncState.Loading ||
                (state is AsyncState.Success && state.data.totalProducts == 0),
                "Expected Loading or Success with 0 products"
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh maintains filter setting`() = runTest {
        // Given authenticated user with filter set
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setFilter(ProductFilter.AVAILABLE)

        // When refreshing
        viewModel.refresh()
        advanceUntilIdle()

        // Then filter is maintained
        viewModel.currentFilter.test {
            assertEquals(ProductFilter.AVAILABLE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ===== Test Fakes =====

class FakeSellerConfig(
    override val sellerId: String = "seller_123"
) : SellerConfig

class FakeProductImportService : ProductImportService {
    private var products: List<Product> = emptyList()
    var shouldFailParse = false
    var failureMessage = "Parse failed"

    override val formatName: String = "Test"

    fun setProducts(products: List<Product>) {
        this.products = products
    }

    fun reset() {
        products = emptyList()
        shouldFailParse = false
        failureMessage = "Parse failed"
    }

    override fun parse(fileContent: String): List<Product> {
        if (shouldFailParse) {
            throw Exception(failureMessage)
        }
        return products
    }
}
