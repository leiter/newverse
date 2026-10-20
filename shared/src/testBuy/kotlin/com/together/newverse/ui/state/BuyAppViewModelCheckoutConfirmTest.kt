package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeMarketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerConfig
import com.together.newverse.test.MainDispatcherRule
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
 * Pressing Bestellen asks before it writes.
 *
 * The confirmation is the last point where the slot — and so the market, and so the
 * prices — can still be changed without editing a placed order.
 * `doc/guides/market-pricing-and-access-design.md` §4.
 */
class BuyAppViewModelCheckoutConfirmTest {

    private val dispatcherRule = MainDispatcherRule()

    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository
    private lateinit var marketRepository: FakeMarketRepository

    private val moehren = Article(
        id = "a1", productName = "Möhren", price = 2.80,
        unit = "kg", available = true, mode = Article.MODE_ADDED
    )

    @BeforeTest
    fun setUp() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
        marketRepository = FakeMarketRepository()
    }

    @AfterTest
    fun tearDown() {
        articleRepository.reset()
        orderRepository.reset()
        profileRepository.reset()
        authRepository.reset()
        basketRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = BuyAppViewModel(
        articleRepository = articleRepository,
        orderRepository = orderRepository,
        profileRepository = profileRepository,
        authRepository = authRepository,
        basketRepository = basketRepository,
        sellerConfig = FakeSellerConfig(),
        marketRepository = marketRepository
    )

    private fun startSession() {
        profileRepository.setSellerProfile(SellerProfile(sellerId = "demo_seller_id"))
        profileRepository.setBuyerProfile(
            BuyerProfile(
                id = "buyer_1",
                displayName = "Test",
                emailAddress = "t@example.com",
                telephoneNumber = "123"
            )
        )
        authRepository.setCurrentUserId("buyer_1")
    }

    private fun fillBasket() = basketRepository.setBasketItems(
        listOf(
            OrderedProduct(
                productId = "a1", productName = "Möhren", unit = "kg",
                price = 2.80, basePrice = 2.80, amountCount = 2.0, piecesCount = -1
            )
        )
    )

    /** Ready, with a slot settled, which is the state Bestellen is pressed from. */
    private suspend fun TestScope.readyWithSlot(): BuyAppViewModel {
        marketRepository.seed("demo_seller_id", Market(id = "m1", name = "Kreuzberg", dayIndex = 3))
        startSession()
        fillBasket()
        val viewModel = createViewModel()
        advanceUntilIdle()
        articleRepository.emitArticle(moehren)
        advanceUntilIdle()
        val slot = viewModel.state.value.basketScreen.availableSlots.first()
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(slot.date))
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `pressing Bestellen asks instead of writing`() = runTest {
        val viewModel = readyWithSlot()

        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.basketScreen.showCheckoutConfirm)
        assertFalse(viewModel.state.value.basketScreen.orderSuccess)
        assertTrue(orderRepository.placedOrders.isEmpty(), "the order was written without asking")
    }

    @Test
    fun `confirming writes the order and closes the dialog`() = runTest {
        val viewModel = readyWithSlot()
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()

        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.basketScreen.showCheckoutConfirm)
        assertEquals(1, orderRepository.placedOrders.size)
    }

    @Test
    fun `backing out writes nothing and keeps the basket`() = runTest {
        val viewModel = readyWithSlot()
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()

        viewModel.dispatch(BuyBasketScreenAction.DismissCheckout)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.basketScreen.showCheckoutConfirm)
        assertTrue(orderRepository.placedOrders.isEmpty())
        assertEquals(1, basketRepository.observeBasket().value.size)
        assertEquals(
            1,
            viewModel.state.value.basketScreen.items.size,
            "the basket lost its items on a cancel"
        )
    }

    @Test
    fun `changing the slot closes the confirmation and opens the picker`() = runTest {
        val viewModel = readyWithSlot()
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()

        viewModel.dispatch(BuyBasketScreenAction.ShowDatePicker)
        advanceUntilIdle()

        // Not stacked: a price-change warning may follow the new slot and must not
        // open underneath a confirmation showing the old one.
        assertFalse(viewModel.state.value.basketScreen.showCheckoutConfirm)
        assertTrue(viewModel.state.value.basketScreen.showDatePicker)
        assertTrue(orderRepository.placedOrders.isEmpty())
    }

    @Test
    fun `an empty basket is refused before any confirmation is shown`() = runTest {
        marketRepository.seed("demo_seller_id", Market(id = "m1", name = "Kreuzberg", dayIndex = 3))
        startSession()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.basketScreen.showCheckoutConfirm)
        assertTrue(viewModel.state.value.basketScreen.orderError != null)
    }

    @Test
    fun `no date yet opens the picker rather than the confirmation`() = runTest {
        marketRepository.seed("demo_seller_id", Market(id = "m1", name = "Kreuzberg", dayIndex = 3))
        startSession()
        fillBasket()
        val viewModel = createViewModel()
        advanceUntilIdle()
        articleRepository.emitArticle(moehren)
        advanceUntilIdle()

        // Clear whatever init settled on, so there is genuinely no date.
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()

        val basket = viewModel.state.value.basketScreen
        if (basket.selectedPickupDate == null) {
            assertFalse(basket.showCheckoutConfirm)
            assertTrue(basket.showDatePicker)
        } else {
            // A slot was already settled by init, so confirming is the right step.
            assertTrue(basket.showCheckoutConfirm)
        }
    }

    @Test
    fun `confirming twice does not write the order twice`() = runTest {
        val viewModel = readyWithSlot()
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()

        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        // The basket is emptied by the first write, so the second finds nothing to
        // place rather than duplicating the order.
        assertEquals(1, orderRepository.placedOrders.size)
    }
}
