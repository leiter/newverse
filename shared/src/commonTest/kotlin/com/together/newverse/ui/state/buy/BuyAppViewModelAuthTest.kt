package com.together.newverse.ui.state.buy

import app.cash.turbine.test
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.test.TestData
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.UnifiedAccountAction
import com.together.newverse.ui.state.UnifiedUserAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for BuyAppViewModel authentication extension functions.
 *
 * Note: Auth tests that use getString(Res.string.*) cannot run as unit tests due to
 * Android Resources not being available. This test file focuses on state-only tests
 * that don't require resource strings.
 *
 * Tests cover:
 * - Social login triggers (Google, Twitter, Apple)
 * - Account linking
 * - Dialog state management (logout, link account, delete account, email linking)
 * - Continue as guest state changes
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelAuthTest {

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

    // ===== Social Login Trigger Tests =====

    @Test
    fun `loginWithGoogle sets triggerGoogleSignIn true`() = runTest {
        // Given
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedUserAction.LoginWithGoogle)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.triggerGoogleSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loginWithTwitter sets triggerTwitterSignIn true`() = runTest {
        // Given
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedUserAction.LoginWithTwitter)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.triggerTwitterSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loginWithApple sets triggerAppleSignIn true`() = runTest {
        // Given
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedUserAction.LoginWithApple)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.triggerAppleSignIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Linking Account Tests =====

    @Test
    fun `linkWithGoogle sets isLinkingAccount true and triggers Google`() = runTest {
        // Given
        authRepository.setCurrentUserId("guest_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "guest_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.LinkWithGoogle)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.triggerGoogleSignIn)
            assertTrue(state.screens.customerProfile.isLinkingAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Logout Warning Dialog Tests =====

    @Test
    fun `showLogoutWarningDialog sets showLogoutWarningDialog true`() = runTest {
        // Given
        authRepository.setCurrentUserId("guest_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "guest_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.ShowLogoutWarning)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.screens.customerProfile.showLogoutWarningDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissLogoutWarningDialog sets showLogoutWarningDialog false`() = runTest {
        // Given
        authRepository.setCurrentUserId("guest_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "guest_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedAccountAction.ShowLogoutWarning)
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.DismissLogoutWarning)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.screens.customerProfile.showLogoutWarningDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Link Account Dialog Tests =====

    @Test
    fun `showLinkAccountDialog sets showLinkAccountDialog true`() = runTest {
        // Given
        authRepository.setCurrentUserId("guest_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "guest_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.ShowLinkAccountDialog)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.screens.customerProfile.showLinkAccountDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissLinkAccountDialog sets showLinkAccountDialog false`() = runTest {
        // Given
        authRepository.setCurrentUserId("guest_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "guest_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedAccountAction.ShowLinkAccountDialog)
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.DismissLinkAccountDialog)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.screens.customerProfile.showLinkAccountDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Delete Account Dialog Tests =====

    @Test
    fun `showDeleteAccountDialog sets showDeleteAccountDialog true`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.ShowDeleteAccountDialog)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.screens.customerProfile.showDeleteAccountDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissDeleteAccountDialog sets showDeleteAccountDialog false`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedAccountAction.ShowDeleteAccountDialog)
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.DismissDeleteAccountDialog)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.screens.customerProfile.showDeleteAccountDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Email Linking Dialog Tests =====

    @Test
    fun `showEmailLinkingDialog sets showEmailLinkingDialog true`() = runTest {
        // Given
        authRepository.setCurrentUserId("guest_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "guest_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.ShowEmailLinkingDialog)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.screens.customerProfile.showEmailLinkingDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissEmailLinkingDialog clears email linking fields`() = runTest {
        // Given
        authRepository.setCurrentUserId("guest_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "guest_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedAccountAction.ShowEmailLinkingDialog)
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.DismissEmailLinkingDialog)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.screens.customerProfile.showEmailLinkingDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateEmailLinkingEmail updates email field`() = runTest {
        // Given
        authRepository.setCurrentUserId("guest_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "guest_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedAccountAction.ShowEmailLinkingDialog)
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.UpdateEmailLinkingEmail("test@example.com"))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals("test@example.com", state.screens.customerProfile.emailLinkingEmail)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateEmailLinkingPassword updates password field`() = runTest {
        // Given
        authRepository.setCurrentUserId("guest_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "guest_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedAccountAction.ShowEmailLinkingDialog)
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.UpdateEmailLinkingPassword("password123"))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals("password123", state.screens.customerProfile.emailLinkingPassword)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateEmailLinkingConfirmPassword updates confirm field`() = runTest {
        // Given
        authRepository.setCurrentUserId("guest_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "guest_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedAccountAction.ShowEmailLinkingDialog)
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedAccountAction.UpdateEmailLinkingConfirmPassword("password123"))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals("password123", state.screens.customerProfile.emailLinkingConfirmPassword)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
