package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.DeliveryAddress
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
import kotlin.test.assertTrue

/**
 * A delivery order reaches the seller with its address, or it does not get placed.
 *
 * The seller cannot read `buyer_profile`, so an order with no destination has none
 * and no way to recover one — which makes the checkout gate the last line.
 */
class BuyAppViewModelDeliveryTest {

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

    private fun startSession(selfPickup: Boolean, street: String = "Hauptstr.", nr: String = "4") {
        profileRepository.setSellerProfile(SellerProfile(sellerId = "demo_seller_id"))
        profileRepository.setBuyerProfile(
            BuyerProfile(
                id = "buyer_1",
                displayName = "Test",
                emailAddress = "t@example.com",
                telephoneNumber = "123",
                street = street,
                houseNumber = nr,
                isSelfPickup = selfPickup
            )
        )
        authRepository.setCurrentUserId("buyer_1")
    }

    private suspend fun TestScope.ready(): BuyAppViewModel {
        marketRepository.seed("demo_seller_id", Market(id = "m1", name = "Kreuzberg", dayIndex = 3))
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

    // ===== the default comes from the profile =====

    @Test
    fun `a self-pickup buyer's basket defaults to pickup`() = runTest {
        startSession(selfPickup = true)
        val viewModel = ready()

        assertEquals(Fulfilment.PICKUP, viewModel.state.value.basketScreen.fulfilment)
    }

    @Test
    fun `a delivery buyer's basket defaults to delivery with their address`() = runTest {
        startSession(selfPickup = false, street = "Bahnhofstr.", nr = "12a")
        val viewModel = ready()

        val basket = viewModel.state.value.basketScreen
        assertEquals(Fulfilment.DELIVERY, basket.fulfilment)
        assertEquals("Bahnhofstr.", basket.deliveryAddress.street)
        assertEquals("12a", basket.deliveryAddress.houseNumber)
    }

    // ===== the seller gets the address =====

    @Test
    fun `a placed delivery order carries its address`() = runTest {
        startSession(selfPickup = false)
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        val placed = orderRepository.placedOrders.single()
        assertEquals(Fulfilment.DELIVERY, placed.fulfilment)
        assertEquals("Hauptstr. 4", placed.deliveryAddress.oneLine())
        assertFalse(placed.isDeliveryWithoutAddress)
    }

    @Test
    fun `a placed pickup order carries no address`() = runTest {
        startSession(selfPickup = true)
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        val placed = orderRepository.placedOrders.single()
        assertEquals(Fulfilment.PICKUP, placed.fulfilment)
        // Not scattered into orders it is irrelevant to.
        assertTrue(placed.deliveryAddress.isEmpty)
    }

    // ===== the gate =====

    @Test
    fun `a delivery with no address cannot be placed`() = runTest {
        startSession(selfPickup = false, street = "", nr = "")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()

        val basket = viewModel.state.value.basketScreen
        assertTrue(basket.orderError != null, "no error shown for an undeliverable order")
        assertTrue(orderRepository.placedOrders.isEmpty())
    }

    @Test
    fun `a delivery chosen inside the confirmation cannot be placed without an address`() = runTest {
        // The gate on pressing Bestellen saw a pickup order and let the dialog open.
        // The buyer then switched to delivery *in* the dialog, where nothing had
        // re-checked them — which is how an addressless delivery got written.
        startSession(selfPickup = true, street = "", nr = "")
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.basketScreen.showCheckoutConfirm)

        viewModel.dispatch(BuyBasketScreenAction.SetFulfilment(Fulfilment.DELIVERY))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        val basket = viewModel.state.value.basketScreen
        assertTrue(orderRepository.placedOrders.isEmpty(), "an addressless delivery was placed")
        assertTrue(basket.orderError != null, "no error shown for an undeliverable order")
        // The dialog stays open on the problem rather than closing on a refusal.
        assertTrue(basket.showCheckoutConfirm)
    }

    @Test
    fun `an order cannot be edited into an addressless delivery`() = runTest {
        startSession(selfPickup = true, street = "", nr = "")
        val viewModel = ready()
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()
        val placed = orderRepository.placedOrders.single()

        viewModel.dispatch(BuyBasketScreenAction.EnableEditing)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SetFulfilment(Fulfilment.DELIVERY))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.UpdateOrder)
        advanceUntilIdle()

        assertTrue(
            orderRepository.updatedOrders.none { it.fulfilment == Fulfilment.DELIVERY },
            "an addressless delivery was written by an edit"
        )
        assertEquals(Fulfilment.PICKUP, placed.fulfilment)
        assertTrue(viewModel.state.value.basketScreen.orderError != null)
    }

    @Test
    fun `a half-typed address is not enough`() = runTest {
        startSession(selfPickup = false, street = "", nr = "")
        val viewModel = ready()

        // Street without a number is not somewhere a van can stop.
        viewModel.dispatch(
            BuyBasketScreenAction.SetDeliveryAddress(DeliveryAddress(street = "Waldweg"))
        )
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        assertTrue(orderRepository.placedOrders.isEmpty())
        assertTrue(viewModel.state.value.basketScreen.orderError != null)
    }

    // ===== the address the buyer keeps =====

    @Test
    fun `the address typed at checkout can be saved to the profile`() = runTest {
        startSession(selfPickup = false, street = "", nr = "")
        val viewModel = ready()

        viewModel.dispatch(
            BuyBasketScreenAction.SetDeliveryAddress(
                DeliveryAddress(street = "Waldweg", houseNumber = "9", note = "hinterm Haus")
            )
        )
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SaveDeliveryAddressToProfile)
        advanceUntilIdle()

        val saved = profileRepository.getBuyerProfile().getOrNull()
        assertEquals("Waldweg", saved?.street)
        assertEquals("9", saved?.houseNumber)
        // The order's own note is not the profile's business.
        assertEquals("hinterm Haus", viewModel.state.value.basketScreen.deliveryAddress.note)
    }

    @Test
    fun `an incomplete address is not saved to the profile`() = runTest {
        startSession(selfPickup = false, street = "Hauptstr.", nr = "4")
        val viewModel = ready()

        viewModel.dispatch(
            BuyBasketScreenAction.SetDeliveryAddress(DeliveryAddress(street = "Waldweg"))
        )
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.SaveDeliveryAddressToProfile)
        advanceUntilIdle()

        // Half an address must not overwrite a whole one.
        val saved = profileRepository.getBuyerProfile().getOrNull()
        assertEquals("Hauptstr.", saved?.street)
        assertEquals("4", saved?.houseNumber)
    }

    @Test
    fun `switching to delivery seeds the address from the profile`() = runTest {
        startSession(selfPickup = true, street = "Bahnhofstr.", nr = "12a")
        val viewModel = ready()
        assertEquals(Fulfilment.PICKUP, viewModel.state.value.basketScreen.fulfilment)

        viewModel.dispatch(BuyBasketScreenAction.SetFulfilment(Fulfilment.DELIVERY))
        advanceUntilIdle()

        // One tap, not a retype of what the app already knows.
        val basket = viewModel.state.value.basketScreen
        assertEquals(Fulfilment.DELIVERY, basket.fulfilment)
        assertEquals("Bahnhofstr. 12a", basket.deliveryAddress.oneLine())
    }

    @Test
    fun `a one-off address is used without touching the profile`() = runTest {
        startSession(selfPickup = false, street = "Hauptstr.", nr = "4")
        val viewModel = ready()

        viewModel.dispatch(
            BuyBasketScreenAction.SetDeliveryAddress(
                DeliveryAddress(street = "Waldweg", houseNumber = "9", note = "hinterm Haus")
            )
        )
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        val placed = orderRepository.placedOrders.single()
        assertEquals("Waldweg 9", placed.deliveryAddress.oneLine())
        assertEquals("hinterm Haus", placed.deliveryAddress.note)
        // The profile is untouched: a one-off delivery is not a change of address.
        assertEquals("Hauptstr.", profileRepository.getBuyerProfile().getOrNull()?.street)
    }

    @Test
    fun `switching back to pickup drops the address from the order`() = runTest {
        startSession(selfPickup = false)
        val viewModel = ready()

        viewModel.dispatch(BuyBasketScreenAction.SetFulfilment(Fulfilment.PICKUP))
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()

        val placed = orderRepository.placedOrders.single()
        assertEquals(Fulfilment.PICKUP, placed.fulfilment)
        assertTrue(placed.deliveryAddress.isEmpty)
    }

    // ===== the edge case: a profile change after an order exists =====

    @Test
    fun `saving a new preference does not re-aim a placed order`() = runTest {
        startSession(selfPickup = true)
        val viewModel = ready()
        viewModel.dispatch(BuyBasketScreenAction.Checkout)
        advanceUntilIdle()
        viewModel.dispatch(BuyBasketScreenAction.ConfirmCheckout)
        advanceUntilIdle()
        assertEquals(Fulfilment.PICKUP, orderRepository.placedOrders.single().fulfilment)

        // The buyer now switches their profile to delivery.
        viewModel.dispatch(BuyProfileAction.FulfilmentPreferenceSaved(isSelfPickup = false))
        advanceUntilIdle()

        // The order the seller may already have packed is untouched; no second order.
        assertEquals(1, orderRepository.placedOrders.size)
        assertEquals(Fulfilment.PICKUP, orderRepository.placedOrders.single().fulfilment)
    }
}
