package com.together.newverse.ui.state.buy

import app.cash.turbine.test
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeBasketRepository
import com.together.newverse.test.FakeOrderRepository
import com.together.newverse.test.FakeProfileRepository
import com.together.newverse.test.MainDispatcherRule
import com.together.newverse.test.TestData
import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.UnifiedNavigationAction
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
 * Unit tests for BuyAppViewModel navigation extension functions.
 *
 * Tests cover:
 * - Route navigation (navigateTo)
 * - Back navigation (navigateBack)
 * - Back stack management
 * - Drawer open/close
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuyAppViewModelNavigationTest {

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

    // ===== NavigateTo Tests =====

    @Test
    fun `navigateTo updates currentRoute`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Profile))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(NavRoutes.Buy.Profile, state.common.navigation.currentRoute)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigateTo adds to backStack`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Profile))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.navigation.backStack.contains(NavRoutes.Buy.Profile))
            assertEquals(2, state.common.navigation.backStack.size) // Home + Profile
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigateTo stores previousRoute`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Profile))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(NavRoutes.Home, state.common.navigation.previousRoute)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigateTo multiple routes updates backStack correctly`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Profile))
        advanceUntilIdle()
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Basket))
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(NavRoutes.Buy.Basket, state.common.navigation.currentRoute)
            assertEquals(NavRoutes.Buy.Profile, state.common.navigation.previousRoute)
            assertEquals(3, state.common.navigation.backStack.size) // Home + Profile + Basket
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== NavigateBack Tests =====

    @Test
    fun `navigateBack pops from backStack`() = runTest {
        // Given navigated forward
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Profile))
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedNavigationAction.NavigateBack)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(1, state.common.navigation.backStack.size)
            assertFalse(state.common.navigation.backStack.contains(NavRoutes.Buy.Profile))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigateBack updates currentRoute to previous`() = runTest {
        // Given navigated forward
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Profile))
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedNavigationAction.NavigateBack)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(NavRoutes.Home, state.common.navigation.currentRoute)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigateBack does nothing when backStack has one item`() = runTest {
        // Given only at Home (initial state)
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When trying to navigate back from initial state
        viewModel.dispatch(UnifiedNavigationAction.NavigateBack)
        advanceUntilIdle()

        // Then state should remain unchanged
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(NavRoutes.Home, state.common.navigation.currentRoute)
            assertEquals(1, state.common.navigation.backStack.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigateBack from deep navigation works correctly`() = runTest {
        // Given deep navigation
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Profile))
        advanceUntilIdle()
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Basket))
        advanceUntilIdle()

        // When navigating back twice
        viewModel.dispatch(UnifiedNavigationAction.NavigateBack)
        advanceUntilIdle()
        viewModel.dispatch(UnifiedNavigationAction.NavigateBack)
        advanceUntilIdle()

        // Then should be back at Home
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(NavRoutes.Home, state.common.navigation.currentRoute)
            assertEquals(1, state.common.navigation.backStack.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Drawer Tests =====

    @Test
    fun `openDrawer sets isDrawerOpen true`() = runTest {
        // Given
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedNavigationAction.OpenDrawer)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.navigation.isDrawerOpen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `closeDrawer sets isDrawerOpen false`() = runTest {
        // Given drawer is open
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedNavigationAction.OpenDrawer)
        advanceUntilIdle()

        // When
        viewModel.dispatch(UnifiedNavigationAction.CloseDrawer)
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.common.navigation.isDrawerOpen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `drawer state is independent of navigation`() = runTest {
        // Given drawer is open
        authRepository.setCurrentUserId("buyer_123")
        profileRepository.setBuyerProfile(TestData.sampleBuyerProfiles[0].copy(id = "buyer_123"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.dispatch(UnifiedNavigationAction.OpenDrawer)
        advanceUntilIdle()

        // When navigating
        viewModel.dispatch(UnifiedNavigationAction.NavigateTo(NavRoutes.Buy.Profile))
        advanceUntilIdle()

        // Then drawer should still be open
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.common.navigation.isDrawerOpen)
            assertEquals(NavRoutes.Buy.Profile, state.common.navigation.currentRoute)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
