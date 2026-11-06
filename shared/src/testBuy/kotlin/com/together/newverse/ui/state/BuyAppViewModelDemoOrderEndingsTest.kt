package com.together.newverse.ui.state

import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderOrigin
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerConfig
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.ui.state.buy.basketScreenCancelOrder
import com.together.newverse.ui.state.buy.basketScreenLoadOrder
import com.together.newverse.ui.state.buy.discardDemoOrder
import com.together.newverse.ui.state.buy.dropUnconfirmableDemoOrders
import com.together.newverse.ui.state.buy.remainingDemoOrders
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * How a leftover demo order ends.
 *
 * Approval tells the buyer their demo order does not count yet and has to be confirmed
 * against a slot that is open now. For a long time that was the whole story: *Bestätigen*
 * existed, nothing else did, and the only reachable action on the basket screen was
 * *Storno* — which is the wrong verb (the seller never saw the order, so there is nobody
 * to tell) and, past the edit deadline, refused outright. A buyer could be left holding a
 * record they could neither confirm nor remove.
 *
 * So there are now two endings, and this pins both: **Verwerfen**, which deletes the
 * record wherever it is held and drops it from the buyer's index, and the sweep on
 * approval that drops the ones whose pickup day has already gone, because there is
 * nothing left to confirm those against.
 *
 * Trunk routing itself is [BuyAppViewModelDemoOrderTrunkTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelDemoOrderEndingsTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private val sellerConfig = FakeSellerConfig()

    private val nextPickup = OrderDateUtils.calculateNextPickupDate().toEpochMilliseconds()
    private val nextPickupKey =
        OrderDateUtils.formatDateKey(OrderDateUtils.calculateNextPickupDate())

    /** A pickup day well in the past: nothing can be confirmed against it. */
    private val gonePickup = Clock.System.now().toEpochMilliseconds() - 14L * 86_400_000
    private val goneKey = "20200102"

    private fun order(
        id: String,
        pickUpDate: Long = nextPickup,
        origin: OrderOrigin = OrderOrigin.DEMO,
    ) = Order(
        id = id,
        sellerId = sellerConfig.sellerId,
        marketId = "market_1",
        createdDate = 1_000L,
        pickUpDate = pickUpDate,
        status = OrderStatus.PLACED,
        origin = origin,
        articles = listOf(
            OrderedProduct(
                productId = "apple", productName = "Apfel", price = 3.04, amountCount = 1.0
            )
        )
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        authRepository.setCurrentUserId("buyer_1")
        profileRepository.setAccessStatus(
            sellerConfig.sellerId,
            profileRepository.currentBuyerId,
            AccessStatus.APPROVED
        )
    }

    @AfterTest
    fun tearDown() {
        orderRepository.reset()
        profileRepository.reset()
        authRepository.reset()
        sellerConfig.clearDemoOrders()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = BuyAppViewModel(
        articleRepository = FakeArticleRepository(),
        orderRepository = orderRepository,
        profileRepository = profileRepository,
        authRepository = authRepository,
        basketRepository = FakeBasketRepository(),
        sellerConfig = sellerConfig
    )

    /** The buyer's order index, which is what their history is read through. */
    private fun indexOf(vararg orders: Order) {
        profileRepository.setBuyerProfile(
            BuyerProfile(
                id = profileRepository.currentBuyerId,
                displayName = "Alice",
                placedOrderIds = orders.associate { o ->
                    (if (o.pickUpDate == nextPickup) nextPickupKey else goneKey) to o.id
                }
            )
        )
    }

    // ===== Verwerfen =====

    @Test
    fun `discarding a demo order deletes the record and drops it from the index`() = runTest {
        val demo = order("order_1")
        orderRepository.setOrders(listOf(demo))
        indexOf(demo)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.discardDemoOrder(demo)
        advanceUntilIdle()

        assertEquals(
            FakeOrderRepository.DeleteCall(nextPickupKey, "order_1"),
            orderRepository.deleteCalls.single()
        )
        assertTrue(orderRepository.cancelCalls.isEmpty(), "removed, not cancelled")
        assertTrue(
            profileRepository.lastSavedBuyerProfile?.placedOrderIds.orEmpty().isEmpty(),
            "an index entry left behind is a history row pointing at nothing"
        )
    }

    @Test
    fun `discarding a locally held demo order removes it from local storage`() = runTest {
        // Everything after the Firebase write allowance runs out lives here instead,
        // under an id the app minted itself.
        val local = order("demo_1700000000000")
        sellerConfig.saveDemoOrder(local)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.discardDemoOrder(local)
        advanceUntilIdle()

        assertTrue(sellerConfig.loadDemoOrders().isEmpty())
        assertTrue(
            orderRepository.deleteCalls.isEmpty(),
            "nothing was ever written to demo_orders/ for this one"
        )
    }

    @Test
    fun `a refused delete leaves the order and its index entry alone`() = runTest {
        // An index entry pointing at a live record is recoverable. The reverse is a
        // history row the buyer can never get rid of.
        val demo = order("order_1")
        orderRepository.setOrders(listOf(demo))
        indexOf(demo)
        orderRepository.deleteDemoOrdersFails = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.discardDemoOrder(demo)
        advanceUntilIdle()

        assertEquals(
            mapOf(nextPickupKey to "order_1"),
            profileRepository.lastSavedBuyerProfile?.placedOrderIds
                ?: mapOf(nextPickupKey to "order_1"),
            "the index still names the order that is still there"
        )
    }

    @Test
    fun `a demo order past its edit deadline can still be discarded`() = runTest {
        // The deadline protects a seller planning around the order. Nobody is planning
        // around a demo order, and approval can arrive long after the day the buyer
        // picked — so holding it to the deadline is what made the record unremovable.
        val stale = order("order_1", pickUpDate = gonePickup)
        orderRepository.setOrders(listOf(stale))
        indexOf(stale)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.basketScreenLoadOrder("order_1", goneKey, forceLoad = true)
        advanceUntilIdle()

        viewModel.basketScreenCancelOrder()
        advanceUntilIdle()

        assertEquals("order_1", orderRepository.deleteCalls.single().orderId)
        assertNull(
            viewModel.state.value.basketScreen.orderError,
            "and not 'Stornierung nicht mehr möglich'"
        )
    }

    // ===== the sweep on approval =====

    @Test
    fun `approval drops the demo orders nothing can confirm any more`() = runTest {
        val stale = order("order_gone", pickUpDate = gonePickup)
        val live = order("order_live")
        orderRepository.setOrders(listOf(stale, live))
        indexOf(stale, live)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val remaining = viewModel.dropUnconfirmableDemoOrders(listOf(stale, live))
        advanceUntilIdle()

        assertEquals(listOf("order_live"), remaining.map { it.id })
        assertEquals(
            listOf("order_gone"),
            orderRepository.deleteCalls.map { it.orderId },
            "the one whose pickup day has gone, and only that one"
        )
    }

    @Test
    fun `an order still ahead of its pickup day is kept for the buyer to decide`() = runTest {
        val live = order("order_live")
        orderRepository.setOrders(listOf(live))
        indexOf(live)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val remaining = viewModel.dropUnconfirmableDemoOrders(listOf(live))
        advanceUntilIdle()

        assertEquals(listOf("order_live"), remaining.map { it.id })
        assertTrue(orderRepository.deleteCalls.isEmpty())
        assertTrue(orderRepository.cancelCalls.isEmpty())
    }

    @Test
    fun `the demo orders still waiting are counted from both trunks`() = runTest {
        // This used to read orders/, where origin is never DEMO, so a buyer whose demo
        // orders were all in Firebase was told there was nothing to confirm.
        val remote = order("order_1")
        val local = order("demo_1700000000000")
        orderRepository.setOrders(listOf(remote))
        indexOf(remote)
        sellerConfig.saveDemoOrder(local)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val waiting = viewModel.remainingDemoOrders()
        advanceUntilIdle()

        assertEquals(
            setOf("order_1", "demo_1700000000000"),
            waiting.map { it.id }.toSet()
        )
    }

    @Test
    fun `a production order handed to the remover is refused, not deleted`() = runTest {
        // Cancelled, never removed: the seller has it and it is in their books. Guarded
        // in removeDemoOrders rather than trusted to the callers, because the trunks
        // were mixed up once already.
        val real = order("order_real", origin = OrderOrigin.PRODUCTION)
        orderRepository.setOrders(listOf(real))
        indexOf(real)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.discardDemoOrder(real)
        advanceUntilIdle()

        assertTrue(orderRepository.deleteCalls.isEmpty())
        assertTrue(orderRepository.cancelCalls.isEmpty())
        assertEquals(
            mapOf(nextPickupKey to "order_real"),
            profileRepository.lastSavedBuyerProfile?.placedOrderIds
                ?: mapOf(nextPickupKey to "order_real"),
            "and it keeps its place in the buyer's history"
        )
    }

    @Test
    fun `a production order is never swept up with the demo ones`() = runTest {
        val real = order("order_real", origin = OrderOrigin.PRODUCTION)
        orderRepository.setOrders(listOf(real))
        indexOf(real)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.remainingDemoOrders().isEmpty())
    }
}
