package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.StockMovementKind
import com.together.newverse.domain.model.TaxRate
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeSaleRepository
import com.together.newverse.test.FakeSellerArticleRepository
import com.together.newverse.test.FakeStockRepository
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Confirming a pickup takes the goods out of storage, and cancelling the booking
 * puts them back — but only for articles the seller watches, and never at the cost
 * of the sale itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PickupStockBookingTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var saleRepository: FakeSaleRepository
    private lateinit var articleRepository: FakeSellerArticleRepository
    private lateinit var stockRepository: FakeStockRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var authRepository: FakeAuthRepository

    private val order = Order(
        id = "order_1",
        sellerId = "seller_123",
        pickUpDate = 5_000L,
        status = OrderStatus.LOCKED,
        articles = listOf(
            OrderedProduct(productId = "apple", productName = "Apfel", unit = "kg", price = 3.19, amountCount = 1.5),
            OrderedProduct(productId = "nuts", productName = "Nüsse", unit = "kg", price = 9.0, amountCount = 2.0)
        )
    )

    /** apple is watched, nuts is not. */
    private fun catalog(appleReorder: Double = 5.0) = listOf(
        SellerArticle(
            Article(id = "apple", productName = "Apfel", unit = "kg", taxRate = TaxRate.REDUCED.rate),
            SellerArticleData(acquirePrice = 1.96, reorderLevel = appleReorder)
        ),
        SellerArticle(
            Article(id = "nuts", productName = "Nüsse", unit = "kg", taxRate = TaxRate.REDUCED.rate),
            SellerArticleData(acquirePrice = 4.0, reorderLevel = 0.0)
        )
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        saleRepository = FakeSaleRepository()
        articleRepository = FakeSellerArticleRepository()
        stockRepository = FakeStockRepository()
        orderRepository = FakeOrderRepository()
        authRepository = FakeAuthRepository()
        authRepository.setCurrentUserId("seller_123")
        orderRepository.setOrders(listOf(order))
        articleRepository.setArticles(catalog())
    }

    @AfterTest
    fun tearDown() {
        saleRepository.reset()
        articleRepository.reset()
        stockRepository.reset()
        orderRepository.reset()
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun viewModel() = PickupViewModel(
        saleRepository = saleRepository,
        sellerArticleRepository = articleRepository,
        stockRepository = stockRepository,
        orderRepository = orderRepository,
        authRepository = authRepository,
        now = { 10_000L }
    )

    private fun TestScope.confirmPickup(): PickupViewModel {
        val vm = viewModel()
        vm.load(order)
        advanceUntilIdle()
        vm.startConfirm()
        vm.confirm()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `confirming takes the watched article out of storage`() = runTest {
        confirmPickup()

        val movements = stockRepository.movements
        assertEquals(1, movements.size, "only the watched article moves")
        val moved = movements.single()
        assertEquals("apple", moved.articleId)
        assertEquals(-1.5, moved.quantity)
        assertEquals(StockMovementKind.SALE, moved.kind)
        assertEquals("kg", moved.unit)
    }

    @Test
    fun `an unwatched article is left out of the ledger`() = runTest {
        confirmPickup()
        assertTrue(stockRepository.movements.none { it.articleId == "nuts" })
    }

    @Test
    fun `nothing is recorded when no article is watched`() = runTest {
        articleRepository.setArticles(catalog(appleReorder = 0.0))
        confirmPickup()
        assertTrue(stockRepository.movements.isEmpty())
    }

    @Test
    fun `the handed-over quantity is what leaves storage, not the ordered one`() = runTest {
        val vm = viewModel()
        vm.load(order)
        advanceUntilIdle()
        vm.startConfirm()
        vm.setQuantity(0, "1,2")   // weighed less than ordered, comma as typed
        vm.confirm()
        advanceUntilIdle()

        assertEquals(-1.2, stockRepository.movements.single { it.articleId == "apple" }.quantity)
    }

    @Test
    fun `a missing item moves no stock`() = runTest {
        val vm = viewModel()
        vm.load(order)
        advanceUntilIdle()
        vm.startConfirm()
        vm.setMissing(0)
        vm.confirm()
        advanceUntilIdle()

        // The line is dropped from the sale, so nothing left storage.
        assertTrue(stockRepository.movements.none { it.articleId == "apple" })
    }

    @Test
    fun `cancelling the booking puts the goods back`() = runTest {
        val vm = confirmPickup()
        assertEquals(-1.5, stockRepository.movements.single().quantity)

        vm.cancelBooking()
        advanceUntilIdle()

        assertEquals(2, stockRepository.movements.size)
        assertEquals(1.5, stockRepository.movements.last().quantity)
        // The ledger nets to nothing, as it should after a sale and its cancellation.
        assertEquals(0.0, stockRepository.movements.sumOf { it.quantity })
    }

    @Test
    fun `a sale still books when the stock ledger refuses`() = runTest {
        stockRepository.shouldFailRecord = true

        val vm = confirmPickup()

        // The books are what matter; the level is repaired by the next stocktake.
        assertEquals(1, saleRepository.sales.size)
        assertIs<PickupStatus.Booked>(vm.state.value.status)
        assertEquals(null, vm.state.value.message)
        assertTrue(stockRepository.movements.isEmpty())
    }

    @Test
    fun `no stock moves when the sale itself fails`() = runTest {
        saleRepository.shouldFailRecord = true

        val vm = confirmPickup()

        assertEquals(PickupMessage.SAVE_FAILED, vm.state.value.message)
        assertTrue(stockRepository.movements.isEmpty())
    }

    @Test
    fun `the movement is marked as coming from the order, not a weighing`() = runTest {
        confirmPickup()
        // Nothing here was on a scale: the quantity came off the order form.
        assertEquals(
            com.together.newverse.domain.model.QuantitySource.ORDERED,
            stockRepository.movements.single().source
        )
    }
}
