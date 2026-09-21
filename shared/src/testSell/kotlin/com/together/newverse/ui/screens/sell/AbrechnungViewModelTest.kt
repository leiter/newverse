package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.TaxRate
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeSellerArticleRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.ui.state.core.AsyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Purchase cost and VAT in the Abrechnung come from the seller's catalog: the tax
 * rate from the public half, the purchase price from the seller-only half.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbrechnungViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeSellerArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeSellerArticleRepository()
        orderRepository = FakeOrderRepository()
        authRepository = FakeAuthRepository()
        authRepository.setCurrentUserId("seller_123")
    }

    @AfterTest
    fun tearDown() {
        articleRepository.reset()
        orderRepository.reset()
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = AbrechnungViewModel(
        sellerArticleRepository = articleRepository,
        orderRepository = orderRepository,
        authRepository = authRepository
    )

    private fun catalogArticle(
        id: String,
        taxRate: TaxRate = TaxRate.REDUCED,
        acquirePrice: Double? = null
    ) = SellerArticle(
        article = Article(id = id, productId = "bnn_$id", productName = "Article $id", taxRate = taxRate.rate),
        sellerData = acquirePrice?.let { SellerArticleData(acquirePrice = it) }
    )

    /** One completed order from yesterday, inside the default 30-day period. */
    private fun completedOrder(vararg items: OrderedProduct) = Order(
        id = "order_1",
        sellerId = "seller_123",
        status = OrderStatus.COMPLETED,
        pickUpDate = Clock.System.now().toEpochMilliseconds() - 86_400_000,
        articles = items.toList()
    )

    private fun item(articleId: String, price: Double, amount: Double) = OrderedProduct(
        id = articleId,
        productId = "bnn_$articleId",
        productName = "Article $articleId",
        price = price,
        amountCount = amount
    )

    private fun periodFinancials(viewModel: AbrechnungViewModel): OrderFinancials {
        val state = viewModel.periodSummary.value
        assertIs<AsyncState.Success<PeriodSummary>>(state)
        return state.data.financials
    }

    @Test
    fun `purchase cost comes from the seller-only half`() = runTest {
        articleRepository.setArticles(listOf(catalogArticle("a", acquirePrice = 1.5)))
        orderRepository.setOrders(listOf(completedOrder(item("a", price = 3.21, amount = 2.0))))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val financials = periodFinancials(viewModel)
        assertEquals(3.0, financials.acquireCost, 0.001)
        assertFalse(financials.isGrossProfitIncomplete)
    }

    @Test
    fun `unknown purchase price is counted as unknown, not as free`() = runTest {
        articleRepository.setArticles(listOf(
            catalogArticle("a", acquirePrice = 1.5),
            catalogArticle("b", acquirePrice = null)
        ))
        orderRepository.setOrders(listOf(completedOrder(
            item("a", price = 3.21, amount = 2.0),
            item("b", price = 5.0, amount = 1.0)
        )))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val financials = periodFinancials(viewModel)
        assertEquals(3.0, financials.acquireCost, 0.001)
        assertEquals(1, financials.itemsWithoutAcquirePrice)
        assertTrue(financials.isGrossProfitIncomplete)
    }

    @Test
    fun `a purchase price of zero counts as unknown`() = runTest {
        articleRepository.setArticles(listOf(catalogArticle("a", acquirePrice = 0.0)))
        orderRepository.setOrders(listOf(completedOrder(item("a", price = 3.21, amount = 1.0))))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, periodFinancials(viewModel).itemsWithoutAcquirePrice)
    }

    @Test
    fun `VAT uses the article's stored tax rate`() = runTest {
        articleRepository.setArticles(listOf(catalogArticle("a", taxRate = TaxRate.STANDARD)))
        orderRepository.setOrders(listOf(completedOrder(item("a", price = 11.90, amount = 1.0))))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val financials = periodFinancials(viewModel)
        assertEquals(1.90, financials.vatAmount19, 0.001)
        assertEquals(0.0, financials.vatAmount7, 0.001)
    }

    @Test
    fun `articles are matched by id before BNN number`() = runTest {
        // Two catalog entries share a BNN number; the order line names the second by id.
        articleRepository.setArticles(listOf(
            catalogArticle("a", acquirePrice = 1.0),
            SellerArticle(
                article = Article(id = "b", productId = "bnn_a", taxRate = TaxRate.REDUCED.rate),
                sellerData = SellerArticleData(acquirePrice = 2.0)
            )
        ))
        orderRepository.setOrders(listOf(completedOrder(
            OrderedProduct(id = "b", productId = "bnn_a", price = 5.0, amountCount = 1.0)
        )))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2.0, periodFinancials(viewModel).acquireCost, 0.001)
    }

    @Test
    fun `articles without a BNN number are not matched to each other`() = runTest {
        // Manually created articles have no productId; a blank key must not match.
        articleRepository.setArticles(listOf(
            SellerArticle(
                article = Article(id = "manual", productId = "", taxRate = TaxRate.REDUCED.rate),
                sellerData = SellerArticleData(acquirePrice = 9.0)
            )
        ))
        orderRepository.setOrders(listOf(completedOrder(
            OrderedProduct(id = "deleted", productId = "", price = 5.0, amountCount = 1.0)
        )))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val financials = periodFinancials(viewModel)
        assertEquals(0.0, financials.acquireCost, 0.001)
        assertEquals(1, financials.itemsWithoutAcquirePrice)
    }
}
