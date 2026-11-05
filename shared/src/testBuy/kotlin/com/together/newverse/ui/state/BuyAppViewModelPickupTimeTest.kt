package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Fulfilment
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The collection time belongs to the order, and to the seller who reads it.
 *
 * It used to be `BuyerProfile.defaultPickUpTime`: a *required* profile field that
 * stood in front of the access request — a buyer could not ask a seller to let them
 * order until they had named an Abholzeit — and that no screen in the seller app ever
 * displayed. Collected under compulsion, delivered to nobody.
 *
 * Now it is asked at checkout against a real pickup date, defaulted from the profile,
 * optional, editable for as long as the order is, and written onto the order where the
 * seller sees it.
 */
class BuyAppViewModelPickupTimeTest {

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

    /**
     * A signed-in buyer whose profile holds [usualTime] — empty for a buyer who never
     * named one, which the old gate refused outright and this one simply accepts.
     */
    private fun startSession(usualTime: String, selfPickup: Boolean = true) {
        profileRepository.setSellerProfile(SellerProfile(sellerId = "demo_seller_id"))
        profileRepository.setBuyerProfile(
            BuyerProfile(
                id = "buyer_1",
                displayName = "Test",
                emailAddress = "t@example.com",
                street = "Hauptstr.",
                houseNumber = "4",
                isSelfPickup = selfPickup,
                defaultPickUpTime = usualTime
            )
        )
        authRepository.setCurrentUserId("buyer_1")
    }

    /** One market, one item in the basket, a slot chosen: ready to press Bestellen. */
    private suspend fun TestScope.ready(): BuyAppViewModel {
        marketRepository.seed(
            "demo_seller_id",
            Market(id = "m1", name = "Kreuzberg", dayIndex = 3, allowsDelivery = true)
        )
        basketRepository.setBasketItems(
            listOf(
                OrderedProduct(
                    productId = "a1", productName = "Möhren", unit = "kg",
                    price = 2.80, basePrice = 2.80, amountCount = 2.0, piecesCount = -1
                )
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        articleRepository.emitArticle(moehren)
        advanceUntilIdle()
        val slot = viewModel.state.value.basketScreen.availableSlots.first()
        viewModel.dispatch(BuyBasketScreenAction.SelectPickupDate(slot.date))
        advanceUntilIdle()
        return viewModel
    }

    private suspend fun TestScope.placeOrder(viewModel: BuyAppViewModel) {
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()
    }

    // ===== the default comes from the profile =====

    @Test
    fun `the basket starts from the buyer's usual time`() = runTest {
        startSession(usualTime = "14:00")
        val viewModel = ready()

        assertEquals("14:00", viewModel.state.value.basketScreen.pickupTime)
    }

    @Test
    fun `a buyer who never named a time is asked for none`() = runTest {
        // Exactly the buyer the old gate turned away: no Abholzeit, so no access
        // request. Now the basket simply carries no time, which is an answer.
        startSession(usualTime = "")
        val viewModel = ready()

        assertEquals("", viewModel.state.value.basketScreen.pickupTime)

        placeOrder(viewModel)

        val placed = orderRepository.placedOrders.single()
        assertEquals("", placed.pickupTime)
        assertNull(placed.statedPickupTime, "nothing to show the seller, and nothing wrong")
    }

    // ===== the seller gets the time =====

    @Test
    fun `a placed pickup order carries the time the buyer named`() = runTest {
        startSession(usualTime = "14:00")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupTime("09:30"))
        advanceUntilIdle()
        placeOrder(viewModel)

        val placed = orderRepository.placedOrders.single()
        assertEquals("09:30", placed.pickupTime)
        assertEquals("09:30", placed.statedPickupTime)
    }

    @Test
    fun `a delivery order carries no time`() = runTest {
        // A delivery arrives when the seller's round reaches it. Carrying the time
        // along would promise an hour nobody is keeping.
        startSession(usualTime = "14:00", selfPickup = false)
        val viewModel = ready()

        placeOrder(viewModel)

        val placed = orderRepository.placedOrders.single()
        assertEquals(Fulfilment.DELIVERY, placed.fulfilment)
        assertEquals("", placed.pickupTime)
    }

    @Test
    fun `a time cleared at checkout is not written`() = runTest {
        // Optional has to mean reversible: a buyer who finds their usual time filled
        // in and does not want to commit to it must be able to take it back.
        startSession(usualTime = "14:00")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupTime(""))
        advanceUntilIdle()
        placeOrder(viewModel)

        assertEquals("", orderRepository.placedOrders.single().pickupTime)
        // And the profile's own default is untouched by one order's blank.
        assertEquals("14:00", profileRepository.getBuyerProfile().getOrNull()?.defaultPickUpTime)
    }

    // ===== the gate =====

    @Test
    fun `an unreadable time is refused with a reason, not written`() = runTest {
        startSession(usualTime = "")
        val viewModel = ready()

        // The field offers a picker, so this cannot be typed — but a restored state
        // could hold it, and the database rules reject the whole write for it.
        viewModel.dispatch(BuyBasketScreenAction.SetPickupTime("14"))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        val basket = viewModel.state.value.basketScreen
        assertTrue(orderRepository.placedOrders.isEmpty(), "an unreadable time was written")
        assertTrue(basket.orderError != null, "refused without saying why")
        // The dialog stays open on the problem rather than closing on a refusal.
        assertTrue(basket.showCheckoutConfirm)
    }

    @Test
    fun `any hour of the day is accepted, not only the profile form's business hours`() =
        runTest {
            // The profile field insists on 7–18. A market's own hours are not
            // modelled, so imposing that here would refuse a stall that opens at six.
            startSession(usualTime = "")
            val viewModel = ready()

            viewModel.dispatch(BuyBasketScreenAction.SetPickupTime("06:15"))
            advanceUntilIdle()
            placeOrder(viewModel)

            assertEquals("06:15", orderRepository.placedOrders.single().pickupTime)
        }

    // ===== changing it while the order is editable =====

    @Test
    fun `a placed order's own time is not an unsaved change`() = runTest {
        startSession(usualTime = "14:00")
        val viewModel = ready()

        placeOrder(viewModel)

        // Otherwise the card asks to save the order over itself the moment it opens.
        val basket = viewModel.state.value.basketScreen
        assertEquals("14:00", basket.pickupTime)
        assertFalse(basket.fulfilmentChanged)
        assertFalse(basket.hasPendingEdits)
    }

    @Test
    fun `editing the time of a placed order is an unsaved change until saved`() = runTest {
        startSession(usualTime = "14:00")
        val viewModel = ready()
        placeOrder(viewModel)
        assertFalse(viewModel.state.value.basketScreen.hasPendingEdits)

        viewModel.dispatch(BuyBasketScreenAction.EnableEditing)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SetPickupTime("16:45"))
        advanceUntilIdle()

        // Nothing in the basket moved, so only the time can be carrying this.
        val edited = viewModel.state.value.basketScreen
        assertFalse(edited.hasChanges)
        assertTrue(edited.fulfilmentChanged)
        assertTrue(edited.hasPendingEdits)
        assertTrue(
            edited.editedOnBasketScreen,
            "asked only here and in the confirmation, so it keeps its confirmation"
        )

        viewModel.dispatch(BuyBasketScreenAction.UpdateOrder)
        advanceUntilIdle()

        assertEquals("16:45", orderRepository.updatedOrders.last().pickupTime)
        // The written order is the new baseline.
        val saved = viewModel.state.value.basketScreen
        assertFalse(saved.fulfilmentChanged)
        assertFalse(saved.hasPendingEdits)
    }

    @Test
    fun `a placed order can have its time taken back`() = runTest {
        startSession(usualTime = "14:00")
        val viewModel = ready()
        placeOrder(viewModel)

        viewModel.dispatch(BuyBasketScreenAction.EnableEditing)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SetPickupTime(""))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.UpdateOrder)
        advanceUntilIdle()

        assertEquals("", orderRepository.updatedOrders.last().pickupTime)
    }

    @Test
    fun `editing a pickup into a delivery drops the time`() = runTest {
        startSession(usualTime = "14:00")
        val viewModel = ready()
        placeOrder(viewModel)

        viewModel.dispatch(BuyBasketScreenAction.EnableEditing)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SetFulfilment(Fulfilment.DELIVERY))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.UpdateOrder)
        advanceUntilIdle()

        val updated = orderRepository.updatedOrders.last()
        assertEquals(Fulfilment.DELIVERY, updated.fulfilment)
        assertEquals("", updated.pickupTime, "a delivery has no collection time to keep")
    }

    // ===== the time the buyer keeps =====

    @Test
    fun `the time chosen at checkout can be saved as the usual one`() = runTest {
        startSession(usualTime = "")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupTime("10:15"))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SavePickupTimeToProfile)
        advanceUntilIdle()

        assertEquals(
            "10:15",
            profileRepository.getBuyerProfile().getOrNull()?.defaultPickUpTime
        )
    }

    @Test
    fun `a blank time is not saved over the usual one`() = runTest {
        // Clearing the field on one order says nothing about the next.
        startSession(usualTime = "14:00")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupTime(""))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SavePickupTimeToProfile)
        advanceUntilIdle()

        assertEquals(
            "14:00",
            profileRepository.getBuyerProfile().getOrNull()?.defaultPickUpTime
        )
    }

    @Test
    fun `a one-off time is used without touching the profile`() = runTest {
        startSession(usualTime = "14:00")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupTime("08:00"))
        advanceUntilIdle()
        placeOrder(viewModel)

        assertEquals("08:00", orderRepository.placedOrders.single().pickupTime)
        assertEquals(
            "14:00",
            profileRepository.getBuyerProfile().getOrNull()?.defaultPickUpTime,
            "coming early once is not a change of habit"
        )
    }

    @Test
    fun `switching to delivery and back keeps the time the buyer chose`() = runTest {
        startSession(usualTime = "14:00")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupTime("09:30"))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SetFulfilment(Fulfilment.DELIVERY))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SetFulfilment(Fulfilment.PICKUP))
        advanceUntilIdle()

        // The field keeps what was typed, exactly as the address fields do.
        assertEquals("09:30", viewModel.state.value.basketScreen.pickupTime)
    }
}
