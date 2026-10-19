package com.together.newverse.ui.state

import com.together.newverse.domain.model.MarketClosure
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
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
    private fun scheduledDates(): List<Long> =
        OrderDateUtils.getAvailablePickupDates(count = 5).map { it.toEpochMilliseconds() }

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
        val scheduled = scheduledDates()
        assertTrue(scheduled.size >= 2, "expected a schedule to test against, got $scheduled")

        // The seller closes the second offered day, as the dev seller did.
        val closed = scheduled[1]
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
        assertTrue(offered.contains(scheduled[0]), "an open day was dropped")
        // The gap is filled from further out rather than shortening the list.
        assertEquals(scheduled.size, offered.size)
    }

    @Test
    fun `with no closures the offered dates are the scheduled ones`() = runTest {
        startRestoredSession(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ShowDatePicker)
        advanceUntilIdle()

        assertEquals(scheduledDates(), viewModel.state.value.basketScreen.availablePickupDates)
    }
}
