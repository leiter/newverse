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
 * The order says who is taking receipt of it, and on what number.
 *
 * Two gaps closed at once. The seller's card named the account holder and nothing
 * else, so a buyer sending a flatmate had to write it into the free-text order message
 * and hope it was read. And `BuyerProfile.telephoneNumber` had been collected on the
 * profile page for as long as there had been one while `orderToMap` never wrote it —
 * the seller's detail screen held a phone row that could not fill.
 *
 * Both now belong to the order: pre-filled, optional, editable while the order is, and
 * written only when they say something the order's own buyer name does not.
 */
class BuyAppViewModelPickupContactTest {

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

    private fun startSession(
        name: String = "Alice Schmidt",
        phone: String = "",
        selfPickup: Boolean = true,
    ) {
        profileRepository.setSellerProfile(SellerProfile(sellerId = "demo_seller_id"))
        profileRepository.setBuyerProfile(
            BuyerProfile(
                id = "buyer_1",
                displayName = name,
                emailAddress = "a@example.com",
                telephoneNumber = phone,
                street = "Hauptstr.",
                houseNumber = "4",
                isSelfPickup = selfPickup,
                defaultPickUpTime = "14:00"
            )
        )
        authRepository.setCurrentUserId("buyer_1")
    }

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

    // ===== what the fields start from =====

    @Test
    fun `the fields start from the buyer's own name and number`() = runTest {
        startSession(name = "Alice Schmidt", phone = "0170 1234567")
        val viewModel = ready()

        val basket = viewModel.state.value.basketScreen
        assertEquals("Alice Schmidt", basket.pickupName)
        assertEquals("0170 1234567", basket.pickupPhone)
    }

    @Test
    fun `a profile with no number asks for none`() = runTest {
        startSession(phone = "")
        val viewModel = ready()

        assertEquals("", viewModel.state.value.basketScreen.pickupPhone)
    }

    // ===== what reaches the seller =====

    @Test
    fun `collecting it yourself puts no second name on the order`() = runTest {
        startSession(name = "Alice Schmidt")
        val viewModel = ready()

        placeOrder(viewModel)

        val placed = orderRepository.placedOrders.single()
        assertEquals("Alice Schmidt", placed.pickupName)
        assertNull(
            placed.statedPickupName,
            "the card is already titled with this name; repeating it makes the " +
                "ordinary case look like an exception"
        )
    }

    @Test
    fun `somebody else collecting is named on the order`() = runTest {
        startSession(name = "Alice Schmidt")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupName("Jonas Weber"))
        advanceUntilIdle()
        placeOrder(viewModel)

        val placed = orderRepository.placedOrders.single()
        assertEquals("Jonas Weber", placed.pickupName)
        assertEquals("Jonas Weber", placed.statedPickupName)
    }

    @Test
    fun `the number the buyer gives reaches the seller`() = runTest {
        // The point of the whole field: the profile has collected a number for years
        // and no order ever carried it.
        startSession(phone = "0170 1234567")
        val viewModel = ready()

        placeOrder(viewModel)

        val placed = orderRepository.placedOrders.single()
        assertEquals("0170 1234567", placed.pickupPhone)
        assertEquals("0170 1234567", placed.statedPickupPhone)
    }

    @Test
    fun `a number cleared at checkout is not written`() = runTest {
        // Asked per order means withdrawable per order, which a profile field is not.
        startSession(phone = "0170 1234567")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupPhone(""))
        advanceUntilIdle()
        placeOrder(viewModel)

        val placed = orderRepository.placedOrders.single()
        assertEquals("", placed.pickupPhone)
        assertNull(placed.statedPickupPhone)
        // And the profile keeps its own number: one order's blank is not a deletion.
        assertEquals(
            "0170 1234567",
            profileRepository.getBuyerProfile().getOrNull()?.telephoneNumber
        )
    }

    @Test
    fun `surrounding spaces are trimmed off both`() = runTest {
        startSession(name = "Alice Schmidt")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupName("  Jonas Weber "))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SetPickupPhone(" 0170 1234567 "))
        advanceUntilIdle()
        placeOrder(viewModel)

        val placed = orderRepository.placedOrders.single()
        assertEquals("Jonas Weber", placed.pickupName)
        assertEquals("0170 1234567", placed.pickupPhone)
    }

    @Test
    fun `a delivery carries them too, because somebody is at the door`() = runTest {
        startSession(name = "Alice Schmidt", phone = "0170 1234567", selfPickup = false)
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupName("Jonas Weber"))
        advanceUntilIdle()
        placeOrder(viewModel)

        val placed = orderRepository.placedOrders.single()
        assertEquals(Fulfilment.DELIVERY, placed.fulfilment)
        assertEquals("Jonas Weber", placed.statedPickupName)
        assertEquals("0170 1234567", placed.statedPickupPhone)
    }

    // ===== the gate =====

    @Test
    fun `a number with letters in it is refused with a reason`() = runTest {
        startSession()
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupPhone("ruft bitte an"))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        val basket = viewModel.state.value.basketScreen
        assertTrue(orderRepository.placedOrders.isEmpty(), "an undialable number was written")
        assertTrue(basket.orderError != null, "refused without saying why")
        assertTrue(basket.showCheckoutConfirm, "the dialog stays open on the problem")
    }

    @Test
    fun `a number written the way people write numbers is accepted`() = runTest {
        startSession()
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupPhone("+49 (030) 12 34-56"))
        advanceUntilIdle()
        placeOrder(viewModel)

        assertEquals("+49 (030) 12 34-56", orderRepository.placedOrders.single().pickupPhone)
    }

    // ===== changing it while the order is editable =====

    @Test
    fun `a placed order's own collector is not an unsaved change`() = runTest {
        startSession(name = "Alice Schmidt", phone = "0170 1234567")
        val viewModel = ready()

        placeOrder(viewModel)

        // Otherwise the card asks to save the order over itself the moment it opens —
        // and the order stores no name at all when the buyer collects it themselves,
        // which is exactly where the field and its baseline could disagree.
        val basket = viewModel.state.value.basketScreen
        assertFalse(basket.contactChanged)
        assertFalse(basket.hasPendingEdits)
    }

    @Test
    fun `naming a collector on a placed order is an unsaved change until saved`() = runTest {
        startSession(name = "Alice Schmidt")
        val viewModel = ready()
        placeOrder(viewModel)
        assertFalse(viewModel.state.value.basketScreen.hasPendingEdits)

        viewModel.dispatch(BuyBasketScreenAction.EnableEditing)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SetPickupName("Jonas Weber"))
        advanceUntilIdle()

        // Nothing in the basket moved and the fulfilment is untouched, so only the
        // collector can be carrying this.
        val edited = viewModel.state.value.basketScreen
        assertFalse(edited.hasChanges)
        assertFalse(edited.fulfilmentChanged)
        assertTrue(edited.contactChanged)
        assertTrue(edited.hasPendingEdits)

        viewModel.dispatch(BuyBasketScreenAction.UpdateOrder)
        advanceUntilIdle()

        assertEquals("Jonas Weber", orderRepository.updatedOrders.last().pickupName)
        val saved = viewModel.state.value.basketScreen
        assertFalse(saved.contactChanged)
        assertFalse(saved.hasPendingEdits)
    }

    @Test
    fun `a number can be added to an order already placed`() = runTest {
        startSession(phone = "")
        val viewModel = ready()
        placeOrder(viewModel)

        viewModel.dispatch(BuyBasketScreenAction.EnableEditing)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SetPickupPhone("0170 1234567"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.basketScreen.hasPendingEdits)
        viewModel.dispatch(BuyBasketScreenAction.UpdateOrder)
        advanceUntilIdle()

        assertEquals("0170 1234567", orderRepository.updatedOrders.last().pickupPhone)
        assertFalse(viewModel.state.value.basketScreen.hasPendingEdits)
    }

    @Test
    fun `a number can be taken off an order already placed`() = runTest {
        startSession(phone = "0170 1234567")
        val viewModel = ready()
        placeOrder(viewModel)

        viewModel.dispatch(BuyBasketScreenAction.EnableEditing)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SetPickupPhone(""))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.UpdateOrder)
        advanceUntilIdle()

        val updated = orderRepository.updatedOrders.last()
        assertEquals("", updated.pickupPhone)
        assertNull(updated.statedPickupPhone)
    }

    // ===== the number the buyer keeps =====

    @Test
    fun `the number given at checkout can be saved to the profile`() = runTest {
        startSession(phone = "")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupPhone("0170 1234567"))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SavePickupPhoneToProfile)
        advanceUntilIdle()

        assertEquals(
            "0170 1234567",
            profileRepository.getBuyerProfile().getOrNull()?.telephoneNumber
        )
    }

    @Test
    fun `a blank number is not saved over a stored one`() = runTest {
        startSession(phone = "0170 1234567")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupPhone(""))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SavePickupPhoneToProfile)
        advanceUntilIdle()

        assertEquals(
            "0170 1234567",
            profileRepository.getBuyerProfile().getOrNull()?.telephoneNumber
        )
    }

    @Test
    fun `an undialable number is not saved to the profile either`() = runTest {
        startSession(phone = "0170 1234567")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupPhone("ruft bitte an"))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SavePickupPhoneToProfile)
        advanceUntilIdle()

        assertEquals(
            "0170 1234567",
            profileRepository.getBuyerProfile().getOrNull()?.telephoneNumber
        )
    }

    @Test
    fun `the collector's name is never written to the profile`() = runTest {
        // A flatmate's name is not the buyer's name. The address and the number have
        // a save-to-profile offer beside them; this deliberately has none.
        startSession(name = "Alice Schmidt")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetPickupName("Jonas Weber"))
        advanceUntilIdle()
        placeOrder(viewModel)

        assertEquals(
            "Alice Schmidt",
            profileRepository.getBuyerProfile().getOrNull()?.displayName
        )
    }
}
