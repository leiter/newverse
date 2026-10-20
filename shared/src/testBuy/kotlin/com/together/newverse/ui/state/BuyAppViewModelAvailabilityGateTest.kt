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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An article the seller has withdrawn cannot be ordered.
 *
 * `Article.available` used to filter the catalogue and nothing else, so a basket
 * that outlived the withdrawal — a persisted draft, a reorder weeks later — could
 * still be placed. `doc/guides/stock-driven-availability-design.md` § "The gate at placement".
 */
class BuyAppViewModelAvailabilityGateTest {

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
    private val spargel = Article(
        id = "a2", productName = "Spargel", price = 9.90,
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

    private fun item(articleId: String, name: String, price: Double) = OrderedProduct(
        productId = articleId, productName = name, unit = "kg",
        price = price, basePrice = price, amountCount = 2.0, piecesCount = -1
    )

    /** A basket with a slot settled, holding whichever items the test puts there. */
    private suspend fun TestScope.readyWith(
        basket: List<OrderedProduct>,
        catalog: List<Article>
    ): BuyAppViewModel {
        marketRepository.seed("demo_seller_id", Market(id = "m1", name = "Kreuzberg", dayIndex = 3))
        startSession()
        basketRepository.setBasketItems(basket)
        val viewModel = createViewModel()
        advanceUntilIdle()
        catalog.forEach { articleRepository.emitArticle(it) }
        advanceUntilIdle()
        val slot = viewModel.state.value.basketScreen.availableSlots.first()
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(slot.date))
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `an order whose article is still available is written`() = runTest {
        val viewModel = readyWith(
            basket = listOf(item("a1", "Möhren", 2.80)),
            catalog = listOf(moehren)
        )

        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        assertEquals(1, orderRepository.placedOrders.size)
    }

    @Test
    fun `an order holding a withdrawn article is refused`() = runTest {
        val viewModel = readyWith(
            basket = listOf(item("a2", "Spargel", 9.90)),
            // The seller withdrew it after it went into the basket.
            catalog = listOf(moehren, spargel.copy(available = false, mode = Article.MODE_CHANGED))
        )

        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        assertTrue(orderRepository.placedOrders.isEmpty(), "an unfillable order was written")
        val error = viewModel.state.value.basketScreen.orderError
        assertNotNull(error)
        assertTrue(error.contains("Spargel"), "the message does not name the article: $error")
    }

    @Test
    fun `the basket keeps its items so the buyer can fix it`() = runTest {
        val viewModel = readyWith(
            basket = listOf(item("a1", "Möhren", 2.80), item("a2", "Spargel", 9.90)),
            catalog = listOf(moehren, spargel.copy(available = false, mode = Article.MODE_CHANGED))
        )

        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.basketScreen.items.size)
        assertEquals(2, basketRepository.observeBasket().value.size)
    }

    @Test
    fun `removing the withdrawn article lets the order through`() = runTest {
        val viewModel = readyWith(
            basket = listOf(item("a1", "Möhren", 2.80), item("a2", "Spargel", 9.90)),
            catalog = listOf(moehren, spargel.copy(available = false, mode = Article.MODE_CHANGED))
        )
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()
        assertTrue(orderRepository.placedOrders.isEmpty())

        viewModel.dispatch(BuyBasketScreenAction.RemoveItem("a2"))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        assertEquals(1, orderRepository.placedOrders.size)
    }

    @Test
    fun `a basket is not refused while the catalogue is still loading`() = runTest {
        // No articles emitted at all: "nothing available" and "nothing loaded" look
        // the same from here, and refusing on a slow network is the worse failure.
        marketRepository.seed("demo_seller_id", Market(id = "m1", name = "Kreuzberg", dayIndex = 3))
        startSession()
        basketRepository.setBasketItems(listOf(item("a1", "Möhren", 2.80)))
        val viewModel = createViewModel()
        advanceUntilIdle()
        val slot = viewModel.state.value.basketScreen.availableSlots.first()
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(slot.date))
        advanceUntilIdle()

        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        assertEquals(1, orderRepository.placedOrders.size)
    }
}
