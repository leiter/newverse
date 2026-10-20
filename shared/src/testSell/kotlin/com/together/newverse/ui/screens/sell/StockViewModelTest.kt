package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.ArticleScarcity
import com.together.newverse.domain.model.MeasuredQuantity
import com.together.newverse.domain.model.BuyerProfile
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
import com.together.newverse.test.FakeMessageRepository
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
    private lateinit var messageRepository: FakeMessageRepository
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
        messageRepository = FakeMessageRepository()
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
        messageRepository.reset()
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun viewModel() = StockViewModel(
        sellerArticleRepository = articleRepository,
        stockRepository = stockRepository,
        orderRepository = orderRepository,
        authRepository = authRepository,
        messageRepository = messageRepository,
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

    @Test
    fun `an oversold article is on the list even though nothing is watched`() = runTest {
        // Caught by running the app: the screen asked "is anything watched?" before
        // "is anything on the list?", so an oversold unwatched article showed as
        // "Kein Artikel wird überwacht" while the badge counted it.
        articleRepository.setArticles(listOf(nuts))
        stockRepository.setMovements(listOf(
            StockMovement.intake("nuts", MeasuredQuantity.typed(1.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(order("o1", articleId = "nuts", amount = 4.0)))

        val state = loaded().state.value

        assertTrue(!state.hasWatchedArticles, "the fixture is meant to watch nothing")
        assertEquals(1, state.refillNeeds.size)
        assertTrue(!state.nothingWatched, "the hint would have hidden an oversold article")
    }

    @Test
    fun `nothing watched and nothing short is the hint's own case`() = runTest {
        articleRepository.setArticles(listOf(nuts))

        assertTrue(loaded().state.value.nothingWatched)
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

    // --- derived availability -------------------------------------------------

    private val gatedApple = SellerArticle(
        Article(id = "apple", productName = "Apfel", unit = "kg", available = true),
        SellerArticleData(reorderLevel = 5.0, sellerAvailable = true, stockGated = true)
    )

    @Test
    fun `a gated article is closed to buyers once orders use up the shelf`() = runTest {
        articleRepository.setArticles(listOf(gatedApple))
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(6.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(order("o1", amount = 6.0)))

        loaded()

        assertEquals(listOf(mapOf("apple" to false)), articleRepository.availabilityWrites)
    }

    @Test
    fun `a gated article is opened again when stock arrives`() = runTest {
        articleRepository.setArticles(listOf(gatedApple.copy(
            article = gatedApple.article.copy(available = false)
        )))
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(20.0, "kg"), recordedAt = 1_000L)
        ))

        loaded()

        assertEquals(listOf(mapOf("apple" to true)), articleRepository.availabilityWrites)
    }

    @Test
    fun `the band is published even when availability does not move`() = runTest {
        // 20 kg against a 5 kg reorder level: still orderable, so `available` stays
        // true — but buyers have been told nothing about how much is left.
        articleRepository.setArticles(listOf(gatedApple))
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(20.0, "kg"), recordedAt = 1_000L)
        ))

        loaded()

        val written = articleRepository.publicationWrites.single().getValue("apple")
        assertEquals(true, written.available)
        assertEquals(ArticleScarcity.PLENTIFUL, written.scarcity)
    }

    @Test
    fun `a published article that already agrees is not written again`() = runTest {
        articleRepository.setArticles(listOf(gatedApple.copy(
            article = gatedApple.article.copy(scarcity = ArticleScarcity.PLENTIFUL)
        )))
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(20.0, "kg"), recordedAt = 1_000L)
        ))

        loaded()

        assertTrue(articleRepository.publicationWrites.isEmpty())
    }

    @Test
    fun `the pass settles instead of writing the same correction twice`() = runTest {
        articleRepository.setArticles(listOf(gatedApple))
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(2.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(order("o1", amount = 2.0)))
        loaded()
        assertEquals(1, articleRepository.availabilityWrites.size)

        // The write re-emits the catalogue; the next evaluation must find nothing.
        advanceUntilIdle()

        assertEquals(1, articleRepository.availabilityWrites.size)
    }

    @Test
    fun `an ungated article is never written by the pass`() = runTest {
        // apple and nuts from the default fixture are both ungated.
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(0.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(order("o1", amount = 50.0)))

        loaded()

        assertTrue(articleRepository.availabilityWrites.isEmpty())
    }

    @Test
    fun `a gated article whose intent was never recorded is left alone`() = runTest {
        articleRepository.setArticles(listOf(gatedApple.copy(
            sellerData = SellerArticleData(reorderLevel = 5.0, stockGated = true)
        )))
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(0.0, "kg"), recordedAt = 1_000L)
        ))

        loaded()

        assertTrue(
            articleRepository.availabilityWrites.isEmpty(),
            "closed an article that could then never be reopened"
        )
    }

    @Test
    fun `a gated article the ledger has never seen is left open`() = runTest {
        articleRepository.setArticles(listOf(gatedApple))

        loaded()

        assertTrue(
            articleRepository.availabilityWrites.isEmpty(),
            "a never-counted article was withdrawn from buyers"
        )
    }

    // --- telling a buyer about a shortfall -------------------------------------

    private fun orderFor(
        id: String,
        buyer: String,
        buyerId: String = "buyer_$buyer",
        amount: Double = 8.0
    ) = Order(
        id = id,
        buyerProfile = BuyerProfile(id = buyerId, displayName = buyer),
        status = OrderStatus.PLACED,
        origin = OrderOrigin.PRODUCTION,
        pickUpDate = 100_000L,
        articles = listOf(
            OrderedProduct(productId = "apple", productName = "Apfel", unit = "kg", amountCount = amount)
        )
    )

    /** One article short, with two buyers waiting for it. */
    private fun TestScope.oversold(): StockViewModel {
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(2.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(
            orderFor("o1", "Anna", amount = 5.0),
            orderFor("o2", "Bert", amount = 5.0)
        ))
        return loaded()
    }

    @Test
    fun `the orders waiting for a short article are listed`() = runTest {
        val vm = oversold()

        val shortfalls = vm.state.value.shortfalls

        assertEquals(listOf("Anna", "Bert"), shortfalls.map { it.buyerName })
        assertEquals("Apfel", shortfalls.first().lines.single().productName)
    }

    @Test
    fun `nothing short means no orders to answer for`() = runTest {
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(50.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(orderFor("o1", "Anna")))

        assertTrue(loaded().state.value.shortfalls.isEmpty())
    }

    @Test
    fun `drafting prepares a message and sends nothing`() = runTest {
        val vm = oversold()

        vm.startShortfallMessage("o1")
        advanceUntilIdle()

        val draft = vm.state.value.messageDraft
        assertEquals("buyer_Anna", draft?.buyerId)
        assertTrue(draft?.text?.contains("Anna") == true)
        assertTrue(messageRepository.sentMessages.isEmpty(), "a draft was sent by itself")
    }

    @Test
    fun `sending writes what the seller edited, not the draft`() = runTest {
        val vm = oversold()
        vm.startShortfallMessage("o1")
        advanceUntilIdle()

        vm.setShortfallMessage("Hallo Anna, ich bringe dir morgen Birnen mit.")
        vm.sendShortfallMessage()
        advanceUntilIdle()

        val sent = messageRepository.sentMessages.single()
        assertEquals("Hallo Anna, ich bringe dir morgen Birnen mit.", sent.text)
        assertEquals("seller_123", sent.senderId)
        assertEquals(listOf("seller_123" to "buyer_Anna"), messageRepository.requestedConversations)
        assertNull(vm.state.value.messageDraft)
        assertEquals(StockMessage.MESSAGE_SENT, vm.state.value.message)
    }

    @Test
    fun `a dismissed draft sends nothing`() = runTest {
        val vm = oversold()
        vm.startShortfallMessage("o1")
        advanceUntilIdle()

        vm.dismissShortfallMessage()
        vm.sendShortfallMessage()
        advanceUntilIdle()

        assertTrue(messageRepository.sentMessages.isEmpty())
    }

    @Test
    fun `an empty message is not sent`() = runTest {
        val vm = oversold()
        vm.startShortfallMessage("o1")
        advanceUntilIdle()

        vm.setShortfallMessage("   ")
        vm.sendShortfallMessage()
        advanceUntilIdle()

        assertTrue(messageRepository.sentMessages.isEmpty())
        // The draft stays open so the seller can write something.
        assertTrue(vm.state.value.messageDraft != null)
    }

    @Test
    fun `a failed send keeps the draft and says so`() = runTest {
        val vm = oversold()
        vm.startShortfallMessage("o1")
        advanceUntilIdle()
        messageRepository.shouldFailSend = true

        vm.sendShortfallMessage()
        advanceUntilIdle()

        assertEquals(StockMessage.MESSAGE_FAILED, vm.state.value.message)
        assertTrue(vm.state.value.messageDraft != null, "the seller's text was thrown away")
    }

    @Test
    fun `an order with no buyer account cannot be answered`() = runTest {
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(1.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(orderFor("o1", "", buyerId = "")))
        val vm = loaded()

        vm.startShortfallMessage("o1")
        advanceUntilIdle()

        assertNull(vm.state.value.messageDraft)
        assertEquals(StockMessage.NO_BUYER_TO_MESSAGE, vm.state.value.message)
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
