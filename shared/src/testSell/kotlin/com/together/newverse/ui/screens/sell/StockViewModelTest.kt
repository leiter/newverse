package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.MeasuredQuantity
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderOrigin
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.QuantitySource
import com.together.newverse.domain.model.RefillState
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.StockMovement
import com.together.newverse.domain.model.StockMovementKind
import com.together.newverse.domain.scale.ScaleStatus
import com.together.newverse.domain.scale.WeightSource
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeSellerArticleRepository
import com.together.newverse.test.FakeStockRepository
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A scale under the test's control, standing in for a connected one. */
private class TestWeightSource(
    initialStatus: ScaleStatus = ScaleStatus.Unsupported
) : WeightSource {
    private val _status = MutableStateFlow(initialStatus)
    private val _readings = MutableStateFlow<MeasuredQuantity?>(null)

    override val status: StateFlow<ScaleStatus> = _status.asStateFlow()
    override val readings: Flow<MeasuredQuantity> = kotlinx.coroutines.flow.flow {
        _readings.collect { if (it != null) emit(it) }
    }

    fun connect() { _status.value = ScaleStatus.Connected("Test scale") }
    fun report(reading: MeasuredQuantity) { _readings.value = reading }

    override suspend fun tare(): Result<Unit> = Result.success(Unit)
    override suspend fun zero(): Result<Unit> = Result.success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class StockViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeSellerArticleRepository
    private lateinit var stockRepository: FakeStockRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var scale: TestWeightSource

    private val apple = SellerArticle(
        Article(id = "apple", productId = "112108", productName = "Apfel", unit = "kg"),
        SellerArticleData(acquirePrice = 1.96, reorderLevel = 5.0)
    )
    private val nuts = SellerArticle(
        Article(id = "nuts", productName = "Nüsse", unit = "kg"),
        SellerArticleData(acquirePrice = 4.0, reorderLevel = 0.0)
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeSellerArticleRepository()
        stockRepository = FakeStockRepository()
        orderRepository = FakeOrderRepository()
        authRepository = FakeAuthRepository()
        scale = TestWeightSource()
        authRepository.setCurrentUserId("seller_123")
        articleRepository.setArticles(listOf(apple, nuts))
    }

    @AfterTest
    fun tearDown() {
        articleRepository.reset()
        stockRepository.reset()
        orderRepository.reset()
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun viewModel() = StockViewModel(
        sellerArticleRepository = articleRepository,
        stockRepository = stockRepository,
        orderRepository = orderRepository,
        authRepository = authRepository,
        weightSource = scale,
        now = { 50_000L }
    )

    private fun TestScope.loaded(): StockViewModel {
        val vm = viewModel()
        advanceUntilIdle()
        return vm
    }

    // --- the refill list ------------------------------------------------------

    @Test
    fun `an article never counted shows up as needing attention`() = runTest {
        val vm = loaded()

        val needs = vm.state.value.refillNeeds
        assertEquals(listOf("apple"), needs.map { it.articleId })
        assertEquals(RefillState.UNCOUNTED, needs.single().state)
        assertTrue(!needs.single().hasLevel)
    }

    @Test
    fun `an unwatched article never reaches the refill list`() = runTest {
        val vm = loaded()
        assertTrue(vm.state.value.refillNeeds.none { it.articleId == "nuts" })
        // But it is still in the full standing, for looking up.
        assertTrue(vm.state.value.standing.any { it.articleId == "nuts" })
    }

    @Test
    fun `a well-stocked article leaves the refill list`() = runTest {
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(12.0, "kg"), recordedAt = 1_000L)
        ))
        val vm = loaded()

        assertTrue(vm.state.value.refillNeeds.isEmpty())
        assertEquals(12.0, vm.state.value.standing.first { it.articleId == "apple" }.onHand)
    }

    @Test
    fun `nothing watched at all is its own state`() = runTest {
        articleRepository.setArticles(listOf(nuts))
        val vm = loaded()

        assertTrue(!vm.state.value.hasWatchedArticles)
        assertTrue(vm.state.value.refillNeeds.isEmpty())
    }

    @Test
    fun `the standing can be searched but the refill list cannot`() = runTest {
        val vm = loaded()
        vm.setQuery("nüs")

        assertEquals(listOf("nuts"), vm.state.value.matchingStanding.map { it.articleId })
        assertEquals(listOf("apple"), vm.state.value.refillNeeds.map { it.articleId })
    }

    // --- committed demand -----------------------------------------------------

    private fun order(
        id: String,
        articleId: String = "apple",
        amount: Double = 8.0,
        unit: String = "kg",
        status: OrderStatus = OrderStatus.PLACED,
        origin: OrderOrigin = OrderOrigin.PRODUCTION
    ) = Order(
        id = id,
        status = status,
        origin = origin,
        pickUpDate = 100_000L,
        articles = listOf(
            OrderedProduct(productId = articleId, productName = articleId, unit = unit, amountCount = amount)
        )
    )

    @Test
    fun `orders the seller cannot cover put the article on the refill list`() = runTest {
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(3.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(order("o1", amount = 8.0)))

        val need = loaded().state.value.refillNeeds.single()

        assertEquals(RefillState.SHORT, need.state)
        assertEquals(8.0, need.committed)
        // 8 kg sold out of 3 kg on hand, and a 5 kg buffer wanted on top.
        assertEquals(10.0, need.shortfall)
        assertEquals(-5.0, need.remaining)
    }

    @Test
    fun `an order arriving later re-derives the list`() = runTest {
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(12.0, "kg"), recordedAt = 1_000L)
        ))
        val vm = loaded()
        assertTrue(vm.state.value.refillNeeds.isEmpty())

        // Demand rises without the seller touching anything.
        orderRepository.setOrders(listOf(order("o1", amount = 20.0)))
        advanceUntilIdle()

        assertEquals(RefillState.SHORT, vm.state.value.refillNeeds.single().state)
    }

    @Test
    fun `a demo order is not demand`() = runTest {
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(12.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(order("o1", amount = 20.0, origin = OrderOrigin.DEMO)))

        assertTrue(loaded().state.value.refillNeeds.isEmpty())
    }

    @Test
    fun `an unwatched article still warns once it is oversold`() = runTest {
        // nuts has no reorder level, so stock alone would never mention it.
        stockRepository.setMovements(listOf(
            StockMovement.intake("nuts", MeasuredQuantity.typed(1.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(order("o1", articleId = "nuts", amount = 4.0)))

        val needs = loaded().state.value.refillNeeds

        assertEquals(listOf("nuts", "apple"), needs.map { it.articleId })
        assertEquals(RefillState.SHORT, needs.first().state)
    }

    @Test
    fun `orders in another unit are flagged instead of counted`() = runTest {
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(12.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(order("o1", amount = 20.0, unit = "Stück")))

        val need = loaded().state.value.refillNeeds.single { it.articleId == "apple" }

        assertTrue(need.unitMismatch)
        assertEquals(0.0, need.committed)
    }

    // --- booking --------------------------------------------------------------

    @Test
    fun `an intake is recorded and marked as typed`() = runTest {
        val vm = loaded()
        vm.startBooking("apple", StockMovementKind.INTAKE)
        vm.setQuantity("15")
        vm.book()
        advanceUntilIdle()

        val moved = stockRepository.movements.single()
        assertEquals(StockMovementKind.INTAKE, moved.kind)
        assertEquals(15.0, moved.quantity)
        assertEquals("kg", moved.unit)
        assertEquals("112108", moved.productId)
        assertEquals(QuantitySource.MANUAL, moved.source)
        assertNull(vm.state.value.editor)
    }

    @Test
    fun `a comma is accepted as the decimal separator`() = runTest {
        val vm = loaded()
        vm.startBooking("apple", StockMovementKind.INTAKE)
        vm.setQuantity("1,25")
        vm.book()
        advanceUntilIdle()

        assertEquals(1.25, stockRepository.movements.single().quantity)
    }

    @Test
    fun `a stocktake starts from what the ledger believes`() = runTest {
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(15.0, "kg"), recordedAt = 1_000L)
        ))
        val vm = loaded()
        vm.startBooking("apple", StockMovementKind.STOCKTAKE)

        assertEquals("15", vm.state.value.editor?.quantityInput)
    }

    @Test
    fun `a stocktake stores the difference and sums to what was counted`() = runTest {
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(15.0, "kg"), recordedAt = 1_000L)
        ))
        val vm = loaded()
        vm.startBooking("apple", StockMovementKind.STOCKTAKE)
        vm.setQuantity("12,4")
        vm.book()
        advanceUntilIdle()

        val count = stockRepository.movements.last()
        assertEquals(-2.6, count.quantity)
        assertEquals(12.4, count.countedTo)
        assertEquals(12.4, vm.state.value.standing.first { it.articleId == "apple" }.onHand)
    }

    @Test
    fun `a stocktake may find nothing`() = runTest {
        val vm = loaded()
        vm.startBooking("apple", StockMovementKind.STOCKTAKE)
        vm.setQuantity("0")
        vm.book()
        advanceUntilIdle()

        assertEquals(0.0, stockRepository.movements.single().countedTo)
        assertEquals(RefillState.EMPTY, vm.state.value.refillNeeds.single().state)
    }

    @Test
    fun `a loss takes away`() = runTest {
        val vm = loaded()
        vm.startBooking("apple", StockMovementKind.LOSS)
        vm.setQuantity("0,5")
        vm.setNote("verdorben")
        vm.book()
        advanceUntilIdle()

        val moved = stockRepository.movements.single()
        assertEquals(-0.5, moved.quantity)
        assertEquals("verdorben", moved.note)
    }

    @Test
    fun `an intake of nothing is refused`() = runTest {
        val vm = loaded()
        vm.startBooking("apple", StockMovementKind.INTAKE)
        vm.setQuantity("0")
        vm.book()
        advanceUntilIdle()

        assertEquals(StockMessage.INVALID_QUANTITY, vm.state.value.message)
        assertTrue(stockRepository.movements.isEmpty())
    }

    @Test
    fun `an unreadable quantity is refused`() = runTest {
        val vm = loaded()
        vm.startBooking("apple", StockMovementKind.INTAKE)
        vm.setQuantity("viele")
        vm.book()
        advanceUntilIdle()

        assertEquals(StockMessage.INVALID_QUANTITY, vm.state.value.message)
        assertTrue(stockRepository.movements.isEmpty())
    }

    @Test
    fun `a failed booking keeps the editor open and says so`() = runTest {
        stockRepository.shouldFailRecord = true
        val vm = loaded()
        vm.startBooking("apple", StockMovementKind.INTAKE)
        vm.setQuantity("15")
        vm.book()
        advanceUntilIdle()

        assertEquals(StockMessage.SAVE_FAILED, vm.state.value.message)
        assertEquals("15", vm.state.value.editor?.quantityInput)
    }

    // --- the scale ------------------------------------------------------------

    @Test
    fun `without a scale the weigh affordance is not offered`() = runTest {
        val vm = loaded()
        assertEquals(ScaleStatus.Unsupported, vm.state.value.scaleStatus)
        assertTrue(!vm.state.value.canWeigh)
        assertTrue(!vm.state.value.scaleStatus.isPossibleHere)
    }

    @Test
    fun `a reading fills the quantity and is recorded as weighed`() = runTest {
        scale.connect()
        val vm = loaded()
        scale.report(MeasuredQuantity(2.5, "kg", QuantitySource.SCALE, measuredAt = 40_000L))
        advanceUntilIdle()

        vm.startBooking("apple", StockMovementKind.INTAKE)
        vm.fillFromScale()
        assertEquals("2.5", vm.state.value.editor?.quantityInput)

        vm.book()
        advanceUntilIdle()
        assertEquals(QuantitySource.SCALE, stockRepository.movements.single().source)
    }

    @Test
    fun `typing over a reading makes it a typed value again`() = runTest {
        scale.connect()
        val vm = loaded()
        scale.report(MeasuredQuantity(2.5, "kg", QuantitySource.SCALE, measuredAt = 40_000L))
        advanceUntilIdle()

        vm.startBooking("apple", StockMovementKind.INTAKE)
        vm.fillFromScale()
        vm.setQuantity("3")
        vm.book()
        advanceUntilIdle()

        // The seller overrode the scale, so this is no longer a measurement.
        assertEquals(QuantitySource.MANUAL, stockRepository.movements.single().source)
    }

    @Test
    fun `an unsettled reading is not accepted`() = runTest {
        scale.connect()
        val vm = loaded()
        scale.report(
            MeasuredQuantity(2.5, "kg", QuantitySource.SCALE, measuredAt = 40_000L, isSettled = false)
        )
        advanceUntilIdle()

        vm.startBooking("apple", StockMovementKind.INTAKE)
        vm.fillFromScale()

        assertEquals(StockMessage.SCALE_NOT_READY, vm.state.value.message)
        assertEquals("", vm.state.value.editor?.quantityInput)
    }
}
