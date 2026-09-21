package com.together.newverse.ui.state

import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerConfig
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.ui.state.buy.basketScreenUpdateOrder
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A buyer changing a placed order changes its items — nothing else. The update used
 * to store a rebuilt order, which dropped the message, the market and the seller's
 * "hidden" flag, and stored the order as a draft.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelUpdateOrderTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var authRepository: FakeAuthRepository
    private val sellerConfig = FakeSellerConfig()

    private val pickupDate = OrderDateUtils.calculateNextPickupDate().toEpochMilliseconds()
    private val dateKey = OrderDateUtils.formatDateKey(OrderDateUtils.calculateNextPickupDate())

    private val stored = Order(
        id = "order_1",
        sellerId = sellerConfig.sellerId,
        createdDate = 1_000L,
        pickUpDate = pickupDate,
        marketId = "market_1",
        message = "Bitte an der Hintertür",
        hiddenBySeller = true,
        status = OrderStatus.PLACED,
        articles = listOf(OrderedProduct(productId = "apple", productName = "Apfel", price = 3.04, amountCount = 1.0))
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        orderRepository = FakeOrderRepository()
        authRepository = FakeAuthRepository()
        authRepository.setCurrentUserId("buyer_1")
        orderRepository.setOrders(listOf(stored))
    }

    @AfterTest
    fun tearDown() {
        orderRepository.reset()
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = BuyAppViewModel(
        articleRepository = FakeArticleRepository(),
        orderRepository = orderRepository,
        profileRepository = FakeProfileRepository(),
        authRepository = authRepository,
        basketRepository = FakeBasketRepository(),
        sellerConfig = sellerConfig
    )

    private val changedItems = listOf(
        OrderedProduct(productId = "apple", productName = "Apfel", price = 3.04, amountCount = 2.0),
        OrderedProduct(productId = "sweet", productName = "Süßkartoffel", price = 4.62, amountCount = 1.0)
    )

    /** The basket as it is while the buyer edits the stored order. */
    private fun BuyAppViewModel.editStoredOrder(items: List<OrderedProduct>) {
        _state.update {
            it.copy(
                basketScreen = it.basketScreen.copy(
                    orderId = stored.id,
                    orderDate = dateKey,
                    pickupDate = stored.pickUpDate,
                    createdDate = stored.createdDate,
                    items = items,
                    isEditMode = true
                )
            )
        }
    }

    private suspend fun savedOrder(): Order =
        orderRepository.loadOrder(sellerConfig.sellerId, stored.id, "orders/${sellerConfig.sellerId}/$dateKey/${stored.id}")
            .getOrThrow()

    @Test
    fun `editing changes the items and keeps everything else`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.editStoredOrder(changedItems)

        viewModel.basketScreenUpdateOrder()
        advanceUntilIdle()

        val saved = savedOrder()
        assertEquals(changedItems, saved.articles)
        assertEquals("Bitte an der Hintertür", saved.message)
        assertEquals("market_1", saved.marketId)
        assertTrue(saved.hiddenBySeller, "the seller's hide must survive a buyer edit")
        assertEquals(OrderStatus.PLACED, saved.status)
        assertNull(viewModel.state.value.basketScreen.orderError)
    }

    @Test
    fun `an order that cannot be loaded is not overwritten`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.editStoredOrder(changedItems)
        orderRepository.setOrders(emptyList())   // gone, or not readable

        viewModel.basketScreenUpdateOrder()
        advanceUntilIdle()

        assertEquals("Bestellung konnte nicht geladen werden", viewModel.state.value.basketScreen.orderError)
    }
}
