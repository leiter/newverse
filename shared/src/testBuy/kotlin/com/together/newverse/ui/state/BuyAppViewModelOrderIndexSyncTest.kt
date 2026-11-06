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
import com.together.newverse.ui.state.buy.loadOrderHistory
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One buyer, two phones: an order placed on either is the same buyer's order.
 *
 * Which orders exist is `BuyerProfile.placedOrderIds`, written into the buyer's own
 * node when an order is placed. The ViewModel has always reloaded the history when
 * that index changes (`observeMainScreenBuyerProfile`), so the contract these tests
 * state is not new — but nothing pinned it, and it is exactly the contract that was
 * silently doing nothing.
 *
 * It did nothing because `observeBuyerProfile` was a plain in-memory StateFlow fed by
 * a profile that `getBuyerProfile` read from Firebase once per process and then cached
 * forever. A write from the buyer's other phone never reached it, so the index never
 * appeared to change and the reload never fired: the second phone could not see an
 * order from the first until it was restarted, and the next profile save from the
 * stale copy wrote the old index back over the live one. The fix is in the repository
 * — the cache is now a live mirror of the node — and needs Firebase, so it cannot be
 * exercised here.
 *
 * What these tests hold is the half above it: given a profile that does change, the
 * history follows the index, and follows nothing else. The repository now makes that
 * antecedent true on every device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelOrderIndexSyncTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private val sellerConfig = FakeSellerConfig()

    private val buyerId = "buyer_1"
    private val pickupDate = OrderDateUtils.calculateNextPickupDate().toEpochMilliseconds()
    private val dateKey = OrderDateUtils.formatDateKey(OrderDateUtils.calculateNextPickupDate())

    private fun order(id: String) = Order(
        id = id,
        sellerId = sellerConfig.sellerId,
        marketId = "market_1",
        createdDate = 1_000L,
        pickUpDate = pickupDate,
        status = OrderStatus.PLACED,
        origin = OrderOrigin.PRODUCTION,
        articles = listOf(
            OrderedProduct(
                productId = "apple", productName = "Apfel", price = 3.04, amountCount = 1.0
            )
        )
    )

    private fun profileWith(placedOrderIds: Map<String, String>) = BuyerProfile(
        id = buyerId,
        displayName = "Annabel",
        emailAddress = "a@example.com",
        placedOrderIds = placedOrderIds
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        authRepository.setCurrentUserId(buyerId)
        profileRepository.currentBuyerId = buyerId
        // Approved: the demo branch of loadOrderHistory reads local storage first and
        // is a different question from this one.
        profileRepository.setAccessStatus(
            sellerConfig.sellerId,
            buyerId,
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

    @Test
    fun `an order indexed by the buyer's other phone reaches the history without a restart`() =
        runTest {
            profileRepository.setBuyerProfile(profileWith(emptyMap()))
            val viewModel = createViewModel()
            viewModel.loadOrderHistory()
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.orderHistory.items.isEmpty(),
                "nothing is indexed yet"
            )

            // The other phone places an order: the record appears, and the buyer's
            // own index names it. Both arrive here through the live profile.
            orderRepository.setOrders(listOf(order("order_1")))
            profileRepository.setBuyerProfile(profileWith(mapOf(dateKey to "order_1")))
            advanceUntilIdle()

            assertEquals(
                listOf("order_1"),
                viewModel.state.value.orderHistory.items.map { it.id },
                "the index changed, so the list it keys must have changed with it"
            )
        }

    @Test
    fun `the history re-subscribes on the new index rather than keeping the first`() = runTest {
        profileRepository.setBuyerProfile(profileWith(mapOf(dateKey to "order_1")))
        orderRepository.setOrders(listOf(order("order_1")))
        val viewModel = createViewModel()
        viewModel.loadOrderHistory()
        advanceUntilIdle()

        val before = orderRepository.observedIndexes.size

        profileRepository.setBuyerProfile(
            profileWith(mapOf(dateKey to "order_1", "20261120" to "order_2"))
        )
        advanceUntilIdle()

        assertTrue(
            orderRepository.observedIndexes.size > before,
            "a changed index has to open a new subscription; capturing it once does not"
        )
        assertEquals(
            mapOf(dateKey to "order_1", "20261120" to "order_2"),
            orderRepository.observedIndexes.last(),
            "and the new subscription asks for both orders"
        )
    }

    @Test
    fun `an index that has not changed does not re-subscribe`() = runTest {
        profileRepository.setBuyerProfile(profileWith(mapOf(dateKey to "order_1")))
        orderRepository.setOrders(listOf(order("order_1")))
        val viewModel = createViewModel()
        viewModel.loadOrderHistory()
        advanceUntilIdle()

        val before = orderRepository.observedIndexes.size

        // A profile write that leaves the index alone -- a new display name, a
        // favourite, a pickup time. The orders must not be torn down and refetched
        // every time the buyer edits something unrelated.
        profileRepository.setBuyerProfile(
            profileWith(mapOf(dateKey to "order_1")).copy(displayName = "Annabel M.")
        )
        advanceUntilIdle()

        assertEquals(
            before,
            orderRepository.observedIndexes.size,
            "the index is what the subscription is keyed on, not the profile"
        )
    }
}
