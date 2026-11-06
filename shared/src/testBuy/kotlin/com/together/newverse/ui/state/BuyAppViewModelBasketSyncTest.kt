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
import com.together.newverse.ui.state.buy.basketScreenLoadOrder
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
import kotlin.test.assertTrue

/**
 * A basket is one basket, and an edit in progress outranks it.
 *
 * The screen is fed by an in-memory list seeded once, so an amount changed on the
 * buyer's other phone used to stay there. It now follows the order (or the draft)
 * live — but only onto an idle screen. The rule is not a detail: a remote value
 * applied over an amount somebody is in the middle of typing is work lost in front
 * of them, and the stale instruction winning is the worst of the two outcomes.
 *
 * So these pin both halves. Idle takes it. Busy holds it and says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelBasketSyncTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository
    private val sellerConfig = FakeSellerConfig()

    private val buyerId = "buyer_1"
    private val pickupDate = OrderDateUtils.calculateNextPickupDate().toEpochMilliseconds()
    private val dateKey = OrderDateUtils.formatDateKey(OrderDateUtils.calculateNextPickupDate())

    private fun apples(amount: Double) = OrderedProduct(
        productId = "apple", productName = "Apfel", price = 3.0, amountCount = amount
    )

    private fun order(amount: Double) = Order(
        id = "order_1",
        sellerId = sellerConfig.sellerId,
        marketId = "market_1",
        createdDate = 1_000L,
        pickUpDate = pickupDate,
        status = OrderStatus.PLACED,
        origin = OrderOrigin.PRODUCTION,
        articles = listOf(apples(amount))
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
        authRepository.setCurrentUserId(buyerId)
        profileRepository.currentBuyerId = buyerId
        profileRepository.setBuyerProfile(
            BuyerProfile(id = buyerId, displayName = "Annabel", emailAddress = "a@example.com")
        )
        profileRepository.setAccessStatus(sellerConfig.sellerId, buyerId, AccessStatus.APPROVED)
    }

    @AfterTest
    fun tearDown() {
        orderRepository.reset()
        profileRepository.reset()
        authRepository.reset()
        basketRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = BuyAppViewModel(
        articleRepository = FakeArticleRepository(),
        orderRepository = orderRepository,
        profileRepository = profileRepository,
        authRepository = authRepository,
        basketRepository = basketRepository,
        sellerConfig = sellerConfig
    )

    /** An approved buyer with one placed order of [amount] kg open on the basket screen. */
    private suspend fun TestScope.holdingOrderOf(amount: Double): BuyAppViewModel {
        orderRepository.setOrders(listOf(order(amount)))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.basketScreenLoadOrder("order_1", dateKey, forceLoad = true)
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `an amount changed on the other phone reaches an idle basket`() = runTest {
        val viewModel = holdingOrderOf(2.0)
        assertEquals(
            listOf(2.0),
            viewModel.state.value.basketScreen.items.map { it.amountCount }
        )

        // The other phone sets it to 5 kg.
        orderRepository.setOrders(listOf(order(5.0)))
        advanceUntilIdle()

        assertEquals(
            listOf(5.0),
            viewModel.state.value.basketScreen.items.map { it.amountCount },
            "the screen was idle, so the basket is simply the basket"
        )
        assertFalse(
            viewModel.state.value.basketScreen.hasChanges,
            "what arrived is the new baseline, not an edit against the old one"
        )
        assertFalse(viewModel.state.value.basketScreen.remoteBasketChangePending)
    }

    @Test
    fun `an unsaved edit is not overwritten, and the buyer is told why`() = runTest {
        val viewModel = holdingOrderOf(2.0)

        // This buyer is mid-edit here: 7 kg, not yet saved.
        basketRepository.setBasketItems(listOf(apples(7.0)))
        advanceUntilIdle()
        assertTrue(
            viewModel.state.value.basketScreen.hasChanges,
            "precondition: the screen has an unsaved edit"
        )

        // And now the other phone writes 5 kg.
        orderRepository.setOrders(listOf(order(5.0)))
        advanceUntilIdle()

        assertEquals(
            listOf(7.0),
            viewModel.state.value.basketScreen.items.map { it.amountCount },
            "the amount being typed is the buyer's intent; the remote value is not"
        )
        assertTrue(
            viewModel.state.value.basketScreen.remoteBasketChangePending,
            "and holding it back silently would let them save over it unknowingly"
        )
    }

    @Test
    fun `the app's own write is not reported as a change from elsewhere`() = runTest {
        val viewModel = holdingOrderOf(2.0)

        // The same order comes back through the subscription, as it does after any
        // write: identical items, nothing to say.
        orderRepository.setOrders(listOf(order(2.0)))
        advanceUntilIdle()

        assertFalse(
            viewModel.state.value.basketScreen.remoteBasketChangePending,
            "an echo of our own state is not news"
        )
        assertEquals(
            listOf(2.0),
            viewModel.state.value.basketScreen.items.map { it.amountCount }
        )
    }
}
