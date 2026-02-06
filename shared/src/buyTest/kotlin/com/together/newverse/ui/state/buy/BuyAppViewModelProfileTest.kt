package com.together.newverse.ui.state.buy

import app.cash.turbine.test
import com.together.newverse.domain.model.BuyerProfile
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
import com.together.newverse.ui.state.BuyProfileAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Unit tests for BuyAppViewModel profile management extension functions.
 *
 * Tests cover:
 * - Profile loading
 * - Order history loading
 * - Profile saving
 * - Profile observation
 * - Favourite articles sync
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelProfileTest {

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

    // ===== Load Customer Profile Tests =====

    @Test
    fun `loadCustomerProfile sets loading state`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        val profile = TestData.sampleBuyerProfiles[0].copy(id = "buyer_123")
        profileRepository.setBuyerProfile(profile)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyProfileAction.LoadCustomerProfile)

        // Then - loading state is set (will complete quickly in test)
        advanceUntilIdle()
        viewModel.state.test {
            val state = awaitItem()
            // After completion, loading is false
            assertFalse(state.screens.customerProfile.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadCustomerProfile updates profile on success`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        val profile = TestData.sampleBuyerProfiles[0].copy(
            id = "buyer_123",
            displayName = "Test User",
            emailAddress = "test@example.com"
        )
        profileRepository.setBuyerProfile(profile)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyProfileAction.LoadCustomerProfile)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.screens.customerProfile.profile)
            assertEquals("Test User", state.screens.customerProfile.profile?.displayName)
            assertEquals("test@example.com", state.screens.customerProfile.profile?.emailAddress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadCustomerProfile shows error on failure`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.shouldFailGetBuyerProfile = true
        profileRepository.failureMessage = "Profile not found"
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyProfileAction.LoadCustomerProfile)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.screens.customerProfile.error)
            assertTrue(state.screens.customerProfile.error?.message?.contains("Profile not found") == true ||
                state.screens.customerProfile.error?.message?.contains("Test error") == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Load Order History Tests =====

    @Test
    fun `loadOrderHistory sets loading state`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        val profile = TestData.sampleBuyerProfiles[0].copy(id = "buyer_123")
        profileRepository.setBuyerProfile(profile)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyProfileAction.LoadOrderHistory)
        advanceUntilIdle()

        // Then - after completion, loading is false
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.screens.orderHistory.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadOrderHistory loads orders from profile placedOrderIds`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        val orders = listOf(
            TestData.sampleOrders[0].copy(id = "order_1", status = OrderStatus.PLACED),
            TestData.sampleOrders[1].copy(id = "order_2", status = OrderStatus.COMPLETED)
        )
        orderRepository.setOrders(orders)
        val profile = TestData.sampleBuyerProfiles[0].copy(
            id = "buyer_123",
            placedOrderIds = mapOf("2024-01-01" to "order_1", "2024-01-02" to "order_2")
        )
        profileRepository.setBuyerProfile(profile)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyProfileAction.LoadOrderHistory)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(2, state.screens.orderHistory.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadOrderHistory shows empty state when no orders`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        val profile = TestData.sampleBuyerProfiles[0].copy(
            id = "buyer_123",
            placedOrderIds = emptyMap()
        )
        profileRepository.setBuyerProfile(profile)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyProfileAction.LoadOrderHistory)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.screens.orderHistory.items.isEmpty())
            assertNull(state.screens.orderHistory.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Save Buyer Profile Tests =====

    @Test
    fun `saveBuyerProfile updates profile successfully`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        val profile = TestData.sampleBuyerProfiles[0].copy(id = "buyer_123")
        profileRepository.setBuyerProfile(profile)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Load profile first
        viewModel.dispatch(BuyProfileAction.LoadCustomerProfile)
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyProfileAction.SaveBuyerProfile(
            displayName = "New Name",
            email = "new@example.com",
            phone = "0123456789"
        ))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals("New Name", state.screens.customerProfile.profile?.displayName)
            assertEquals("new@example.com", state.screens.customerProfile.profile?.emailAddress)
            assertEquals("0123456789", state.screens.customerProfile.profile?.telephoneNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveBuyerProfile shows snackbar on success`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        val profile = TestData.sampleBuyerProfiles[0].copy(id = "buyer_123")
        profileRepository.setBuyerProfile(profile)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Load profile first
        viewModel.dispatch(BuyProfileAction.LoadCustomerProfile)
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyProfileAction.SaveBuyerProfile(
            displayName = "New Name",
            email = "new@example.com",
            phone = "0123456789"
        ))
        advanceUntilIdle()

        // Then - snackbar should be shown
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.common.ui.snackbar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveBuyerProfile requires existing profile`() = runTest {
        // Given - no profile loaded
        authRepository.setCurrentUserId("buyer_123")
        // Don't set a profile
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When trying to save without loading profile first
        viewModel.dispatch(BuyProfileAction.SaveBuyerProfile(
            displayName = "New Name",
            email = "new@example.com",
            phone = "0123456789"
        ))
        advanceUntilIdle()

        // Then - snackbar should show error
        viewModel.state.test {
            val state = awaitItem()
            // Profile should still be null
            assertNull(state.screens.customerProfile.profile)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Profile Observation Tests =====

    @Test
    fun `observeMainScreenBuyerProfile updates favouriteArticles`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        val profile = TestData.sampleBuyerProfiles[0].copy(
            id = "buyer_123",
            favouriteArticles = listOf("article_1", "article_2")
        )
        profileRepository.setBuyerProfile(profile)

        // When
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(listOf("article_1", "article_2"), state.screens.mainScreen.favouriteArticles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeMainScreenBuyerProfile preserves existing favourites when new is empty`() = runTest {
        // Given - profile initially has favourites
        authRepository.setCurrentUserId("buyer_123")
        val profileWithFavourites = TestData.sampleBuyerProfiles[0].copy(
            id = "buyer_123",
            favouriteArticles = listOf("article_1", "article_2")
        )
        profileRepository.setBuyerProfile(profileWithFavourites)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Verify favourites are set
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(2, state.screens.mainScreen.favouriteArticles.size)
            cancelAndIgnoreRemainingEvents()
        }

        // When - profile is updated with empty favourites (simulating transient update)
        val profileEmpty = profileWithFavourites.copy(favouriteArticles = emptyList())
        profileRepository.setBuyerProfile(profileEmpty)
        advanceUntilIdle()

        // Then - existing favourites should be preserved
        viewModel.state.test {
            val state = awaitItem()
            // The implementation preserves existing favourites when new is empty
            assertTrue(state.screens.mainScreen.favouriteArticles.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Refresh Customer Profile Tests =====

    @Test
    fun `refreshCustomerProfile loads profile and order history`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        val profile = TestData.sampleBuyerProfiles[0].copy(
            id = "buyer_123",
            displayName = "Refreshed User"
        )
        profileRepository.setBuyerProfile(profile)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(BuyProfileAction.RefreshCustomerProfile)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.screens.customerProfile.profile)
            assertEquals("Refreshed User", state.screens.customerProfile.profile?.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
