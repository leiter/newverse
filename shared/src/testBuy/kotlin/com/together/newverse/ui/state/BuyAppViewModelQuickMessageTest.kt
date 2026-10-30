package com.together.newverse.ui.state

import com.together.newverse.domain.model.ActionAudience
import com.together.newverse.domain.model.ActionKind
import com.together.newverse.domain.model.ActionLocation
import com.together.newverse.domain.model.ActionPersistence
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.SellerProfile
import com.together.newverse.domain.model.SellerUiAction
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeMarketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.FakeSellerConfig
import com.together.newverse.test.FakeSellerInboxRepository
import com.together.newverse.test.FakeSellerUiActionRepository
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A message box the seller published reaches the buyer, and what the buyer types in it
 * reaches the seller's inbox.
 *
 * The field itself is in `QuickMessageInput`; what is testable here is the part that
 * decides — which box is shown, what a sent message carries, and what makes it go
 * away. The last of those is the half of the feature a buyer most needs to work: a
 * standing text field in the title of a screen they open every day has to be
 * closeable, and has to stay closed.
 */
class BuyAppViewModelQuickMessageTest {

    private val dispatcherRule = MainDispatcherRule()

    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository
    private lateinit var marketRepository: FakeMarketRepository
    private lateinit var uiActionRepository: FakeSellerUiActionRepository
    private lateinit var inboxRepository: FakeSellerInboxRepository

    @BeforeTest
    fun setUp() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
        marketRepository = FakeMarketRepository()
        uiActionRepository = FakeSellerUiActionRepository()
        inboxRepository = FakeSellerInboxRepository()

        profileRepository.setSellerProfile(SellerProfile(sellerId = "demo_seller_id"))
        profileRepository.setBuyerProfile(
            BuyerProfile(
                id = "buyer_1",
                displayName = "Test Käufer",
                emailAddress = "t@example.com",
                telephoneNumber = "123",
                isSelfPickup = true
            )
        )
        authRepository.setCurrentUserId("buyer_1")
    }

    @AfterTest
    fun tearDown() {
        articleRepository.reset()
        orderRepository.reset()
        profileRepository.reset()
        authRepository.reset()
        basketRepository.reset()
        uiActionRepository.reset()
        inboxRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = BuyAppViewModel(
        articleRepository = articleRepository,
        orderRepository = orderRepository,
        profileRepository = profileRepository,
        authRepository = authRepository,
        basketRepository = basketRepository,
        sellerConfig = FakeSellerConfig(),
        marketRepository = marketRepository,
        sellerUiActionRepository = uiActionRepository,
        sellerInboxRepository = inboxRepository
    )

    private fun box(
        id: String = "q1",
        question: String = "Was fehlt euch im Angebot?",
        location: ActionLocation = ActionLocation.TOPBAR_HOME_INPUT,
        persistence: ActionPersistence = ActionPersistence.UNTIL_CANCELLED,
        audience: ActionAudience = ActionAudience.EVERYONE,
        isActive: Boolean = true,
    ) = SellerUiAction(
        id = id,
        kind = ActionKind.QUICK_MESSAGE,
        value = question,
        location = location,
        audience = audience,
        isActive = isActive,
        persistence = persistence,
    ).readyToStore()

    // ===== the box reaches the buyer =====

    @Test
    fun `a published box reaches the buy app`() = runTest {
        uiActionRepository.seed("demo_seller_id", box())

        val viewModel = createViewModel()
        advanceUntilIdle()

        val prompt = viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT)
        assertEquals("Was fehlt euch im Angebot?", prompt?.value)
    }

    @Test
    fun `a box published while the app is open appears without a restart`() = runTest {
        // The same subscription the buttons needed, and the same reason: a seller
        // publishes while buyers already have the app open.
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))

        uiActionRepository.seed("demo_seller_id", box())
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))
    }

    @Test
    fun `a box the seller deletes disappears without a restart`() = runTest {
        uiActionRepository.seed("demo_seller_id", box())
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))

        uiActionRepository.deleteAction("demo_seller_id", "q1")
        advanceUntilIdle()

        assertNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))
    }

    @Test
    fun `a box the seller switches off disappears without a restart`() = runTest {
        // Deactivating is not deleting, and the buyer must not be able to tell the
        // difference: both take the row off the screen of an app already open.
        uiActionRepository.seed("demo_seller_id", box())
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))

        uiActionRepository.seed("demo_seller_id", box(isActive = false))
        advanceUntilIdle()

        assertNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))
    }

    @Test
    fun `a box lands only in the slot it names`() = runTest {
        uiActionRepository.seed(
            "demo_seller_id",
            box(id = "q1", question = "Zuhause?", location = ActionLocation.TOPBAR_HOME_INPUT),
            box(id = "q2", question = "Im Profil?", location = ActionLocation.TOPBAR_PROFILE_INPUT)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Zuhause?", state.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT)?.value)
        assertEquals("Im Profil?", state.titlePromptFor(ActionLocation.TOPBAR_PROFILE_INPUT)?.value)
    }

    @Test
    fun `a box is never drawn as a button`() = runTest {
        // The announcement's rule, applied to the other non-button slot: an input is
        // reached through titlePromptFor and never through forLocation.
        uiActionRepository.seed("demo_seller_id", box())
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.topBarActionsFor(ActionLocation.TOPBAR_HOME).isEmpty())
        assertTrue(state.topBarActionsFor(ActionLocation.TOPBAR_HOME_INPUT).isEmpty())
    }

    // ===== sending =====

    @Test
    fun `a sent message carries the sender, the question and the kind of box`() = runTest {
        uiActionRepository.seed("demo_seller_id", box())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(
            BuyUiAction.SendPrompt(ActionLocation.TOPBAR_HOME_INPUT, "  Mehr Dinkelmehl  ")
        )
        advanceUntilIdle()

        val sent = inboxRepository.sent.single()
        assertEquals("demo_seller_id", sent.sellerId)
        assertEquals("buyer_1", sent.buyerId)
        assertEquals("Test Käufer", sent.buyerName)
        assertEquals(ActionKind.QUICK_MESSAGE, sent.requestKind)
        assertEquals("q1", sent.requestId)
        // Snapshotted, so the answer still means something after the seller rewords
        // or deletes the question.
        assertEquals("Was fehlt euch im Angebot?", sent.prompt)
        assertEquals("Mehr Dinkelmehl", sent.text)
    }

    @Test
    fun `a blank message is not sent`() = runTest {
        uiActionRepository.seed("demo_seller_id", box())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(BuyUiAction.SendPrompt(ActionLocation.TOPBAR_HOME_INPUT, "   "))
        advanceUntilIdle()

        assertTrue(inboxRepository.sent.isEmpty())
    }

    @Test
    fun `nothing is sent for a slot with no box`() = runTest {
        // The row went away between the tap and the dispatch. Nothing to answer.
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(BuyUiAction.SendPrompt(ActionLocation.TOPBAR_HOME_INPUT, "Hallo"))
        advanceUntilIdle()

        assertTrue(inboxRepository.sent.isEmpty())
    }

    @Test
    fun `a landed send is reported so the field can clear itself`() = runTest {
        uiActionRepository.seed("demo_seller_id", box())
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(0, viewModel.state.value.quickMessage.sentCount)

        viewModel.dispatch(BuyUiAction.SendPrompt(ActionLocation.TOPBAR_HOME_INPUT, "Dinkelmehl"))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.quickMessage.sentCount)
        assertFalse(viewModel.state.value.quickMessage.isSending)
        assertNull(viewModel.state.value.quickMessage.error)
    }

    @Test
    fun `a refused send is reported and the box stays`() = runTest {
        // The one failure the buyer cannot see: a wish that went nowhere. The box and
        // the typed text stay so they can try again.
        uiActionRepository.seed("demo_seller_id", box())
        inboxRepository.shouldFailSend = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(BuyUiAction.SendPrompt(ActionLocation.TOPBAR_HOME_INPUT, "Dinkelmehl"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.quickMessage.error)
        assertFalse(state.quickMessage.isSending)
        assertEquals(0, state.quickMessage.sentCount)
        assertNotNull(state.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))
    }

    @Test
    fun `a reported failure is cleared once it has been shown`() = runTest {
        uiActionRepository.seed("demo_seller_id", box())
        inboxRepository.shouldFailSend = true
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(BuyUiAction.SendPrompt(ActionLocation.TOPBAR_HOME_INPUT, "Dinkelmehl"))
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.quickMessage.error)

        viewModel.dispatch(BuyUiAction.ClearPromptError)
        advanceUntilIdle()

        assertNull(viewModel.state.value.quickMessage.error)
    }

    // ===== going away =====

    @Test
    fun `the buyer can close the box`() = runTest {
        uiActionRepository.seed("demo_seller_id", box())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(BuyUiAction.DismissPrompt(ActionLocation.TOPBAR_HOME_INPUT))
        advanceUntilIdle()

        assertNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))
    }

    @Test
    fun `closing one input slot leaves the other alone`() = runTest {
        uiActionRepository.seed(
            "demo_seller_id",
            box(id = "q1", location = ActionLocation.TOPBAR_HOME_INPUT),
            box(id = "q2", location = ActionLocation.TOPBAR_PROFILE_INPUT)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(BuyUiAction.DismissPrompt(ActionLocation.TOPBAR_HOME_INPUT))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))
        assertNotNull(state.titlePromptFor(ActionLocation.TOPBAR_PROFILE_INPUT))
    }

    @Test
    fun `a box set to stay survives a sent message`() = runTest {
        // The default, and the wishbox case: a buyer with a second wish must be able
        // to send it.
        uiActionRepository.seed("demo_seller_id", box())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(BuyUiAction.SendPrompt(ActionLocation.TOPBAR_HOME_INPUT, "Dinkelmehl"))
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))
    }

    @Test
    fun `a box marked hide-on-action goes once it is answered`() = runTest {
        uiActionRepository.seed(
            "demo_seller_id",
            box(persistence = ActionPersistence.HIDE_ON_ACTION)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(BuyUiAction.SendPrompt(ActionLocation.TOPBAR_HOME_INPUT, "Ja, Samstag"))
        advanceUntilIdle()

        assertEquals(1, inboxRepository.sent.size)
        assertNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))
    }

    @Test
    fun `a refused send does not hide a hide-on-action box`() = runTest {
        // Honoured only on success: a buyer whose answer never arrived has not
        // answered, and must still be able to.
        uiActionRepository.seed(
            "demo_seller_id",
            box(persistence = ActionPersistence.HIDE_ON_ACTION)
        )
        inboxRepository.shouldFailSend = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(BuyUiAction.SendPrompt(ActionLocation.TOPBAR_HOME_INPUT, "Ja, Samstag"))
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))
    }

    @Test
    fun `re-wording the question asks a buyer who already answered`() = runTest {
        uiActionRepository.seed(
            "demo_seller_id",
            box(persistence = ActionPersistence.HIDE_ON_ACTION)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(BuyUiAction.SendPrompt(ActionLocation.TOPBAR_HOME_INPUT, "Ja"))
        advanceUntilIdle()
        assertNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))

        // Same row id, new wording — a different question, so it is asked again. This
        // is the silent-stale-configuration failure this feature has shipped twice.
        uiActionRepository.seed(
            "demo_seller_id",
            box(
                question = "Kommt ihr nächsten Samstag?",
                persistence = ActionPersistence.HIDE_ON_ACTION
            )
        )
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT))
    }

    // ===== published buttons, now that they can hide too =====

    @Test
    fun `a button marked hide-on-action goes once it is tapped`() = runTest {
        val button = SellerUiAction(
            id = "a1",
            label = "Umfrage",
            value = "example.org/umfrage",
            location = ActionLocation.TOPBAR_HOME,
            persistence = ActionPersistence.HIDE_ON_ACTION,
        )
        uiActionRepository.seed("demo_seller_id", button)
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_HOME).size)

        viewModel.dispatch(BuyUiAction.NotePublishedActionUsed(button))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_HOME).isEmpty())
    }

    @Test
    fun `an ordinary button survives being tapped`() = runTest {
        // Every tap is reported and most of them mean nothing, which is the point:
        // the renderer does not have to know what the flag means.
        val button = SellerUiAction(
            id = "a1",
            label = "Wünsche",
            value = "example.org",
            location = ActionLocation.TOPBAR_HOME,
        )
        uiActionRepository.seed("demo_seller_id", button)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(BuyUiAction.NotePublishedActionUsed(button))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_HOME).size)
    }
}
