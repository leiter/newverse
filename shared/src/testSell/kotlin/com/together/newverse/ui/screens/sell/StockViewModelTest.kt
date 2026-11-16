package com.together.newverse.ui.screens.sell

import com.together.newverse.domain.model.Article
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
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
        Article(
            id = "apple",
            productId = "112108",
            productName = "Apfel",
            searchTerms = "topaz,obst",
            category = "Obst",
            unit = "kg"
        ),
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
    fun `the standing is searched by article number, which is what the seller is holding`() = runTest {
        // The number on the delivery note. It used to find nothing here, because the
        // standing matched the product name alone -- on the one screen the seller is
        // standing in front of a shelf with the note in their hand.
        val vm = loaded()
        vm.setQuery("112108")

        assertEquals(listOf("apple"), vm.state.value.matchingStanding.map { it.articleId })
    }

    @Test
    fun `the standing is searched by the seller's own search terms`() = runTest {
        val vm = loaded()
        vm.setQuery("topaz")

        assertEquals(listOf("apple"), vm.state.value.matchingStanding.map { it.articleId })
    }

    @Test
    fun `the standing is searched by category`() = runTest {
        val vm = loaded()
        vm.setQuery("obst")

        assertEquals(listOf("apple"), vm.state.value.matchingStanding.map { it.articleId })
    }

    @Test
    fun `a query matching nothing empties the standing rather than ignoring itself`() = runTest {
        val vm = loaded()
        vm.setQuery("kohlrabi")

        assertTrue(vm.state.value.matchingStanding.isEmpty())
    }

    @Test
    fun `a blank query is the whole standing`() = runTest {
        val vm = loaded()
        vm.setQuery("   ")

        assertEquals(2, vm.state.value.matchingStanding.size)
    }

    // ===== The standing's filter chips =====

    @Test
    fun `the filter starts at ALL, so nothing is hidden from a lookup`() = runTest {
        val vm = loaded()

        assertEquals(StockFilter.ALL, vm.state.value.filter)
        assertEquals(2, vm.state.value.matchingStanding.size)
    }

    @Test
    fun `watched means a reorder level, not merely a state that is not UNWATCHED`() = runTest {
        // The trap: refillNeed gives UNWATCHED when the reorder level is 0 AND nothing
        // is ordered -- so an unwatched article that has been counted and then oversold
        // gets a real state instead, and filtering on `state != UNWATCHED` would call
        // that article watched. Nüsse is counted and oversold here, and watches nothing.
        stockRepository.setMovements(listOf(
            StockMovement.intake("nuts", MeasuredQuantity.typed(1.0, "kg"), recordedAt = 1_000L)
        ))
        orderRepository.setOrders(listOf(order("o1", articleId = "nuts", amount = 4.0)))
        val vm = loaded()

        val nuts = vm.state.value.standing.first { it.articleId == "nuts" }
        assertTrue(nuts.state != RefillState.UNWATCHED, "demand should give it a real state")
        assertEquals(0.0, nuts.reorderLevel)

        vm.setFilter(StockFilter.WATCHED)

        assertEquals(listOf("apple"), vm.state.value.matchingStanding.map { it.articleId })
    }

    @Test
    fun `uncounted means no movement, watched or not`() = runTest {
        // UNCOUNTED as a state is only ever given to watched articles. The seller
        // going round with a scale wants every article nobody has counted.
        val vm = loaded()
        vm.setFilter(StockFilter.UNCOUNTED)

        assertEquals(
            listOf("apple", "nuts"),
            vm.state.value.matchingStanding.map { it.articleId }.sorted()
        )
    }

    @Test
    fun `a counted article leaves the uncounted filter`() = runTest {
        stockRepository.setMovements(listOf(
            StockMovement.intake("apple", MeasuredQuantity.typed(12.0, "kg"), recordedAt = 1_000L)
        ))
        val vm = loaded()
        vm.setFilter(StockFilter.UNCOUNTED)

        assertEquals(listOf("nuts"), vm.state.value.matchingStanding.map { it.articleId })
    }

    @Test
    fun `the refill filter shows what the refill list shows`() = runTest {
        val vm = loaded()
        vm.setFilter(StockFilter.NEEDS_REFILL)

        assertEquals(
            vm.state.value.refillNeeds.map { it.articleId }.toSet(),
            vm.state.value.matchingStanding.map { it.articleId }.toSet()
        )
    }

    @Test
    fun `the filter and the query both have to hold`() = runTest {
        val vm = loaded()
        vm.setFilter(StockFilter.WATCHED)
        vm.setQuery("nüs")

        // Nüsse matches the query but is not watched, so nothing is left.
        assertTrue(vm.state.value.matchingStanding.isEmpty())
    }

    @Test
    fun `the counts are taken after the query`() = runTest {
        val vm = loaded()
        vm.setQuery("apf")

        val counts = vm.state.value.filterCounts
        // One article matches the query, and it is the watched one.
        assertEquals(1, counts[StockFilter.ALL])
        assertEquals(1, counts[StockFilter.WATCHED])
        assertEquals(0, counts.getValue(StockFilter.ALL) - counts.getValue(StockFilter.UNCOUNTED))
    }

    @Test
    fun `every chip is counted, including the ones that would show nothing`() = runTest {
        val vm = loaded()
        vm.setQuery("nüs")

        val counts = vm.state.value.filterCounts
        // A chip reading 0 is an answer; a chip that vanished as the seller typed
        // would be a surprise.
        assertEquals(StockFilter.entries.toSet(), counts.keys)
        assertEquals(0, counts[StockFilter.WATCHED])
        assertEquals(1, counts[StockFilter.ALL])
    }

    @Test
    fun `the refill list itself is never filtered`() = runTest {
        val vm = loaded()
        val before = vm.state.value.refillNeeds.map { it.articleId }

        vm.setFilter(StockFilter.UNCOUNTED)

        assertEquals(before, vm.state.value.refillNeeds.map { it.articleId })
    }

    // ===== The standing's order =====

    private fun stocked(id: String, name: String, reorderLevel: Double) = SellerArticle(
        Article(id = id, productName = name, unit = "kg"),
        SellerArticleData(reorderLevel = reorderLevel)
    )

    /** Four articles whose numbers differ on every axis the sort offers. */
    private fun TestScope.sortable(): StockViewModel {
        articleRepository.setArticles(listOf(
            stocked("beet", "Rote Bete", reorderLevel = 10.0),
            stocked("apple", "Apfel", reorderLevel = 5.0),
            stocked("carrot", "Karotte", reorderLevel = 0.0),
            stocked("dill", "Dill", reorderLevel = 2.0)
        ))
        stockRepository.setMovements(listOf(
            // beet: plenty counted long ago. apple: almost gone, counted recently.
            // dill: a little, never counted by a stocktake. carrot: no movement at all.
            StockMovement.intake("beet", MeasuredQuantity.typed(40.0, "kg"), recordedAt = 1_000L),
            StockMovement.intake("apple", MeasuredQuantity.typed(1.0, "kg"), recordedAt = 2_000L),
            StockMovement.intake("dill", MeasuredQuantity.typed(3.0, "kg"), recordedAt = 3_000L)
        ))
        return loaded()
    }

    private fun names(vm: StockViewModel) = vm.state.value.matchingStanding.map { it.productName }

    @Test
    fun `the standing is sorted by name to begin with`() = runTest {
        val vm = sortable()

        assertEquals(StockSort.NAME, vm.state.value.sort)
        assertEquals(listOf("Apfel", "Dill", "Karotte", "Rote Bete"), names(vm))
    }

    @Test
    fun `by stock puts what is nearly gone first`() = runTest {
        val vm = sortable()
        vm.setSort(StockSort.ON_HAND)

        // Karotte has no level at all, which reads as 0.0 and sorts with the empty.
        assertEquals(listOf("Karotte", "Apfel", "Dill", "Rote Bete"), names(vm))
    }

    @Test
    fun `by shortfall puts the biggest gap first and the settled last`() = runTest {
        val vm = sortable()
        vm.setSort(StockSort.SHORTFALL)

        // beet is above its level and carrot is unwatched, so both need nothing and
        // fall to the bottom in name order. apple wants 4, dill wants... less.
        val ordered = names(vm)
        assertEquals("Apfel", ordered.first())
        assertEquals(listOf("Karotte", "Rote Bete"), ordered.takeLast(2))
    }

    @Test
    fun `by last counted puts what was never counted first`() = runTest {
        val vm = sortable()
        vm.setSort(StockSort.LAST_COUNTED)

        // An intake is not a count, so nothing here has a lastCountedAt and the whole
        // list is "no evidence", in name order.
        assertEquals(listOf("Apfel", "Dill", "Karotte", "Rote Bete"), names(vm))
    }

    @Test
    fun `a counted article sorts after everything never counted`() = runTest {
        val vm = sortable()
        vm.startBooking("beet", StockMovementKind.STOCKTAKE)
        vm.setQuantity("40")
        vm.book()
        advanceUntilIdle()
        vm.setSort(StockSort.LAST_COUNTED)

        // Rote Bete is the only one a count has confirmed, so it goes last: never
        // counted is no evidence at all, not merely old evidence.
        assertEquals("Rote Bete", names(vm).last())
    }

    @Test
    fun `the sort holds while the filter and the query narrow`() = runTest {
        val vm = sortable()
        vm.setSort(StockSort.ON_HAND)
        vm.setFilter(StockFilter.WATCHED)

        // Karotte is unwatched and drops out; the rest keep the on-hand order.
        assertEquals(listOf("Apfel", "Dill", "Rote Bete"), names(vm))
    }

    @Test
    fun `the refill list keeps its own order whatever the standing does`() = runTest {
        val vm = sortable()
        val before = vm.state.value.refillNeeds.map { it.productName }

        vm.setSort(StockSort.LAST_COUNTED)

        // Urgency, then how much is missing, then name -- the order the seller buys
        // in, which is an answer rather than a presentation.
        assertEquals(before, vm.state.value.refillNeeds.map { it.productName })
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

    // StockViewModel reads the system clock, and demand only counts an order whose
    // pickup day is still ahead, so these orders are collected tomorrow. A fixed epoch
    // value would make every one of them read as long since collected.
    private val tomorrow = Clock.System.now().plus(1.days).toEpochMilliseconds()

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
        pickUpDate = tomorrow,
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
        pickUpDate = tomorrow,
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
