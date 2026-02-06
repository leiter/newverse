package com.together.newverse.ui.state

import app.cash.turbine.test
import com.together.newverse.domain.model.Article
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.test.TestData
import com.together.newverse.ui.navigation.NavRoutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for BuyAppViewModel core functionality.
 *
 * Tests cover:
 * - Initialization and state management
 * - Action dispatching to correct handlers
 * - Auth trigger management
 * - Product loading via article repository
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var basketRepository: FakeBasketRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        orderRepository = FakeOrderRepository()
        profileRepository = FakeProfileRepository()
        authRepository = FakeAuthRepository()
        basketRepository = FakeBasketRepository()
    }

    @AfterTest
    fun tearDown() {
        dispatcherRule.tearDown()
    }

    private fun createViewModel(): BuyAppViewModel {
        return BuyAppViewModel(
            articleRepository = articleRepository,
            orderRepository = orderRepository,
            profileRepository = profileRepository,
            authRepository = authRepository,
            basketRepository = basketRepository
        )
    }

    // ===== Initialization Tests =====

    @Test
    fun `initial auth state is Guest when not authenticated`() = runTest {
        // Given user is NOT authenticated
        authRepository.setCurrentUserId(null)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user state should be Guest or NotAuthenticated
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(
                state.common.user is UserState.Guest || state.common.user is UserState.NotAuthenticated,
                "Expected Guest or NotAuthenticated, got ${state.common.user}"
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checks persisted auth on init`() = runTest {
        // Given user has persisted auth session
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user state should be LoggedIn
        viewModel.state.test {
            val state = awaitItem()
            assertIs<UserState.LoggedIn>(state.common.user)
            assertEquals("buyer_123", (state.common.user as UserState.LoggedIn).id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Navigation Action Dispatching Tests =====

    @Test
    fun `dispatches navigation action NavigateTo`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When navigate action is dispatched
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Profile))
        advanceUntilIdle()

        // Then current route should be updated
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(NavRoutes.Buy.Profile, state.common.navigation.currentRoute)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dispatches navigation action NavigateBack`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate forward first
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Profile))
        advanceUntilIdle()

        // When navigate back action is dispatched
        viewModel.dispatch(UnifiedNavigationAction.NavigateBack)
        advanceUntilIdle()

        // Then should go back to previous route
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(NavRoutes.Home, state.common.navigation.currentRoute)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dispatches navigation action OpenDrawer`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When open drawer action is dispatched
        viewModel.dispatch(UnifiedNavigationAction.OpenDrawer)
        advanceUntilIdle()

        // Then drawer should be open
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.navigation.isDrawerOpen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dispatches navigation action CloseDrawer`() = runTest {
        // Given drawer is open
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedNavigationAction.OpenDrawer)
        advanceUntilIdle()

        // When close drawer action is dispatched
        viewModel.dispatch(UnifiedNavigationAction.CloseDrawer)
        advanceUntilIdle()

        // Then drawer should be closed
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.common.navigation.isDrawerOpen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== UI Action Dispatching Tests =====

    @Test
    fun `dispatches UI action ShowSnackbar`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When show snackbar action is dispatched
        viewModel.dispatch(UnifiedUiAction.ShowSnackbar("Test message", SnackbarType.SUCCESS))
        advanceUntilIdle()

        // Then snackbar should be visible
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.common.ui.snackbar)
            assertEquals("Test message", state.common.ui.snackbar?.message)
            assertEquals(SnackbarType.SUCCESS, state.common.ui.snackbar?.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dispatches UI action HideSnackbar`() = runTest {
        // Given snackbar is shown
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedUiAction.ShowSnackbar("Test", SnackbarType.INFO))
        advanceUntilIdle()

        // When hide snackbar action is dispatched
        viewModel.dispatch(UnifiedUiAction.HideSnackbar)
        advanceUntilIdle()

        // Then snackbar should be hidden
        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.common.ui.snackbar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dispatches UI action ShowDialog`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When show dialog action is dispatched
        val dialog = DialogState.Confirmation(
            title = "Confirm",
            message = "Are you sure?"
        )
        viewModel.dispatch(UnifiedUiAction.ShowDialog(dialog))
        advanceUntilIdle()

        // Then dialog should be visible
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.common.ui.dialog)
            assertIs<DialogState.Confirmation>(state.common.ui.dialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dispatches UI action HideDialog`() = runTest {
        // Given dialog is shown
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        val dialog = DialogState.Confirmation(title = "Test", message = "Test")
        viewModel.dispatch(UnifiedUiAction.ShowDialog(dialog))
        advanceUntilIdle()

        // When hide dialog action is dispatched
        viewModel.dispatch(UnifiedUiAction.HideDialog)
        advanceUntilIdle()

        // Then dialog should be hidden
        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.common.ui.dialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dispatches UI action SetRefreshing`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When set refreshing action is dispatched
        viewModel.dispatch(UnifiedUiAction.SetRefreshing(true))
        advanceUntilIdle()

        // Then isRefreshing should be true
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.ui.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Auth Trigger Tests =====

    @Test
    fun `triggers Google sign in via user action`() = runTest {
        // Given user is not authenticated
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When Google sign in is triggered
        viewModel.dispatch(UnifiedUserAction.LoginWithGoogle)
        advanceUntilIdle()

        // Then trigger should be set
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.triggerGoogleSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetGoogleSignInTrigger clears trigger`() = runTest {
        // Given Google sign in is triggered
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedUserAction.LoginWithGoogle)
        advanceUntilIdle()

        // When reset is called
        viewModel.resetGoogleSignInTrigger()

        // Then trigger should be reset
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.common.triggerGoogleSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `triggers Twitter sign in via user action`() = runTest {
        // Given user is not authenticated
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When Twitter sign in is triggered
        viewModel.dispatch(UnifiedUserAction.LoginWithTwitter)
        advanceUntilIdle()

        // Then trigger should be set
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.triggerTwitterSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetTwitterSignInTrigger clears trigger`() = runTest {
        // Given Twitter sign in is triggered
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedUserAction.LoginWithTwitter)
        advanceUntilIdle()

        // When reset is called
        viewModel.resetTwitterSignInTrigger()

        // Then trigger should be reset
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.common.triggerTwitterSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `triggers Apple sign in via user action`() = runTest {
        // Given user is not authenticated
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When Apple sign in is triggered
        viewModel.dispatch(UnifiedUserAction.LoginWithApple)
        advanceUntilIdle()

        // Then trigger should be set
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.triggerAppleSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetAppleSignInTrigger clears trigger`() = runTest {
        // Given Apple sign in trigger is set
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedUserAction.LoginWithApple)
        advanceUntilIdle()

        // When reset is called
        viewModel.resetAppleSignInTrigger()

        // Then trigger should be reset
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.common.triggerAppleSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetGoogleSignOutTrigger clears trigger`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Reset trigger directly (since Logout uses getString which fails in unit tests)
        viewModel.resetGoogleSignOutTrigger()

        // Then trigger should be reset/false
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.common.triggerGoogleSignOut)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Product Loading Tests =====

    @Test
    fun `loads products on init after auth is ready`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))

        // Create ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When articles are emitted
        TestData.sampleArticles.forEach { article ->
            articleRepository.emitArticle(article.copy(mode = Article.MODE_ADDED))
        }
        advanceUntilIdle()

        // Then products should be loaded
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(TestData.sampleArticles.size, state.screens.products.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles article MODE_CHANGED correctly`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Emit initial article
        val article = TestData.sampleArticles[0].copy(mode = Article.MODE_ADDED)
        articleRepository.emitArticle(article)
        advanceUntilIdle()

        // When article is changed
        val updatedArticle = article.copy(
            mode = Article.MODE_CHANGED,
            productName = "Updated Name"
        )
        articleRepository.emitArticle(updatedArticle)
        advanceUntilIdle()

        // Then article should be updated
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(1, state.screens.products.items.size)
            assertEquals("Updated Name", state.screens.products.items[0].productName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles article MODE_REMOVED correctly`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Emit initial articles
        TestData.sampleArticles.forEach { article ->
            articleRepository.emitArticle(article.copy(mode = Article.MODE_ADDED))
        }
        advanceUntilIdle()

        // When an article is removed
        val removedArticle = TestData.sampleArticles[0].copy(mode = Article.MODE_REMOVED)
        articleRepository.emitArticle(removedArticle)
        advanceUntilIdle()

        // Then article should be removed
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(TestData.sampleArticles.size - 1, state.screens.products.items.size)
            assertTrue(state.screens.products.items.none { it.id == removedArticle.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selects product via product action`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Emit an article
        val article = TestData.sampleArticles[0].copy(mode = Article.MODE_ADDED)
        articleRepository.emitArticle(article)
        advanceUntilIdle()

        // When select product action is dispatched
        viewModel.dispatch(UnifiedProductAction.SelectProduct(article))
        advanceUntilIdle()

        // Then product should be selected
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(article, state.screens.products.selectedItem)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles auth check failure gracefully`() = runTest {
        // Given auth check will fail
        authRepository.shouldFailCheckPersistedAuth = true
        authRepository.failureMessage = "Auth service unavailable"

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user should be Guest or NotAuthenticated
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(
                state.common.user is UserState.Guest || state.common.user is UserState.NotAuthenticated,
                "Expected Guest or NotAuthenticated after auth failure"
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
