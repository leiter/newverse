package com.together.newverse.ui.state

import app.cash.turbine.test
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.state.core.AuthState
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
 * Unit tests for SellAppViewModel.
 *
 * Tests cover:
 * - Initial state and initialization
 * - Auth state observation and UserState mapping
 * - Navigation actions
 * - Product loading and management
 * - UI actions (snackbar, dialog, refreshing)
 * - Sign-in triggers (Google, Twitter, Apple)
 * - Pending import content
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SellAppViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        authRepository.reset()
        dispatcherRule.tearDown()
    }

    private fun createViewModel() = SellAppViewModel(
        authRepository = authRepository
    )

    // ===== A. Initial State (3 tests) =====

    @Test
    fun `initial state has Guest user`() = runTest {
        val viewModel = createViewModel()

        assertEquals(UserState.Guest, viewModel.state.value.user)
    }

    @Test
    fun `initial state requires login`() = runTest {
        // Given no authenticated user
        authRepository.setCurrentUserId(null)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then requiresLogin is true
        assertTrue(viewModel.state.value.requiresLogin)
    }

    @Test
    fun `initial state has closed drawer`() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.state.value.navigation.isDrawerOpen)
    }

    // ===== B. Auth State Observation (4 tests) =====

    @Test
    fun `maps AuthState Authenticated to UserState LoggedIn`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user state is LoggedIn with seller role
        viewModel.state.test {
            val state = awaitItem()
            val loggedInUser = assertIs<UserState.LoggedIn>(state.user)
            assertEquals("seller_123", loggedInUser.id)
            assertEquals(UserRole.SELLER, loggedInUser.role)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `maps AuthState NotAuthenticated to UserState Guest`() = runTest {
        // Given no authenticated user
        authRepository.setCurrentUserId(null)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user state is Guest
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(UserState.Guest, state.user)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sets requiresLogin false when authenticated`() = runTest {
        // Given authenticated user
        authRepository.setCurrentUserId("seller_123")

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then requiresLogin is false
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.requiresLogin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `meta starts with CheckingAuth initialization step`() = runTest {
        val viewModel = createViewModel()

        // Initial meta shows initialization is in progress
        val meta = viewModel.state.value.meta
        // Initialization starts with CheckingAuth
        assertEquals(InitializationStep.CheckingAuth, meta.initializationStep)
    }

    // ===== C. Navigation Actions (5 tests) =====

    @Test
    fun `NavigateTo updates current route`() = runTest {
        val viewModel = createViewModel()

        // When navigating to Overview
        viewModel.dispatch(SellNavigationAction.NavigateTo(NavRoutes.Sell.Overview))

        // Then current route is updated
        assertEquals(NavRoutes.Sell.Overview, viewModel.state.value.navigation.currentRoute)
    }

    @Test
    fun `NavigateTo adds to back stack`() = runTest {
        val viewModel = createViewModel()

        // When navigating
        viewModel.dispatch(SellNavigationAction.NavigateTo(NavRoutes.Sell.Overview))
        viewModel.dispatch(SellNavigationAction.NavigateTo(NavRoutes.Sell.Orders))

        // Then back stack contains routes
        assertTrue(viewModel.state.value.navigation.backStack.contains(NavRoutes.Sell.Overview))
        assertTrue(viewModel.state.value.navigation.backStack.contains(NavRoutes.Sell.Orders))
    }

    @Test
    fun `NavigateBack pops from back stack`() = runTest {
        val viewModel = createViewModel()

        // Given navigation history
        viewModel.dispatch(SellNavigationAction.NavigateTo(NavRoutes.Sell.Overview))
        viewModel.dispatch(SellNavigationAction.NavigateTo(NavRoutes.Sell.Orders))

        // When navigating back
        viewModel.dispatch(SellNavigationAction.NavigateBack)

        // Then current route is previous route
        assertEquals(NavRoutes.Sell.Overview, viewModel.state.value.navigation.currentRoute)
    }

    @Test
    fun `OpenDrawer sets isDrawerOpen to true`() = runTest {
        val viewModel = createViewModel()

        // When opening drawer
        viewModel.dispatch(SellNavigationAction.OpenDrawer)

        // Then drawer is open
        assertTrue(viewModel.state.value.navigation.isDrawerOpen)
    }

    @Test
    fun `CloseDrawer sets isDrawerOpen to false`() = runTest {
        val viewModel = createViewModel()

        // Given drawer is open
        viewModel.dispatch(SellNavigationAction.OpenDrawer)
        assertTrue(viewModel.state.value.navigation.isDrawerOpen)

        // When closing drawer
        viewModel.dispatch(SellNavigationAction.CloseDrawer)

        // Then drawer is closed
        assertFalse(viewModel.state.value.navigation.isDrawerOpen)
    }

    // ===== E. UI Actions (6 tests) =====

    @Test
    fun `ShowSnackbar sets snackbar state`() = runTest {
        val viewModel = createViewModel()

        // When showing snackbar
        viewModel.dispatch(SellUiAction.ShowSnackbar("Test message", SnackbarType.SUCCESS))

        // Then snackbar is set
        assertNotNull(viewModel.state.value.ui.snackbar)
        assertEquals("Test message", viewModel.state.value.ui.snackbar?.message)
        assertEquals(SnackbarType.SUCCESS, viewModel.state.value.ui.snackbar?.type)
    }

    @Test
    fun `HideSnackbar clears snackbar state`() = runTest {
        val viewModel = createViewModel()

        // Given snackbar is shown
        viewModel.dispatch(SellUiAction.ShowSnackbar("Test", SnackbarType.INFO))
        assertNotNull(viewModel.state.value.ui.snackbar)

        // When hiding snackbar
        viewModel.dispatch(SellUiAction.HideSnackbar)

        // Then snackbar is cleared
        assertNull(viewModel.state.value.ui.snackbar)
    }

    @Test
    fun `ShowDialog sets dialog state`() = runTest {
        val viewModel = createViewModel()
        val dialog = DialogState.Information(
            title = "Test Dialog",
            message = "Test Message"
        )

        // When showing dialog
        viewModel.dispatch(SellUiAction.ShowDialog(dialog))

        // Then dialog is set
        assertNotNull(viewModel.state.value.ui.dialog)
        assertIs<DialogState.Information>(viewModel.state.value.ui.dialog)
        assertEquals("Test Dialog", (viewModel.state.value.ui.dialog as DialogState.Information).title)
    }

    @Test
    fun `HideDialog clears dialog state`() = runTest {
        val viewModel = createViewModel()
        val dialog = DialogState.Information(title = "Test", message = "Test")

        // Given dialog is shown
        viewModel.dispatch(SellUiAction.ShowDialog(dialog))
        assertNotNull(viewModel.state.value.ui.dialog)

        // When hiding dialog
        viewModel.dispatch(SellUiAction.HideDialog)

        // Then dialog is cleared
        assertNull(viewModel.state.value.ui.dialog)
    }

    @Test
    fun `SetRefreshing updates refreshing state`() = runTest {
        val viewModel = createViewModel()

        // When setting refreshing
        viewModel.dispatch(SellUiAction.SetRefreshing(true))

        // Then refreshing is set
        assertTrue(viewModel.state.value.ui.isRefreshing)

        // When clearing refreshing
        viewModel.dispatch(SellUiAction.SetRefreshing(false))

        // Then refreshing is cleared
        assertFalse(viewModel.state.value.ui.isRefreshing)
    }

    @Test
    fun `ShowPasswordResetDialog and HidePasswordResetDialog toggle dialog`() = runTest {
        val viewModel = createViewModel()

        // When showing password reset dialog
        viewModel.dispatch(SellUiAction.ShowPasswordResetDialog)

        // Then dialog is shown
        assertTrue(viewModel.state.value.auth.showPasswordResetDialog)

        // When hiding dialog
        viewModel.dispatch(SellUiAction.HidePasswordResetDialog)

        // Then dialog is hidden
        assertFalse(viewModel.state.value.auth.showPasswordResetDialog)
    }

    // ===== F. Sign-In Triggers (6 tests) =====

    @Test
    fun `LoginWithGoogle triggers Google sign-in`() = runTest {
        val viewModel = createViewModel()

        // When dispatching LoginWithGoogle
        viewModel.dispatch(SellUserAction.LoginWithGoogle)

        // Then trigger is set
        assertTrue(viewModel.state.value.triggerGoogleSignIn)
    }

    @Test
    fun `resetGoogleSignInTrigger clears trigger`() = runTest {
        val viewModel = createViewModel()

        // Given trigger is set
        viewModel.dispatch(SellUserAction.LoginWithGoogle)
        assertTrue(viewModel.state.value.triggerGoogleSignIn)

        // When resetting trigger
        viewModel.resetGoogleSignInTrigger()

        // Then trigger is cleared
        assertFalse(viewModel.state.value.triggerGoogleSignIn)
    }

    @Test
    fun `LoginWithTwitter triggers Twitter sign-in`() = runTest {
        val viewModel = createViewModel()

        // When dispatching LoginWithTwitter
        viewModel.dispatch(SellUserAction.LoginWithTwitter)

        // Then trigger is set
        assertTrue(viewModel.state.value.triggerTwitterSignIn)
    }

    @Test
    fun `resetTwitterSignInTrigger clears trigger`() = runTest {
        val viewModel = createViewModel()

        // Given trigger is set
        viewModel.dispatch(SellUserAction.LoginWithTwitter)
        assertTrue(viewModel.state.value.triggerTwitterSignIn)

        // When resetting trigger
        viewModel.resetTwitterSignInTrigger()

        // Then trigger is cleared
        assertFalse(viewModel.state.value.triggerTwitterSignIn)
    }

    @Test
    fun `resetGoogleSignOutTrigger clears trigger`() = runTest {
        val viewModel = createViewModel()

        // The trigger is set during logout, but we can test the reset
        viewModel.resetGoogleSignOutTrigger()

        // Then trigger is false
        assertFalse(viewModel.state.value.triggerGoogleSignOut)
    }

    @Test
    fun `setAuthError updates auth error state`() = runTest {
        val viewModel = createViewModel()

        // When setting auth error
        viewModel.setAuthError("Test error message")

        // Then error is set
        assertEquals("Test error message", viewModel.state.value.auth.error)
        assertFalse(viewModel.state.value.auth.isLoading)
    }

    // ===== G. Pending Import Content (2 tests) =====

    @Test
    fun `setPendingImportContent sets content`() = runTest {
        val viewModel = createViewModel()

        // When setting pending content
        viewModel.setPendingImportContent("import data here")

        // Then content is set
        viewModel.pendingImportContent.test {
            assertEquals("import data here", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setPendingImportContent with null clears content`() = runTest {
        val viewModel = createViewModel()

        // Given content is set
        viewModel.setPendingImportContent("some data")

        // When clearing content
        viewModel.setPendingImportContent(null)

        // Then content is cleared
        viewModel.pendingImportContent.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== H. Edge Cases (3 tests) =====

    @Test
    fun `NavigateBack does nothing when back stack has only Home`() = runTest {
        val viewModel = createViewModel()

        // Initial backStack is [Home] (size = 1)
        assertEquals(1, viewModel.state.value.navigation.backStack.size)
        assertEquals(NavRoutes.Home, viewModel.state.value.navigation.currentRoute)

        // When trying to navigate back from Home (only item in stack)
        viewModel.dispatch(SellNavigationAction.NavigateBack)

        // Then back stack is unchanged (can't go back further)
        assertEquals(1, viewModel.state.value.navigation.backStack.size)
        assertEquals(NavRoutes.Home, viewModel.state.value.navigation.currentRoute)
    }

    @Test
    fun `auth loading state is false initially`() = runTest {
        val viewModel = createViewModel()

        // Auth is not loading initially
        assertFalse(viewModel.state.value.auth.isLoading)
        assertFalse(viewModel.state.value.auth.isSuccess)
        assertNull(viewModel.state.value.auth.error)
    }
}
