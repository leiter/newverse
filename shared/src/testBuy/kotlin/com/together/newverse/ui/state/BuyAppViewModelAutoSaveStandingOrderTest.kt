package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
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
import com.together.newverse.ui.state.buy.basketScreenUpdateQuantity
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
import kotlin.test.assertTrue

/**
 * Where the edit was made decides whether the buyer is asked to confirm it.
 *
 * An amount dialled on the home screen or the product detail goes straight into a
 * standing, still-editable order: the buyer already said what they wanted, and walking
 * them to the basket screen to press *Bestellung aktualisieren* asked the same question
 * twice — and left the order unchanged when they did not. An edit made on the basket
 * screen keeps its button, because that screen is where a whole order is reviewed at
 * once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelAutoSaveStandingOrderTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository
    private val sellerConfig = FakeSellerConfig()

    private val pickupDate = OrderDateUtils.calculateNextPickupDate().toEpochMilliseconds()
    private val dateKey = OrderDateUtils.formatDateKey(OrderDateUtils.calculateNextPickupDate())

    private val apple = OrderedProduct(
        productId = "apple", productName = "Apfel", price = 3.04, amountCount = 1.0
    )

    private val moehren = Article(
        id = "moehren", productName = "Möhren", price = 2.80,
        unit = "kg", available = true, mode = Article.MODE_ADDED
    )

    /**
     * The catalogue entry behind the article already in the order.
     *
     * Needed in `mainScreen.articles` for every one of these tests: editing an order
     * is placing it again, so `unorderableInBasket` re-checks each basket item against
     * the catalogue and an absent one reads as withdrawn by the seller. Leaving it out
     * made the auto-save refuse with "Apfel ist nicht mehr verfügbar" — the right
     * answer to the wrong question.
     */
    private val appleArticle = Article(
        id = "apple", productName = "Apfel", price = 3.04,
        unit = "kg", available = true, mode = Article.MODE_ADDED
    )

    private val stored = Order(
        id = "order_1",
        sellerId = sellerConfig.sellerId,
        createdDate = 1_000L,
        pickUpDate = pickupDate,
        marketId = "market_1",
        message = "Bitte an der Hintertür",
        status = OrderStatus.PLACED,
        articles = listOf(apple)
    )

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        orderRepository = FakeOrderRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
        authRepository.setCurrentUserId("buyer_1")
        orderRepository.setOrders(listOf(stored))
    }

    @AfterTest
    fun tearDown() {
        orderRepository.reset()
        authRepository.reset()
        basketRepository.reset()
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

    /**
     * The app as it stands with a placed, still-editable order open: its articles in
     * the basket, the same list as the baseline, and nothing edited yet.
     *
     * [canEdit] is the one knob these tests turn — it is what the deadline resolves to,
     * and the auto-save reads it rather than recomputing the deadline itself.
     */
    private fun BuyAppViewModel.withStandingOrder(canEdit: Boolean = true) {
        // Qualified: inside a BuyAppViewModel extension, a bare `basketRepository`
        // resolves to the ViewModel's own, which is the interface and has no setter.
        this@BuyAppViewModelAutoSaveStandingOrderTest.basketRepository
            .setBasketItems(stored.articles)
        _state.update {
            it.copy(
                basketScreen = it.basketScreen.copy(
                    orderId = stored.id,
                    orderDate = dateKey,
                    pickupDate = stored.pickUpDate,
                    createdDate = stored.createdDate,
                    items = stored.articles,
                    originalOrderItems = stored.articles,
                    canEdit = canEdit,
                    hasChanges = false
                ),
                mainScreen = it.mainScreen.copy(
                    articles = listOf(appleArticle, moehren),
                    canEditOrder = canEdit
                )
            )
        }
    }

    /** What the home screen and the product detail both do: pick, dial, add. */
    private fun BuyAppViewModel.addFromHome(article: Article, quantity: Double) {
        dispatch(BuyMainScreenAction.SelectArticle(article))
        dispatch(BuyMainScreenAction.UpdateQuantity(quantity))
        dispatch(BuyMainScreenAction.AddToCart)
    }

    private suspend fun savedOrder(): Order = orderRepository
        .loadOrder(
            sellerConfig.sellerId,
            stored.id,
            "orders/${sellerConfig.sellerId}/$dateKey/${stored.id}"
        )
        .getOrThrow()

    @Test
    fun `adding from the home screen writes the standing order with no confirmation`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.withStandingOrder()

        viewModel.addFromHome(moehren, 2.0)
        advanceUntilIdle()

        val saved = savedOrder()
        assertEquals(
            listOf("apple", "moehren"),
            saved.articles.map { it.productId },
            "the added article must be in the order itself, not only in the basket"
        )
        assertEquals(2.0, saved.articles.first { it.productId == "moehren" }.amountCount)
        assertEquals(
            "Bitte an der Hintertür", saved.message,
            "an auto-save is still the ordinary update: it must not drop the rest of the order"
        )

        val basket = viewModel.state.value.basketScreen
        assertFalse(
            basket.hasPendingEdits,
            "nothing is left to confirm, so the basket screen offers no update button"
        )
        assertFalse(basket.editedOnBasketScreen)
        assertFalse(
            basket.orderSuccess,
            "no success banner on a screen the buyer is not looking at"
        )
    }

    @Test
    fun `changing an amount from the home screen writes the standing order`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.withStandingOrder()

        // The same article already in the order, re-dialled: addMainScreenToCart takes
        // the update-quantity branch rather than adding a second row.
        viewModel.addFromHome(appleArticle, 5.0)
        advanceUntilIdle()

        val saved = savedOrder()
        assertEquals(1, saved.articles.size, "re-dialling an amount must not duplicate the row")
        assertEquals(5.0, saved.articles.single().amountCount)
        assertFalse(viewModel.state.value.basketScreen.hasPendingEdits)
    }

    @Test
    fun `an edit made on the basket screen keeps its confirmation`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.withStandingOrder()

        viewModel.basketScreenUpdateQuantity("apple", 4.0)
        advanceUntilIdle()

        assertEquals(
            1.0, savedOrder().articles.single().amountCount,
            "the order must not move until the buyer confirms it"
        )
        val basket = viewModel.state.value.basketScreen
        assertTrue(basket.editedOnBasketScreen)
        assertTrue(basket.hasPendingEdits, "the update button is what saves this one")
    }

    @Test
    fun `a home add waits while an unconfirmed basket-screen edit is pending`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.withStandingOrder()

        viewModel.basketScreenUpdateQuantity("apple", 4.0)
        advanceUntilIdle()
        viewModel.addFromHome(moehren, 2.0)
        advanceUntilIdle()

        assertEquals(
            listOf("apple"), savedOrder().articles.map { it.productId },
            "auto-saving here would carry the unconfirmed basket edit along with it"
        )
        assertTrue(viewModel.state.value.basketScreen.hasPendingEdits)
    }

    @Test
    fun `emptying the basket from the home screen does not cancel the order`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.withStandingOrder()

        // Dialling the only article to zero removes it, leaving an empty basket. An
        // order with no articles is a cancelled order, which is never done on the
        // buyer's behalf from another screen.
        viewModel.addFromHome(appleArticle, 0.0)
        advanceUntilIdle()

        assertEquals(
            listOf("apple"), savedOrder().articles.map { it.productId },
            "the standing order is left intact for the basket screen's cancel flow"
        )
    }

    @Test
    fun `nothing is written once the edit deadline has passed`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.withStandingOrder(canEdit = false)

        viewModel.addFromHome(moehren, 2.0)
        advanceUntilIdle()

        assertEquals(
            listOf("apple"), savedOrder().articles.map { it.productId },
            "a locked order is not editable from any screen"
        )
    }

    @Test
    fun `a draft basket is untouched by the auto-save`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        // No order loaded: basketScreen.orderId stays null, so this is an ordinary add
        // to a draft that checkout will ask about in the usual way.
        viewModel._state.update {
            it.copy(mainScreen = it.mainScreen.copy(articles = listOf(appleArticle, moehren)))
        }

        viewModel.addFromHome(moehren, 2.0)
        advanceUntilIdle()

        assertEquals(
            listOf("apple"), savedOrder().articles.map { it.productId },
            "the stored order was never opened, so it cannot have been written to"
        )
        assertEquals(
            listOf("moehren"),
            basketRepository.observeBasket().value.map { it.productId },
            "the add still lands in the basket"
        )
    }
}
