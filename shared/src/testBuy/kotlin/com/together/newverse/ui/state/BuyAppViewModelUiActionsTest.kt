package com.together.newverse.ui.state

import com.together.newverse.domain.model.ActionAudience
import com.together.newverse.domain.model.ActionLocation
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
import com.together.newverse.test.FakeSellerUiActionRepository
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Buttons the seller published reach the buy app's state, in the slot they name.
 *
 * The rendering itself is in `AppScaffold`; what is testable here is that the actions
 * are loaded at startup and sorted into slots — and that a record this version cannot
 * render is dropped rather than shown somewhere arbitrary.
 */
class BuyAppViewModelUiActionsTest {

    private val dispatcherRule = MainDispatcherRule()

    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository
    private lateinit var marketRepository: FakeMarketRepository
    private lateinit var uiActionRepository: FakeSellerUiActionRepository

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

        profileRepository.setSellerProfile(SellerProfile(sellerId = "demo_seller_id"))
        profileRepository.setBuyerProfile(
            BuyerProfile(
                id = "buyer_1",
                displayName = "Test",
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
        sellerUiActionRepository = uiActionRepository
    )

    private fun action(
        id: String,
        label: String,
        location: ActionLocation = ActionLocation.TOPBAR_PROFILE,
        value: String = "example.org"
    ) = SellerUiAction(id = id, label = label, value = value, location = location)

    @Test
    fun `a published button reaches the buy app`() = runTest {
        uiActionRepository.seed("demo_seller_id", action("a1", "Wünsche"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val onProfile = viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_PROFILE)
        assertEquals(listOf("Wünsche"), onProfile.map { it.label })
        assertEquals("https://example.org", onProfile.single().toUri())
    }

    @Test
    fun `a button published while the app is open appears without a restart`() = runTest {
        // The bug this feature shipped with: the actions were read once at startup, so
        // the first button published on dev did not appear until the app was killed.
        // Configuration that needs a restart to take effect is not configuration.
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_HOME).isEmpty())

        uiActionRepository.seed("demo_seller_id", action("a1", "Wünsche", ActionLocation.TOPBAR_HOME))
        advanceUntilIdle()

        assertEquals(
            listOf("Wünsche"),
            viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_HOME).map { it.label }
        )
    }

    @Test
    fun `a button the seller removes disappears without a restart`() = runTest {
        uiActionRepository.seed("demo_seller_id", action("a1", "Wünsche", ActionLocation.TOPBAR_HOME))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_HOME).size)

        uiActionRepository.deleteAction("demo_seller_id", "a1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_HOME).isEmpty())
    }

    @Test
    fun `a button lands only in the slot it names`() = runTest {
        uiActionRepository.seed(
            "demo_seller_id",
            action("a1", "Wünsche", ActionLocation.TOPBAR_PROFILE),
            action("a2", "Angebote", ActionLocation.TOPBAR_HOME)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("Wünsche"), state.topBarActionsFor(ActionLocation.TOPBAR_PROFILE).map { it.label })
        assertEquals(listOf("Angebote"), state.topBarActionsFor(ActionLocation.TOPBAR_HOME).map { it.label })
    }

    @Test
    fun `a seller who published nothing gets no buttons`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_PROFILE).isEmpty())
        assertTrue(viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_HOME).isEmpty())
    }

    @Test
    fun `an unapproved buyer still sees the published buttons`() = runTest {
        // Unlike the contact shortcut, which is hidden until approval: a link the
        // seller chose to publish is what they want seen, and a button that appears
        // only after approval reads as broken to a new install.
        uiActionRepository.seed("demo_seller_id", action("a1", "Wünsche"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isDemoMode, "fixture is meant to be unapproved")
        assertEquals(1, viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_PROFILE).size)
    }

    @Test
    fun `a slot shows no more than it has room for`() = runTest {
        uiActionRepository.seed(
            "demo_seller_id",
            *(1..6).map { action("a$it", "Knopf $it") }.toTypedArray()
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            SellerUiAction.MAX_PER_LOCATION,
            viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_PROFILE).size
        )
    }

    @Test
    fun `an approved-only button is withheld from a demo buyer`() = runTest {
        // The fixture buyer is unapproved, which is what isDemoMode reports.
        uiActionRepository.seed(
            "demo_seller_id",
            SellerUiAction(
                id = "a1", label = "Wochenliste", value = "example.org",
                location = ActionLocation.TOPBAR_PROFILE,
                audience = ActionAudience.APPROVED_ONLY
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isDemoMode, "fixture is meant to be unapproved")
        assertTrue(viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_PROFILE).isEmpty())
    }

    @Test
    fun `a demo-only button reaches a demo buyer`() = runTest {
        uiActionRepository.seed(
            "demo_seller_id",
            SellerUiAction(
                id = "a1", label = "Freischalten", value = "example.org",
                location = ActionLocation.TOPBAR_PROFILE,
                audience = ActionAudience.DEMO_ONLY
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf("Freischalten"),
            viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_PROFILE).map { it.label }
        )
    }

    @Test
    fun `an unusable record is not rendered`() = runTest {
        uiActionRepository.seed(
            "demo_seller_id",
            action("a1", label = ""),
            action("a2", label = "Gut", value = "")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_PROFILE).isEmpty())
    }

    // ===== the home-title announcement =====

    private fun announcement(id: String, text: String) = SellerUiAction(
        id = id,
        location = ActionLocation.TOPBAR_HOME_TITLE,
        value = text,
    ).readyToStore()

    @Test
    fun `an announcement the seller publishes reaches the home title`() = runTest {
        uiActionRepository.seed("demo_seller_id", announcement("n1", "Heute ab 9: Bodenschätze"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            "Heute ab 9: Bodenschätze",
            viewModel.state.value.homeTitleAnnouncement?.value
        )
    }

    @Test
    fun `an announcement is not drawn as a button`() = runTest {
        // The title slot is reached through homeTitleAnnouncement only. Were it to
        // reach topBarActionsFor as well, the buyer would get the announcement twice:
        // once as the title and once as a TextButton labelled with its opening words.
        uiActionRepository.seed("demo_seller_id", announcement("n1", "Heute ab 9: Bodenschätze"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_HOME).isEmpty())
        assertTrue(viewModel.state.value.topBarActionsFor(ActionLocation.TOPBAR_PROFILE).isEmpty())
    }

    @Test
    fun `the buyer can close an announcement`() = runTest {
        uiActionRepository.seed("demo_seller_id", announcement("n1", "Heute ab 9: Bodenschätze"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.homeTitleAnnouncement != null)

        viewModel.dispatch(BuyUiAction.DismissHomeAnnouncement)
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.homeTitleAnnouncement)
    }

    @Test
    fun `a closed announcement stays closed while the seller leaves it published`() = runTest {
        uiActionRepository.seed("demo_seller_id", announcement("n1", "Heute ab 9: Bodenschätze"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(BuyUiAction.DismissHomeAnnouncement)
        advanceUntilIdle()

        // The live subscription re-emits the same row. A dismissal that did not
        // survive that would last until the next snapshot, which is no time at all.
        uiActionRepository.seed("demo_seller_id", announcement("n1", "Heute ab 9: Bodenschätze"))
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.homeTitleAnnouncement)
    }

    @Test
    fun `re-wording an announcement brings it back`() = runTest {
        // The reason the dismissal key hashes the text. The seller corrects the time
        // on the row they already have; every buyer who closed the old one must see
        // the new one, or the correction reaches nobody.
        uiActionRepository.seed("demo_seller_id", announcement("n1", "Heute ab 9 Uhr"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(BuyUiAction.DismissHomeAnnouncement)
        advanceUntilIdle()
        assertEquals(null, viewModel.state.value.homeTitleAnnouncement)

        uiActionRepository.seed("demo_seller_id", announcement("n1", "Heute ab 10 Uhr"))
        advanceUntilIdle()

        assertEquals("Heute ab 10 Uhr", viewModel.state.value.homeTitleAnnouncement?.value)
    }

    @Test
    fun `a narrowed announcement waits for the access status`() = runTest {
        uiActionRepository.seed(
            "demo_seller_id",
            announcement("n1", "Nur für Freigeschaltete")
                .copy(audience = ActionAudience.APPROVED_ONLY)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        // This buyer is not approved in this fixture, so the announcement is absent
        // rather than shown and withdrawn once the status lands.
        assertEquals(null, viewModel.state.value.homeTitleAnnouncement)
    }

    @Test
    fun `an announcement the seller withdraws disappears without a restart`() = runTest {
        uiActionRepository.seed("demo_seller_id", announcement("n1", "Heute ab 9: Bodenschätze"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.homeTitleAnnouncement != null)

        uiActionRepository.deleteAction("demo_seller_id", "n1")
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.homeTitleAnnouncement)
    }
}
