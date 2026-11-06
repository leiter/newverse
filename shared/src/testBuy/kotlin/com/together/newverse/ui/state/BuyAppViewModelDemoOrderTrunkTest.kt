package com.together.newverse.ui.state

import com.together.newverse.domain.model.AccessStatus
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
import com.together.newverse.ui.state.buy.basketScreenUpdateOrder
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which trunk an order lives in is a fact about the order, not about the buyer.
 *
 * Approval does not move a demo order — `promptToConfirmDemoOrders` leaves it in
 * `demo_orders/` for the buyer to confirm against a slot that is open now. But every
 * write used to pick its trunk from `BuyAppState.isDemoMode`, which is derived from the
 * access status and flips the instant the seller approves. A freshly approved buyer's
 * leftover demo order was therefore written to `orders/`, where it had never been.
 *
 * And a missing order there is *refused*, not reported absent: the read rule is
 * `data.child('buyerId').val() === auth.uid` and the level above grants the seller
 * alone, so absent data has no `buyerId` to match. The buyer pressed Storno and was
 * told they had no permission to cancel their own order.
 *
 * These tests pin the trunk to the order. The read side always worked this way —
 * `historyTrunks()` reads both trunks whatever the access status and `historyOrigin()`
 * takes the provenance from the path — so this is the write side catching up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelDemoOrderTrunkTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private val sellerConfig = FakeSellerConfig()

    private val pickupDate = OrderDateUtils.calculateNextPickupDate().toEpochMilliseconds()
    private val dateKey = OrderDateUtils.formatDateKey(OrderDateUtils.calculateNextPickupDate())

    private fun order(origin: OrderOrigin) = Order(
        id = "order_1",
        sellerId = sellerConfig.sellerId,
        marketId = "market_1",
        createdDate = 1_000L,
        pickUpDate = pickupDate,
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
        // Approved, which is the state the bug needed: before approval the account's
        // mode and the order's trunk agree and nothing can tell them apart.
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

    /** An approved buyer with one order of [origin] open on the basket screen. */
    private suspend fun TestScope.holding(origin: OrderOrigin): BuyAppViewModel {
        orderRepository.setOrders(listOf(order(origin)))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.basketScreenLoadOrder("order_1", dateKey, forceLoad = true)
        advanceUntilIdle()
        return viewModel
    }

    // ===== the trunk the basket remembers =====

    @Test
    fun `a demo order loaded by an approved buyer is remembered as a demo order`() = runTest {
        val viewModel = holding(OrderOrigin.DEMO)

        assertEquals("order_1", viewModel.state.value.basketScreen.orderId)
        assertTrue(
            viewModel.state.value.basketScreen.orderIsDemo,
            "approval does not move it, so it is still in demo_orders/"
        )
        assertFalse(
            viewModel.state.value.isDemoMode,
            "and the account is out of demo mode, which is what used to decide this"
        )
    }

    @Test
    fun `a production order is remembered as one`() = runTest {
        assertFalse(holding(OrderOrigin.PRODUCTION).state.value.basketScreen.orderIsDemo)
    }

    @Test
    fun `a transitioned order is remembered as a real one`() = runTest {
        // TRANSITIONED says where it came from, not where it is. It is in orders/.
        assertFalse(holding(OrderOrigin.TRANSITIONED).state.value.basketScreen.orderIsDemo)
    }

    @Test
    fun `an order in neither trunk loads nothing and says so plainly`() = runTest {
        orderRepository.setOrders(emptyList())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.basketScreenLoadOrder("order_1", dateKey, forceLoad = true)
        advanceUntilIdle()

        val basket = viewModel.state.value.basketScreen
        assertNull(basket.orderId)
        assertEquals(
            "Bestellung nicht gefunden",
            basket.orderError,
            "absence reads as absence, in German, never as a permission problem"
        )
    }

    // ===== cancelling =====

    @Test
    fun `cancelling a leftover demo order is aimed at the demo trunk`() = runTest {
        val viewModel = holding(OrderOrigin.DEMO)

        viewModel.basketScreenCancelOrder()
        advanceUntilIdle()

        val call = orderRepository.cancelCalls.single()
        assertEquals("order_1", call.orderId)
        assertEquals(dateKey, call.date)
        assertTrue(call.isDemo, "the order is in demo_orders/, whatever the account is")
    }

    @Test
    fun `cancelling a production order is aimed at the real trunk`() = runTest {
        val viewModel = holding(OrderOrigin.PRODUCTION)

        viewModel.basketScreenCancelOrder()
        advanceUntilIdle()

        assertFalse(orderRepository.cancelCalls.single().isDemo)
    }

    @Test
    fun `a cancelled leftover demo order leaves no error behind`() = runTest {
        // The symptom from the buyer's side: the rules' own words, shown verbatim.
        val viewModel = holding(OrderOrigin.DEMO)

        viewModel.basketScreenCancelOrder()
        advanceUntilIdle()

        assertNull(viewModel.state.value.basketScreen.orderError)
    }

    // ===== editing =====

    @Test
    fun `editing a leftover demo order keeps it in the demo trunk`() = runTest {
        val viewModel = holding(OrderOrigin.DEMO)

        viewModel.basketScreenUpdateOrder()
        advanceUntilIdle()

        // updateOrder routes on the order's own origin, which is only right if the
        // order handed to it came back from the trunk it actually lives in. Read back
        // through the demo path, which is where it has to be.
        val saved = orderRepository.loadOrder(
            sellerConfig.sellerId,
            "order_1",
            "demo_orders/${sellerConfig.sellerId}/$dateKey/order_1"
        ).getOrThrow()
        assertTrue(saved.origin.isDemo)
        assertNull(viewModel.state.value.basketScreen.orderError)
    }
}
