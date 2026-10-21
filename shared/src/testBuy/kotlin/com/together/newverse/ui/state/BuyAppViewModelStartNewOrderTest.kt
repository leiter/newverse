package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.DeliveryAddress
import com.together.newverse.domain.model.Fulfilment
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
import com.together.newverse.ui.state.buy.handleMainScreenAction
import com.together.newverse.ui.state.buy.startNewOrder
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A buyer whose edit deadline has passed is offered a new order. Taking that offer
 * used to clear the basket and the main screen's lock but leave `basketScreen` bound
 * to the locked order: the basket screen kept drawing that order's card, whose only
 * action is Update, which the deadline makes impossible. The draft could be built and
 * never sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelStartNewOrderTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository
    private val sellerConfig = FakeSellerConfig()

    private val pickupDate = OrderDateUtils.calculateNextPickupDate().toEpochMilliseconds()
    private val dateKey = OrderDateUtils.formatDateKey(OrderDateUtils.calculateNextPickupDate())

    private val apple = Article(
        id = "apple",
        productName = "Apfel",
        unit = "kg",
        price = 3.04,
        available = true
    )

    private val pear = Article(
        id = "pear",
        productName = "Birne",
        unit = "kg",
        price = 2.50,
        available = true
    )

    /** The order whose deadline has passed: owed, unchangeable, still the seller's to fulfil. */
    private val locked = Order(
        id = "order_locked",
        sellerId = sellerConfig.sellerId,
        createdDate = 1_000L,
        pickUpDate = pickupDate,
        marketId = "market_1",
        status = OrderStatus.LOCKED,
        articles = listOf(
            OrderedProduct(productId = "apple", productName = "Apfel", price = 3.04, amountCount = 1.0)
        )
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        orderRepository = FakeOrderRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
        authRepository.setCurrentUserId("buyer_1")
        orderRepository.setOrders(listOf(locked))
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
        basketRepository = basketRepository,
        sellerConfig = sellerConfig
    )

    /** The state the buyer is in when the snackbar offers a new order. */
    private fun BuyAppViewModel.lookingAtLockedOrder() {
        _state.update {
            it.copy(
                mainScreen = it.mainScreen.copy(
                    canEditOrder = false,
                    showNewOrderSnackbar = true,
                    articles = listOf(apple, pear)
                ),
                basket = it.basket.copy(
                    currentOrderId = locked.id,
                    currentOrderDate = dateKey
                ),
                basketScreen = it.basketScreen.copy(
                    orderId = locked.id,
                    orderDate = dateKey,
                    pickupDate = locked.pickUpDate,
                    createdDate = locked.createdDate,
                    items = locked.articles,
                    originalOrderItems = locked.articles,
                    total = 3.04,
                    canEdit = false,
                    isEditMode = false,
                    orderError = "Bearbeitungsfrist abgelaufen (Dienstag 23:59)",
                    fulfilment = Fulfilment.DELIVERY,
                    deliveryAddress = DeliveryAddress(street = "Hauptstraße", houseNumber = "7")
                )
            )
        }
    }

    @Test
    fun `starting a new order unbinds the basket screen from the locked order`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.lookingAtLockedOrder()

        viewModel.startNewOrder()
        advanceUntilIdle()

        val basket = viewModel.state.value.basketScreen
        // orderId is the switch the basket screen keys on: null is what makes it draw
        // the pickup-date selector and the Bestellen button instead of the order card.
        assertNull(basket.orderId, "the new draft must not be bound to the locked order")
        assertNull(basket.orderDate)
        assertNull(basket.pickupDate)
        assertNull(basket.createdDate)
        assertNull(basket.orderError, "the stale deadline message must not survive")
        assertTrue(basket.items.isEmpty(), "the draft starts empty")
        assertTrue(basket.originalOrderItems.isEmpty())
        assertEquals(0.0, basket.total)
        assertFalse(basket.isEditMode)
        assertFalse(basket.hasChanges)
        assertNull(basket.selectedPickupDate, "the buyer picks the slot for the new order")
    }

    @Test
    fun `starting a new order clears the main screen lock and the basket pointer`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.lookingAtLockedOrder()

        viewModel.startNewOrder()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.mainScreen.canEditOrder, "the new draft is editable")
        assertFalse(state.mainScreen.showNewOrderSnackbar)
        assertNull(state.basket.currentOrderId)
        assertNull(state.basket.currentOrderDate)
    }

    @Test
    fun `the new draft has a slot to be placed against`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.lookingAtLockedOrder()

        viewModel.startNewOrder()
        advanceUntilIdle()

        val basket = viewModel.state.value.basketScreen
        assertTrue(
            basket.availablePickupDates.isNotEmpty(),
            "a new order after the deadline is placed against the next open day"
        )
        basket.availablePickupDates.forEach { date ->
            assertTrue(
                OrderDateUtils.isPickupDateValid(kotlin.time.Instant.fromEpochMilliseconds(date)),
                "every offered slot must still be orderable"
            )
        }
    }

    @Test
    fun `the fulfilment choice survives into the new draft`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.lookingAtLockedOrder()

        viewModel.startNewOrder()
        advanceUntilIdle()

        // Seeded from the profile once at startup; rebuilding the state from scratch
        // would silently drop a delivery buyer back to pickup.
        val basket = viewModel.state.value.basketScreen
        assertEquals(Fulfilment.DELIVERY, basket.fulfilment)
        assertEquals("Hauptstraße", basket.deliveryAddress.street)
    }

    @Test
    fun `the locked order is left alone`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.lookingAtLockedOrder()

        viewModel.startNewOrder()
        advanceUntilIdle()

        val stored = orderRepository.loadOrder(
            sellerConfig.sellerId,
            locked.id,
            "orders/${sellerConfig.sellerId}/$dateKey/${locked.id}"
        ).getOrNull()
        assertNotNull(stored, "starting a new order must not cancel the order still owed")
        assertEquals(locked.articles, stored.articles)
        assertEquals(OrderStatus.LOCKED, stored.status)
    }

    @Test
    fun `the amount the buyer dialled is kept while the order is locked`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.lookingAtLockedOrder()

        // The quantity is scratch state on the main screen and never reaches the
        // basket, so the lock has nothing to protect there. Swallowing it lost the
        // amount before the buyer could carry it anywhere.
        viewModel.handleMainScreenAction(BuyMainScreenAction.SelectArticle(pear))
        viewModel.handleMainScreenAction(BuyMainScreenAction.UpdateQuantity(2.0))
        advanceUntilIdle()

        assertEquals(2.0, viewModel.state.value.mainScreen.selectedQuantity)
    }

    @Test
    fun `an add refused by the lock is carried into the new order`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.lookingAtLockedOrder()

        viewModel.handleMainScreenAction(BuyMainScreenAction.SelectArticle(pear))
        viewModel.handleMainScreenAction(BuyMainScreenAction.UpdateQuantity(2.0))
        viewModel.handleMainScreenAction(BuyMainScreenAction.AddToCart)
        advanceUntilIdle()

        // Refused, but remembered, and the buyer is offered a new order.
        assertTrue(viewModel.state.value.mainScreen.showNewOrderSnackbar)
        assertTrue(basketRepository.addedItems.isEmpty(), "the locked order must not change")
        assertEquals("pear", viewModel.state.value.mainScreen.pendingAdd?.articleId)

        viewModel.handleMainScreenAction(BuyMainScreenAction.StartNewOrder)
        advanceUntilIdle()

        val items = basketRepository.observeBasket().value
        assertEquals(1, items.size, "the article the buyer picked must survive the reset")
        val carried = items.single()
        assertEquals("pear", carried.productId)
        assertEquals(2.0, carried.amountCount)
        assertEquals(2.50, carried.price, "priced like any other add")
        assertNull(viewModel.state.value.mainScreen.pendingAdd, "applied, so no longer pending")
    }

    @Test
    fun `declining the new order drops the add with it`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.lookingAtLockedOrder()

        viewModel.handleMainScreenAction(BuyMainScreenAction.SelectArticle(pear))
        viewModel.handleMainScreenAction(BuyMainScreenAction.UpdateQuantity(2.0))
        viewModel.handleMainScreenAction(BuyMainScreenAction.AddToCart)
        advanceUntilIdle()
        viewModel.handleMainScreenAction(BuyMainScreenAction.DismissNewOrderSnackbar)
        advanceUntilIdle()

        assertNull(viewModel.state.value.mainScreen.pendingAdd)
        assertFalse(viewModel.state.value.mainScreen.showNewOrderSnackbar)

        // Starting a new order later is starting it empty, not replaying a tap from
        // an offer the buyer let go.
        viewModel.handleMainScreenAction(BuyMainScreenAction.StartNewOrder)
        advanceUntilIdle()
        assertTrue(basketRepository.observeBasket().value.isEmpty())
    }

    @Test
    fun `an article withdrawn in the meantime is not carried over`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.lookingAtLockedOrder()

        viewModel.handleMainScreenAction(BuyMainScreenAction.SelectArticle(pear))
        viewModel.handleMainScreenAction(BuyMainScreenAction.UpdateQuantity(2.0))
        viewModel.handleMainScreenAction(BuyMainScreenAction.AddToCart)
        advanceUntilIdle()

        // The seller sells out or withdraws it between the refusal and the new order.
        viewModel._state.update {
            it.copy(mainScreen = it.mainScreen.copy(articles = listOf(apple, pear.copy(available = false))))
        }

        viewModel.handleMainScreenAction(BuyMainScreenAction.StartNewOrder)
        advanceUntilIdle()

        assertTrue(
            basketRepository.observeBasket().value.isEmpty(),
            "an order that cannot be placed is worse than one item short"
        )
        assertNull(viewModel.state.value.mainScreen.pendingAdd)
    }
}
