package com.together.newverse.ui.state

import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerConfig
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.ui.state.buy.connectWithToken
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Asking for access is a question, not a qualification.
 *
 * Tapping *Zugang anfragen* used to check the buyer's profile and, if anything was
 * missing, refuse — raising a dialog that sent them off to a form. The form asked for
 * a pickup time and a street address, neither of which has anything to do with the
 * decision the seller is about to make. Now the tap opens a sheet that asks the two
 * things that decision does rest on: a name, and optionally a line the seller can
 * place the buyer by.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelAccessRequestSheetTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private val sellerConfig = FakeSellerConfig()

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        authRepository.setCurrentUserId("buyer_1")
        profileRepository.setSellerProfile(SellerProfile(sellerId = sellerConfig.sellerId))
    }

    @AfterTest
    fun tearDown() {
        profileRepository.reset()
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = BuyAppViewModel(
        articleRepository = FakeArticleRepository(),
        orderRepository = FakeOrderRepository(),
        profileRepository = profileRepository,
        authRepository = authRepository,
        basketRepository = FakeBasketRepository(),
        sellerConfig = sellerConfig
    )

    /**
     * A buyer with nothing filled in but a name — which, before this, was exactly the
     * buyer the old gate turned away: `isSelfPickup` defaults to false, so the profile
     * owed a street and a house number it had no reason to hold.
     */
    private fun seedProfile(displayName: String = "Alice") {
        profileRepository.setBuyerProfile(
            BuyerProfile(
                id = "buyer_1",
                displayName = displayName,
                emailAddress = "a@example.com"
            )
        )
    }

    private fun BuyAppViewModel.openSheet() = dispatch(BuySellerAction.RequestAccess)

    @Test
    fun `the sheet opens pre-filled with the profile name`() = runTest {
        seedProfile("Alice")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openSheet()

        val draft = viewModel.state.value.accessRequestDraft
        assertNotNull(draft, "tapping the button opens the sheet rather than sending")
        assertEquals("Alice", draft.name)
        assertEquals("", draft.note)
        assertTrue(draft.canSubmit)
    }

    @Test
    fun `an empty profile no longer blocks the request, it just asks`() = runTest {
        // No profile at all. The old gate refused here and raised a dialog sending
        // the buyer to the profile form; that dialog has since been deleted outright,
        // so the assertion left to make is that the sheet opened and is empty.
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openSheet()

        val draft = viewModel.state.value.accessRequestDraft
        assertNotNull(draft, "the request is asked for, not refused")
        assertEquals("", draft.name)
    }

    @Test
    fun `submitting sends the name and the note to the seller`() = runTest {
        seedProfile("Alice")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openSheet()
        viewModel.dispatch(BuySellerAction.UpdateAccessRequestNote("war Donnerstag am Stand"))
        viewModel.dispatch(BuySellerAction.SubmitAccessRequest)
        advanceUntilIdle()

        val request = profileRepository.accessRequests.single()
        assertEquals("Alice", request.buyerDisplayName)
        assertEquals("war Donnerstag am Stand", request.note)
        assertNull(
            viewModel.state.value.accessRequestDraft,
            "a sent request closes the sheet"
        )
        assertEquals(false, viewModel.state.value.isRequestingAccess)
    }

    @Test
    fun `a note is optional`() = runTest {
        seedProfile("Alice")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openSheet()
        viewModel.dispatch(BuySellerAction.SubmitAccessRequest)
        advanceUntilIdle()

        assertEquals("", profileRepository.accessRequests.single().note)
    }

    @Test
    fun `a blank name is refused in the sheet, not by sending nothing`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openSheet()
        viewModel.dispatch(BuySellerAction.UpdateAccessRequestName("   "))
        viewModel.dispatch(BuySellerAction.SubmitAccessRequest)
        advanceUntilIdle()

        val draft = viewModel.state.value.accessRequestDraft
        assertNotNull(draft, "the sheet stays open on the field that needs answering")
        assertTrue(draft.nameError)
        assertTrue(
            profileRepository.accessRequests.isEmpty(),
            "nothing was sent"
        )
    }

    @Test
    fun `typing clears the name error`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openSheet()
        viewModel.dispatch(BuySellerAction.SubmitAccessRequest)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.accessRequestDraft!!.nameError)

        viewModel.dispatch(BuySellerAction.UpdateAccessRequestName("B"))

        assertEquals(false, viewModel.state.value.accessRequestDraft!!.nameError)
    }

    @Test
    fun `the name given in the sheet becomes the profile default`() = runTest {
        seedProfile("Alice")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openSheet()
        viewModel.dispatch(BuySellerAction.UpdateAccessRequestName("Alice Müller"))
        viewModel.dispatch(BuySellerAction.SubmitAccessRequest)
        advanceUntilIdle()

        assertEquals(
            "Alice Müller",
            profileRepository.getBuyerProfile().getOrNull()?.displayName,
            "the name is kept so the sheet is pre-filled next time and the seller's " +
                "customer list agrees with the request they approved"
        )
        assertEquals(
            "Alice Müller",
            profileRepository.accessRequests.single().buyerDisplayName
        )
    }

    @Test
    fun `dismissing sends nothing and leaves the profile alone`() = runTest {
        seedProfile("Alice")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openSheet()
        viewModel.dispatch(BuySellerAction.UpdateAccessRequestName("Someone Else"))
        viewModel.dispatch(BuySellerAction.DismissAccessRequest)
        advanceUntilIdle()

        assertNull(viewModel.state.value.accessRequestDraft)
        assertTrue(profileRepository.accessRequests.isEmpty())
        assertEquals("Alice", profileRepository.getBuyerProfile().getOrNull()?.displayName)
    }

    @Test
    fun `the request names the market the buyer chose`() = runTest {
        seedProfile("Alice")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openSheet()
        viewModel.dispatch(BuySellerAction.SubmitAccessRequest)
        advanceUntilIdle()

        // Whatever contextualMarketId resolved to is what was asked for; the point is
        // that composing the request in a sheet did not lose it.
        assertEquals(
            viewModel.state.value.contextualMarketId.orEmpty(),
            profileRepository.lastRequestedMarketId
        )
    }

    @Test
    fun `a buyer with no name arriving by QR link is asked, then redeemed`() = runTest {
        // No profile name. The token already proves the seller authorised them, so
        // there is nothing to qualify for — but the access record carries a name, so
        // the same sheet asks for one and holds the token while it does.
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.connectWithToken(sellerConfig.sellerId, "tok1")
        advanceUntilIdle()

        val draft = viewModel.state.value.accessRequestDraft
        assertNotNull(draft, "asked for a name rather than bounced to the profile form")
        assertEquals(sellerConfig.sellerId to "tok1", draft.pendingToken)

        viewModel.dispatch(BuySellerAction.UpdateAccessRequestName("Bob"))
        viewModel.dispatch(BuySellerAction.SubmitAccessRequest)
        advanceUntilIdle()

        assertNull(viewModel.state.value.accessRequestDraft)
        assertEquals(
            AccessStatus.APPROVED,
            profileRepository.getAccessStatus(sellerConfig.sellerId),
            "submitting redeemed the token instead of sending a request"
        )
        assertTrue(
            profileRepository.accessRequests.isEmpty(),
            "a redemption is not a request"
        )
    }
}
