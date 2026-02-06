package com.together.newverse.ui.state.buy

import app.cash.turbine.test
import com.together.newverse.domain.model.DraftBasket
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.test.TestData
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.InitializationStep
import com.together.newverse.ui.state.UserState
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
 * Unit tests for BuyAppViewModel initialization extension functions.
 *
 * Tests cover:
 * - checkAuthenticationStatus - session restoration and login screen
 * - initializeApp - full initialization flow
 * - observeAuthState - auth state transitions
 * - loadUserProfile - profile loading with auth provider info
 * - loadCurrentOrder - draft basket and order loading
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelInitializationTest {

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

    // ===== checkAuthenticationStatus Tests =====

    @Test
    fun `checkAuthenticationStatus restores persisted session`() = runTest {
        // Given user has valid persisted session
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

    @Test
    fun `checkAuthenticationStatus shows login when no session`() = runTest {
        // Given user has NO persisted session
        authRepository.setCurrentUserId(null)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user state should be NotAuthenticated
        viewModel.state.test {
            val state = awaitItem()
            assertIs<UserState.NotAuthenticated>(state.common.user)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkAuthenticationStatus handles auth failure gracefully`() = runTest {
        // Given auth check will fail
        authRepository.shouldFailCheckPersistedAuth = true
        authRepository.failureMessage = "Auth service unavailable"

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user should be NotAuthenticated (show login)
        viewModel.state.test {
            val state = awaitItem()
            assertIs<UserState.NotAuthenticated>(state.common.user)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== initializeApp Tests =====

    @Test
    fun `initializeApp completes full flow when authenticated`() = runTest {
        // Given user is authenticated with profile
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))

        // When ViewModel is created
        val viewModel = createViewModel()
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
    fun `initializeApp stops at auth when not authenticated`() = runTest {
        // Given user is NOT authenticated
        authRepository.setCurrentUserId(null)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then initialization should stop (waiting for user to login)
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.meta.isInitialized)
            assertFalse(state.meta.isInitializing)
            assertIs<UserState.NotAuthenticated>(state.common.user)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== observeAuthState Tests =====

    @Test
    fun `observeAuthState updates user state on sign in`() = runTest {
        // Given user is initially not authenticated
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Verify initial state
        viewModel.state.test {
            val state = awaitItem()
            assertIs<UserState.NotAuthenticated>(state.common.user)
            cancelAndIgnoreRemainingEvents()
        }

        // When user signs in
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_456"))
        authRepository.setCurrentUserId("buyer_456")
        advanceUntilIdle()

        // Then user state should update to LoggedIn
        viewModel.state.test {
            val state = awaitItem()
            assertIs<UserState.LoggedIn>(state.common.user)
            assertEquals("buyer_456", (state.common.user as UserState.LoggedIn).id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAuthState keeps NotAuthenticated until user chooses`() = runTest {
        // Given user is not authenticated
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then user should remain NotAuthenticated (not Guest)
        viewModel.state.test {
            val state = awaitItem()
            assertIs<UserState.NotAuthenticated>(state.common.user)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== loadUserProfile Tests =====

    @Test
    fun `loadUserProfile updates profile with auth provider info`() = runTest {
        // Given user is authenticated with profile
        authRepository.setCurrentUserId("buyer_123")
        val profile = TestData.sampleBuyerProfiles[0].copy(
            id = "buyer_123",
            displayName = "Test User",
            emailAddress = "test@example.com"
        )
        profileRepository.setBuyerProfile(profile)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then profile should be loaded
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.screens.customerProfile.profile)
            assertEquals("Test User", state.screens.customerProfile.profile?.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== loadCurrentOrder Tests =====

    @Test
    fun `loadCurrentOrder loads draft basket when present`() = runTest {
        // Given user has a draft basket
        authRepository.setCurrentUserId("buyer_123")
        val draftItems = TestData.sampleOrderedProducts.take(2)
        val profile = TestData.sampleBuyerProfiles[0].copy(
            id = "buyer_123",
            draftBasket = DraftBasket(
                items = draftItems,
                selectedPickupDate = null,
                lastModified = System.currentTimeMillis()
            )
        )
        profileRepository.setBuyerProfile(profile)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then draft basket should be loaded
        val basketItems = basketRepository.observeBasket().value
        assertEquals(2, basketItems.size)
    }

    @Test
    fun `loadCurrentOrder loads upcoming order when no draft`() = runTest {
        // Given user has placed orders but no draft
        authRepository.setCurrentUserId("buyer_123")
        val order = TestData.sampleOrders[0].copy(
            id = "order_123",
            status = OrderStatus.PLACED,
            // Set pickup date to future
            pickUpDate = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
        )
        orderRepository.setOrders(listOf(order))
        val profile = TestData.sampleBuyerProfiles[0].copy(
            id = "buyer_123",
            draftBasket = null,
            placedOrderIds = mapOf("20240101" to "order_123")
        )
        profileRepository.setBuyerProfile(profile)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then order items should be loaded into basket
        // Note: This depends on the order being found and loaded
        viewModel.state.test {
            val state = awaitItem()
            // The basket state should have order info
            assertNotNull(state.common.basket.currentOrderId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadCurrentOrder handles no orders gracefully`() = runTest {
        // Given user has no orders or draft
        authRepository.setCurrentUserId("buyer_123")
        val profile = TestData.sampleBuyerProfiles[0].copy(
            id = "buyer_123",
            draftBasket = null,
            placedOrderIds = emptyMap()
        )
        profileRepository.setBuyerProfile(profile)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then basket should be empty
        val basketItems = basketRepository.observeBasket().value
        assertTrue(basketItems.isEmpty())

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.common.basket.currentOrderId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Integration Tests =====

    @Test
    fun `full initialization flow loads profile and order`() = runTest {
        // Given user is authenticated with profile and order
        authRepository.setCurrentUserId("buyer_123")
        val order = TestData.sampleOrders[0].copy(
            id = "order_123",
            status = OrderStatus.PLACED,
            pickUpDate = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
            articles = TestData.sampleOrderedProducts.take(2)
        )
        orderRepository.setOrders(listOf(order))
        val profile = TestData.sampleBuyerProfiles[0].copy(
            id = "buyer_123",
            displayName = "Full Init User",
            placedOrderIds = mapOf("20240101" to "order_123")
        )
        profileRepository.setBuyerProfile(profile)

        // When ViewModel is created
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            // User is logged in
            assertIs<UserState.LoggedIn>(state.common.user)
            // Profile is loaded
            assertNotNull(state.screens.customerProfile.profile)
            assertEquals("Full Init User", state.screens.customerProfile.profile?.displayName)
            // Initialization is complete
            assertTrue(state.meta.isInitialized)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
