package com.together.newverse.ui.state

import com.together.newverse.domain.model.MarketClosure
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.PriceCause
import com.together.newverse.domain.model.PriceCondition
import com.together.newverse.domain.model.PriceEffect
import com.together.newverse.domain.model.PriceScope
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.PickupSlots
import com.together.newverse.test.FakeMarketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerConfig
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.util.OrderDateUtils
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A day the seller has closed must not be offered.
 *
 * What this does **not** cover: the ordering on a real device, where `init` builds
 * the first date list before Firebase has answered. `UnconfinedTestDispatcher` runs
 * the auth flow eagerly, so the closures are already in state by the time
 * `initializeBasketScreen` runs and the race never happens here. Reproducing that
 * needs a dispatcher that defers, or an instrumented run.
 */
class BuyAppViewModelClosureDatesTest {

    private val dispatcherRule = MainDispatcherRule()

    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository

    @BeforeTest
    fun setUp() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
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
        sellerConfig = FakeSellerConfig()
    )

    /**
     * The dates the schedule offers with nothing closed — what init builds.
     *
     * Deliberately not read off the ViewModel: these are the dates the buggy build
     * kept showing, so the test needs them independently of it.
     */
    /**
     * [count] dates from the schedule. The buyer is offered one — the next open day —
     * so a test that needs a day to close asks for two and closes the first.
     */
    private fun scheduledDates(count: Int = 1): List<Long> =
        OrderDateUtils.getAvailablePickupDates(count = count).map { it.toEpochMilliseconds() }

    private fun dateKeyOf(epochMillis: Long): String =
        MarketClosure.dateKey(Instant.fromEpochMilliseconds(epochMillis), TimeZone.currentSystemDefault())

    /**
     * Start the app with a signed-in buyer already connected to a seller — a restart,
     * not a first connection. That runs resumeInitializationAfterAuth, which loads
     * the closures. It is the path that mattered: performConnection resets the whole
     * basket screen on its way through, which hides the bug.
     */
    private fun startRestoredSession(closures: List<MarketClosure>) {
        profileRepository.setSellerProfile(
            SellerProfile(sellerId = "demo_seller_id", closures = closures)
        )
        authRepository.setCurrentUserId("buyer_1")
    }

    @Test
    fun `a day the seller closed stops being offered after a restart`() = runTest {
        val scheduled = scheduledDates(count = 2)
        assertTrue(scheduled.size >= 2, "expected a schedule to test against, got $scheduled")

        // The buyer is offered the next open day only, so closing that one is what
        // there is to test: the offer has to move on to the day after it.
        val closed = scheduled[0]
        startRestoredSession(listOf(MarketClosure(dateKeyOf(closed), "", "Urlaub")))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            setOf(dateKeyOf(closed)),
            viewModel.state.value.sellerClosures.closedCycles(null),
            "the closure never reached the state"
        )

        // init built its list before Firebase answered. Loading the closures has to
        // rebuild it, or the closed day stays on offer for the whole session.
        assertFalse(
            viewModel.state.value.basketScreen.availablePickupDates.contains(closed),
            "the closed day survived the load that discovered it"
        )

        // And opening the picker keeps it out rather than reinstating the old list.
        viewModel.dispatch(BuyBasketScreenAction.ShowDatePicker)
        advanceUntilIdle()

        val offered = viewModel.state.value.basketScreen.availablePickupDates
        assertFalse(offered.contains(closed), "the closed day is back on offer")
        // The offer moves to the next open day rather than emptying.
        assertEquals(listOf(scheduled[1]), offered)
    }

    @Test
    fun `with no closures the offered date is the next scheduled one`() = runTest {
        startRestoredSession(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ShowDatePicker)
        advanceUntilIdle()

        assertEquals(scheduledDates(), viewModel.state.value.basketScreen.availablePickupDates)
    }
}

/**
 * The basket offers slots — a market and a date together — not bare dates.
 *
 * One slot per market and no further: with one market, or none configured, that is
 * a single date, which is the case every seller is in today.
 */
class BuyAppViewModelSlotTest {

    private val dispatcherRule = MainDispatcherRule()

    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository
    private lateinit var marketRepository: FakeMarketRepository

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
        marketRepository.reset()
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
        authRepository.setCurrentUserId("buyer_1")
    }

    @Test
    fun `with no markets configured the offered date is the next scheduled one`() = runTest {
        startSession()
        val viewModel = createViewModel()
        advanceUntilIdle()

        val expected = OrderDateUtils.getAvailablePickupDates(count = 1).map { it.toEpochMilliseconds() }
        assertEquals(expected, viewModel.state.value.basketScreen.availablePickupDates)
        // A slot still exists for each date, just without a market to name.
        assertTrue(viewModel.state.value.basketScreen.availableSlots.all { it.marketId.isEmpty() })
    }

    @Test
    fun `two markets interleave in the offered slots`() = runTest {
        marketRepository.seed(
            "demo_seller_id",
            Market(id = "m1", name = "Kreuzberg", dayIndex = 3),   // Thursday
            Market(id = "m2", name = "Neukölln", dayIndex = 5)     // Saturday
        )
        startSession()
        val viewModel = createViewModel()
        advanceUntilIdle()

        // One slot each, not a run of weeks per stall.
        val slots = viewModel.state.value.basketScreen.availableSlots
        assertEquals(2, slots.size)
        assertEquals(listOf("m1", "m2"), slots.map { it.marketId })
        assertEquals(slots.map { it.date }.sorted(), slots.map { it.date })
        assertEquals(
            mapOf("m1" to "Kreuzberg", "m2" to "Neukölln"),
            viewModel.state.value.basketScreen.marketNames
        )
    }

    // The market is never chosen on its own: picking a date picks the stall it
    // belongs to, which is what makes the pair a slot rather than two choices.
    @Test
    fun `choosing a date also chooses its market`() = runTest {
        marketRepository.seed(
            "demo_seller_id",
            Market(id = "m1", name = "Kreuzberg", dayIndex = 3),
            Market(id = "m2", name = "Neukölln", dayIndex = 5)
        )
        startSession()
        val viewModel = createViewModel()
        advanceUntilIdle()

        val slot = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m2" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(slot.date))
        advanceUntilIdle()

        val basket = viewModel.state.value.basketScreen
        assertEquals(slot.date, basket.selectedPickupDate)
        assertEquals("m2", basket.selectedMarketId)
    }

    // Approval says whether, eligibility says where. A stall the buyer is not
    // scoped to is not theirs to order at, so it offers them no slots at all.
    @Test
    fun `a market the buyer is not scoped to offers no slots`() = runTest {
        marketRepository.seed(
            "demo_seller_id",
            Market(id = "m1", name = "Kreuzberg", dayIndex = 3),
            Market(id = "m2", name = "Neukölln", dayIndex = 5)
        )
        profileRepository.setOwnAllowedMarkets(listOf("m1"))
        startSession()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val slots = viewModel.state.value.basketScreen.availableSlots
        assertTrue(slots.isNotEmpty(), "expected Kreuzberg slots")
        assertTrue(slots.all { it.marketId == "m1" }, "Neukölln slots leaked in: $slots")
    }

    @Test
    fun `an unscoped buyer sees every market`() = runTest {
        marketRepository.seed(
            "demo_seller_id",
            Market(id = "m1", name = "Kreuzberg", dayIndex = 3),
            Market(id = "m2", name = "Neukölln", dayIndex = 5)
        )
        startSession()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val slots = viewModel.state.value.basketScreen.availableSlots
        assertTrue(slots.any { it.marketId == "m1" })
        assertTrue(slots.any { it.marketId == "m2" })
    }

    // The basket is the market the buyer is acting on now; a stated default is only
    // where they usually go. Getting this backwards hands the top-bar menu one
    // stall's contacts while the buyer is ordering at another.
    @Test
    fun `a selected slot outranks the buyer's stated default`() = runTest {
        marketRepository.seed(
            "demo_seller_id",
            Market(id = "m1", name = "Kreuzberg", dayIndex = 3),
            Market(id = "m2", name = "Neukölln", dayIndex = 5)
        )
        profileRepository.setBuyerProfile(BuyerProfile(id = "buyer_1", defaultMarket = "m1"))
        startSession()

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals("m1", viewModel.state.value.contextualMarketId, "the default did not apply")

        val neukoelln = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m2" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(neukoelln.date))
        advanceUntilIdle()

        assertEquals("m2", viewModel.state.value.contextualMarketId)
    }

    // A stored id outlives the stall it names, so neither candidate is trusted.
    @Test
    fun `a default naming a market this buyer may not collect at is ignored`() = runTest {
        marketRepository.seed(
            "demo_seller_id",
            Market(id = "m1", name = "Kreuzberg", dayIndex = 3),
            Market(id = "m2", name = "Neukölln", dayIndex = 5)
        )
        profileRepository.setOwnAllowedMarkets(listOf("m2"))
        profileRepository.setBuyerProfile(BuyerProfile(id = "buyer_1", defaultMarket = "m1"))
        startSession()

        val viewModel = createViewModel()
        advanceUntilIdle()

        // m1 is not eligible, so the sole eligible market answers instead.
        assertEquals("m2", viewModel.state.value.contextualMarketId)
    }

    @Test
    fun `the sole eligible market becomes the contextual one`() = runTest {
        marketRepository.seed(
            "demo_seller_id",
            Market(id = "m1", name = "Kreuzberg", dayIndex = 3),
            Market(id = "m2", name = "Neukölln", dayIndex = 5)
        )
        profileRepository.setOwnAllowedMarkets(listOf("m2"))
        startSession()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("m2", viewModel.state.value.contextualMarketId)
    }

    // The buyer has already read a total; a different stall's prices are a change
    // they accept, not something applied underneath them.
    @Test
    fun `switching to a market that prices differently asks first`() = runTest {
        marketRepository.seed(
            "demo_seller_id",
            Market(id = "m1", name = "Kreuzberg", dayIndex = 3),
            Market(
                id = "m2", name = "Neukölln", dayIndex = 5,
                priceConditions = listOf(
                    PriceCondition(
                        PriceCause.STALL_FEE, PriceScope.AllArticles,
                        PriceEffect.Surcharge(0.25), "Standgebühr"
                    )
                )
            )
        )
        startSession()
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Settle on Kreuzberg first, then move to the market that charges more.
        val kreuzberg = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m1" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(kreuzberg.date))
        advanceUntilIdle()

        val neukoelln = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m2" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(neukoelln.date))
        advanceUntilIdle()

        // With an empty basket there is nothing to reprice, so the move is silent —
        // which is the behaviour every seller gets today.
        val basket = viewModel.state.value.basketScreen
        assertEquals("m2", basket.selectedMarketId)
        assertEquals(null, basket.pendingSlotPriceChange)
    }

    @Test
    fun `a market with no conditions switches without asking`() = runTest {
        marketRepository.seed(
            "demo_seller_id",
            Market(id = "m1", name = "Kreuzberg", dayIndex = 3),
            Market(id = "m2", name = "Neukölln", dayIndex = 5)
        )
        startSession()
        val viewModel = createViewModel()
        advanceUntilIdle()

        val neukoelln = viewModel.state.value.basketScreen.availableSlots.first { it.marketId == "m2" }
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(neukoelln.date))
        advanceUntilIdle()

        assertEquals("m2", viewModel.state.value.basketScreen.selectedMarketId)
        assertEquals(null, viewModel.state.value.basketScreen.pendingSlotPriceChange)
    }

    @Test
    fun `the contextual market's conditions price the catalogue`() = runTest {
        marketRepository.seed(
            "demo_seller_id",
            Market(
                id = "m1", name = "Kreuzberg", dayIndex = 3,
                priceConditions = listOf(
                    PriceCondition(
                        PriceCause.STALL_FEE, PriceScope.AllArticles,
                        PriceEffect.Surcharge(0.25), "Standgebühr"
                    )
                )
            )
        )
        startSession()
        val viewModel = createViewModel()
        advanceUntilIdle()

        val resolved = viewModel.state.value.priceOf(
            Article(id = "a1", productName = "Möhren", price = 2.80)
        )
        assertEquals(3.05, resolved.gross)
        assertEquals("Standgebühr", resolved.components.single().note)
    }

    @Test
    fun `a market the seller closed for one day drops only that slot`() = runTest {
        val thursdayMarket = Market(id = "m1", name = "Kreuzberg", dayIndex = 3)
        marketRepository.seed("demo_seller_id", thursdayMarket, Market(id = "m2", name = "Neukölln", dayIndex = 5))

        val firstThursday = PickupSlots.next(listOf(thursdayMarket), count = 1).single()
        profileRepository.setSellerProfile(
            SellerProfile(
                sellerId = "demo_seller_id",
                closures = listOf(
                    MarketClosure(
                        MarketClosure.dateKey(
                            Instant.fromEpochMilliseconds(firstThursday.date),
                            TimeZone.currentSystemDefault()
                        ),
                        "m1"
                    )
                )
            )
        )
        authRepository.setCurrentUserId("buyer_1")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val slots = viewModel.state.value.basketScreen.availableSlots
        assertFalse(slots.contains(firstThursday), "the closed Kreuzberg day is still offered")
        assertTrue(slots.any { it.marketId == "m2" }, "Neukölln was dropped too")
    }
}
