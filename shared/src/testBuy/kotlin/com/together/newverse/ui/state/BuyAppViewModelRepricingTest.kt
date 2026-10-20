package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.PriceCause
import com.together.newverse.domain.model.PriceCondition
import com.together.newverse.domain.model.PriceEffect
import com.together.newverse.domain.model.PriceScope
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeMarketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerConfig
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Accepting a market's prices has to move the money, not just the market.
 *
 * The warning dialog was shipped before the repricing was, so for a while the app
 * told the buyer that four articles would change price and then charged them the
 * old total. These pin the two halves together.
 */
class BuyAppViewModelRepricingTest {

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

    /** Kreuzberg charges nothing extra; Neukölln adds 25 cents a unit. */
    private fun seedTwoMarkets() = marketRepository.seed(
        "demo_seller_id",
        Market(id = "m1", name = "Kreuzberg", dayIndex = 3),
        Market(
            id = "m2", name = "Neukölln", dayIndex = 5, pricingRevision = 4L,
            priceConditions = listOf(
                PriceCondition(
                    PriceCause.STALL_FEE, PriceScope.AllArticles,
                    PriceEffect.Surcharge(0.25), "Standgebühr"
                )
            )
        )
    )

    private fun startSession() {
        profileRepository.setSellerProfile(SellerProfile(sellerId = "demo_seller_id"))
        authRepository.setCurrentUserId("buyer_1")
    }

    private fun basketWithMoehren() = basketRepository.setBasketItems(
        listOf(
            OrderedProduct(
                productId = "a1", productName = "Möhren", unit = "kg",
                price = 2.80, basePrice = 2.80, amountCount = 2.0, piecesCount = -1
            )
        )
    )

    @Test
    fun `accepting the switch reprices the basket`() = runTest {
        seedTwoMarkets()
        startSession()
        basketWithMoehren()

        val viewModel = createViewModel()
        advanceUntilIdle()
        articleRepository.emitArticle(moehren)
        advanceUntilIdle()

        // Settle on the market that charges nothing, then move to the one that does.
        val kreuzberg = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m1" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(kreuzberg.date))
        advanceUntilIdle()

        val neukoelln = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m2" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(neukoelln.date))
        advanceUntilIdle()

        // Asked, not applied.
        val pending = viewModel.state.value.basketScreen.pendingSlotPriceChange
        assertTrue(pending != null, "the buyer was not asked before the prices moved")
        assertEquals("m1", viewModel.state.value.basketScreen.selectedMarketId)
        assertEquals(2.80, basketRepository.observeBasket().value.single().price)

        viewModel.dispatch(BuyBasketScreenAction.ConfirmSlotPriceChange)
        advanceUntilIdle()

        assertEquals("m2", viewModel.state.value.basketScreen.selectedMarketId)
        assertEquals(null, viewModel.state.value.basketScreen.pendingSlotPriceChange)
        // The point of the whole exercise: the accepted surcharge is in the item.
        val repriced = basketRepository.observeBasket().value.single()
        assertEquals(3.05, repriced.price)
        assertEquals(2.80, repriced.basePrice)
        assertEquals(PriceCause.STALL_FEE, repriced.priceComponents.single().cause)
        assertEquals("Standgebühr", repriced.priceComponents.single().note)
    }

    @Test
    fun `declining the switch leaves both the market and the prices alone`() = runTest {
        seedTwoMarkets()
        startSession()
        basketWithMoehren()

        val viewModel = createViewModel()
        advanceUntilIdle()
        articleRepository.emitArticle(moehren)
        advanceUntilIdle()

        val kreuzberg = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m1" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(kreuzberg.date))
        advanceUntilIdle()
        val neukoelln = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m2" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(neukoelln.date))
        advanceUntilIdle()

        viewModel.dispatch(BuyBasketScreenAction.DismissSlotPriceChange)
        advanceUntilIdle()

        assertEquals("m1", viewModel.state.value.basketScreen.selectedMarketId)
        assertEquals(2.80, basketRepository.observeBasket().value.single().price)
        assertTrue(basketRepository.observeBasket().value.single().priceComponents.isEmpty())
    }

    @Test
    fun `a market with no conditions reprices without asking`() = runTest {
        marketRepository.seed(
            "demo_seller_id",
            Market(id = "m1", name = "Kreuzberg", dayIndex = 3),
            Market(id = "m2", name = "Neukölln", dayIndex = 5)
        )
        startSession()
        basketWithMoehren()

        val viewModel = createViewModel()
        advanceUntilIdle()
        articleRepository.emitArticle(moehren)
        advanceUntilIdle()

        val neukoelln = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m2" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(neukoelln.date))
        advanceUntilIdle()

        assertEquals("m2", viewModel.state.value.basketScreen.selectedMarketId)
        assertEquals(null, viewModel.state.value.basketScreen.pendingSlotPriceChange)
        // Same price either side, so nothing to warn about and nothing to change.
        assertEquals(2.80, basketRepository.observeBasket().value.single().price)
    }

    // Onboarding: the buyer has not settled anywhere yet, so there is no total for a
    // different market's prices to be a change *from*.
    @Test
    fun `the first pick applies the market's prices without asking`() = runTest {
        seedTwoMarkets()
        startSession()
        basketWithMoehren()

        val viewModel = createViewModel()
        advanceUntilIdle()
        articleRepository.emitArticle(moehren)
        advanceUntilIdle()

        // Straight to the market that charges more, with nothing selected before it.
        assertEquals("", viewModel.state.value.basketScreen.selectedMarketId)
        val neukoelln = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m2" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(neukoelln.date))
        advanceUntilIdle()

        assertEquals(
            null,
            viewModel.state.value.basketScreen.pendingSlotPriceChange,
            "the buyer was warned about a change from a market they had not chosen"
        )
        assertEquals("m2", viewModel.state.value.basketScreen.selectedMarketId)
        // Silent, but not skipped: the prices still move.
        assertEquals(3.05, basketRepository.observeBasket().value.single().price)
    }

    // ...but the second one is a change, and still has to be asked about.
    @Test
    fun `a later pick between two real markets still asks`() = runTest {
        seedTwoMarkets()
        startSession()
        basketWithMoehren()

        val viewModel = createViewModel()
        advanceUntilIdle()
        articleRepository.emitArticle(moehren)
        advanceUntilIdle()

        val kreuzberg = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m1" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(kreuzberg.date))
        advanceUntilIdle()
        val neukoelln = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m2" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(neukoelln.date))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.basketScreen.pendingSlotPriceChange != null)
    }

    @Test
    fun `the revision to snapshot comes from the market the basket settled on`() = runTest {
        seedTwoMarkets()
        startSession()
        basketWithMoehren()

        val viewModel = createViewModel()
        advanceUntilIdle()
        articleRepository.emitArticle(moehren)
        advanceUntilIdle()

        assertEquals(0L, viewModel.state.value.pricingRevisionOf("m1"))
        assertEquals(4L, viewModel.state.value.pricingRevisionOf("m2"))
        assertEquals(0L, viewModel.state.value.pricingRevisionOf("nonexistent"))
    }
}
