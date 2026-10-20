package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
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
 * Where the buyer collects is asked, not inferred.
 *
 * It used to be a side effect of opening the slot picker: the market the basket
 * happened to settle on became the market the access request asked for, and so the
 * market the seller approved. See
 * `doc/guides/market-pricing-and-access-design.md` §2a.
 */
class BuyAppViewModelMarketChoiceTest {

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

    private fun startSession(defaultMarket: String = "") {
        profileRepository.setSellerProfile(SellerProfile(sellerId = "demo_seller_id"))
        profileRepository.setBuyerProfile(
            BuyerProfile(
                id = "buyer_1",
                displayName = "Test",
                emailAddress = "t@example.com",
                defaultMarket = defaultMarket
            )
        )
        authRepository.setCurrentUserId("buyer_1")
    }

    /** Receiver needed: `advanceUntilIdle` is a TestScope extension. */
    private suspend fun TestScope.ready(): BuyAppViewModel {
        val viewModel = createViewModel()
        advanceUntilIdle()
        articleRepository.emitArticle(moehren)
        advanceUntilIdle()
        return viewModel
    }

    // ===== when the question is worth asking =====

    @Test
    fun `two markets and no stated default means ask`() = runTest {
        seedTwoMarkets()
        startSession()

        val viewModel = ready()

        assertTrue(viewModel.state.value.needsMarketChoice)
    }

    @Test
    fun `one market is not a choice`() = runTest {
        marketRepository.seed("demo_seller_id", Market(id = "m1", name = "Kreuzberg", dayIndex = 3))
        startSession()

        val viewModel = ready()

        // contextualMarketId already resolves to "the only market there is".
        assertFalse(viewModel.state.value.needsMarketChoice)
        assertEquals("m1", viewModel.state.value.contextualMarketId)
    }

    @Test
    fun `a stated default stops the asking`() = runTest {
        seedTwoMarkets()
        startSession(defaultMarket = "m2")

        val viewModel = ready()

        assertFalse(viewModel.state.value.needsMarketChoice)
        assertEquals("m2", viewModel.state.value.chosenDefaultMarket?.id)
    }

    @Test
    fun `a default the seller has since narrowed away does not count as answered`() = runTest {
        seedTwoMarkets()
        startSession(defaultMarket = "m2")
        // The seller scopes this buyer to Kreuzberg only, after they chose Neukölln.
        profileRepository.setOwnAllowedMarkets(listOf("m1"))

        val viewModel = ready()

        assertEquals(null, viewModel.state.value.chosenDefaultMarket)
        // With one eligible market left there is nothing to ask, and the contextual
        // market falls through to the only one they may use.
        assertFalse(viewModel.state.value.needsMarketChoice)
        assertEquals("m1", viewModel.state.value.contextualMarketId)
    }

    @Test
    fun `dismissing stops the asking for this session only`() = runTest {
        seedTwoMarkets()
        startSession()

        val viewModel = ready()
        assertTrue(viewModel.state.value.needsMarketChoice)

        viewModel.dispatch(BuySellerAction.DismissMarketPicker)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.needsMarketChoice)
        assertFalse(viewModel.state.value.showMarketPicker)
        // Nothing persisted, so a fresh launch asks again.
        assertEquals("", profileRepository.getBuyerProfile().getOrNull()?.defaultMarket)
    }

    // ===== answering =====

    @Test
    fun `choosing a market persists it as the buyer's default`() = runTest {
        seedTwoMarkets()
        startSession()

        val viewModel = ready()
        viewModel.dispatch(BuySellerAction.ChooseDefaultMarket("m2"))
        advanceUntilIdle()

        assertEquals("m2", profileRepository.getBuyerProfile().getOrNull()?.defaultMarket)
        assertEquals("m2", viewModel.state.value.chosenDefaultMarket?.id)
        assertFalse(viewModel.state.value.needsMarketChoice)
        assertFalse(viewModel.state.value.showMarketPicker)
    }

    @Test
    fun `the chosen market becomes the contextual one, so the catalogue prices there`() = runTest {
        seedTwoMarkets()
        startSession()

        val viewModel = ready()
        viewModel.dispatch(BuySellerAction.ChooseDefaultMarket("m2"))
        advanceUntilIdle()

        assertEquals("m2", viewModel.state.value.contextualMarketId)
        assertEquals(3.05, viewModel.state.value.priceOf(moehren).gross)
    }

    @Test
    fun `choosing reprices a basket that was built before the answer`() = runTest {
        seedTwoMarkets()
        startSession()
        basketRepository.setBasketItems(
            listOf(
                OrderedProduct(
                    productId = "a1", productName = "Möhren", unit = "kg",
                    price = 2.80, basePrice = 2.80, amountCount = 2.0, piecesCount = -1
                )
            )
        )

        val viewModel = ready()
        viewModel.dispatch(BuySellerAction.ChooseDefaultMarket("m2"))
        advanceUntilIdle()

        val item = basketRepository.observeBasket().value.single()
        assertEquals(3.05, item.price)
        assertEquals(2.80, item.basePrice)
        assertEquals(PriceCause.STALL_FEE, item.priceComponents.single().cause)
    }

    @Test
    fun `a slot the buyer already picked outranks the stated default`() = runTest {
        seedTwoMarkets()
        startSession()
        basketRepository.setBasketItems(
            listOf(
                OrderedProduct(
                    productId = "a1", productName = "Möhren", unit = "kg",
                    price = 2.80, basePrice = 2.80, amountCount = 2.0, piecesCount = -1
                )
            )
        )

        val viewModel = ready()

        // Settle the basket on Kreuzberg, then state Neukölln as the default.
        val kreuzberg = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m1" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(kreuzberg.date))
        advanceUntilIdle()

        viewModel.dispatch(BuySellerAction.ChooseDefaultMarket("m2"))
        advanceUntilIdle()

        // The default is recorded, but the basket keeps the slot's market and its
        // prices: a slot the buyer chose is the stronger claim.
        assertEquals("m2", viewModel.state.value.chosenDefaultMarket?.id)
        assertEquals("m1", viewModel.state.value.contextualMarketId)
        assertEquals(2.80, basketRepository.observeBasket().value.single().price)
    }

    @Test
    fun `a market outside the buyer's scope is refused`() = runTest {
        seedTwoMarkets()
        startSession()
        profileRepository.setOwnAllowedMarkets(listOf("m1"))

        val viewModel = ready()
        viewModel.dispatch(BuySellerAction.ChooseDefaultMarket("m2"))
        advanceUntilIdle()

        assertEquals("", profileRepository.getBuyerProfile().getOrNull()?.defaultMarket)
        assertEquals(null, viewModel.state.value.chosenDefaultMarket)
    }

    // ===== the provisional-pricing line =====

    @Test
    fun `a market with conditions is named for the buyer`() = runTest {
        seedTwoMarkets()
        startSession(defaultMarket = "m2")

        val viewModel = ready()

        assertEquals("Neukölln", viewModel.state.value.adjustedPricingMarketName)
    }

    @Test
    fun `a market without conditions is not named, because nothing changed`() = runTest {
        seedTwoMarkets()
        startSession(defaultMarket = "m1")

        val viewModel = ready()

        assertEquals("", viewModel.state.value.adjustedPricingMarketName)
    }
}
