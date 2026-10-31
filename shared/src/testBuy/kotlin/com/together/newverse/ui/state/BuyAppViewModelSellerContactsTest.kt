package com.together.newverse.ui.state

import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.ContactChannel
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.SellerContact
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
 * The seller's contact channels reach the buy app, and keep reaching it.
 *
 * The thing under test is a subscription, not a fetch. A buyer holding a number the
 * seller changed this morning is worse off than a buyer holding none: they ring it,
 * nobody answers, and the business looks shut. So what matters here is not that the
 * contacts arrive once but that a later edit arrives too, and that both the top bar
 * and the About card see the same edit — they read one field, which is what makes
 * that true by construction rather than by two subscriptions agreeing.
 *
 * The visibility rule is the other half: the top bar's person button is driven by
 * `topBarContacts` being empty, so "no contacts published" and "button hidden" are
 * the same statement.
 */
class BuyAppViewModelSellerContactsTest {

    private val dispatcherRule = MainDispatcherRule()

    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository
    private lateinit var marketRepository: FakeMarketRepository

    private val sellerId = "demo_seller_id"
    private val buyerId = "buyer_1"

    private val office = SellerContact(ContactChannel.PHONE, "+4930111111", "Büro")
    private val stall = SellerContact(ContactChannel.PHONE, "+4917022222", "Stand Kollwitzplatz")

    @BeforeTest
    fun setUp() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
        marketRepository = FakeMarketRepository()

        profileRepository.setSellerProfile(SellerProfile(sellerId = sellerId))
        profileRepository.setBuyerProfile(
            BuyerProfile(
                id = buyerId,
                displayName = "Test Käufer",
                emailAddress = "t@example.com",
                telephoneNumber = "123",
                isSelfPickup = true
            )
        )
        authRepository.setCurrentUserId(buyerId)
        profileRepository.currentBuyerId = buyerId
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

    /** An approved buyer, which is what the top-bar shortcut is gated on. */
    private fun approve() =
        profileRepository.setAccessStatus(sellerId, buyerId, AccessStatus.APPROVED)

    private fun sellerWith(
        contacts: List<SellerContact> = emptyList(),
        markets: List<Market> = emptyList(),
    ) = SellerProfile(sellerId = sellerId, contacts = contacts, markets = markets)

    // ===== the directory arrives =====

    @Test
    fun `the seller's contacts reach the app without being asked for`() = runTest {
        profileRepository.setSellerProfile(sellerWith(contacts = listOf(office)))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(office), viewModel.state.value.sellerContacts.sellerContacts)
    }

    @Test
    fun `a market's own contacts arrive alongside the business's`() = runTest {
        profileRepository.setSellerProfile(
            sellerWith(
                contacts = listOf(office),
                markets = listOf(Market(id = "m1", name = "Kollwitzplatz", contacts = listOf(stall)))
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val directory = viewModel.state.value.sellerContacts
        assertEquals(listOf(office), directory.sellerContacts)
        assertEquals(listOf(stall), directory.marketContacts["m1"])
        assertEquals("Kollwitzplatz", directory.marketNames["m1"])
    }

    // ===== and keeps arriving =====

    @Test
    fun `a contact added while the app is open appears without a restart`() = runTest {
        profileRepository.setSellerProfile(sellerWith(contacts = listOf(office)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // The seller adds a second channel from their own app, mid-session.
        val mail = SellerContact(ContactChannel.EMAIL, "hallo@bodenschaetze.de")
        profileRepository.setSellerProfile(sellerWith(contacts = listOf(office, mail)))
        advanceUntilIdle()

        assertEquals(listOf(office, mail), viewModel.state.value.sellerContacts.sellerContacts)
    }

    @Test
    fun `a changed number replaces the old one rather than joining it`() = runTest {
        profileRepository.setSellerProfile(sellerWith(contacts = listOf(office)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        val moved = office.copy(value = "+4930999999")
        profileRepository.setSellerProfile(sellerWith(contacts = listOf(moved)))
        advanceUntilIdle()

        // The whole directory is replaced, so the number nobody answers is gone.
        assertEquals(listOf(moved), viewModel.state.value.sellerContacts.sellerContacts)
    }

    @Test
    fun `withdrawing every contact empties the directory`() = runTest {
        profileRepository.setSellerProfile(sellerWith(contacts = listOf(office)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        profileRepository.setSellerProfile(sellerWith(contacts = emptyList()))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sellerContacts.isEmpty)
    }

    // ===== what the top bar does with it =====

    @Test
    fun `no published contacts means no top bar button`() = runTest {
        approve()
        profileRepository.setSellerProfile(sellerWith(contacts = emptyList()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        // topBarContacts empty is exactly the condition AppScaffold hides the
        // person icon on, so an empty menu can never be opened.
        assertTrue(viewModel.state.value.topBarContacts.isEmpty())
    }

    @Test
    fun `the top bar button appears as soon as the seller publishes one`() = runTest {
        approve()
        profileRepository.setSellerProfile(sellerWith(contacts = emptyList()))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.topBarContacts.isEmpty())

        profileRepository.setSellerProfile(sellerWith(contacts = listOf(office)))
        advanceUntilIdle()

        assertEquals(listOf(office), viewModel.state.value.topBarContacts)
    }

    @Test
    fun `the top bar button goes away again when the last contact is withdrawn`() = runTest {
        approve()
        profileRepository.setSellerProfile(sellerWith(contacts = listOf(office)))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.topBarContacts.isNotEmpty())

        profileRepository.setSellerProfile(sellerWith(contacts = emptyList()))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.topBarContacts.isEmpty())
    }

    @Test
    fun `a blank value is not a contact`() = runTest {
        approve()
        // A seller who cleared the field but left the row behind has published
        // nothing, so the button must stay hidden rather than dial an empty number.
        profileRepository.setSellerProfile(
            sellerWith(contacts = listOf(SellerContact(ContactChannel.PHONE, "")))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.topBarContacts.isEmpty())
    }

    // ===== the top bar and About cannot disagree =====

    @Test
    fun `the top bar and the About card read the same edit`() = runTest {
        approve()
        profileRepository.setSellerProfile(sellerWith(contacts = listOf(office)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        val mail = SellerContact(ContactChannel.EMAIL, "hallo@bodenschaetze.de")
        profileRepository.setSellerProfile(sellerWith(contacts = listOf(office, mail)))
        advanceUntilIdle()

        val state = viewModel.state.value
        // About is handed state.sellerContacts; the top bar reads topBarContacts,
        // which is derived from it. One edit, one source, both current.
        assertEquals(listOf(office, mail), state.sellerContacts.sellerContacts)
        assertEquals(listOf(office, mail), state.topBarContacts)
    }
}
