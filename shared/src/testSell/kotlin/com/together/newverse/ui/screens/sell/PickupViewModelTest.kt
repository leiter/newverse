package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.TaxRate
import com.together.newverse.domain.model.toSale
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeSaleRepository
import com.together.newverse.test.FakeSellerArticleRepository
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PickupViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var saleRepository: FakeSaleRepository
    private lateinit var articleRepository: FakeSellerArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var authRepository: FakeAuthRepository
    private var clock = 10_000L

    private val order = Order(
        id = "order_1",
        sellerId = "seller_123",
        pickUpDate = 5_000L,
        status = OrderStatus.LOCKED,
        articles = listOf(
            OrderedProduct(id = "apple", productId = "112108", productName = "Apfel Topaz",
                unit = "kg", price = 3.19, amountCount = 1.5),
            OrderedProduct(id = "sweet", productId = "122654", productName = "Süßkartoffel",
                unit = "kg", price = 4.62, amountCount = 1.0)
        )
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        saleRepository = FakeSaleRepository()
        articleRepository = FakeSellerArticleRepository()
        orderRepository = FakeOrderRepository()
        authRepository = FakeAuthRepository()
        authRepository.setCurrentUserId("seller_123")
        orderRepository.setOrders(listOf(order))
        articleRepository.setArticles(listOf(
            SellerArticle(Article(id = "apple", taxRate = TaxRate.REDUCED.rate), SellerArticleData(acquirePrice = 1.96)),
            SellerArticle(Article(id = "sweet", taxRate = TaxRate.STANDARD.rate), SellerArticleData(acquirePrice = 2.68))
        ))
    }

    @AfterTest
    fun tearDown() {
        saleRepository.reset()
        articleRepository.reset()
        orderRepository.reset()
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = PickupViewModel(
        saleRepository = saleRepository,
        sellerArticleRepository = articleRepository,
        orderRepository = orderRepository,
        authRepository = authRepository,
        now = { clock }
    )

    private fun lastStatusUpdate() = orderRepository.statusUpdates.lastOrNull()?.status

    // --- state ---

    @Test
    fun `an unbooked order is open`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()

        assertEquals(PickupStatus.Open, viewModel.state.value.status)
    }

    @Test
    fun `drafts, cancelled and demo orders cannot be booked`() = runTest {
        val viewModel = createViewModel()
        for (o in listOf(order.copy(status = OrderStatus.DRAFT), order.copy(status = OrderStatus.CANCELLED),
            order.copy(isDemoOrder = true))) {
            viewModel.load(o)
            advanceUntilIdle()
            assertEquals(PickupStatus.Unavailable, viewModel.state.value.status, "$o")
        }
    }

    @Test
    fun `booked state comes from the sales, not the order status`() = runTest {
        // The buyer app may have set COMPLETED on its own; that is not a booking.
        val viewModel = createViewModel()
        viewModel.load(order.copy(status = OrderStatus.COMPLETED))
        advanceUntilIdle()

        assertEquals(PickupStatus.Open, viewModel.state.value.status)
    }

    @Test
    fun `unreadable sales offer no confirmation`() = runTest {
        saleRepository.shouldFailLookup = true
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()

        assertEquals(PickupStatus.Unknown, viewModel.state.value.status)
        viewModel.startConfirm()
        assertTrue(!viewModel.state.value.isEditing)
    }

    // --- confirming ---

    @Test
    fun `the editor starts with the ordered quantities`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()

        viewModel.startConfirm()

        assertEquals(listOf("1.5", "1"), viewModel.state.value.lines.map { it.input })
    }

    @Test
    fun `confirming books what was handed over and completes the order`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()
        viewModel.startConfirm()
        viewModel.setQuantity(0, "1,62")   // weighed, decimal comma

        viewModel.confirm()
        advanceUntilIdle()

        val sale = saleRepository.sales.single()
        assertEquals("order_1", sale.orderId)
        assertEquals(10_000L, sale.confirmedAt)
        assertEquals(listOf(1.62, 1.0), sale.lines.map { it.quantity })
        assertEquals(listOf(0.07, 0.19), sale.lines.map { it.taxRate })
        assertEquals(listOf(196L, 268L), sale.lines.map { it.acquirePriceCents })
        assertIs<PickupStatus.Booked>(viewModel.state.value.status)
        assertTrue(!viewModel.state.value.isEditing)
        assertEquals(OrderStatus.COMPLETED, lastStatusUpdate())
        assertEquals("19700101", orderRepository.statusUpdates.last().date)
    }

    @Test
    fun `a missing item is left out of the sale`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()
        viewModel.startConfirm()
        viewModel.setMissing(1)

        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(listOf("apple"), saleRepository.sales.single().lines.map { it.articleId })
    }

    @Test
    fun `nothing handed over books nothing and points to not picked up`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()
        viewModel.startConfirm()
        viewModel.setMissing(0)
        viewModel.setMissing(1)

        viewModel.confirm()
        advanceUntilIdle()

        assertTrue(saleRepository.sales.isEmpty())
        assertEquals(PickupMessage.NOTHING_HANDED_OVER, viewModel.state.value.message)
    }

    @Test
    fun `an invalid quantity books nothing`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()
        viewModel.startConfirm()
        viewModel.setQuantity(0, "abc")

        viewModel.confirm()
        advanceUntilIdle()

        assertTrue(saleRepository.sales.isEmpty())
        assertEquals(PickupMessage.INVALID_QUANTITY, viewModel.state.value.message)
    }

    @Test
    fun `an order booked meanwhile on another device is not booked twice`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()
        viewModel.startConfirm()
        // Another device books it while the editor is open
        saleRepository.setSales(listOf(
            order.toSaleOf(1.5, 1.0).copy(id = "other_device")
        ))

        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(listOf("other_device"), saleRepository.sales.map { it.id })
        assertEquals(PickupMessage.ALREADY_BOOKED, viewModel.state.value.message)
        assertIs<PickupStatus.Booked>(viewModel.state.value.status)
    }

    @Test
    fun `a failed booking keeps the order open`() = runTest {
        saleRepository.shouldFailRecord = true
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()
        viewModel.startConfirm()

        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(PickupStatus.Open, viewModel.state.value.status)
        assertEquals(PickupMessage.SAVE_FAILED, viewModel.state.value.message)
        assertNull(lastStatusUpdate())
    }

    // --- cancelling ---

    @Test
    fun `cancelling books a reversal and reopens the order`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()
        viewModel.startConfirm()
        viewModel.confirm()
        advanceUntilIdle()
        clock = 20_000L

        viewModel.cancelBooking()
        advanceUntilIdle()

        val (original, storno) = saleRepository.sales
        assertEquals(original.id, storno.reverses)
        assertEquals(-original.grossCents, storno.grossCents)
        assertEquals(20_000L, storno.confirmedAt)
        assertEquals(PickupStatus.Open, viewModel.state.value.status)
        assertEquals(OrderStatus.LOCKED, lastStatusUpdate())
    }

    @Test
    fun `after a cancellation the order can be booked again`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()
        viewModel.startConfirm(); viewModel.confirm(); advanceUntilIdle()
        viewModel.cancelBooking(); advanceUntilIdle()

        viewModel.startConfirm()
        viewModel.setQuantity(0, "2")
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(3, saleRepository.sales.size)
        val booked = assertIs<PickupStatus.Booked>(viewModel.state.value.status).sale
        assertEquals(2.0, booked.lines.first().quantity)
        assertEquals(booked, PickupViewModel.activeSale(saleRepository.sales))
    }

    @Test
    fun `a reloaded order shows its latest booking`() = runTest {
        val first = order.toSaleOf(1.5, 1.0).copy(id = "s1", confirmedAt = 1_000L)
        val storno = first.reversal(confirmedAt = 2_000L).copy(id = "s2")
        val second = order.toSaleOf(2.0, 1.0).copy(id = "s3", confirmedAt = 3_000L)
        saleRepository.setSales(listOf(first, storno, second))

        val viewModel = createViewModel()
        viewModel.load(order.copy(status = OrderStatus.COMPLETED))
        advanceUntilIdle()

        assertEquals("s3", assertIs<PickupStatus.Booked>(viewModel.state.value.status).sale.id)
    }

    @Test
    fun `a fully cancelled order is open again`() {
        val first = order.toSaleOf(1.5, 1.0).copy(id = "s1", confirmedAt = 1_000L)
        val storno = first.reversal(confirmedAt = 2_000L).copy(id = "s2")

        assertNull(PickupViewModel.activeSale(listOf(first, storno)))
    }

    // --- not picked up ---

    @Test
    fun `not picked up books nothing and marks the order`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()

        viewModel.markNotPickedUp()
        advanceUntilIdle()

        assertTrue(saleRepository.sales.isEmpty())
        assertEquals(OrderStatus.NOT_PICKED_UP, lastStatusUpdate())
        assertEquals(PickupStatus.NotPickedUp, viewModel.state.value.status)
    }

    @Test
    fun `not picked up can be undone`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order.copy(status = OrderStatus.NOT_PICKED_UP))
        advanceUntilIdle()
        assertEquals(PickupStatus.NotPickedUp, viewModel.state.value.status)

        viewModel.undoNotPickedUp()
        advanceUntilIdle()

        assertEquals(OrderStatus.LOCKED, lastStatusUpdate())
        assertEquals(PickupStatus.Open, viewModel.state.value.status)
    }

    @Test
    fun `a booked order cannot be marked not picked up`() = runTest {
        val viewModel = createViewModel()
        viewModel.load(order)
        advanceUntilIdle()
        viewModel.startConfirm(); viewModel.confirm(); advanceUntilIdle()

        viewModel.markNotPickedUp()
        advanceUntilIdle()

        assertEquals(OrderStatus.COMPLETED, lastStatusUpdate())
        assertIs<PickupStatus.Booked>(viewModel.state.value.status)
    }

    private fun Order.toSaleOf(vararg quantities: Double) =
        toSale(quantities.toList(), catalog = emptyMap(), confirmedAt = 1_000L)
}
