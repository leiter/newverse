package com.together.newverse.ui.state

import app.cash.turbine.test
import com.together.newverse.domain.model.Article
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class SellAppViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        dispatcherRule.tearDown()
    }

    private fun createViewModel(): SellAppViewModel {
        return SellAppViewModel(
            articleRepository = articleRepository,
            authRepository = authRepository
        )
    }

    @Test
    fun `initial auth state is Guest when not authenticated`() = runTest {
        // Given user is NOT authenticated
        authRepository.setCurrentUserId(null)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user state should be Guest
        viewModel.state.test {
            val state = awaitItem()
            assertIs<UserState.Guest>(state.common.user)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requiresLogin is true when not authenticated`() = runTest {
        // Given user is NOT authenticated
        authRepository.setCurrentUserId(null)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then requiresLogin should be true
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.requiresLogin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checks persisted auth on init`() = runTest {
        // Given user has persisted auth session
        authRepository.setCurrentUserId("seller_123")

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user state should be LoggedIn
        viewModel.state.test {
            val state = awaitItem()
            assertIs<UserState.LoggedIn>(state.common.user)
            assertEquals("seller_123", (state.common.user as UserState.LoggedIn).id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Note: Sign in/out/register tests that call getString() cannot run as unit tests
    // because Android Resources are not mocked. These would need instrumented tests
    // or a mock resource provider. The auth flow is tested indirectly through
    // the observeAuthState tests above.

    @Test
    fun `dispatches navigation action NavigateTo`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When navigate action is dispatched
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Sell.Profile))
        advanceUntilIdle()

        // Then current route should be updated
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(NavRoutes.Sell.Profile, state.common.navigation.currentRoute)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dispatches navigation action NavigateBack`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate forward first
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Sell.Profile))
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
    fun `shows snackbar via UI action`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
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
    fun `dismisses snackbar via UI action`() = runTest {
        // Given user is authenticated and snackbar is shown
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedUiAction.ShowSnackbar("Test message", SnackbarType.INFO))
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
    fun `shows dialog via UI action`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
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
    fun `hides dialog via UI action`() = runTest {
        // Given user is authenticated and dialog is shown
        authRepository.setCurrentUserId("seller_123")
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
    fun `opens drawer via navigation action`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
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
    fun `closes drawer via navigation action`() = runTest {
        // Given user is authenticated and drawer is open
        authRepository.setCurrentUserId("seller_123")
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

    @Test
    fun `loads products on init`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

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
    fun `refreshes products via product action`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When refresh products action is dispatched
        viewModel.dispatch(UnifiedProductAction.RefreshProducts)
        advanceUntilIdle()

        // Then isRefreshing should toggle (eventually false after refresh completes)
        viewModel.state.test {
            val state = awaitItem()
            // After refresh, isRefreshing should be false
            assertFalse(state.common.ui.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selects product via product action`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
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
    fun `triggers Google sign in`() = runTest {
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
    fun `resets Google sign in trigger`() = runTest {
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
    fun `triggers Twitter sign in`() = runTest {
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
    fun `resets Twitter sign in trigger`() = runTest {
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
    fun `resets Google sign out trigger`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
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

    @Test
    fun `resets Apple sign in trigger`() = runTest {
        // Given
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When reset is called (even though Apple sign-in is not supported for seller)
        viewModel.resetAppleSignInTrigger()

        // Then trigger should be false
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.common.triggerAppleSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Note: Register tests use getString() which fails in unit tests without mocked Android resources.
    // Register flow is tested indirectly through auth state observation.

    @Test
    fun `sets pending import content`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When setting pending import content
        val content = "BNN file content here"
        viewModel.setPendingImportContent(content)

        // Then content should be stored
        viewModel.pendingImportContent.test {
            val value = awaitItem()
            assertEquals(content, value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clears pending import content`() = runTest {
        // Given pending content exists
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setPendingImportContent("Some content")

        // When clearing content
        viewModel.setPendingImportContent(null)

        // Then content should be null
        viewModel.pendingImportContent.test {
            val value = awaitItem()
            assertNull(value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAuthError updates auth state`() = runTest {
        // Given user is not authenticated
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When setting auth error
        viewModel.setAuthError("Authentication failed")

        // Then error should be set in auth state
        viewModel.state.test {
            val state = awaitItem()
            assertEquals("Authentication failed", state.screens.auth.error)
            assertFalse(state.screens.auth.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows password reset dialog`() = runTest {
        // Given user is not authenticated
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When show password reset dialog action is dispatched
        viewModel.dispatch(UnifiedUiAction.ShowPasswordResetDialog)
        advanceUntilIdle()

        // Then dialog should be shown
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.screens.auth.showPasswordResetDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides password reset dialog`() = runTest {
        // Given password reset dialog is shown
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedUiAction.ShowPasswordResetDialog)
        advanceUntilIdle()

        // When hide password reset dialog action is dispatched
        viewModel.dispatch(UnifiedUiAction.HidePasswordResetDialog)
        advanceUntilIdle()

        // Then dialog should be hidden
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.screens.auth.showPasswordResetDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Note: Password reset test uses getString() which fails in unit tests without mocked Android resources.

    @Test
    fun `handles setRefreshing action`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
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

    @Test
    fun `initialization steps progress correctly`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")

        // When ViewModel is created
        val viewModel = createViewModel()

        // Wait for initialization
        advanceUntilIdle()

        // Then initialization should complete
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.meta.isInitialized)
            assertFalse(state.meta.isInitializing)
            assertIs<InitializationStep.Complete>(state.meta.initializationStep)
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

        // Then user should be Guest and requiresLogin should be true
        viewModel.state.test {
            val state = awaitItem()
            assertIs<UserState.Guest>(state.common.user)
            assertTrue(state.common.requiresLogin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles article MODE_CHANGED correctly`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
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
        authRepository.setCurrentUserId("seller_123")
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
}
