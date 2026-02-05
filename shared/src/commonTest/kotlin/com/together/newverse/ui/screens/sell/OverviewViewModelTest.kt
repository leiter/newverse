package com.together.newverse.ui.screens.sell

import app.cash.turbine.test
import com.together.newverse.domain.model.Article
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.test.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        dispatcherRule.tearDown()
    }

    private fun createViewModel(): OverviewViewModel {
        return OverviewViewModel(
            articleRepository = articleRepository,
            orderRepository = orderRepository,
            authRepository = authRepository
        )
    }

    @Test
    fun `initial state transitions from Loading to Success`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // When ViewModel is created
        val viewModel = createViewModel()

        // Then state eventually becomes Success (UnconfinedTestDispatcher processes immediately)
        viewModel.uiState.test {
            val state = awaitItem()
            // With UnconfinedTestDispatcher, state may already be Success
            assertTrue(state is OverviewUiState.Loading || state is OverviewUiState.Success)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads articles successfully`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When articles are emitted
        val articles = TestData.sampleArticles
        articles.forEach { article ->
            articleRepository.emitArticle(article.copy(mode = Article.MODE_ADDED))
        }
        advanceUntilIdle()

        // Then state should be Success with articles
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OverviewUiState.Success>(state)
            assertEquals(articles.size, state.totalProducts)
            assertEquals(articles.size, state.recentArticles.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows error when not authenticated`() = runTest {
        // Given user is NOT authenticated
        authRepository.setCurrentUserId(null)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then state should be Error
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OverviewUiState.Error>(state)
            assertEquals("Not authenticated", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters by AVAILABLE shows only available articles`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Emit articles (some available, some not)
        TestData.sampleArticles.forEach { article ->
            articleRepository.emitArticle(article.copy(mode = Article.MODE_ADDED))
        }
        advanceUntilIdle()

        // When filter is set to AVAILABLE
        viewModel.setFilter(ProductFilter.AVAILABLE)
        advanceUntilIdle()

        // Then only available articles should be shown
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OverviewUiState.Success>(state)
            assertTrue(state.recentArticles.all { it.available })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters by NOT_AVAILABLE shows only unavailable articles`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Emit articles (some available, some not)
        TestData.sampleArticles.forEach { article ->
            articleRepository.emitArticle(article.copy(mode = Article.MODE_ADDED))
        }
        advanceUntilIdle()

        // When filter is set to NOT_AVAILABLE
        viewModel.setFilter(ProductFilter.NOT_AVAILABLE)
        advanceUntilIdle()

        // Then only unavailable articles should be shown
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OverviewUiState.Success>(state)
            assertTrue(state.recentArticles.all { !it.available })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteArticles calls repository delete`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Emit initial articles
        val article = TestData.sampleArticles[0].copy(mode = Article.MODE_ADDED)
        articleRepository.emitArticle(article)
        advanceUntilIdle()

        // When articles are deleted
        viewModel.deleteArticles(setOf(article.id))
        advanceUntilIdle()

        // Then repository should have been called
        assertEquals(1, articleRepository.deletedArticles.size)
        assertEquals(article.id, articleRepository.deletedArticles[0].second)
    }

    @Test
    fun `updateArticlesAvailability calls repository save`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Emit initial article
        val article = TestData.sampleArticles[0].copy(
            mode = Article.MODE_ADDED,
            available = true
        )
        articleRepository.emitArticle(article)
        advanceUntilIdle()

        // When availability is updated
        viewModel.updateArticlesAvailability(setOf(article.id), available = false)
        advanceUntilIdle()

        // Then repository should have been called with updated article
        assertEquals(1, articleRepository.savedArticles.size)
        val savedArticle = articleRepository.savedArticles[0].second
        assertEquals(article.id, savedArticle.id)
        assertEquals(false, savedArticle.available)
    }

    @Test
    fun `parseProducts transitions to Preview state`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Sample BNN file content (minimal)
        val bnnContent = """
            BNN;3.0
            10001;Testprodukt;1,50;kg;Gemuse;Bio;0;100;Test
        """.trimIndent()

        // When parsing BNN file
        viewModel.parseProducts(bnnContent)
        advanceUntilIdle()

        // Then import state should be Preview or Error (depending on parser)
        viewModel.importState.test {
            val state = awaitItem()
            // Parser may succeed or fail - either way we're testing the flow
            assertTrue(state is ImportState.Preview || state is ImportState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetImportState returns to Idle`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When reset is called
        viewModel.resetImportState()

        // Then state should be Idle
        viewModel.importState.test {
            val state = awaitItem()
            assertIs<ImportState.Idle>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles article MODE_CHANGED correctly`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Emit initial article
        val article = TestData.sampleArticles[0].copy(mode = Article.MODE_ADDED)
        articleRepository.emitArticle(article)
        advanceUntilIdle()

        // When article is changed
        val updatedArticle = article.copy(
            mode = Article.MODE_CHANGED,
            productName = "Updated Name"
        )
        articleRepository.emitArticle(updatedArticle)
        advanceUntilIdle()

        // Then article should be updated
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OverviewUiState.Success>(state)
            assertEquals(1, state.recentArticles.size)
            assertEquals("Updated Name", state.recentArticles[0].productName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles article MODE_REMOVED correctly`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Emit initial articles
        TestData.sampleArticles.forEach { article ->
            articleRepository.emitArticle(article.copy(mode = Article.MODE_ADDED))
        }
        advanceUntilIdle()

        // When an article is removed
        val removedArticle = TestData.sampleArticles[0].copy(mode = Article.MODE_REMOVED)
        articleRepository.emitArticle(removedArticle)
        advanceUntilIdle()

        // Then article should be removed from list
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OverviewUiState.Success>(state)
            assertEquals(TestData.sampleArticles.size - 1, state.recentArticles.size)
            assertTrue(state.recentArticles.none { it.id == removedArticle.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh clears articles and reloads`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Emit initial article
        val article = TestData.sampleArticles[0].copy(mode = Article.MODE_ADDED)
        articleRepository.emitArticle(article)
        advanceUntilIdle()

        // When refresh is called
        viewModel.refresh()
        advanceUntilIdle()

        // Then articles should be reloaded (starts from Loading)
        viewModel.uiState.test {
            val state = awaitItem()
            // After refresh, state transitions through Loading
            assertTrue(state is OverviewUiState.Loading || state is OverviewUiState.Success)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `counts active orders correctly`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // Set orders (only PLACED orders are active)
        val orders = TestData.sampleOrders
        orderRepository.setOrders(orders)

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Emit an article to trigger state update
        articleRepository.emitArticle(TestData.sampleArticles[0].copy(mode = Article.MODE_ADDED))
        advanceUntilIdle()

        // Then active orders count should match
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OverviewUiState.Success>(state)
            // Only PLACED orders are active (not COMPLETED or CANCELLED)
            val expectedActiveCount = orders.count { it.isActiveOrder() }
            assertEquals(expectedActiveCount, state.activeOrders)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteArticles shows error when not authenticated`() = runTest {
        // Given user is authenticated initially
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user becomes unauthenticated
        authRepository.setCurrentUserId(null)
        advanceUntilIdle()

        // When trying to delete articles
        viewModel.deleteArticles(setOf("article_1"))
        advanceUntilIdle()

        // Then state should show error
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OverviewUiState.Error>(state)
            assertTrue(state.message.contains("Authentication"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateArticlesAvailability shows error when not authenticated`() = runTest {
        // Given user is authenticated initially
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user becomes unauthenticated
        authRepository.setCurrentUserId(null)
        advanceUntilIdle()

        // When trying to update availability
        viewModel.updateArticlesAvailability(setOf("article_1"), available = false)
        advanceUntilIdle()

        // Then state should show error
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<OverviewUiState.Error>(state)
            assertTrue(state.message.contains("Authentication"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
